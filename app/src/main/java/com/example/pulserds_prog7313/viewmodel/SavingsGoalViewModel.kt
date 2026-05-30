package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import com.example.pulserds_prog7313.utils.SingleLiveEvent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Changes from the Room version:
//   - userId, goalId, linkedAccountId are now String.
//   - insert() returns a String (Firestore document ID); check isNotBlank() instead of > 0.
// All business logic and validation is identical.
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

class SavingsGoalViewModel(private val repo: SavingsGoalRepository) : ViewModel() {

    sealed class GoalResult {
        object Success    : GoalResult()
        object FundsAdded : GoalResult()
        data class Error(val message: String) : GoalResult()
    }

    private val _totalSaved  = MutableLiveData(0.0)
    val totalSaved: LiveData<Double> get() = _totalSaved

    private val _totalTarget = MutableLiveData(0.0)
    val totalTarget: LiveData<Double> get() = _totalTarget

    private val _saveResult = SingleLiveEvent<GoalResult>()
    val saveResult: LiveData<GoalResult> get() = _saveResult

    fun consumeResult() { /* no-op -- SingleLiveEvent self-consumes */ }

    fun getAllGoalsLive(userId: String): LiveData<List<SavingsGoal>> =
        repo.getAllGoalsLive(userId)

    fun refreshTotals(userId: String) {
        viewModelScope.launch {
            _totalSaved.postValue(repo.getTotalSaved(userId))
            _totalTarget.postValue(repo.getTotalTarget(userId))
        }
    }

    fun createGoal(
        userId         : String,
        name           : String,
        icon           : String,
        targetAmount   : Double,
        initialDeposit : Double,
        targetDate     : String,
        linkedAccountId: String,
        autoSaveEnabled: Boolean,
        autoSaveAmount : Double,
        autoSaveFreq   : String
    ) {
        when {
            name.isBlank() -> {
                _saveResult.setValue(GoalResult.Error("Please enter a goal name."))
                return
            }
            targetAmount <= 0 -> {
                _saveResult.setValue(GoalResult.Error("Target amount must be greater than R 0."))
                return
            }
            initialDeposit < 0 -> {
                _saveResult.setValue(GoalResult.Error("Initial deposit cannot be negative."))
                return
            }
            initialDeposit > targetAmount -> {
                _saveResult.setValue(GoalResult.Error("Initial deposit cannot exceed the target amount."))
                return
            }
            autoSaveEnabled && autoSaveAmount <= 0 -> {
                _saveResult.setValue(GoalResult.Error("Please enter an auto-save amount."))
                return
            }
        }

        val now       = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val completed = initialDeposit >= targetAmount

        val goal = SavingsGoal(
            userId            = userId,
            name              = name.trim(),
            icon              = icon,
            targetAmount      = targetAmount,
            savedAmount       = initialDeposit,
            targetDate        = targetDate,
            linkedAccountId   = linkedAccountId,
            autoSaveEnabled   = autoSaveEnabled,
            autoSaveAmount    = autoSaveAmount,
            autoSaveFrequency = autoSaveFreq,
            isCompleted       = completed,
            completedDate     = if (completed) now else ""
        )

        viewModelScope.launch {
            val id = repo.insert(goal)   // Returns Firestore document ID
            if (id.isNotBlank()) {
                refreshTotals(userId)
                _saveResult.postValue(GoalResult.Success)
            } else {
                _saveResult.postValue(GoalResult.Error("Could not save goal. Please try again."))
            }
        }
    }

    fun addFunds(goalId: String, amount: Double, userId: String) {
        if (amount <= 0) {
            _saveResult.setValue(GoalResult.Error("Please enter a valid amount."))
            return
        }
        val completedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            repo.addFunds(goalId, amount, completedDate)
            refreshTotals(userId)
            _saveResult.postValue(GoalResult.FundsAdded)
        }
    }

    fun deleteGoal(goal: SavingsGoal, userId: String) {
        viewModelScope.launch {
            repo.delete(goal)
            refreshTotals(userId)
        }
    }
}