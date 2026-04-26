package com.example.pulserds_prog7313.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface BankAccountDao {

    /** Insert a new account. Returns the generated row id. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: BankAccount): Long

    /** Update an existing account record. */
    @Update
    suspend fun update(account: BankAccount)

    /** Delete an account record. */
    @Delete
    suspend fun delete(account: BankAccount)

    /**
     * LiveData stream of all accounts for a user - the UI observes this
     * so the list refreshes automatically after every insert/delete/update.
     */
    @Query("SELECT * FROM bank_accounts WHERE userId = :userId ORDER BY isPrimary DESC, id DESC")
    fun getAccountsForUser(userId: Int): LiveData<List<BankAccount>>

    /** One-shot fetch - use inside suspend functions / coroutines. */
    @Query("SELECT * FROM bank_accounts WHERE userId = :userId ORDER BY isPrimary DESC, id DESC")
    suspend fun getAccountsOnce(userId: Int): List<BankAccount>

    /** Fetch a single account by its primary key. */
    @Query("SELECT * FROM bank_accounts WHERE id = :accountId LIMIT 1")
    suspend fun getById(accountId: Int): BankAccount?

    /**
     * Sum of all account balances for a user that are marked includeInTotal.
     * Used on the Balances / Accounts header card.
     */
    @Query("SELECT COALESCE(SUM(balance), 0.0) FROM bank_accounts WHERE userId = :userId AND includeInTotal = 1")
    suspend fun getTotalBalance(userId: Int): Double

    /**
     * Clears the isPrimary flag on every account for a user.
     * Call this before marking a new account as primary so only one is ever primary.
     */
    @Query("UPDATE bank_accounts SET isPrimary = 0 WHERE userId = :userId")
    suspend fun clearPrimaryFlag(userId: Int)

    /**
     * Atomically adjust the balance of a single account by [delta].
     * Positive delta = credit (income), negative delta = debit (expense).
     */
    @Query("UPDATE bank_accounts SET balance = balance + :delta WHERE id = :accountId")
    suspend fun adjustBalance(accountId: Int, delta: Double)
}
