package com.example.pulserds_prog7313.ui

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.View
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
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.repository.ExpenseEntryRepository
import com.example.pulserds_prog7313.ui.adapters.CategoryTileAdapter
import com.example.pulserds_prog7313.ui.adapters.SpendingRowAdapter
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ExpenseEntryViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

/**
 * CategoryTotalsActivity
 * ──────────────────────
 * Feature: View category totals in a period  [10 Marks]
 *
 * Displays every user-defined category with its total income/expense amount
 * for the selected period. The period can be one of:
 *   Week · Month · Quarter · Year · Custom (date-picker range)
 *
 * Layout  : activity_category_totals.xml
 * Adapters:
 *   • CategoryTileAdapter   → categoryTilesRecycler  (2-col grid)
 *   • SpendingRowAdapter    → spendingBreakdownRecycler (vertical list)
 * ViewModels:
 *   • CategoryViewModel     → all categories for the logged-in user
 *   • ExpenseEntryViewModel → period-filtered entries, income total, expense total
 */
class CategoryTotalsActivity : BaseActivity() {

    // ── ViewModels ────────────────────────────────────────────────────────────
    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var expenseViewModel: ExpenseEntryViewModel

    // ── Adapters ──────────────────────────────────────────────────────────────
    private lateinit var tileAdapter: CategoryTileAdapter
    private lateinit var spendingAdapter: SpendingRowAdapter

    // ── Period state ──────────────────────────────────────────────────────────
    enum class Period { WEEK, MONTH, QUARTER, YEAR, CUSTOM }
    private var activePeriod = Period.MONTH

    private var periodStart: Date = Date()
    private var periodEnd: Date   = Date()
    private var periodLabel: String = ""

    // ── View references ───────────────────────────────────────────────────────
    private lateinit var chipWeek: TextView
    private lateinit var chipMonth: TextView
    private lateinit var chipQuarter: TextView
    private lateinit var chipYear: TextView
    private lateinit var chipCustom: TextView

    private lateinit var heroPeriodLabel: TextView
    private lateinit var totalIncomeView: TextView
    private lateinit var totalExpensesView: TextView
    private lateinit var emptyStateText: TextView

    // ── Date formatting ───────────────────────────────────────────────────────
    private val dbDateFmt   = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val shortFmt    = SimpleDateFormat("d MMM",      Locale.getDefault())
    private val monthFmt    = SimpleDateFormat("MMMM yyyy",  Locale.getDefault())
    private val yearFmt     = SimpleDateFormat("yyyy",       Locale.getDefault())
    private val longFmt     = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    // ── Cached data (both must be present before adapters update) ─────────────
    private var cachedCategories: List<Category> = emptyList()
    private var cachedTotals: Map<Int, Double>   = emptyMap()

    // ── Swappable LiveData observers (removed & re-added on every period change)
    private var incomeLiveData: LiveData<Double>?   = null
    private var expensesLiveData: LiveData<Double>? = null

    private val incomeObserver = Observer<Double> { income ->
        val value = income ?: 0.0
        totalIncomeView.text = "R ${"%,.2f".format(value)}"
    }
    private val expensesObserver = Observer<Double> { expenses ->
        val value = kotlin.math.abs(expenses ?: 0.0)
        totalExpensesView.text = "R ${"%,.2f".format(value)}"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_category_totals)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bindViews()
        setupViewModels()
        setupCategoryTilesRecycler()
        setupSpendingBreakdownRecycler()
        observeCategories()
        observeEntries()
        setupPeriodChips()

        // Default to Month on first launch
        selectPeriod(Period.MONTH)

        setupBottomNav(R.id.navAnalytics)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────────

    private fun bindViews() {
        chipWeek      = findViewById(R.id.chipWeek)
        chipMonth     = findViewById(R.id.chipMonth)
        chipQuarter   = findViewById(R.id.chipQuarter)
        chipYear      = findViewById(R.id.chipYear)
        chipCustom    = findViewById(R.id.chipCustom)

        heroPeriodLabel   = findViewById(R.id.heroPeriodLabel)
        totalIncomeView   = findViewById(R.id.totalIncome)
        totalExpensesView = findViewById(R.id.totalExpenses)
        emptyStateText    = findViewById(R.id.emptyStateText)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewModels
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupViewModels() {
        val db = AppDatabase.getDatabase(applicationContext)

        categoryViewModel = ViewModelProvider(
            this, ViewModelFactory { CategoryViewModel(CategoryRepository(db.categoryDao())) }
        )[CategoryViewModel::class.java]

        expenseViewModel = ViewModelProvider(
            this, ViewModelFactory { ExpenseEntryViewModel(ExpenseEntryRepository(db.expenseEntryDao())) }
        )[ExpenseEntryViewModel::class.java]
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RecyclerViews
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupCategoryTilesRecycler() {
        tileAdapter = CategoryTileAdapter()
        findViewById<RecyclerView>(R.id.categoryTilesRecycler).apply {
            layoutManager = GridLayoutManager(this@CategoryTotalsActivity, 2)
            adapter = tileAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupSpendingBreakdownRecycler() {
        spendingAdapter = SpendingRowAdapter()
        findViewById<RecyclerView>(R.id.spendingBreakdownRecycler).apply {
            layoutManager = LinearLayoutManager(this@CategoryTotalsActivity)
            adapter = spendingAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Period chip setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupPeriodChips() {
        chipWeek.setOnClickListener    { selectPeriod(Period.WEEK) }
        chipMonth.setOnClickListener   { selectPeriod(Period.MONTH) }
        chipQuarter.setOnClickListener { selectPeriod(Period.QUARTER) }
        chipYear.setOnClickListener    { selectPeriod(Period.YEAR) }
        chipCustom.setOnClickListener  { showCustomDatePicker() }
    }

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

    // ─────────────────────────────────────────────────────────────────────────
    // Date-range computation
    // ─────────────────────────────────────────────────────────────────────────

    private fun computeDateRange(period: Period) {
        val cal = Calendar.getInstance()

        when (period) {
            Period.WEEK -> {
                // Monday → Sunday of the current week
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                periodStart = cal.time
                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
                periodEnd = cal.time
                val startCal = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
                periodLabel = "${shortFmt.format(startCal.time)} – ${shortFmt.format(periodEnd)}"
            }

            Period.MONTH -> {
                // 1st → last day of the current month
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                periodStart = cal.time
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
                periodEnd = cal.time
                periodLabel = monthFmt.format(periodStart)
            }

            Period.QUARTER -> {
                // First day of the current quarter → last day
                val month  = cal.get(Calendar.MONTH)
                val qStart = (month / 3) * 3
                cal.set(Calendar.MONTH, qStart)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                periodStart = cal.time
                cal.add(Calendar.MONTH, 3)
                cal.add(Calendar.DAY_OF_MONTH, -1)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
                periodEnd = cal.time
                periodLabel = "Q${(qStart / 3) + 1} ${yearFmt.format(periodStart)}"
            }

            Period.YEAR -> {
                // 1 Jan → 31 Dec of the current year
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                periodStart = cal.time
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
                periodEnd = cal.time
                periodLabel = yearFmt.format(periodStart)
            }

            Period.CUSTOM -> { /* dates set directly by showCustomDatePicker */ }
        }
    }

    /**
     * Two-step DatePickerDialog: pick start date, then pick end date.
     * Validates that end ≥ start before accepting the range.
     */
    private fun showCustomDatePicker() {
        val today = Calendar.getInstance()
        DatePickerDialog(this, { _, sy, sm, sd ->
            val startCal = Calendar.getInstance().apply {
                set(sy, sm, sd, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            periodStart = startCal.time

            DatePickerDialog(this, { _, ey, em, ed ->
                val endCal = Calendar.getInstance().apply {
                    set(ey, em, ed, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                if (endCal.before(startCal)) {
                    Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }
                periodEnd   = endCal.time
                periodLabel = "${longFmt.format(periodStart)} – ${longFmt.format(periodEnd)}"

                activePeriod = Period.CUSTOM
                updateChipStyles(Period.CUSTOM)
                heroPeriodLabel.text = periodLabel
                reloadData()

            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()

        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data loading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Re-triggers all DB queries whenever the period changes.
     *
     * Income/Expense observers are detached before new ones are attached so
     * that switching periods never accumulates stale duplicate observers.
     */
    private fun reloadData() {
        val userId = SessionManager(this).getUserId()
        if (userId == -1) return

        val startStr = dbDateFmt.format(periodStart)
        val endStr   = dbDateFmt.format(periodEnd)

        // Entries (drives cachedTotals via observeEntries)
        categoryViewModel.loadCategoriesForUser(userId)
        expenseViewModel.loadEntriesForPeriod(userId, startStr, endStr)

        // Income / expense totals for the hero card
        incomeLiveData?.removeObserver(incomeObserver)
        expensesLiveData?.removeObserver(expensesObserver)

        incomeLiveData = expenseViewModel.getIncomeLive(userId, startStr, endStr).also {
            it.observe(this, incomeObserver)
        }
        expensesLiveData = expenseViewModel.getExpensesLive(userId, startStr, endStr).also {
            it.observe(this, expensesObserver)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LiveData observers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Observes the full category list.
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
     * Observes period-filtered expense entries.
     * Computes per-category spend totals (absolute value of negative amounts),
     * caches them, and delegates to [pushAdapterUpdates].
     */
    private fun observeEntries() {
        expenseViewModel.entries.observe(this) { entries ->
            cachedTotals = if (entries.isNullOrEmpty()) {
                emptyMap()
            } else {
                entries
                    .filter { it.amount < 0 }          // expenses are stored as negatives
                    .groupBy { it.categoryId }
                    .mapValues { (_, list) ->
                        list.sumOf { kotlin.math.abs(it.amount) }
                    }
            }
            pushAdapterUpdates()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Adapter updates
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Single source of truth for updating both RecyclerView adapters.
     *
     * Always reads [cachedCategories] + [cachedTotals] so the result is
     * consistent regardless of which LiveData observer fires first.
     *
     *  • Category Tiles  — every category (EXPENSE, INCOME, BOTH)
     *  • Spending Rows   — EXPENSE/BOTH categories; falls back to showing all
     *                      expense categories with R 0 if no spend exists yet
     *
     * The empty-state message is shown when there are no categories at all.
     */
    private fun pushAdapterUpdates() {
        if (cachedCategories.isEmpty()) {
            emptyStateText.visibility = View.VISIBLE
            return
        }
        emptyStateText.visibility = View.GONE

        // All categories → tile grid
        tileAdapter.submitCategories(cachedCategories, cachedTotals)

        // EXPENSE/BOTH categories → breakdown list
        val expenseCategories = cachedCategories.filter { it.type != "INCOME" }
        val withSpend         = expenseCategories.filter { (cachedTotals[it.id] ?: 0.0) > 0.0 }
        val breakdownList     = withSpend.ifEmpty { expenseCategories }
        spendingAdapter.submitData(breakdownList, cachedTotals)
    }
}