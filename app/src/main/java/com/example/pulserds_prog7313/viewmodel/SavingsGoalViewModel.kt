package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    // null = no pending result; observers must guard against null
    private val _saveResult = MutableLiveData<GoalResult?>(null)
    val saveResult: LiveData<GoalResult?> get() = _saveResult

    /** Reset after the UI has consumed the result so it never re-fires. */
    fun consumeResult() {
        _saveResult.value = null
    }

    fun getAllGoalsLive(userId: Int): LiveData<List<SavingsGoal>> =
        repo.getAllGoalsLive(userId)

    fun refreshTotals(userId: Int) {
        viewModelScope.launch {
            _totalSaved.value  = repo.getTotalSaved(userId)
            _totalTarget.value = repo.getTotalTarget(userId)
        }
    }

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
        when {
            name.isBlank()        -> { _saveResult.value = GoalResult.Error("Please enter a goal name."); return }
            targetAmount <= 0     -> { _saveResult.value = GoalResult.Error("Target amount must be greater than R 0."); return }
            initialDeposit < 0    -> { _saveResult.value = GoalResult.Error("Initial deposit cannot be negative."); return }
            initialDeposit > targetAmount -> { _saveResult.value = GoalResult.Error("Initial deposit cannot exceed the target amount."); return }
            autoSaveEnabled && autoSaveAmount <= 0 -> { _saveResult.value = GoalResult.Error("Please enter an auto-save amount."); return }
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
            val id = repo.insert(goal)
            if (id > 0) {
                refreshTotals(userId)
                _saveResult.value = GoalResult.Success
            } else {
                _saveResult.value = GoalResult.Error("Could not save goal. Please try again.")
            }
        }
    }

    fun addFunds(goalId: Int, amount: Double, userId: Int) {
        if (amount <= 0) { _saveResult.value = GoalResult.Error("Please enter a valid amount."); return }
        val completedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        viewModelScope.launch {
            repo.addFunds(goalId, amount, completedDate)
            refreshTotals(userId)
            _saveResult.value = GoalResult.FundsAdded
        }
    }

    fun deleteGoal(goal: SavingsGoal, userId: Int) {
        viewModelScope.launch {
            repo.delete(goal)
            refreshTotals(userId)
        }
    }
}