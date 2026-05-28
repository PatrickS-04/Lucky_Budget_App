package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.service.GeminiSpendingService
import com.example.pulserds_prog7313.service.SpendingInsight
import kotlinx.coroutines.launch

class SpendingInsightViewModel : ViewModel() {

    private val service = GeminiSpendingService()

    private val _insight = MutableLiveData<SpendingInsight?>()
    val insight: LiveData<SpendingInsight?> = _insight

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadInsights(
        entries: List<ExpenseEntry>,
        categories: List<Category>,
        goals: List<SavingsGoal>,
        periodLabel: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _insight.value = service.analyzeSpending(entries, categories, goals, periodLabel)
            _isLoading.value = false
        }
    }
}