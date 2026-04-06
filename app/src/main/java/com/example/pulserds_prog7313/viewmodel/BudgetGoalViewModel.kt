package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.BudgetGoal
import com.example.pulserds_prog7313.repository.BudgetGoalRepository
import kotlinx.coroutines.launch

class BudgetGoalViewModel(private val budgetGoalRepository: BudgetGoalRepository) : ViewModel() {

    private val _currentGoal = MutableLiveData<BudgetGoal?>()
    val currentGoal: LiveData<BudgetGoal?> get() = _currentGoal

    fun loadGoalForMonth(userId: Int, month: Int, year: Int) {
        viewModelScope.launch {
            _currentGoal.value = budgetGoalRepository.getGoalForMonth(userId, month, year)
        }
    }

    fun saveGoal(goal: BudgetGoal) {
        viewModelScope.launch {
            budgetGoalRepository.saveGoal(goal)
            loadGoalForMonth(goal.userId, goal.month, goal.year)
        }
    }
}