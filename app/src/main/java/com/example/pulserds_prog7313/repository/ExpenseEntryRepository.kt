package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.data.ExpenseEntryDao

// Repository pattern as recommended by the Android Guide to App Architecture.
// [5] Google, "Guide to app architecture," Android Developers, 2024.
class ExpenseEntryRepository(private val expenseEntryDao: ExpenseEntryDao) {

    suspend fun addEntry(entry: ExpenseEntry) {
        expenseEntryDao.insert(entry)
    }

    suspend fun deleteEntry(entry: ExpenseEntry) {
        expenseEntryDao.delete(entry)
    }

    suspend fun getEntriesForPeriod(userId: Int, startDate: String, endDate: String): List<ExpenseEntry> {
        return expenseEntryDao.getEntriesForPeriod(userId, startDate, endDate)
    }

    suspend fun getTotalForCategory(userId: Int, categoryId: Int, startDate: String, endDate: String): Double {
        return expenseEntryDao.getTotalForCategory(userId, categoryId, startDate, endDate)
    }

    fun getAllEntriesLive(userId: Int): LiveData<List<ExpenseEntry>> =
        expenseEntryDao.getAllEntriesLive(userId)

    suspend fun getAllEntriesOnce(userId: Int): List<ExpenseEntry> =
        expenseEntryDao.getAllEntriesOnce(userId)

    fun getEntriesForPeriodLive(userId: Int, startDate: String, endDate: String): LiveData<List<ExpenseEntry>> =
        expenseEntryDao.getEntriesForPeriodLive(userId, startDate, endDate)

    fun getIncomeLive(userId: Int, startDate: String, endDate: String): LiveData<Double> =
        expenseEntryDao.getIncomeLive(userId, startDate, endDate)

    fun getExpensesLive(userId: Int, startDate: String, endDate: String): LiveData<Double> =
        expenseEntryDao.getExpensesLive(userId, startDate, endDate)

    suspend fun getTotalIncome(userId: Int): Double = expenseEntryDao.getTotalIncome(userId)

    suspend fun getTotalExpenses(userId: Int): Double = expenseEntryDao.getTotalExpenses(userId)

    suspend fun getExpensesByAccount(userId: Int, startDate: String, endDate: String) =
        expenseEntryDao.getExpensesByAccount(userId, startDate, endDate)
}