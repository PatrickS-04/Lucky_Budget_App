package com.example.pulserds_prog7313.data

import androidx.room.*

@Dao
interface ExpenseEntryDao {
    @Insert
    suspend fun insert(entry: ExpenseEntry)

    @Delete
    suspend fun delete(entry: ExpenseEntry)

    @Query("SELECT * FROM expense_entries WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getEntriesForPeriod(userId: Int, startDate: String, endDate: String): List<ExpenseEntry>

    @Query("SELECT SUM(amount) FROM expense_entries WHERE userId = :userId AND categoryId = :categoryId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalForCategory(userId: Int, categoryId: Int, startDate: String, endDate: String): Double
}