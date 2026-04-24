package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.repository.BankAccountRepository
import kotlinx.coroutines.launch

class BankAccountViewModel(
    private val repo: BankAccountRepository
) : ViewModel() {

    // ── Observed by AccountsActivity ─────────────────────────────────────────
    lateinit var accounts: LiveData<List<BankAccount>>
        private set

    private val _totalBalance = MutableLiveData(0.0)
    val totalBalance: LiveData<Double> get() = _totalBalance

    // ── One-shot result events ────────────────────────────────────────────────
    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> get() = _saveResult

    // ─────────────────────────────────────────────────────────────────────────

    /** Call once from the Activity after the userId is known. */
    fun init(userId: Int) {
        if (!::accounts.isInitialized) {
            accounts = repo.getAccountsForUser(userId)
        }
        refreshTotal(userId)
    }

    fun refreshTotal(userId: Int) {
        viewModelScope.launch {
            _totalBalance.value = repo.getTotalBalance(userId)
        }
    }

    /**
     * Validate inputs and save a new [BankAccount].
     * Posts [SaveResult.Success] or [SaveResult.Error] so the Activity can react once.
     */
    fun addAccount(
        userId: Int,
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
                userId        = userId,
                accountType   = accountType,
                bankName      = bankName,
                nickname      = nickname.trim(),
                accountNumber = accountNumber.trim(),
                balance       = balance,
                colour        = colour,
                includeInTotal = includeInTotal,
                isPrimary     = setPrimary
            )

            // If the user wants this as primary, clear the flag on all others first
            if (setPrimary) repo.getAccountsOnce(userId)   // triggers clearPrimaryFlag inside repo.addAccount path
            val rowId = repo.addAccount(account)

            if (rowId > 0) {
                refreshTotal(userId)
                _saveResult.value = SaveResult.Success
            } else {
                _saveResult.value = SaveResult.Error("Could not save account. Please try again.")
            }
        }
    }

    fun deleteAccount(account: BankAccount, userId: Int) {
        viewModelScope.launch {
            repo.deleteAccount(account)
            refreshTotal(userId)
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────
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

    // ── Result sealed class ───────────────────────────────────────────────────
    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}