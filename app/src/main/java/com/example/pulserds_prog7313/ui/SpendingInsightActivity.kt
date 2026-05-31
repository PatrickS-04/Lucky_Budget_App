package com.example.pulserds_prog7313.ui

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.repository.ExpenseEntryRepository
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ExpenseEntryViewModel
import com.example.pulserds_prog7313.viewmodel.SavingsGoalViewModel
import com.example.pulserds_prog7313.viewmodel.SpendingInsightViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class SpendingInsightActivity : BaseActivity() {

    private lateinit var insightViewModel: SpendingInsightViewModel
    private lateinit var expenseViewModel: ExpenseEntryViewModel
    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var savingsViewModel: SavingsGoalViewModel

    // Cached data — AI is triggered once entries + categories are both ready
    private var cachedEntries: List<ExpenseEntry> = emptyList()
    private var cachedCategories: List<Category> = emptyList()
    private var cachedGoals: List<SavingsGoal> = emptyList()
    private var aiTriggered = false
    private var entriesLoaded = false
    private var categoriesLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spending_insight)

        val periodLabel = intent.getStringExtra("periodLabel") ?: "This Month"
        val userId = SessionManager(this).getUserId()

        // -- Toolbar back navigation ------------------------------------------
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // -- ViewModels -------------------------------------------------------
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

        // -- Load data using methods that actually exist ----------------------

        // Entries: use loadEntriesForPeriod with current month start/end dates
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val monthStart = fmt.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val monthEnd = fmt.format(cal.time)

        expenseViewModel.loadEntriesForPeriod(userId, monthStart, monthEnd)
        categoryViewModel.loadCategoriesForUser(userId)

        // Goals: getAllGoalsLive returns LiveData directly — observe it
        savingsViewModel.getAllGoalsLive(userId).observe(this) { goals ->
            cachedGoals = goals ?: emptyList()
        }

        // -- Observe data and trigger AI when both entries + categories ready --
        expenseViewModel.entries.observe(this) { entries ->
            cachedEntries = entries ?: emptyList()
            entriesLoaded = true
            tryTriggerAI(periodLabel)
        }

        categoryViewModel.categories.observe(this) { categories ->
            cachedCategories = categories ?: emptyList()
            categoriesLoaded = true
            tryTriggerAI(periodLabel)
        }

        // -- Observe AI results -----------------------------------------------
        insightViewModel.isLoading.observe(this) { loading ->
            findViewById<ProgressBar>(R.id.aiLoadingSpinner).visibility =
                if (loading) View.VISIBLE else View.GONE
            findViewById<ScrollView>(R.id.insightScrollView).visibility =
                if (loading) View.GONE else View.VISIBLE
        }

        insightViewModel.insight.observe(this) { insight ->
            insight ?: return@observe
            findViewById<TextView>(R.id.txtSummary).text    = insight.summary
            findViewById<TextView>(R.id.txtTopRisk).text    = insight.topRisk
            findViewById<TextView>(R.id.txtWarnings).text   =
                insight.overspendingWarnings.joinToString("\n") { "• $it" }
            findViewById<TextView>(R.id.txtSuggestions).text =
                insight.budgetSuggestions.joinToString("\n") { "💡 $it" }
            findViewById<TextView>(R.id.txtSavingsAdvice).text = insight.savingsAdvice
        }
    }

    /**
     * Only fires the AI call once, and only after both entries and categories
     * have loaded - whichever LiveData observer fires last wins.
     */
    private fun tryTriggerAI(periodLabel: String) {
        if (aiTriggered) return
        // Wait until BOTH LiveData sources have fired at least once.
        // Previously this fired as soon as either one arrived, passing an
        // empty list for the other. Now we wait for both, then call the AI
        // regardless of whether the lists are empty (the AI handles no-data).
        if (!entriesLoaded || !categoriesLoaded) return
        aiTriggered = true
        android.util.Log.d("SpendingInsight", "Triggering AI: ${cachedEntries.size} entries, ${cachedCategories.size} cats, ${cachedGoals.size} goals")
        insightViewModel.loadInsights(cachedEntries, cachedCategories, cachedGoals, periodLabel)
    }
}