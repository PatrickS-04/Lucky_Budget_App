package com.example.pulserds_prog7313.service

import com.example.pulserds_prog7313.BuildConfig
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.data.SavingsGoal
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig

data class SpendingInsight(
    val summary: String,
    val overspendingWarnings: List<String>,
    val budgetSuggestions: List<String>,
    val savingsAdvice: String,
    val topRisk: String
)

class GeminiSpendingService {

    private val model = GenerativeModel(
        modelName = "gemini-1.5-flash",   // fast & free-tier friendly
        apiKey = BuildConfig.GEMINI_API_KEY,
        generationConfig = generationConfig {
            temperature = 0.4f             // lower = more factual, less creative
            maxOutputTokens = 800
        }
    )

    /**
     * Builds a structured prompt from the user's real data and returns parsed insights.
     * Call this from a coroutine (viewModelScope.launch).
     */
    suspend fun analyzeSpending(
        entries: List<ExpenseEntry>,
        categories: List<Category>,
        savingsGoals: List<SavingsGoal>,
        periodLabel: String,
        currency: String = "R"
    ): SpendingInsight {

        val categoryMap = categories.associateBy { it.id }

        // Build a spending breakdown string for the prompt
        val spendByCategory = entries
            .filter { it.amount < 0 }
            .groupBy { categoryMap[it.categoryId]?.name ?: "Unknown" }
            .mapValues { (_, list) -> list.sumOf { kotlin.math.abs(it.amount) } }
            .entries
            .sortedByDescending { it.value }
            .joinToString("\n") { (cat, total) ->
                "  - $cat: $currency ${"%.2f".format(total)}"
            }

        val income = entries.filter { it.amount > 0 }.sumOf { it.amount }
        val totalSpend = entries.filter { it.amount < 0 }.sumOf { kotlin.math.abs(it.amount) }
        val saved = (income - totalSpend).coerceAtLeast(0.0)

        val goalsText = if (savingsGoals.isEmpty()) "No active savings goals." else
            savingsGoals.joinToString("\n") { goal ->
                val progress = if (goal.targetAmount > 0)
                    (goal.savedAmount / goal.targetAmount * 100).toInt() else 0
                "  - ${goal.name}: ${currency} ${"%.2f".format(goal.savedAmount)} " +
                        "of ${currency} ${"%.2f".format(goal.targetAmount)} ($progress%) — due ${goal.targetDate}"
            }

        val prompt = """
            You are a personal finance AI for a South African budgeting app called Pulser.
            Analyse the user's spending data for the period: $periodLabel.
            
            FINANCIAL SUMMARY:
            - Total Income:  $currency ${"%.2f".format(income)}
            - Total Spent:   $currency ${"%.2f".format(totalSpend)}
            - Net Saved:     $currency ${"%.2f".format(saved)}
            - Savings Rate:  ${if (income > 0) "%.1f".format(saved / income * 100) else "0"}%
            
            SPENDING BY CATEGORY:
            $spendByCategory
            
            SAVINGS GOALS:
            $goalsText
            
            Respond ONLY in the following JSON format, no extra text:
            {
              "summary": "2-sentence overall financial health summary",
              "overspendingWarnings": ["warning 1", "warning 2"],
              "budgetSuggestions": ["suggestion 1", "suggestion 2", "suggestion 3"],
              "savingsAdvice": "one paragraph on savings goal progress",
              "topRisk": "the single biggest financial risk in one sentence"
            }
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            val json = response.text
                ?.removePrefix("```json")
                ?.removeSuffix("```")
                ?.trim() ?: "{}"
            parseInsight(json)
        } catch (e: Exception) {
            SpendingInsight(
                summary = "Could not load AI insights. Check your connection.",
                overspendingWarnings = emptyList(),
                budgetSuggestions = emptyList(),
                savingsAdvice = "",
                topRisk = e.message ?: "Unknown error"
            )
        }
    }

    private fun parseInsight(json: String): SpendingInsight {
        // Simple manual parse — avoids adding a Gson/Moshi dependency
        fun field(key: String) = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
            .find(json)?.groupValues?.get(1) ?: ""
        fun arrayField(key: String) = Regex("\"$key\"\\s*:\\s*\\[([^]]+)]")
            .find(json)?.groupValues?.get(1)
            ?.split(",")
            ?.map { it.trim().removeSurrounding("\"") }
            ?: emptyList()

        return SpendingInsight(
            summary = field("summary"),
            overspendingWarnings = arrayField("overspendingWarnings"),
            budgetSuggestions = arrayField("budgetSuggestions"),
            savingsAdvice = field("savingsAdvice"),
            topRisk = field("topRisk")
        )
    }
}