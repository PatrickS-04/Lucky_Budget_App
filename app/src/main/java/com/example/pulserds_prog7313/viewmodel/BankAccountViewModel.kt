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
//[1] Google, "Save data in a local database using Room," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/data-storage/room.
// [Accessed: 28 Apr. 2026].
//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
// [Accessed: 28 Apr. 2026].
//[3] Google, "LiveData overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/livedata.
// [Accessed: 28 Apr. 2026].
//[4] JetBrains, "Coroutines guide," Kotlin Documentation, 2024. [Online].
// Available: https://kotlinlang.org/docs/coroutines-guide.html.
// [Accessed: 28 Apr. 2026].
//[5] Google, "Guide to app architecture," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/architecture.
// [Accessed: 28 Apr. 2026].
//[6] Google, AndroidX Room, ver. 2.7.0-alpha11. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/room.
//[7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/lifecycle.
//[26] 	A. Mamo, "Android Room and Firebase in a MVVM Architecture - Offline First Application," Stack Exchange Inc, 18 November 2022. [Online].
// Available: https://stackoverflow.com/questions/74484239/android-room-and-firebase-in-a-mvvm-architecture-offline-first-application.
// [Accessed 30 May 2026].

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