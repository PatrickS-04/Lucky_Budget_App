package com.example.pulserds_prog7313.data

import androidx.lifecycle.LiveData
import androidx.room.*

// Normalises a stored date to yyyy/MM/dd for correct lexicographic comparison.
// Handles both formats that may exist in the database:
//   yyyy-MM-dd  (e.g. "2026-04-28") -- detected by a '-' or '/' at position 5
//   dd/MM/yyyy  (e.g. "28/04/2026") -- everything else
private const val NORM_COL = """
    CASE WHEN substr(date,5,1) IN ('-','/')
         THEN substr(date,1,4)||'/'||substr(date,6,2)||'/'||substr(date,9,2)
         ELSE substr(date,7,4)||'/'||substr(date,4,2)||'/'||substr(date,1,2)
    END"""

// Same normalisation for a bound parameter (:name becomes 7 repeated ? bindings
// because Room doesn't support expressions in named parameters).
// Use NORM_PARAM_START for :startDate and NORM_PARAM_END for :endDate.
private const val NORM_PARAM = """
    CASE WHEN substr(?,5,1) IN ('-','/')
         THEN substr(?,1,4)||'/'||substr(?,6,2)||'/'||substr(?,9,2)
         ELSE substr(?,7,4)||'/'||substr(?,4,2)||'/'||substr(?,1,2)
    END"""

// Room DAO pattern
// [1] Google, "Save data in a local database using Room," Android Developers, 2024.
@Dao
interface ExpenseEntryDao {

    // -- Insert / delete -------------------------------------------------------
    @Insert
    suspend fun insert(entry: ExpenseEntry)

    @Delete
    suspend fun delete(entry: ExpenseEntry)

    // -- Suspend queries -------------------------------------------------------

    // Returns all entries for a user within a date range, newest first
    @Query("""SELECT * FROM expense_entries
        WHERE userId = :userId
        AND ($NORM_COL) BETWEEN
            (CASE WHEN substr(:startDate,5,1) IN ('-','/') THEN substr(:startDate,1,4)||'/'||substr(:startDate,6,2)||'/'||substr(:startDate,9,2) ELSE substr(:startDate,7,4)||'/'||substr(:startDate,4,2)||'/'||substr(:startDate,1,2) END)
        AND (CASE WHEN substr(:endDate,5,1) IN ('-','/') THEN substr(:endDate,1,4)||'/'||substr(:endDate,6,2)||'/'||substr(:endDate,9,2) ELSE substr(:endDate,7,4)||'/'||substr(:endDate,4,2)||'/'||substr(:endDate,1,2) END)
        ORDER BY ($NORM_COL) DESC, id DESC""")
    suspend fun getEntriesForPeriod(userId: Int, startDate: String, endDate: String): List<ExpenseEntry>

    // Returns the sum of amounts for a specific category within a date range
    @Query("""SELECT SUM(amount) FROM expense_entries
        WHERE userId = :userId AND categoryId = :categoryId
        AND ($NORM_COL) BETWEEN
            (CASE WHEN substr(:startDate,5,1) IN ('-','/') THEN substr(:startDate,1,4)||'/'||substr(:startDate,6,2)||'/'||substr(:startDate,9,2) ELSE substr(:startDate,7,4)||'/'||substr(:startDate,4,2)||'/'||substr(:startDate,1,2) END)
        AND (CASE WHEN substr(:endDate,5,1) IN ('-','/') THEN substr(:endDate,1,4)||'/'||substr(:endDate,6,2)||'/'||substr(:endDate,9,2) ELSE substr(:endDate,7,4)||'/'||substr(:endDate,4,2)||'/'||substr(:endDate,1,2) END)""")
    suspend fun getTotalForCategory(userId: Int, categoryId: Int, startDate: String, endDate: String): Double

    // Returns all entries for a user as a one-shot snapshot, newest first
    @Query("SELECT * FROM expense_entries WHERE userId = :userId ORDER BY ($NORM_COL) DESC, id DESC")
    suspend fun getAllEntriesOnce(userId: Int): List<ExpenseEntry>

    // Returns the lifetime income total (positive amounts) for a user
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expense_entries WHERE userId = :userId AND amount > 0")
    suspend fun getTotalIncome(userId: Int): Double

    // Returns the lifetime expense total (negative amounts) for a user
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expense_entries WHERE userId = :userId AND amount < 0")
    suspend fun getTotalExpenses(userId: Int): Double

    // Returns expenses grouped by account for the given period (used by spending-by-account chart)
    @Query("""SELECT bankAccountId, COALESCE(SUM(amount), 0.0) as total FROM expense_entries
        WHERE userId = :userId AND amount < 0
        AND ($NORM_COL) BETWEEN
            (CASE WHEN substr(:startDate,5,1) IN ('-','/') THEN substr(:startDate,1,4)||'/'||substr(:startDate,6,2)||'/'||substr(:startDate,9,2) ELSE substr(:startDate,7,4)||'/'||substr(:startDate,4,2)||'/'||substr(:startDate,1,2) END)
        AND (CASE WHEN substr(:endDate,5,1) IN ('-','/') THEN substr(:endDate,1,4)||'/'||substr(:endDate,6,2)||'/'||substr(:endDate,9,2) ELSE substr(:endDate,7,4)||'/'||substr(:endDate,4,2)||'/'||substr(:endDate,1,2) END)
        GROUP BY bankAccountId""")
    suspend fun getExpensesByAccount(userId: Int, startDate: String, endDate: String): List<AccountSpending>

    // -- LiveData queries ------------------------------------------------------

    // Observes all entries for a user as a LiveData stream, newest first
    @Query("SELECT * FROM expense_entries WHERE userId = :userId ORDER BY ($NORM_COL) DESC, id DESC")
    fun getAllEntriesLive(userId: Int): LiveData<List<ExpenseEntry>>

    // Observes income total (positive amounts) for a period as LiveData
    @Query("""SELECT COALESCE(SUM(amount), 0.0) FROM expense_entries
        WHERE userId = :userId AND amount > 0
        AND ($NORM_COL) BETWEEN
            (CASE WHEN substr(:startDate,5,1) IN ('-','/') THEN substr(:startDate,1,4)||'/'||substr(:startDate,6,2)||'/'||substr(:startDate,9,2) ELSE substr(:startDate,7,4)||'/'||substr(:startDate,4,2)||'/'||substr(:startDate,1,2) END)
        AND (CASE WHEN substr(:endDate,5,1) IN ('-','/') THEN substr(:endDate,1,4)||'/'||substr(:endDate,6,2)||'/'||substr(:endDate,9,2) ELSE substr(:endDate,7,4)||'/'||substr(:endDate,4,2)||'/'||substr(:endDate,1,2) END)""")
    fun getIncomeLive(userId: Int, startDate: String, endDate: String): LiveData<Double>

    // Observes expense total (negative amounts) for a period as LiveData
    @Query("""SELECT COALESCE(SUM(amount), 0.0) FROM expense_entries
        WHERE userId = :userId AND amount < 0
        AND ($NORM_COL) BETWEEN
            (CASE WHEN substr(:startDate,5,1) IN ('-','/') THEN substr(:startDate,1,4)||'/'||substr(:startDate,6,2)||'/'||substr(:startDate,9,2) ELSE substr(:startDate,7,4)||'/'||substr(:startDate,4,2)||'/'||substr(:startDate,1,2) END)
        AND (CASE WHEN substr(:endDate,5,1) IN ('-','/') THEN substr(:endDate,1,4)||'/'||substr(:endDate,6,2)||'/'||substr(:endDate,9,2) ELSE substr(:endDate,7,4)||'/'||substr(:endDate,4,2)||'/'||substr(:endDate,1,2) END)""")
    fun getExpensesLive(userId: Int, startDate: String, endDate: String): LiveData<Double>

    // Observes entries for a user within a date range as LiveData, newest first
    @Query("""SELECT * FROM expense_entries
        WHERE userId = :userId
        AND ($NORM_COL) BETWEEN
            (CASE WHEN substr(:startDate,5,1) IN ('-','/') THEN substr(:startDate,1,4)||'/'||substr(:startDate,6,2)||'/'||substr(:startDate,9,2) ELSE substr(:startDate,7,4)||'/'||substr(:startDate,4,2)||'/'||substr(:startDate,1,2) END)
        AND (CASE WHEN substr(:endDate,5,1) IN ('-','/') THEN substr(:endDate,1,4)||'/'||substr(:endDate,6,2)||'/'||substr(:endDate,9,2) ELSE substr(:endDate,7,4)||'/'||substr(:endDate,4,2)||'/'||substr(:endDate,1,2) END)
        ORDER BY ($NORM_COL) DESC, id DESC""")
    fun getEntriesForPeriodLive(userId: Int, startDate: String, endDate: String): LiveData<List<ExpenseEntry>>
}

// -- Projection model ----------------------------------------------------------
// Holds the per-account spending total returned by getExpensesByAccount()
data class AccountSpending(val bankAccountId: Int, val total: Double)