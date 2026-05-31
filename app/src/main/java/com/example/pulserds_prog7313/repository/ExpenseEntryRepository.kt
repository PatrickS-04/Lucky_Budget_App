package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pulserds_prog7313.data.AccountSpending
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

// ExpenseEntryRepository now reads/writes to Firestore collection "expense_entries".
// Date filtering is done client-side after fetching -- Firestore doesn't support
// complex date range queries on string-formatted dates directly.
//[1] Google, "Save data in a local database using Room," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/data-storage/room.
// [Accessed: 28 Apr. 2026].
//[3] Google, "LiveData overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/livedata.
// [Accessed: 28 Apr. 2026].
//[4] JetBrains, "Coroutines guide," Kotlin Documentation, 2024. [Online].
// Available: https://kotlinlang.org/docs/coroutines-guide.html.
// [Accessed: 28 Apr. 2026].
//[5] Google, "Guide to app architecture," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/architecture.
// [Accessed: 28 Apr. 2026].
//[6] Google, AndroidX Room, ver. 2.7.0-alpha11. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/room.
//[8] JetBrains, kotlinx-coroutines-android, ver. 1.8.1. [Software]. JetBrains, 2024.
// Available: https://github.com/Kotlin/kotlinx.coroutines.
//[14] Google, "KSP quickstart," Android Developers, 2024. [Online].
// Available: https://developer.android.com/build/migrate-to-ksp.
// [Accessed: 28 Apr. 2026].
//[25] 	F. van Puffelen, "Save a Room Database file in Firebase," Stack Exchange Inc, 22 August 2021. [Online].
// Available: https://stackoverflow.com/questions/68878022/save-a-room-database-file-in-firebase.
// [Accessed 30 May 2026].

class ExpenseEntryRepository {

    private val db  = FirebaseFirestore.getInstance()
    private val col get() = db.collection("expense_entries")

    // -- Write operations ------------------------------------------------------

    suspend fun addEntry(entry: ExpenseEntry) {
        val ref = col.add(entry).await()
        col.document(ref.id).update("id", ref.id).await()
    }

    suspend fun deleteEntry(entry: ExpenseEntry) {
        col.document(entry.id).delete().await()
    }

    // -- One-shot fetch helpers ------------------------------------------------

    // Fetches all entries for a user within a date range.
    // Dates are stored as "dd/MM/yyyy" strings; filtering is done in-memory.
    suspend fun getEntriesForPeriod(
        userId: String,
        startDate: String,
        endDate: String
    ): List<ExpenseEntry> {
        return getAllEntriesOnce(userId).filter { entry ->
            isDateInRange(entry.date, startDate, endDate)
        }
    }

    // Returns the sum of amounts for a specific category within a date range.
    suspend fun getTotalForCategory(
        userId: String,
        categoryId: String,
        startDate: String,
        endDate: String
    ): Double {
        return getEntriesForPeriod(userId, startDate, endDate)
            .filter { it.categoryId == categoryId }
            .sumOf { it.amount }
    }

    // Fetches all entries for a user as a one-shot snapshot, newest first.
    suspend fun getAllEntriesOnce(userId: String): List<ExpenseEntry> {
        val snapshot = col.whereEqualTo("userId", userId).get().await()
        return snapshot.documents
            .mapNotNull { doc -> doc.toObject(ExpenseEntry::class.java)?.copy(id = doc.id) }
            .sortedByDescending { normaliseDateForSort(it.date) }
    }

    suspend fun getTotalIncome(userId: String): Double =
        getAllEntriesOnce(userId).filter { it.amount > 0 }.sumOf { it.amount }

    suspend fun getTotalExpenses(userId: String): Double =
        getAllEntriesOnce(userId).filter { it.amount < 0 }.sumOf { it.amount }

    suspend fun getExpensesByAccount(
        userId: String,
        startDate: String,
        endDate: String
    ): List<AccountSpending> {
        return getEntriesForPeriod(userId, startDate, endDate)
            .filter { it.amount < 0 }
            .groupBy { it.bankAccountId }
            .map { (accountId, entries) ->
                AccountSpending(accountId, entries.sumOf { abs(it.amount) })
            }
    }

    // -- LiveData streams ------------------------------------------------------

    // Observes all entries for a user as a LiveData stream.
    fun getAllEntriesLive(userId: String): LiveData<List<ExpenseEntry>> {
        val liveData = MutableLiveData<List<ExpenseEntry>>()
        col.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents
                        .mapNotNull { doc -> doc.toObject(ExpenseEntry::class.java)?.copy(id = doc.id) }
                        .sortedByDescending { normaliseDateForSort(it.date) }
                    liveData.postValue(list)
                }
            }
        return liveData
    }

    // Observes entries filtered to a date range as a LiveData stream.
    fun getEntriesForPeriodLive(
        userId: String,
        startDate: String,
        endDate: String
    ): LiveData<List<ExpenseEntry>> {
        val liveData = MutableLiveData<List<ExpenseEntry>>()
        col.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents
                        .mapNotNull { doc -> doc.toObject(ExpenseEntry::class.java)?.copy(id = doc.id) }
                        .filter { isDateInRange(it.date, startDate, endDate) }
                        .sortedByDescending { normaliseDateForSort(it.date) }
                    liveData.postValue(list)
                }
            }
        return liveData
    }

    // Observes the income total for a period as LiveData.
    fun getIncomeLive(userId: String, startDate: String, endDate: String): LiveData<Double> {
        val liveData = MutableLiveData<Double>()
        col.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val total = snapshot.documents
                        .mapNotNull { doc -> doc.toObject(ExpenseEntry::class.java) }
                        .filter { it.amount > 0 && isDateInRange(it.date, startDate, endDate) }
                        .sumOf { it.amount }
                    liveData.postValue(total)
                }
            }
        return liveData
    }

    // Observes the expense total for a period as LiveData.
    fun getExpensesLive(userId: String, startDate: String, endDate: String): LiveData<Double> {
        val liveData = MutableLiveData<Double>()
        col.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val total = snapshot.documents
                        .mapNotNull { doc -> doc.toObject(ExpenseEntry::class.java) }
                        .filter { it.amount < 0 && isDateInRange(it.date, startDate, endDate) }
                        .sumOf { it.amount }
                    liveData.postValue(total)
                }
            }
        return liveData
    }

    // -- Date helpers ----------------------------------------------------------

    // Converts "dd/MM/yyyy" or "yyyy-MM-dd" to "yyyy/MM/dd" for lexicographic comparison.
    private fun normaliseDateForSort(date: String): String {
        return if (date.length >= 5 && (date[4] == '-' || date[4] == '/')) {
            // Already yyyy-MM-dd or yyyy/MM/dd
            date.replace('-', '/')
        } else if (date.length == 10) {
            // dd/MM/yyyy -> yyyy/MM/dd
            "${date.substring(6)}/${date.substring(3, 5)}/${date.substring(0, 2)}"
        } else date
    }

    // Returns true if the entry date falls within [startDate, endDate] inclusive.
    // All three dates must be in "dd/MM/yyyy" format.
    private fun isDateInRange(date: String, startDate: String, endDate: String): Boolean {
        return try {
            val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            fmt.isLenient = false
            val d     = fmt.parse(date)     ?: return false
            val start = fmt.parse(startDate) ?: return false
            val end   = fmt.parse(endDate)   ?: return false
            !d.before(start) && !d.after(end)
        } catch (e: Exception) {
            false
        }
    }
}