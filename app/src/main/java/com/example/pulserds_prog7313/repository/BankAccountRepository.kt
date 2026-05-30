package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pulserds_prog7313.data.BankAccount
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// BankAccountRepository now reads/writes to Firestore collection "bank_accounts".
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

class BankAccountRepository {

    private val db  = FirebaseFirestore.getInstance()
    private val col get() = db.collection("bank_accounts")

    // -- LiveData stream -------------------------------------------------------

    // Observes all accounts for a user. Updates automatically when Firestore changes.
    fun getAccountsForUser(userId: String): LiveData<List<BankAccount>> {
        val liveData = MutableLiveData<List<BankAccount>>()
        col.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents
                        .mapNotNull { doc -> doc.toObject(BankAccount::class.java)?.copy(id = doc.id) }
                        .sortedWith(compareByDescending<BankAccount> { it.isPrimary }.thenByDescending { it.id })
                    liveData.postValue(list)
                }
            }
        return liveData
    }

    // -- One-shot fetches ------------------------------------------------------

    suspend fun getAccountsOnce(userId: String): List<BankAccount> {
        val snapshot = col.whereEqualTo("userId", userId).get().await()
        return snapshot.documents
            .mapNotNull { doc -> doc.toObject(BankAccount::class.java)?.copy(id = doc.id) }
            .sortedWith(compareByDescending<BankAccount> { it.isPrimary }.thenByDescending { it.id })
    }

    suspend fun getById(accountId: String): BankAccount? {
        val doc = col.document(accountId).get().await()
        return doc.toObject(BankAccount::class.java)?.copy(id = doc.id)
    }

    // -- Write operations ------------------------------------------------------

    // Inserts a new account. If isPrimary is true, clears the flag on all
    // existing accounts first so only one account is ever marked primary.
    // Returns the Firestore-generated document ID.
    suspend fun addAccount(account: BankAccount): String {
        if (account.isPrimary) clearPrimaryFlag(account.userId)
        val ref = col.add(account).await()
        col.document(ref.id).update("id", ref.id).await()
        return ref.id
    }

    suspend fun updateAccount(account: BankAccount) {
        col.document(account.id).set(account).await()
    }

    suspend fun deleteAccount(account: BankAccount) {
        col.document(account.id).delete().await()
    }

    // -- Aggregates ------------------------------------------------------------

    suspend fun getTotalBalance(userId: String): Double {
        return getAccountsOnce(userId)
            .filter { it.includeInTotal }
            .sumOf { it.balance }
    }

    // Atomically adds [delta] to an account's balance using a Firestore transaction.
    // Positive delta = credit (income), negative delta = debit (expense).
    suspend fun adjustBalance(accountId: String, delta: Double) {
        val ref = col.document(accountId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val current  = snapshot.getDouble("balance") ?: 0.0
            transaction.update(ref, "balance", current + delta)
        }.await()
    }

    // -- Helpers ---------------------------------------------------------------

    // Clears the isPrimary flag on every account for this user.
    private suspend fun clearPrimaryFlag(userId: String) {
        val accounts = getAccountsOnce(userId).filter { it.isPrimary }
        for (account in accounts) {
            col.document(account.id).update("isPrimary", false).await()
        }
    }
}