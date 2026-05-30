package com.example.pulserds_prog7313.ui

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import kotlin.jvm.java
import android.content.Intent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.DonutChartView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.repository.ExpenseEntryRepository
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.ui.adapters.AccountSpendingAdapter
import com.example.pulserds_prog7313.ui.adapters.CategoryTileAdapter
import com.example.pulserds_prog7313.ui.adapters.SpendingRowAdapter
import com.example.pulserds_prog7313.utils.CurrencyManager
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ExpenseEntryViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.example.pulserds_prog7313.utils.AnalyticsPdfExporter
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

/**
 * AnalyticsActivity
 * -----------------
 * Main analytics screen that gives the user a financial overview for a
 * selected time period (Week, Month, Quarter, Year, or Custom range).
 *
 * Displays:
 *   - Hero balance card with income, expense, and saved totals.
 *   - Donut chart summarising saved vs. spent percentages.
 *   - Category tile grid (all categories).
 *   - Spending breakdown list (expense/both categories only).
 *   - Per-bank-account spending breakdown.
 *
 * Layout   : activity_analytics.xml
 * Adapters :
 *   - CategoryTileAdapter      -> categoryTilesRecycler      (2-col grid)
 *   - SpendingRowAdapter       -> spendingBreakdownRecycler  (vertical list)
 *   - AccountSpendingAdapter   -> accountsBreakdownRecycler  (vertical list)
 * ViewModels:
 *   - CategoryViewModel        -> all categories for the logged-in user
 *   - ExpenseEntryViewModel    -> period-filtered entries, income/expense totals
 *   - BankAccountViewModel     -> bank accounts for per-account spending cross-ref
 */
class AnalyticsActivity : BaseActivity() {

    // -- ViewModels -----------------------------------------------------------
    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var expenseViewModel: ExpenseEntryViewModel

    /** Provides the account list needed to label per-account spending rows. */
    private lateinit var bankAccountViewModel: BankAccountViewModel

    // -- Adapters -------------------------------------------------------------
    private lateinit var tileAdapter: CategoryTileAdapter
    private lateinit var spendingAdapter: SpendingRowAdapter
    private lateinit var accountSpendingAdapter: AccountSpendingAdapter

    // -- Period state ---------------------------------------------------------
    /** Represents the available reporting periods the user can select. */
    enum class Period { WEEK, MONTH, QUARTER, YEAR, CUSTOM }

    /** The currently active period; defaults to MONTH on launch. */
    private var activePeriod = Period.MONTH

    /** Start and end boundaries of the current period window. */
    private var periodStart: Date = Date()
    private var periodEnd: Date = Date()

    /** Human-readable label shown in the hero card (e.g. "April 2025", "Q2 2025"). */
    private var periodLabel: String = ""

    // -- View references ------------------------------------------------------
    private lateinit var chipWeek: TextView
    private lateinit var chipMonth: TextView
    private lateinit var chipQuarter: TextView
    private lateinit var chipYear: TextView
    private lateinit var chipCustom: TextView

    private lateinit var heroPeriodLabel: TextView
    private lateinit var heroBalance: TextView
    private lateinit var heroChangeBadge: TextView
    private lateinit var totalIncomeView: TextView
    private lateinit var totalExpensesView: TextView
    private lateinit var summaryPieChart: DonutChartView
    private lateinit var legendIncomeValue: TextView
    private lateinit var legendExpensesValue: TextView
    private lateinit var legendSavedValue: TextView

    // -- Date formatting ------------------------------------------------------
    /** Formatter used to convert period boundaries into DB query strings. */
    private val dbDateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // -- Spending goal chart views -------------------------------------------
    private lateinit var spendingGoalChart: com.example.pulserds_prog7313.SpendingGoalChartView
    private lateinit var spendingGoalStatus: TextView
    private lateinit var spendingGoalSelectorLabel: TextView

    /** ViewModel for loading savings goals (which carry min/maxSpendingGoal). */
    private lateinit var savingsGoalViewModel: com.example.pulserds_prog7313.viewmodel.SavingsGoalViewModel

    // -- Cached income/expense values -----------------------------------------
    // Both values are kept so the hero balance can be recomputed from either
    // observer regardless of which one fires last.
    private var lastIncome: Double = 0.0
    private var lastExpenses: Double = 0.0

    // -- Cached data (both must be present before adapters update) ------------
    /** Full list of categories for the logged-in user. */
    private var cachedCategories: List<Category> = emptyList()

    /** Map of categoryId -> total spend amount for the current period. */
    private var cachedTotals: Map<String, Double> = emptyMap()

    // -- Swappable LiveData observers -----------------------------------------
    // References are kept so old observers can be removed before new ones are
    // attached whenever the user switches periods, preventing duplicate callbacks.
    // [3] Google, "LiveData overview," Android Developers, 2024.
    private var incomeLiveData: LiveData<Double>? = null
    private var expensesLiveData: LiveData<Double>? = null

    /** Updates the income label and triggers a hero balance recalculation. */
    private val incomeObserver = Observer<Double> { income ->
        lastIncome = income ?: 0.0
        totalIncomeView.text = CurrencyManager.format(this, lastIncome)
        updateHeroBalance()
    }

    /**
     * Updates the expense label (stored as negatives, displayed as positive)
     * and triggers a hero balance recalculation.
     */
    private val expensesObserver = Observer<Double> { expenses ->
        lastExpenses = kotlin.math.abs(expenses ?: 0.0)
        totalExpensesView.text = CurrencyManager.format(this, lastExpenses)
        updateHeroBalance()
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        // Adjust root layout padding so content is not obscured by system bars
        // (status bar, navigation bar) on edge-to-edge displays.
        val navBarContainer = findViewById<LinearLayout>(R.id.containerForBottomNavBar)

        ViewCompat.setOnApplyWindowInsetsListener(navBarContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val basePadding = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(bars.left, bars.top, bars.right, basePadding + bars.bottom)
            insets
        }

        bindViews()
        setupViewModels()
        setupCategoryTilesRecycler()
        setupSpendingBreakdownRecycler()
        setupAccountSpendingRecycler()
        observeCategories()
        observeEntries()
        observeAccountSpending()
        setupPeriodChips()
        selectPeriod(Period.MONTH) // Default to Month view on first launch.

        findViewById<MaterialButton>(R.id.exportButton)?.setOnClickListener {
            exportAnalyticsPdf()
        }

        // "See all categories" link navigates to the dedicated CategoryTotalsActivity.
        findViewById<TextView>(R.id.seeAllCategories)?.setOnClickListener {
            startActivity(android.content.Intent(this, CategoryTotalsActivity::class.java))
        }

        setupBottomNav(R.id.navAnalytics)
    }

    // -------------------------------------------------------------------------
    // View binding
    // Centralises all findViewById calls so onCreate stays clean and readable.
    // -------------------------------------------------------------------------

    private fun bindViews() {
        chipWeek     = findViewById(R.id.chipWeek)
        chipMonth    = findViewById(R.id.chipMonth)
        chipQuarter  = findViewById(R.id.chipQuarter)
        chipYear     = findViewById(R.id.chipYear)
        chipCustom   = findViewById(R.id.chipCustom)

        heroPeriodLabel   = findViewById(R.id.heroPeriodLabel)
        heroBalance       = findViewById(R.id.heroBalance)
        heroChangeBadge   = findViewById(R.id.heroChangeBadge)
        totalIncomeView   = findViewById(R.id.totalIncome)
        totalExpensesView = findViewById(R.id.totalExpenses)
        summaryPieChart   = findViewById(R.id.summaryPieChart)
        legendIncomeValue   = findViewById(R.id.legendIncomeValue)
        legendExpensesValue = findViewById(R.id.legendExpensesValue)
        legendSavedValue    = findViewById(R.id.legendSavedValue)
        spendingGoalChart         = findViewById(R.id.spendingGoalChart)
        spendingGoalStatus        = findViewById(R.id.spendingGoalStatus)
        spendingGoalSelectorLabel = findViewById(R.id.spendingGoalSelectorLabel)
    }

    // -------------------------------------------------------------------------
    // Period chips
    // -------------------------------------------------------------------------

    /** Attaches click listeners to each period chip. */
    private fun setupPeriodChips() {
        chipWeek.setOnClickListener    { selectPeriod(Period.WEEK) }
        chipMonth.setOnClickListener   { selectPeriod(Period.MONTH) }
        chipQuarter.setOnClickListener { selectPeriod(Period.QUARTER) }
        chipYear.setOnClickListener    { selectPeriod(Period.YEAR) }
        chipCustom.setOnClickListener  { showCustomDatePicker() }
    }

    /**
     * Switches the active period: updates chip styles, recomputes the date
     * range, refreshes the hero label, and triggers a data reload.
     */
    private fun selectPeriod(period: Period) {
        activePeriod = period
        updateChipStyles(period)
        computeDateRange(period)
        heroPeriodLabel.text = periodLabel
        reloadData()
    }

    /**
     * Highlights the active chip in darkerGreen and resets the rest to
     * backgroundOrange, matching the style used throughout the app.
     */
    private fun updateChipStyles(active: Period) {
        val chips = mapOf(
            Period.WEEK    to chipWeek,
            Period.MONTH   to chipMonth,
            Period.QUARTER to chipQuarter,
            Period.YEAR    to chipYear,
            Period.CUSTOM  to chipCustom
        )
        chips.forEach { (period, chip) ->
            if (period == active) {
                chip.setBackgroundColor(getColor(R.color.darkerGreen))
                chip.setTextColor(Color.parseColor("#1A1A1A"))
                chip.paint.isFakeBoldText = true
            } else {
                chip.setBackgroundColor(getColor(R.color.backgroundOrange))
                chip.setTextColor(getColor(R.color.very_darkGreen))
                chip.paint.isFakeBoldText = false
            }
        }
    }

    // -------------------------------------------------------------------------
    // Date-range computation
    // -------------------------------------------------------------------------

    /**
     * Sets [periodStart], [periodEnd], and [periodLabel] for the given [period].
     * All non-CUSTOM periods compute their boundaries relative to today's date.
     * CUSTOM is a no-op here - its dates are set directly by [showCustomDatePicker].
     */
    private fun computeDateRange(period: Period) {
        val cal      = Calendar.getInstance()
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault()) // e.g. "April 2025"
        val shortFmt = SimpleDateFormat("d MMM",     Locale.getDefault()) // e.g. "1 Apr"
        val yearFmt  = SimpleDateFormat("yyyy",      Locale.getDefault()) // e.g. "2025"

        when (period) {
            Period.WEEK -> {
                // Monday -> Sunday of the current week.
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                periodStart = cal.time
                cal.add(Calendar.DAY_OF_WEEK, 6)
                periodEnd = cal.time
                val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
                periodLabel = "${shortFmt.format(start.time)} - ${shortFmt.format(periodEnd)}"
            }

            Period.MONTH -> {
                // 1st -> last day of the current month.
                cal.set(Calendar.DAY_OF_MONTH, 1)
                periodStart = cal.time
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                periodEnd = cal.time
                periodLabel = monthFmt.format(periodStart)
            }

            Period.QUARTER -> {
                // First day of the current quarter -> last day.
                // Quarter derived by grouping months: Jan-Mar = Q1, Apr-Jun = Q2, etc.
                val month = cal.get(Calendar.MONTH)
                val qStart = (month / 3) * 3
                cal.set(Calendar.MONTH, qStart)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                periodStart = cal.time
                cal.add(Calendar.MONTH, 3)
                cal.add(Calendar.DAY_OF_MONTH, -1)
                periodEnd = cal.time
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.MONTH, qStart); set(Calendar.DAY_OF_MONTH, 1)
                }
                periodLabel = "Q${(qStart / 3) + 1} ${yearFmt.format(startCal.time)}"
            }

            Period.YEAR -> {
                // 1 Jan -> 31 Dec of the current year.
                cal.set(Calendar.DAY_OF_YEAR, 1)
                periodStart = cal.time
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                periodEnd = cal.time
                periodLabel = yearFmt.format(periodStart)
            }

            Period.CUSTOM -> { /* Dates set directly by showCustomDatePicker - nothing to compute here. */ }
        }
    }

    /**
     * Two-step DatePickerDialog: pick start date, then pick end date.
     * Validates that end >= start before accepting the range.
     */
    private fun showCustomDatePicker() {
        val today = Calendar.getInstance()
        DatePickerDialog(this, { _, sy, sm, sd ->
            // Step 1: User selects the start date.
            val startCal = Calendar.getInstance().apply { set(sy, sm, sd, 0, 0, 0) }
            periodStart = startCal.time

            // Step 2: User selects the end date.
            DatePickerDialog(this, { _, ey, em, ed ->
                val endCal = Calendar.getInstance().apply { set(ey, em, ed, 23, 59, 59) }

                // Guard: reject a range where the end date precedes the start date.
                if (endCal.before(startCal)) {
                    Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }
                periodEnd = endCal.time
                val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                periodLabel = "${fmt.format(periodStart)} - ${fmt.format(periodEnd)}"

                activePeriod = Period.CUSTOM
                updateChipStyles(Period.CUSTOM)
                heroPeriodLabel.text = periodLabel
                reloadData()
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
    }

    // -------------------------------------------------------------------------
    // ViewModels
    // Build the dependency chain: Database -> DAO -> Repository -> ViewModel.
    // -------------------------------------------------------------------------

    private fun setupViewModels() {
        categoryViewModel = ViewModelProvider(
            this, ViewModelFactory { CategoryViewModel(CategoryRepository()) }
        )[CategoryViewModel::class.java]

        expenseViewModel = ViewModelProvider(
            this, ViewModelFactory { ExpenseEntryViewModel(ExpenseEntryRepository()) }
        )[ExpenseEntryViewModel::class.java]

        bankAccountViewModel = ViewModelProvider(
            this, ViewModelFactory { BankAccountViewModel(BankAccountRepository()) }
        )[BankAccountViewModel::class.java]

        val userId = SessionManager(this).getUserId()
        if (userId.isNotBlank()) bankAccountViewModel.init(userId)

        savingsGoalViewModel = ViewModelProvider(
            this, ViewModelFactory { com.example.pulserds_prog7313.viewmodel.SavingsGoalViewModel(com.example.pulserds_prog7313.repository.SavingsGoalRepository()) }
        )[com.example.pulserds_prog7313.viewmodel.SavingsGoalViewModel::class.java]
    }

    // -------------------------------------------------------------------------
    // RecyclerViews
    // -------------------------------------------------------------------------

    /** Sets up the 2-column grid that shows a tile per category. */
    private fun setupCategoryTilesRecycler() {
        tileAdapter = CategoryTileAdapter()
        findViewById<RecyclerView>(R.id.categoryTilesRecycler).apply {
            layoutManager = GridLayoutManager(this@AnalyticsActivity, 2)
            adapter = tileAdapter
            isNestedScrollingEnabled = false // Parent ScrollView handles scrolling.
        }
    }

    /** Sets up the vertical list that shows a spending row per expense category. */
    private fun setupSpendingBreakdownRecycler() {
        spendingAdapter = SpendingRowAdapter()
        findViewById<RecyclerView>(R.id.spendingBreakdownRecycler).apply {
            layoutManager = LinearLayoutManager(this@AnalyticsActivity)
            adapter = spendingAdapter
            isNestedScrollingEnabled = false // Parent ScrollView handles scrolling.
        }
    }

    /** Sets up the vertical list that shows spending grouped by bank account. */
    private fun setupAccountSpendingRecycler() {
        accountSpendingAdapter = AccountSpendingAdapter()
        findViewById<RecyclerView>(R.id.accountsBreakdownRecycler).apply {
            layoutManager = LinearLayoutManager(this@AnalyticsActivity)
            adapter = accountSpendingAdapter
            isNestedScrollingEnabled = false // Parent ScrollView handles scrolling.
        }
    }

    // -------------------------------------------------------------------------
    // Data loading & observation
    // -------------------------------------------------------------------------

    /**
     * Central method: re-triggers all data loads whenever the period changes.
     *
     * Income/Expense LiveData observers are removed before being re-added so
     * changing the period never stacks up duplicate observers that fire stale data.
     */
    private fun reloadData() {
        val userId = SessionManager(this).getUserId()
        if (userId.isBlank()) return

        val startStr = dbDateFmt.format(periodStart)
        val endStr   = dbDateFmt.format(periodEnd)

        // Reload categories for the current user.
        categoryViewModel.loadCategoriesForUser(userId)

        // Reload period-filtered entries (triggers observeEntries -> cachedTotals update).
        expenseViewModel.loadEntriesForPeriod(userId, startStr, endStr)

        // Reload per-account spending totals for the Spending by Account card.
        expenseViewModel.loadExpensesByAccount(userId, startStr, endStr)

        // Detach stale income/expense observers before attaching new ones for the
        // new period, preventing duplicate callbacks from the old LiveData instances.
        incomeLiveData?.removeObserver(incomeObserver)
        expensesLiveData?.removeObserver(expensesObserver)

        incomeLiveData = expenseViewModel.getIncomeLive(userId, startStr, endStr).also {
            it.observe(this, incomeObserver)
        }
        expensesLiveData = expenseViewModel.getExpensesLive(userId, startStr, endStr).also {
            it.observe(this, expensesObserver)
        }

        // Spending-goals chart always shows the past calendar month
        loadSpendingGoalChart(userId)
    }

    /**
     * Permanent observer on the category list for the logged-in user.
     * Caches it and calls [pushAdapterUpdates] so any pending totals are
     * applied immediately even if entries arrived first.
     */
    private fun observeCategories() {
        categoryViewModel.categories.observe(this) { categories ->
            cachedCategories = categories ?: emptyList()
            pushAdapterUpdates()
        }
    }

    /**
     * Permanent observer on the period-filtered expense entries.
     * Computes per-category spend totals (absolute value of negative amounts),
     * caches them, and delegates to [pushAdapterUpdates] - eliminating the race
     * condition where cachedCategories was still empty when entries arrived first.
     */
    private fun observeEntries() {
        expenseViewModel.entries.observe(this) { entries ->
            cachedTotals = if (entries.isNullOrEmpty()) {
                emptyMap()
            } else {
                entries
                    .filter { it.amount < 0 }          // Expenses are stored as negative values.
                    .groupBy { it.categoryId }
                    .mapValues { (_, list) -> list.sumOf { kotlin.math.abs(it.amount) } }
            }
            pushAdapterUpdates()
        }
    }

    /**
     * Single source of truth for updating both category RecyclerView adapters.
     *
     * Always reads [cachedCategories] + [cachedTotals] so the result is
     * consistent regardless of which LiveData observer fires first.
     *
     *  - Category Tiles  - every category (EXPENSE, INCOME, BOTH).
     *  - Spending Rows   - EXPENSE/BOTH categories only; falls back to showing
     *                      all expense categories with R 0 if none have spend yet.
     */
    private fun pushAdapterUpdates() {
        // Pass all categories to the tile grid regardless of type.
        tileAdapter.submitCategories(cachedCategories, cachedTotals)

        // The spending breakdown excludes pure income categories (e.g. Salary).
        val expenseCategories = cachedCategories.filter { it.type != "INCOME" }
        val withSpend = expenseCategories.filter { (cachedTotals[it.id] ?: 0.0) > 0.0 }

        // If no categories have recorded spend yet, fall back to all expense
        // categories so the list is never empty.
        val breakdownList = withSpend.ifEmpty { expenseCategories }
        spendingAdapter.submitData(breakdownList, cachedTotals)
    }

    /**
     * Observes per-account spending and cross-references with the account list
     * from [bankAccountViewModel] to populate the Spending by Account card.
     *
     * Both observers update the adapter so the card stays correct whichever
     * LiveData (accounts or spending) emits a new value first.
     */
    private fun observeAccountSpending() {
        expenseViewModel.accountSpending.observe(this) { spending ->
            val accounts = bankAccountViewModel.accounts.value ?: emptyList()
            accountSpendingAdapter.submitData(accounts, spending ?: emptyList())
        }
        bankAccountViewModel.accounts.observe(this) { accounts ->
            val spending = expenseViewModel.accountSpending.value ?: emptyList()
            accountSpendingAdapter.submitData(accounts ?: emptyList(), spending)
        }
    }

    /**
     * Recalculates and renders the hero balance card from the cached
     * [lastIncome] and [lastExpenses] values.
     *
     * Also updates:
     *   - The legend labels (income, expenses, saved).
     *   - The donut chart segments and centre label (saved % vs spent %).
     *   - The hero change badge text.
     *
     * When no income has been recorded, the chart is cleared and a
     * "No income recorded" message is shown instead.
     */
    private fun updateHeroBalance() {
        // Saved = income minus expenses, floored at zero (cannot save a negative amount).
        val saved = (lastIncome - lastExpenses).coerceAtLeast(0.0)
        heroBalance.text = CurrencyManager.format(this, saved)

        legendIncomeValue.text = CurrencyManager.format(this, lastIncome)
        legendExpensesValue.text = CurrencyManager.format(this, lastExpenses)
        legendSavedValue.text = CurrencyManager.format(this, saved)

        if (lastIncome > 0) {
            // Compute saved and expense percentages, clamped to [0, 100] to avoid
            // chart rendering issues when expenses exceed income.
            val savedPct = (saved / lastIncome * 100).coerceIn(0.0, 100.0)
            val expPct   = (lastExpenses / lastIncome * 100).coerceIn(0.0, 100.0)
            heroChangeBadge.text = "Saved ${"%.1f".format(savedPct)}% of income"

            val savedColor = Color.parseColor("#2EC4B6")
            val expColor   = Color.parseColor("#2A1A1A")

            // Draw two donut segments: saved (teal) and spent (dark).
            summaryPieChart.setSegments(listOf(
                Pair(savedPct.toFloat(), savedColor),
                Pair(expPct.toFloat(), expColor)
            ))
            summaryPieChart.setCenterLabel(
                "${"%.1f".format(savedPct)}%",
                "Saved"
            )
        } else {
            // No income recorded - clear the chart and show a placeholder message.
            heroChangeBadge.text = "No income recorded this period"
            summaryPieChart.setSegments(emptyList())
            summaryPieChart.setCenterLabel("-", "No data")
        }
    }

    private fun launchAiInsights() {
        val intent = Intent(this, SpendingInsightActivity::class.java).apply {
            putExtra("periodLabel", periodLabel)
        }
        startActivity(intent)
    }

    private fun exportAnalyticsPdf() {
        val accounts       = bankAccountViewModel.accounts.value ?: emptyList()
        val accountSpend   = expenseViewModel.accountSpending.value ?: emptyList()
        val shareIntent    = AnalyticsPdfExporter.export(
            context        = this,
            periodLabel    = periodLabel,
            income         = lastIncome,
            expenses       = lastExpenses,
            categories     = cachedCategories,
            totals         = cachedTotals,
            accounts       = accounts,
            accountSpending = accountSpend
        )
        startActivity(Intent.createChooser(shareIntent, "Share Analytics Report"))
    }


    // -------------------------------------------------------------------------
    // Spending Goal Chart
    // -------------------------------------------------------------------------

    /**
     * Loads per-day expense totals for the current calendar month and the
     * aggregate min/max spending goals from SavingsGoals, then renders
     * SpendingGoalChartView with a tinted goal band and a status summary.
     *
     * The chart is always month-scoped as required by the PoE spec.
     */
    private fun loadSpendingGoalChart(userId: String) {
        if (userId.isBlank()) return

        val cal      = java.util.Calendar.getInstance()
        val lastDay  = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val month    = cal.get(java.util.Calendar.MONTH) + 1
        val year     = cal.get(java.util.Calendar.YEAR)
        val startStr = "01/%02d/%d".format(month, year)
        val endStr   = "%02d/%02d/%d".format(lastDay, month, year)

        // LiveData stream: expense entries filtered to this calendar month
        expenseViewModel.getEntriesForPeriodLive(userId, startStr, endStr)
            .observe(this) { entries ->

                // Bucket absolute expense amounts by day-of-month (0-indexed)
                val dailyTotals = DoubleArray(lastDay) { 0.0 }
                val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                entries?.forEach { entry ->
                    if (entry.amount < 0) {
                        try {
                            val d    = fmt.parse(entry.date) ?: return@forEach
                            val cal2 = java.util.Calendar.getInstance().also { c -> c.time = d }
                            val day  = cal2.get(java.util.Calendar.DAY_OF_MONTH) - 1
                            if (day in 0 until lastDay)
                                dailyTotals[day] += kotlin.math.abs(entry.amount)
                        } catch (e: Exception) { /* skip malformed dates */ }
                    }
                }

                // LiveData stream: savings goals that carry a min/max spending band
                savingsGoalViewModel.getAllGoalsLive(userId)
                    .observe(this) { goals ->

                        val withBand = goals?.filter {
                            it.minSpendingGoal > 0 || it.maxSpendingGoal > 0
                        } ?: emptyList()

                        // Convert monthly totals to per-day averages for chart lines
                        val sumMin   = withBand.sumOf { it.minSpendingGoal }
                        val sumMax   = withBand.sumOf { it.maxSpendingGoal }
                        val dailyMin = if (lastDay > 0) sumMin / lastDay else 0.0
                        val dailyMax = if (lastDay > 0) sumMax / lastDay else 0.0

                        spendingGoalChart.setData(
                            dailyAmounts = dailyTotals.toList(),
                            minGoal      = dailyMin,
                            maxGoal      = if (dailyMax > dailyMin) dailyMax else 0.0
                        )

                        // Goal selector label — lists the goals that have a band set
                        val names = withBand.joinToString(", ") { it.name }
                        spendingGoalSelectorLabel.text =
                            if (names.isNotBlank()) "Goals: $names"
                            else "Set min/max goals in a Savings Goal to see your band"

                        // Status summary (days elapsed so far this month)
                        val todayDay   = java.util.Calendar.getInstance()
                            .get(java.util.Calendar.DAY_OF_MONTH)
                        val elapsed    = minOf(todayDay, lastDay)
                        val activeDays = dailyTotals.take(elapsed)

                        spendingGoalStatus.text = when {
                            activeDays.isEmpty() || activeDays.sum() == 0.0 ->
                                "No spending recorded this month"
                            dailyMax <= 0 ->
                                "${CurrencyManager.format(this@AnalyticsActivity, activeDays.sum())} spent — add min/max goals to see your band"
                            else -> {
                                val within  = activeDays.count { it in dailyMin..dailyMax }
                                val overMax = activeDays.count { it > dailyMax }
                                val pct     = (within.toDouble() / elapsed * 100).toInt()
                                when {
                                    pct >= 80 -> "On track! $pct% of days within your spending goal band"
                                    overMax > elapsed / 2 -> "$overMax day(s) over budget — consider cutting back"
                                    else -> "$within/$elapsed days within goal band ($pct%)"
                                }
                            }
                        }
                    }
            }
    }
}
