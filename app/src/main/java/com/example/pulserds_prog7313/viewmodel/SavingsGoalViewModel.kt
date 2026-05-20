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