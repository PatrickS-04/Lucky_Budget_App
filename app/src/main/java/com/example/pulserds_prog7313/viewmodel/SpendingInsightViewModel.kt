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

// Bridges SpendingInsightActivity and GeminiSpendingService.
// Exposes two LiveData streams: isLoading (drives the spinner) and
// insight (the parsed AI response). loadInsights() is the single entry
// point -- it delegates to GeminiSpendingService on an IO thread via
// viewModelScope so the UI thread is never blocked during the network call.
class SpendingInsightViewModel : ViewModel() {

    // GeminiSpendingService wraps the OkHttp REST client and prompt builder.
    private val service = GeminiSpendingService()

    // Nullable so the observer can distinguish "not yet loaded" from "loaded with data".
    private val _insight = MutableLiveData<SpendingInsight?>()
    val insight: LiveData<SpendingInsight?> = _insight

    // True while the network call is in-flight; false otherwise.
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /**
     * Sends the user's spending data to Gemini and posts the result to [insight].
     * [isLoading] is set to true immediately so the activity can show a spinner,
     * and reset to false once the call completes (whether or not it succeeded).
     *
     * @param entries     All expense entries for the period being analysed.
     * @param categories  Full category list used to resolve human-readable names.
     * @param goals       Active savings goals included in the AI prompt context.
     * @param periodLabel Human-readable period string shown in the prompt, e.g. "April 2025".
     */
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