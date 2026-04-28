package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.BankAccountDao

class BankAccountRepository(private val dao: BankAccountDao) {

    fun getAccountsForUser(userId: Int): LiveData<List<BankAccount>> =
        dao.getAccountsForUser(userId)

    suspend fun getAccountsOnce(userId: Int): List<BankAccount> =
        dao.getAccountsOnce(userId)

    suspend fun getById(accountId: Int): BankAccount? =
        dao.getById(accountId)

    suspend fun addAccount(account: BankAccount): Long {
        if (account.isPrimary) dao.clearPrimaryFlag(account.userId)
        return dao.insert(account)
    }

    suspend fun updateAccount(account: BankAccount) = dao.update(account)

    suspend fun deleteAccount(account: BankAccount) = dao.delete(account)

    suspend fun getTotalBalance(userId: Int): Double = dao.getTotalBalance(userId)

    /**
     * Atomically credit or debit an account balance.
     * [delta] is positive for income, negative for expense.
     */
    suspend fun adjustBalance(accountId: Int, delta: Double) =
        dao.adjustBalance(accountId, delta)
}
