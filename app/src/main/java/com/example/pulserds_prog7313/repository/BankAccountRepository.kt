package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.BankAccountDao

class BankAccountRepository(private val dao: BankAccountDao) {

    suspend fun addAccount(account: BankAccount): Long = dao.insert(account)

    suspend fun updateAccount(account: BankAccount) = dao.update(account)

    suspend fun deleteAccount(account: BankAccount) = dao.delete(account)

    /** LiveData stream — observe in AccountsActivity for automatic list refresh. */
    fun getAccountsForUser(userId: Int): LiveData<List<BankAccount>> =
        dao.getAccountsForUser(userId)

    suspend fun getAccountsOnce(userId: Int): List<BankAccount> =
        dao.getAccountsOnce(userId)

    suspend fun getTotalBalance(userId: Int): Double =
        dao.getTotalBalance(userId)

    /** Ensures only one account is marked primary at a time. */
    suspend fun setPrimary(account: BankAccount) {
        dao.clearPrimaryFlag(account.userId)
        dao.update(account.copy(isPrimary = true))
    }
}