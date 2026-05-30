package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.AccountSpending
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.repository.ExpenseEntryRepository
import kotlinx.coroutines.launch

// Only change from the Room version: userId/categoryId/accountId parameters
// are now String instead of Int. All business logic is identical.
//[1] Google, "Save data in a local database using Room," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/data-storage/room.
// [Accessed: 28 Apr. 2026].
//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
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
//[7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/lifecycle.
//[26] 	A. Mamo, "Android Room and Firebase in a MVVM Architecture - Offline First Application," Stack Exchange Inc, 18 November 2022. [Online].
// Available: https://stackoverflow.com/questions/74484239/android-room-and-firebase-in-a-mvvm-architecture-offline-first-application.
// [Accessed 30 May 2026].

class ExpenseEntryViewModel(private val expenseEntryRepository: ExpenseEntryRepository) : ViewModel() {

    private val _entries = MutableLiveData<List<ExpenseEntry>>()
    val entries: LiveData<List<ExpenseEntry>> get() = _entries

    private val _categoryTotal = MutableLiveData<Double>()
    val categoryTotal: LiveData<Double> get() = _categoryTotal

    private val _accountSpending = MutableLiveData<List<AccountSpending>>()
    val accountSpending: LiveData<List<AccountSpending>> get() = _accountSpending

    fun getAllEntriesLive(userId: String): LiveData<List<ExpenseEntry>> =
        expenseEntryRepository.getAllEntriesLive(userId)

    fun getEntriesForPeriodLive(userId: String, startDate: String, endDate: String): LiveData<List<ExpenseEntry>> =
        expenseEntryRepository.getEntriesForPeriodLive(userId, startDate, endDate)

    fun getIncomeLive(userId: String, startDate: String, endDate: String): LiveData<Double> =
        expenseEntryRepository.getIncomeLive(userId, startDate, endDate)

    fun getExpensesLive(userId: String, startDate: String, endDate: String): LiveData<Double> =
        expenseEntryRepository.getExpensesLive(userId, startDate, endDate)

    fun loadEntriesForPeriod(userId: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            _entries.value = expenseEntryRepository.getEntriesForPeriod(userId, startDate, endDate)
        }
    }

    fun addEntry(entry: ExpenseEntry, startDate: String, endDate: String) {
        viewModelScope.launch {
            expenseEntryRepository.addEntry(entry)
            loadEntriesForPeriod(entry.userId, startDate, endDate)
        }
    }

    fun deleteEntry(entry: ExpenseEntry, userId: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            expenseEntryRepository.deleteEntry(entry)
            loadEntriesForPeriod(userId, startDate, endDate)
        }
    }

    fun loadTotalForCategory(userId: String, categoryId: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            _categoryTotal.value = expenseEntryRepository.getTotalForCategory(userId, categoryId, startDate, endDate)
        }
    }

    fun loadExpensesByAccount(userId: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            _accountSpending.value = expenseEntryRepository.getExpensesByAccount(userId, startDate, endDate)
        }
    }
}