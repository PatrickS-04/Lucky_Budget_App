package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.repository.BankAccountRepository
import kotlinx.coroutines.launch

// MVVM ViewModel — based off the official Google documents.
// [2] Google, "ViewModel overview," Android Developers, 2024.
// [7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.

/**
 * BankAccountViewModel
 * --------------------
 * Manages all bank account data for the logged-in user and exposes it
 * to the UI layer via observable LiveData.
 *
 * Responsibilities:
 *   - Provides a LiveData list of [BankAccount] objects observed by AccountsActivity.
 *   - Aggregates and exposes the user's total balance across included accounts.
 *   - Validates and persists new accounts via [BankAccountRepository].
 *   - Exposes one-shot [SaveResult] events so the UI can react to save outcomes.
 *   - Handles account deletion and keeps the total balance in sync afterwards.
 *
 * @param repo Repository that abstracts all Room database operations for bank accounts.
 */
class BankAccountViewModel(
    private val repo: BankAccountRepository
) : ViewModel() {

    // -- Observed by AccountsActivity -----------------------------------------

    /**
     * Live list of all accounts belonging to the logged-in user.
     * Initialised lazily in [init] once the userId is known.
     * Private setter prevents external reassignment.
     */
    lateinit var accounts: LiveData<List<BankAccount>>
        private set

    /**
     * Aggregated balance across all accounts where includeInTotal = true.
     * Backed by a MutableLiveData that is refreshed after every add/delete.
     */
    private val _totalBalance = MutableLiveData(0.0)
    val totalBalance: LiveData<Double> get() = _totalBalance

    // -- One-shot result events ------------------------------------------------

    /**
     * Emits a [SaveResult] after [addAccount] completes — either [SaveResult.Success]
     * or [SaveResult.Error] with a user-facing message. Observed once by the UI
     * to avoid reacting to stale events on re-subscription.
     */
    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> get() = _saveResult

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Must be called once from the Activity after the userId is known
     * (i.e. after [SessionManager] has been queried).
     *
     * Guards against re-initialisation on configuration changes — if [accounts]
     * is already initialised the LiveData subscription is kept as-is and only
     * the total balance is refreshed.
     *
     * @param userId The ID of the currently logged-in user.
     */
    fun init(userId: Int) {
        if (!::accounts.isInitialized) {
            // Attach the Room-backed LiveData only once; Room keeps it updated automatically.
            accounts = repo.getAccountsForUser(userId)
        }
        refreshTotal(userId)
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Fetches the aggregated total balance from the repository and posts it
     * to [totalBalance]. Called after every add or delete operation to keep
     * the displayed total in sync with the database.
     *
     * @param userId The ID of the currently logged-in user.
     */
    fun refreshTotal(userId: Int) {
        viewModelScope.launch {
            _totalBalance.value = repo.getTotalBalance(userId)
        }
    }

    /**
     * Validates inputs and saves a new [BankAccount] to the database.
     *
     * Posts [SaveResult.Success] on a successful insert, or
     * [SaveResult.Error] with a descriptive message if validation fails
     * or the insert returns an invalid row ID.
     *
     * @param userId         ID of the logged-in user who owns this account.
     * @param accountType    Type of account (e.g. "Cheque", "Savings").
     * @param bankName       Name of the bank (e.g. "FNB", "Nedbank").
     * @param nickname       Short user-defined label for the account.
     * @param accountNumber  Full account number (min 6 digits).
     * @param balance        Opening balance for the account.
     * @param colour         Hex colour string used for the account's dot and chart segment.
     * @param includeInTotal Whether this account contributes to the total balance.
     * @param setPrimary     Whether to mark this as the user's primary account.
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
        // Run validation before launching a coroutine — fail fast on the main thread.
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

            // If the user wants this as primary, the repository clears the isPrimary
            // flag on all existing accounts before inserting the new one.
            if (setPrimary) repo.getAccountsOnce(userId)
            val rowId = repo.addAccount(account)

            if (rowId > 0) {
                // Insert succeeded — keep the total balance label in sync.
                refreshTotal(userId)
                _saveResult.value = SaveResult.Success
            } else {
                _saveResult.value = SaveResult.Error("Could not save account. Please try again.")
            }
        }
    }

    /**
     * Deletes [account] from the database and refreshes the total balance
     * so the UI reflects the removal immediately.
     *
     * @param account The [BankAccount] to delete.
     * @param userId  The ID of the logged-in user, used to recalculate the total.
     */
    fun deleteAccount(account: BankAccount, userId: Int) {
        viewModelScope.launch {
            repo.deleteAccount(account)
            refreshTotal(userId) // Recalculate total after deletion.
        }
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Validates the fields required to create a new bank account.
     *
     * Rules (checked in priority order):
     *   - Account type must be selected.
     *   - Bank name must be selected.
     *   - Nickname must not be blank.
     *   - Account number must not be blank.
     *   - Account number must be at least 6 digits long.
     *
     * @return A user-facing error message string, or null if all rules pass.
     */
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
        else                     -> null // All rules passed.
    }

    // -------------------------------------------------------------------------
    // Result sealed class
    // -------------------------------------------------------------------------

    /**
     * Represents the outcome of an [addAccount] operation.
     *
     * - [Success] — the account was inserted successfully.
     * - [Error]   — validation failed or the insert returned an invalid row ID;
     *               [Error.message] contains a user-facing description.
     */
    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}