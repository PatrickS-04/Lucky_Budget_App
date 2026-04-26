package com.example.pulserds_prog7313.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ExpenseEntryDao {
    @Insert
    suspend fun insert(entry: ExpenseEntry)

    @Delete
    suspend fun delete(entry: ExpenseEntry)

    @Query("SELECT * FROM expense_entries WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC, id DESC")
    suspend fun getEntriesForPeriod(userId: Int, startDate: String, endDate: String): List<ExpenseEntry>

    @Query("SELECT SUM(amount) FROM expense_entries WHERE userId = :userId AND categoryId = :categoryId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalForCategory(userId: Int, categoryId: Int, startDate: String, endDate: String): Double

    /** LiveData stream of ALL entries for a user - observed by TransactionsActivity */
    @Query("SELECT * FROM expense_entries WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getAllEntriesLive(userId: Int): LiveData<List<ExpenseEntry>>

    /** One-shot all entries - for search/filter in coroutine */
    @Query("SELECT * FROM expense_entries WHERE userId = :userId ORDER BY date DESC, id DESC")
    suspend fun getAllEntriesOnce(userId: Int): List<ExpenseEntry>

    /** Sum of positive amounts (income) for a user */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expense_entries WHERE userId = :userId AND amount > 0")
    suspend fun getTotalIncome(userId: Int): Double

    /** Sum of negative amounts (expenses) for a user - returns a negative or zero value */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expense_entries WHERE userId = :userId AND amount < 0")
    suspend fun getTotalExpenses(userId: Int): Double

    /** LiveData sum of income for period filters */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expense_entries WHERE userId = :userId AND amount > 0 AND date BETWEEN :startDate AND :endDate")
    fun getIncomeLive(userId: Int, startDate: String, endDate: String): LiveData<Double>

    /** LiveData sum of expenses for period filters */
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expense_entries WHERE userId = :userId AND amount < 0 AND date BETWEEN :startDate AND :endDate")
    fun getExpensesLive(userId: Int, startDate: String, endDate: String): LiveData<Double>

    /** LiveData entries filtered by date range */
    @Query("SELECT * FROM expense_entries WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC, id DESC")
    fun getEntriesForPeriodLive(userId: Int, startDate: String, endDate: String): LiveData<List<ExpenseEntry>>
}
