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