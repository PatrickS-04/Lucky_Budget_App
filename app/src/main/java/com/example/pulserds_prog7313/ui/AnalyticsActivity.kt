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
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.ui.adapters.CategoryTileAdapter
import com.example.pulserds_prog7313.ui.adapters.SpendingRowAdapter
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsActivity : BaseActivity() {

    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var tileAdapter: CategoryTileAdapter
    private lateinit var spendingAdapter: SpendingRowAdapter

    // Tracks the active period selection
    enum class Period { WEEK, MONTH, QUARTER, YEAR, CUSTOM }
    private var activePeriod = Period.MONTH

    // Date range currently applied — updated whenever a chip is tapped
    private var periodStart: Date = Date()
    private var periodEnd: Date = Date()

    // Label shown in the hero card (e.g. "April 2026", "17 Apr – 23 Apr")
    private var periodLabel: String = ""

    // Chip views — held so we can toggle their appearance
    private lateinit var chipWeek: TextView
    private lateinit var chipMonth: TextView
    private lateinit var chipQuarter: TextView
    private lateinit var chipYear: TextView
    private lateinit var chipCustom: TextView

    // Hero period label TextView
    private lateinit var heroPeriodLabel: TextView

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
        setupViewModel()
        setupCategoryTilesRecycler()
        setupSpendingBreakdownRecycler()
        observeCategories()
        setupPeriodChips()

        // Select Month by default — mirrors the XML's pre-highlighted chip
        selectPeriod(Period.MONTH)

        findViewById<MaterialButton>(R.id.exportButton)?.setOnClickListener {
            Toast.makeText(this, "Export coming soon", Toast.LENGTH_SHORT).show()
        }

        setupBottomNav(R.id.navAnalytics)
    }

    // ─── View binding ──────────────────────────────────────────────────────────

    private fun bindViews() {
        chipWeek    = findViewById(R.id.chipWeek)
        chipMonth   = findViewById(R.id.chipMonth)
        chipQuarter = findViewById(R.id.chipQuarter)
        chipYear    = findViewById(R.id.chipYear)
        chipCustom  = findViewById(R.id.chipCustom)

        // The period label is the first TextView inside the hero card's top LinearLayout
        // It currently shows the hardcoded "March 2026" text — give it an id in XML or
        // find it by its hardcoded text. We reference it by ID below — add
        // android:id="@+id/heroPeriodLabel" to that TextView in activity_analytics.xml.
        heroPeriodLabel = findViewById(R.id.heroPeriodLabel)
    }

    // ─── Period chips ──────────────────────────────────────────────────────────

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
        loadCategoriesForCurrentUser()
    }

    /**
     * Highlights the active chip and resets all others to the inactive style,
     * matching the original XML colour scheme.
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

    /**
     * Sets [periodStart], [periodEnd], and [periodLabel] for the chosen period.
     * All ranges are computed relative to today.
     */
    private fun computeDateRange(period: Period) {
        val cal = Calendar.getInstance()
        val monthFmt  = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val shortFmt  = SimpleDateFormat("d MMM", Locale.getDefault())
        val yearFmt   = SimpleDateFormat("yyyy", Locale.getDefault())

        when (period) {
            Period.WEEK -> {
                // Mon → Sun of the current ISO week
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
                // Round down to the start of the current quarter
                val month = cal.get(Calendar.MONTH)
                val quarterStartMonth = (month / 3) * 3
                cal.set(Calendar.MONTH, quarterStartMonth)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                periodStart = cal.time
                cal.add(Calendar.MONTH, 3)
                cal.add(Calendar.DAY_OF_MONTH, -1)
                periodEnd = cal.time
                val startCal = Calendar.getInstance().apply {
                    set(Calendar.MONTH, quarterStartMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                periodLabel = "Q${(quarterStartMonth / 3) + 1} ${yearFmt.format(startCal.time)}"
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

    /**
     * Shows a start-date picker followed by an end-date picker for Custom range.
     */
    private fun showCustomDatePicker() {
        val today = Calendar.getInstance()
        DatePickerDialog(this, { _, startYear, startMonth, startDay ->
            val startCal = Calendar.getInstance().apply {
                set(startYear, startMonth, startDay, 0, 0, 0)
            }
            periodStart = startCal.time

            // After picking start, immediately ask for end date
            DatePickerDialog(this, { _, endYear, endMonth, endDay ->
                val endCal = Calendar.getInstance().apply {
                    set(endYear, endMonth, endDay, 23, 59, 59)
                }
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
                loadCategoriesForCurrentUser()
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()

        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
    }

    // ─── ViewModel ─────────────────────────────────────────────────────────────

    private fun setupViewModel() {
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = CategoryRepository(db.categoryDao())
        categoryViewModel = ViewModelProvider(
            this,
            ViewModelFactory { CategoryViewModel(repository) }
        )[CategoryViewModel::class.java]
    }

    // ─── RecyclerViews ─────────────────────────────────────────────────────────

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

    // ─── Observation ───────────────────────────────────────────────────────────

    private fun observeCategories() {
        categoryViewModel.categories.observe(this) { categories ->
            // TODO: replace emptyMap() with expense totals filtered by periodStart..periodEnd
            // e.g. expenseEntryDao.getTotalsForUserBetween(userId, periodStart, periodEnd)
            val totals: Map<Int, Double> = emptyMap()

            tileAdapter.submitList(categories)
            spendingAdapter.submitData(categories, totals)
        }
    }

    private fun loadCategoriesForCurrentUser() {
        val userId = SessionManager(this).getUserId()
        if (userId != -1) {
            categoryViewModel.loadCategoriesForUser(userId)
        }
    }
}