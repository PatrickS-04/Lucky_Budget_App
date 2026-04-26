package com.example.pulserds_prog7313.ui

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.DonutChartView
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
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsActivity : BaseActivity() {

    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var expenseViewModel: ExpenseEntryViewModel
    private lateinit var tileAdapter: CategoryTileAdapter
    private lateinit var spendingAdapter: SpendingRowAdapter

    enum class Period { WEEK, MONTH, QUARTER, YEAR, CUSTOM }
    private var activePeriod = Period.MONTH

    private var periodStart: Date = Date()
    private var periodEnd: Date = Date()
    private var periodLabel: String = ""

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

    private val dbDateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var cachedCategories: List<Category> = emptyList()

    // Track last-seen income/expense so balance can be recomputed from either observer
    private var lastIncome: Double = 0.0
    private var lastExpenses: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupViewModels()
        setupCategoryTilesRecycler()
        setupSpendingBreakdownRecycler()
        observeCategories()
        observeEntries()
        setupPeriodChips()
        selectPeriod(Period.MONTH)

        findViewById<MaterialButton>(R.id.exportButton)?.setOnClickListener {
            Toast.makeText(this, "Export coming soon", Toast.LENGTH_SHORT).show()
        }

        setupBottomNav(R.id.navAnalytics)
    }

    // ── View binding ──────────────────────────────────────────────────────────

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
    }

    // ── Period chips ──────────────────────────────────────────────────────────

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

    private fun computeDateRange(period: Period) {
        val cal      = Calendar.getInstance()
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val shortFmt = SimpleDateFormat("d MMM", Locale.getDefault())
        val yearFmt  = SimpleDateFormat("yyyy", Locale.getDefault())

        when (period) {
            Period.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                periodStart = cal.time
                cal.add(Calendar.DAY_OF_WEEK, 6)
                periodEnd = cal.time
                val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
                periodLabel = "${shortFmt.format(start.time)} – ${shortFmt.format(periodEnd)}"
            }
            Period.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                periodStart = cal.time
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                periodEnd = cal.time
                periodLabel = monthFmt.format(periodStart)
            }
            Period.QUARTER -> {
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
                cal.set(Calendar.DAY_OF_YEAR, 1)
                periodStart = cal.time
                cal.set(Calendar.MONTH, Calendar.DECEMBER)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                periodEnd = cal.time
                periodLabel = yearFmt.format(periodStart)
            }
            Period.CUSTOM -> { /* set by showCustomDatePicker */ }
        }
    }

    private fun showCustomDatePicker() {
        val today = Calendar.getInstance()
        DatePickerDialog(this, { _, sy, sm, sd ->
            val startCal = Calendar.getInstance().apply { set(sy, sm, sd, 0, 0, 0) }
            periodStart = startCal.time
            DatePickerDialog(this, { _, ey, em, ed ->
                val endCal = Calendar.getInstance().apply { set(ey, em, ed, 23, 59, 59) }
                if (endCal.before(startCal)) {
                    Toast.makeText(this, "End date must be after start date", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }
                periodEnd = endCal.time
                val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                periodLabel = "${fmt.format(periodStart)} – ${fmt.format(periodEnd)}"
                activePeriod = Period.CUSTOM
                updateChipStyles(Period.CUSTOM)
                heroPeriodLabel.text = periodLabel
                reloadData()
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ── ViewModels ────────────────────────────────────────────────────────────

    private fun setupViewModels() {
        val db = AppDatabase.getDatabase(applicationContext)

        categoryViewModel = ViewModelProvider(
            this, ViewModelFactory { CategoryViewModel(CategoryRepository(db.categoryDao())) }
        )[CategoryViewModel::class.java]

        expenseViewModel = ViewModelProvider(
            this, ViewModelFactory { ExpenseEntryViewModel(ExpenseEntryRepository(db.expenseEntryDao())) }
        )[ExpenseEntryViewModel::class.java]
    }

    // ── RecyclerViews ─────────────────────────────────────────────────────────

    private fun setupCategoryTilesRecycler() {
        tileAdapter = CategoryTileAdapter()
        findViewById<RecyclerView>(R.id.categoryTilesRecycler).apply {
            layoutManager = GridLayoutManager(this@AnalyticsActivity, 2)
            adapter = tileAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupSpendingBreakdownRecycler() {
        spendingAdapter = SpendingRowAdapter()
        findViewById<RecyclerView>(R.id.spendingBreakdownRecycler).apply {
            layoutManager = LinearLayoutManager(this@AnalyticsActivity)
            adapter = spendingAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ── Data loading & observation ────────────────────────────────────────────

    /** Central method: re-triggers all data loads whenever the period changes. */
    private fun reloadData() {
        val userId = SessionManager(this).getUserId()
        if (userId == -1) return

        val startStr = dbDateFmt.format(periodStart)
        val endStr   = dbDateFmt.format(periodEnd)

        // Reload categories (which triggers observeCategories → adapter update)
        categoryViewModel.loadCategoriesForUser(userId)

        // Reload entries for the period (which triggers observeEntries → totals map)
        expenseViewModel.loadEntriesForPeriod(userId, startStr, endStr)

        // Hero income / expense totals via LiveData
        expenseViewModel.getIncomeLive(userId, startStr, endStr).observe(this) { income ->
            lastIncome = income ?: 0.0
            totalIncomeView.text = "R ${ "%,.2f".format(lastIncome) }"
            updateHeroBalance()
        }

        expenseViewModel.getExpensesLive(userId, startStr, endStr).observe(this) { expenses ->
            lastExpenses = kotlin.math.abs(expenses ?: 0.0)
            totalExpensesView.text = "R ${ "%,.2f".format(lastExpenses) }"
            updateHeroBalance()
        }
    }

    /** Observes the category list. When it changes, refresh the tile adapter. */
    private fun observeCategories() {
        categoryViewModel.categories.observe(this) { categories ->
            cachedCategories = categories ?: emptyList()
            // Pass empty totals initially; observeEntries() will supply real totals
            tileAdapter.submitCategories(cachedCategories, emptyMap())
        }
    }

    /**
     * Observes entries loaded by [reloadData]. Computes per-category totals
     * (expenses only, as positive values) and pushes them to both adapters.
     */
    private fun observeEntries() {
        expenseViewModel.entries.observe(this) { entries ->
            val totals: Map<Int, Double> = if (entries.isNullOrEmpty()) {
                emptyMap()
            } else {
                entries
                    .filter { it.amount < 0 }
                    .groupBy { it.categoryId }
                    .mapValues { (_, list) -> list.sumOf { kotlin.math.abs(it.amount) } }
            }
            tileAdapter.submitCategories(cachedCategories, totals)
            spendingAdapter.submitData(cachedCategories, totals)
        }
    }

    /** Recalculates the hero balance card from cached income/expense values. */
    private fun updateHeroBalance() {
        val saved = (lastIncome - lastExpenses).coerceAtLeast(0.0)
        heroBalance.text = "R ${ "%,.2f".format(saved) }"

        if (lastIncome > 0) {
            val savedPct = (saved / lastIncome * 100).coerceIn(0.0, 100.0)
            val expPct   = (lastExpenses / lastIncome * 100).coerceIn(0.0, 100.0)
            heroChangeBadge.text = "Saved ${"%.1f".format(savedPct)}% of income"

            // Update donut chart with real data using DonutChartView's actual API
            val savedColor  = Color.parseColor("#2EC4B6")
            val expColor    = Color.parseColor("#2A1A1A")
            summaryPieChart.setSegments(listOf(
                Pair(savedPct.toFloat(), savedColor),
                Pair(expPct.toFloat(), expColor)
            ))
            summaryPieChart.setCenterLabel(
                "${"%.1f".format(savedPct)}%",
                "Saved"
            )
        } else {
            heroChangeBadge.text = "No income recorded this period"
            summaryPieChart.setSegments(emptyList())
            summaryPieChart.setCenterLabel("—", "No data")
        }
    }
}