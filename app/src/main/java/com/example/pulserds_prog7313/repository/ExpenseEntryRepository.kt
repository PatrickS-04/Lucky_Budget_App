package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.data.ExpenseEntryDao

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
}