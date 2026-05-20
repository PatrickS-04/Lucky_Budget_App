package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.repository.BankAccountRepository
import kotlinx.coroutines.launch

// Changes from the Room version:
//   - userId is now String (Firebase Auth UID).
//   - addAccount() checks for a non-blank String ID instead of rowId > 0.
// All other logic is identical.
class BankAccountViewModel(
    private val repo: BankAccountRepository
) : ViewModel() {

    lateinit var accounts: LiveData<List<BankAccount>>
        private set

    private val _totalBalance = MutableLiveData(0.0)
    val totalBalance: LiveData<Double> get() = _totalBalance

    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> get() = _saveResult

    fun init(userId: String) {
        if (!::accounts.isInitialized) {
            accounts = repo.getAccountsForUser(userId)
        }
        refreshTotal(userId)
    }

    fun refreshTotal(userId: String) {
        viewModelScope.launch {
            _totalBalance.value = repo.getTotalBalance(userId)
        }
    }

    fun addAccount(
        userId: String,
        accountType: String,
        bankName: String,
        nickname: String,
        accountNumber: String,
        balance: Double,
        colour: String,
        includeInTotal: Boolean,
        setPrimary: Boolean
    ) {
        val error = validate(accountType, bankName, nickname, accountNumber)
        if (error != null) {
            _saveResult.value = SaveResult.Error(error)
            return
        }

        viewModelScope.launch {
            val account = BankAccount(
                userId         = userId,
                accountType    = accountType,
                bankName       = bankName,
                nickname       = nickname.trim(),
                accountNumber  = accountNumber.trim(),
                balance        = balance,
                colour         = colour,
                includeInTotal = includeInTotal,
                isPrimary      = setPrimary
            )
            val newId = repo.addAccount(account)   // Returns Firestore document ID (String)
            if (newId.isNotBlank()) {
                refreshTotal(userId)
                _saveResult.value = SaveResult.Success
            } else {
                _saveResult.value = SaveResult.Error("Could not save account. Please try again.")
            }
        }
    }

    fun deleteAccount(account: BankAccount, userId: String) {
        viewModelScope.launch {
            repo.deleteAccount(account)
            refreshTotal(userId)
        }
    }

    private fun validate(
        accountType: String,
        bankName: String,
        nickname: String,
        accountNumber: String
    ): String? = when {
        accountType.isBlank()    -> "Please select an account type."
        bankName.isBlank()       -> "Please select your bank."
        nickname.isBlank()       -> "Account nickname cannot be empty."
        accountNumber.isBlank()  -> "Account number cannot be empty."
        accountNumber.length < 6 -> "Account number is too short (min 6 digits)."
        else                     -> null
    }

    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}