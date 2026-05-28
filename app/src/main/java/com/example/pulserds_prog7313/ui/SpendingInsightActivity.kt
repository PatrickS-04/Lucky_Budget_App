package com.example.pulserds_prog7313.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.lifecycle.ViewModelProvider
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.repository.*
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.*

class SpendingInsightActivity : BaseActivity() {

    private lateinit var insightViewModel: SpendingInsightViewModel
    private lateinit var expenseViewModel: ExpenseEntryViewModel
    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var savingsViewModel: SavingsGoalViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spending_insight)

        val periodLabel = intent.getStringExtra("periodLabel") ?: "This Month"
        val userId = SessionManager(this).getUserId()

        insightViewModel = ViewModelProvider(
            this, ViewModelFactory { SpendingInsightViewModel() }
        )[SpendingInsightViewModel::class.java]

        expenseViewModel = ViewModelProvider(
            this, ViewModelFactory { ExpenseEntryViewModel(ExpenseEntryRepository()) }
        )[ExpenseEntryViewModel::class.java]

        categoryViewModel = ViewModelProvider(
            this, ViewModelFactory { CategoryViewModel(CategoryRepository()) }
        )[CategoryViewModel::class.java]

        savingsViewModel = ViewModelProvider(
            this, ViewModelFactory { SavingsGoalViewModel(SavingsGoalRepository()) }
        )[SavingsGoalViewModel::class.java]

        // Load supporting data first, then trigger AI once all data is ready
        categoryViewModel.loadCategoriesForUser(userId)
        savingsViewModel.loadGoalsForUser(userId)
        expenseViewModel.loadEntriesForCurrentMonth(userId)  // or pass date range

        // Observe all three, then call AI when ready
        var categories = emptyList<com.example.pulserds_prog7313.data.Category>()
        var entries = emptyList<com.example.pulserds_prog7313.data.ExpenseEntry>()
        var goals = emptyList<com.example.pulserds_prog7313.data.SavingsGoal>()

        fun tryTriggerAI() {
            if (categories.isNotEmpty() || entries.isNotEmpty()) {
                insightViewModel.loadInsights(entries, categories, goals, periodLabel)
            }
        }

        categoryViewModel.categories.observe(this) { cats ->
            categories = cats ?: emptyList(); tryTriggerAI()
        }
        expenseViewModel.entries.observe(this) { ents ->
            entries = ents ?: emptyList(); tryTriggerAI()
        }
        savingsViewModel.goals.observe(this) { g ->
            goals = g ?: emptyList()
        }

        // Observe results
        insightViewModel.isLoading.observe(this) { loading ->
            findViewById<ProgressBar>(R.id.aiLoadingSpinner).visibility =
                if (loading) View.VISIBLE else View.GONE
            findViewById<ScrollView>(R.id.insightScrollView).visibility =
                if (loading) View.GONE else View.VISIBLE
        }

        insightViewModel.insight.observe(this) { insight ->
            insight ?: return@observe
            findViewById<TextView>(R.id.txtSummary).text = insight.summary
            findViewById<TextView>(R.id.txtTopRisk).text = "⚠️ ${insight.topRisk}"
            findViewById<TextView>(R.id.txtWarnings).text =
                insight.overspendingWarnings.joinToString("\n") { "• $it" }
            findViewById<TextView>(R.id.txtSuggestions).text =
                insight.budgetSuggestions.joinToString("\n") { "💡 $it" }
            findViewById<TextView>(R.id.txtSavingsAdvice).text = insight.savingsAdvice
        }
    }
}