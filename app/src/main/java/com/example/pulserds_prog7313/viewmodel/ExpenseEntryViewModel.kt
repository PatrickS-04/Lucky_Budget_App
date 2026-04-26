package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.repository.ExpenseEntryRepository
import kotlinx.coroutines.launch

class ExpenseEntryViewModel(private val expenseEntryRepository: ExpenseEntryRepository) : ViewModel() {

    private val _entries = MutableLiveData<List<ExpenseEntry>>()
    val entries: LiveData<List<ExpenseEntry>> get() = _entries

    private val _categoryTotal = MutableLiveData<Double>()
    val categoryTotal: LiveData<Double> get() = _categoryTotal

    /** Observe all entries for a user as a LiveData stream */
    fun getAllEntriesLive(userId: Int): LiveData<List<ExpenseEntry>> =
        expenseEntryRepository.getAllEntriesLive(userId)

    /** Observe entries filtered by date range as LiveData */
    fun getEntriesForPeriodLive(userId: Int, startDate: String, endDate: String): LiveData<List<ExpenseEntry>> =
        expenseEntryRepository.getEntriesForPeriodLive(userId, startDate, endDate)

    /** LiveData income total for a period */
    fun getIncomeLive(userId: Int, startDate: String, endDate: String): LiveData<Double> =
        expenseEntryRepository.getIncomeLive(userId, startDate, endDate)

    /** LiveData expenses total for a period */
    fun getExpensesLive(userId: Int, startDate: String, endDate: String): LiveData<Double> =
        expenseEntryRepository.getExpensesLive(userId, startDate, endDate)

    fun loadEntriesForPeriod(userId: Int, startDate: String, endDate: String) {
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

    fun deleteEntry(entry: ExpenseEntry, userId: Int, startDate: String, endDate: String) {
        viewModelScope.launch {
            expenseEntryRepository.deleteEntry(entry)
            loadEntriesForPeriod(userId, startDate, endDate)
        }
    }

    fun loadTotalForCategory(userId: Int, categoryId: Int, startDate: String, endDate: String) {
        viewModelScope.launch {
            _categoryTotal.value = expenseEntryRepository.getTotalForCategory(userId, categoryId, startDate, endDate)
        }
    }
}
