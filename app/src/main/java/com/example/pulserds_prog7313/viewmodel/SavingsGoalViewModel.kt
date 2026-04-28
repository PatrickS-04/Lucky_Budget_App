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

// MVVM ViewModel -- based off the official google documents
// [2] Google, "ViewModel overview," Android Developers, 2024.
// [7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.
class SavingsGoalViewModel(private val repo: SavingsGoalRepository) : ViewModel() {

    // -- Result sealed class ---------------------------------------------------
    sealed class GoalResult {
        object Success    : GoalResult()
        object FundsAdded : GoalResult()
        data class Error(val message: String) : GoalResult()
    }

    // -- LiveData --------------------------------------------------------------
    private val _totalSaved  = MutableLiveData(0.0)
    val totalSaved: LiveData<Double> get() = _totalSaved

    private val _totalTarget = MutableLiveData(0.0)
    val totalTarget: LiveData<Double> get() = _totalTarget

    // SingleLiveEvent: each posted value is delivered exactly once to one observer.
    // Eliminates stale-result replays that caused instant finish() on screen open.
    private val _saveResult = SingleLiveEvent<GoalResult>()
    val saveResult: LiveData<GoalResult> get() = _saveResult

    fun consumeResult() { /* no-op -- SingleLiveEvent self-consumes */ }

    // -- Data access -----------------------------------------------------------
    fun getAllGoalsLive(userId: Int): LiveData<List<SavingsGoal>> =
        repo.getAllGoalsLive(userId)

    fun refreshTotals(userId: Int) {
        viewModelScope.launch {
            _totalSaved.postValue(repo.getTotalSaved(userId))
            _totalTarget.postValue(repo.getTotalTarget(userId))
        }
    }

    // -- Create goal -----------------------------------------------------------
    fun createGoal(
        userId         : Int,
        name           : String,
        icon           : String,
        targetAmount   : Double,
        initialDeposit : Double,
        targetDate     : String,
        linkedAccountId: Int,
        autoSaveEnabled: Boolean,
        autoSaveAmount : Double,
        autoSaveFreq   : String
    ) {
        // Validation runs on main thread -- use setValue() directly as a method call
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

        // DB insert runs on a background thread -- use postValue()
        viewModelScope.launch {
            val id = repo.insert(goal)
            if (id > 0) {
                refreshTotals(userId)
                _saveResult.postValue(GoalResult.Success)
            } else {
                _saveResult.postValue(GoalResult.Error("Could not save goal. Please try again."))
            }
        }
    }

    // -- Add funds -------------------------------------------------------------
    fun addFunds(goalId: Int, amount: Double, userId: Int) {
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

    // -- Delete goal -----------------------------------------------------------
    fun deleteGoal(goal: SavingsGoal, userId: Int) {
        viewModelScope.launch {
            repo.delete(goal)
            refreshTotals(userId)
        }
    }
}