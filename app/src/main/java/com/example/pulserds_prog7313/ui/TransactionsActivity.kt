package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.repository.ExpenseEntryRepository
import com.example.pulserds_prog7313.ui.adapters.TransactionAdapter
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ExpenseEntryViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * TransactionsActivity - The main transaction ledger screen.
 *
 * Features:
 *  - Observes Room LiveData so the list refreshes automatically after every add/delete
 *  - Period filter chips (All / Today / This Week / This Month / This Year)
 *  - Real-time text search across description, category name, and account label
 *  - Summary strip shows live Income / Expenses / Net totals for the active filter
 *  - RecyclerView-backed list replaces all hard-coded placeholder rows
 *
 * Extends BaseActivity to inherit shared bottom navigation bar behaviour.
 */

//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
// [Accessed: 28 Apr. 2026].
//[3] Google, "LiveData overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/livedata.
// [Accessed: 28 Apr. 2026].
//[4] JetBrains, "Coroutines guide," Kotlin Documentation, 2024. [Online].
// Available: https://kotlinlang.org/docs/coroutines-guide.html.
// [Accessed: 28 Apr. 2026].
//[9] Google, AndroidX Core KTX, ver. 1.13.1. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/core.
//[10] Google, AndroidX AppCompat, ver. 1.7.0. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/appcompat.
//[11] Google, Material Design Components for Android, ver. 1.12.0. [Software]. Google, 2024.
// Available: https://github.com/material-components/material-components-android.
//[15] Google, "View Binding," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/view-binding.
// [Accessed: 28 Apr. 2026].
//[25] 	F. van Puffelen, "Save a Room Database file in Firebase," Stack Exchange Inc, 22 August 2021. [Online].
// Available: https://stackoverflow.com/questions/68878022/save-a-room-database-file-in-firebase.
// [Accessed 30 May 2026].
//[26] 	A. Mamo, "Android Room and Firebase in a MVVM Architecture - Offline First Application," Stack Exchange Inc, 18 November 2022. [Online].
// Available: https://stackoverflow.com/questions/74484239/android-room-and-firebase-in-a-mvvm-architecture-offline-first-application.
// [Accessed 30 May 2026].

class TransactionsActivity : BaseActivity() {

    // -------------------------------------------------------------------------
    // View references - lateinit because they are resolved after setContentView()
    // -------------------------------------------------------------------------

    /** RecyclerView that displays the transaction rows */
    private lateinit var recycler          : RecyclerView
    /** Shown in place of the list when there are no matching transactions */
    private lateinit var emptyText         : TextView
    /** Summary strip TextViews displaying aggregated totals */
    private lateinit var summaryIncome     : TextView
    private lateinit var summaryExpenses   : TextView
    private lateinit var summaryNet        : TextView
    /** Real-time search field - filters the visible list as the user types */
    private lateinit var searchInput       : TextInputEditText

    /** Period-filter chip TextViews - one active at a time */
    private lateinit var chipAll           : TextView
    private lateinit var chipToday         : TextView
    private lateinit var chipWeek          : TextView
    private lateinit var chipMonth         : TextView
    private lateinit var chipYear          : TextView

    // -------------------------------------------------------------------------
    // Data layer - adapter and the three ViewModels that feed it
    // -------------------------------------------------------------------------

    /** RecyclerView adapter that maps ExpenseEntry rows to card views */
    private lateinit var adapter           : TransactionAdapter
    /** ViewModel for expense entries - primary data source for this screen */
    private lateinit var entryViewModel    : ExpenseEntryViewModel
    /** ViewModel for bank accounts - needed to resolve account labels for search */
    private lateinit var bankViewModel     : BankAccountViewModel
    /** ViewModel for categories - needed to resolve category names for search */
    private lateinit var categoryViewModel : CategoryViewModel

    /** ID of the currently logged-in user; -1 if no session exists */
    private var userId = ""

    // -------------------------------------------------------------------------
    // In-memory caches - kept in sync with the latest LiveData emissions so
    // that search filtering can be applied without an additional DB query.
    // -------------------------------------------------------------------------

    /** Full unfiltered list of entries for the active period, used as the search base */
    private var allEntries    : List<ExpenseEntry> = emptyList()
    /** All categories for the current user, used to resolve category names */
    private var allCategories : List<Category>     = emptyList()
    /** All bank accounts for the current user, used to resolve account labels */
    private var allAccounts   : List<BankAccount>  = emptyList()

    // -------------------------------------------------------------------------
    // Active filter state - updated whenever a period chip is tapped
    // -------------------------------------------------------------------------

    /** Inclusive start date of the active period filter; null means "All time" */
    private var activeStartDate: String? = null
    /** Inclusive end date of the active period filter; null means "All time" */
    private var activeEndDate  : String? = null
    /** Reference to the currently highlighted chip for visual state management */
    private var activePeriodChip: TextView? = null

    /**
     * Holds the currently observed LiveData source for entries.
     * Swapped out (and previous observer removed) whenever the period filter changes,
     * preventing stale observers from accumulating across filter switches.
     */
    private var entriesLiveData: LiveData<List<ExpenseEntry>>? = null

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialises the screen: edge-to-edge display, view binding, RecyclerView,
     * ViewModels, chip filters, search, and the add-transaction button.
     * Defaults to showing all transactions with the "All" chip selected.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transactions)

        // Offset content so it is not hidden behind system status/nav bars
        val navBarContainer = findViewById<LinearLayout>(R.id.containerForBottomNavBar)

        ViewCompat.setOnApplyWindowInsetsListener(navBarContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val basePadding = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(bars.left, bars.top, bars.right, basePadding + bars.bottom)
            insets
        }

        userId = SessionManager(this).getUserId()

        bindViews()
        setupRecycler()
        setupViewModels()
        setupChips()
        setupSearch()
        setupAddButton()
        setupBottomNav(R.id.navTransactions)

        // Apply the default "All time" filter on first launch
        selectChip(chipAll)
        applyAllFilter()
    }

    // -------------------------------------------------------------------------
    // View binding
    // Resolves all layout views by ID into the lateinit properties declared
    // above. Called once from onCreate before any other setup methods.
    // -------------------------------------------------------------------------
    private fun bindViews() {
        recycler        = findViewById(R.id.transactionsRecycler)
        emptyText       = findViewById(R.id.emptyTransactionsText)
        summaryIncome   = findViewById(R.id.summaryIncome)
        summaryExpenses = findViewById(R.id.summaryExpenses)
        summaryNet      = findViewById(R.id.summaryNet)
        searchInput     = findViewById(R.id.searchEditText)
        chipAll         = findViewById(R.id.chipAll)
        chipToday       = findViewById(R.id.chipToday)
        chipWeek        = findViewById(R.id.chipWeek)
        chipMonth       = findViewById(R.id.chipMonth)
        chipYear        = findViewById(R.id.chipYear)
    }

    // -------------------------------------------------------------------------
    // RecyclerView setup
    // Attaches a LinearLayoutManager and the TransactionAdapter. Nested
    // scrolling is disabled so the RecyclerView defers to any parent scroll
    // container without double-scrolling artefacts.
    // -------------------------------------------------------------------------
    private fun setupRecycler() {
        adapter = TransactionAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        // Disable nested scrolling so a parent ScrollView/NestedScrollView
        // handles vertical scroll events correctly
        recycler.isNestedScrollingEnabled = false
    }

    // -------------------------------------------------------------------------
    // ViewModel initialisation
    // Creates the three ViewModels via a shared ViewModelFactory, seeds default
    // categories for new users, and starts observing the accounts and categories
    // LiveData so the in-memory caches stay current.
    // -------------------------------------------------------------------------
    private fun setupViewModels() {
        entryViewModel = ViewModelProvider(
            this,
            ViewModelFactory { ExpenseEntryViewModel(ExpenseEntryRepository()) }
        )[ExpenseEntryViewModel::class.java]

        bankViewModel = ViewModelProvider(
            this,
            ViewModelFactory { BankAccountViewModel(BankAccountRepository()) }
        )[BankAccountViewModel::class.java]
        bankViewModel.init(userId)

        categoryViewModel = ViewModelProvider(
            this,
            ViewModelFactory { CategoryViewModel(CategoryRepository()) }
        )[CategoryViewModel::class.java]

        if (userId.isNotBlank()) {
            lifecycleScope.launch {
                categoryViewModel.seedDefaultCategories(userId)
            }
        }

        // Keep allAccounts in sync - triggers adapter resubmit so account labels
        // appear correctly without waiting for a full list refresh
        bankViewModel.accounts.observe(this) { accounts ->
            allAccounts = accounts
            resubmitAdapter()
        }

        // Keep allCategories in sync - same rationale as accounts above
        categoryViewModel.getCategoriesLive(userId).observe(this) { cats ->
            allCategories = cats
            resubmitAdapter()
        }
    }

    // -------------------------------------------------------------------------
    // Period chip setup
    // Each chip maps to its own filter method. selectChip() handles the visual
    // active/inactive state; the apply*Filter() methods update the LiveData source.
    // -------------------------------------------------------------------------
    private fun setupChips() {
        chipAll.setOnClickListener   { selectChip(chipAll);   applyAllFilter() }
        chipToday.setOnClickListener { selectChip(chipToday); applyTodayFilter() }
        chipWeek.setOnClickListener  { selectChip(chipWeek);  applyWeekFilter() }
        chipMonth.setOnClickListener { selectChip(chipMonth); applyMonthFilter() }
        chipYear.setOnClickListener  { selectChip(chipYear);  applyYearFilter() }
    }

    /**
     * Applies the active/inactive visual style to each chip.
     * The selected chip gets a green background with dark text and bold typeface;
     * all others revert to the default background colour and normal typeface.
     *
     * @param chip The chip that was just tapped and should appear active.
     */
    private fun selectChip(chip: TextView) {
        val activeColour   = "#4ADE80"   // Green - matches the app's income colour
        val inactiveColour = resources.getColor(R.color.backgroundOrange, theme)
        val activeText     = "#0D1B2A"   // Near-black for contrast on green
        val inactiveText   = resources.getColor(R.color.very_darkGreen, theme)

        listOf(chipAll, chipToday, chipWeek, chipMonth, chipYear).forEach { c ->
            if (c == chip) {
                // Highlight the selected chip
                c.setBackgroundColor(Color.parseColor(activeColour))
                c.setTextColor(Color.parseColor(activeText))
                c.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                // Reset all other chips to their resting state
                c.setBackgroundColor(inactiveColour)
                c.setTextColor(inactiveText)
                c.typeface = android.graphics.Typeface.DEFAULT
            }
        }
        activePeriodChip = chip
    }

    // -------------------------------------------------------------------------
    // Filter helpers
    // Each method sets the activeStartDate / activeEndDate state and then calls
    // observeEntries() with the appropriate LiveData query from the ViewModel.
    // Dates are formatted as "dd/MM/yyyy" to match the format stored in the DB.
    // -------------------------------------------------------------------------

    /** Date formatter shared across all filter calculations */
    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    /** Shows every transaction for this user regardless of date */
    private fun applyAllFilter() {
        activeStartDate = null
        activeEndDate   = null
        observeEntries(entryViewModel.getAllEntriesLive(userId))
    }

    /** Shows only transactions whose date equals today's date */
    private fun applyTodayFilter() {
        val today = fmt.format(Date())
        activeStartDate = today
        activeEndDate   = today
        observeEntries(entryViewModel.getEntriesForPeriodLive(userId, today, today))
    }

    /**
     * Shows transactions from the start of the current week (locale-aware first
     * day of week) through to today.
     */
    private fun applyWeekFilter() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)   // Rewind to Monday (or Sunday)
        val start = fmt.format(cal.time)
        val end   = fmt.format(Date())
        activeStartDate = start
        activeEndDate   = end
        observeEntries(entryViewModel.getEntriesForPeriodLive(userId, start, end))
    }

    /** Shows transactions from the 1st of the current month through to today */
    private fun applyMonthFilter() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)   // First day of the current month
        val start = fmt.format(cal.time)
        val end   = fmt.format(Date())
        activeStartDate = start
        activeEndDate   = end
        observeEntries(entryViewModel.getEntriesForPeriodLive(userId, start, end))
    }

    /** Shows transactions from the 1st of January of the current year through to today */
    private fun applyYearFilter() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)    // 1st January of the current year
        val start = fmt.format(cal.time)
        val end   = fmt.format(Date())
        activeStartDate = start
        activeEndDate   = end
        observeEntries(entryViewModel.getEntriesForPeriodLive(userId, start, end))
    }

    /**
     * Swaps the currently observed LiveData source for entries.
     *
     * Removes the observer on the previous LiveData before attaching to the new
     * one. This prevents ghost observers from prior filter selections from firing
     * and corrupting [allEntries] with stale data.
     *
     * On each emission the in-memory list is updated, the search filter is
     * re-applied (so any active query is preserved across period switches),
     * and the summary strip is recalculated.
     *
     * @param liveData The new period-scoped LiveData to observe.
     */
    private fun observeEntries(liveData: LiveData<List<ExpenseEntry>>) {
        entriesLiveData?.removeObservers(this)   // Detach stale observer
        entriesLiveData = liveData
        liveData.observe(this) { entries ->
            allEntries = entries ?: emptyList()
            applySearchFilter()          // Re-run any active search query
            updateSummary(allEntries)    // Recalculate totals for the new period
        }
    }

    // -------------------------------------------------------------------------
    // Search
    // A TextWatcher triggers applySearchFilter() on every keystroke so the list
    // narrows in real time. Search operates on the in-memory allEntries list and
    // matches against the entry's description, its resolved category name, and
    // the combined bank account nickname + bank name string.
    // -------------------------------------------------------------------------

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            // React to every character change
            override fun afterTextChanged(s: Editable?) { applySearchFilter() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    /**
     * Filters [allEntries] against the current search query and submits the
     * result to the adapter. An empty query shows the full list.
     *
     * Matching is case-insensitive and checks:
     *  1. Entry description
     *  2. Resolved category name (looked up from [allCategories])
     *  3. Resolved account label - nickname + bank name (from [allAccounts])
     */
    private fun applySearchFilter() {
        val query = searchInput.text?.toString()?.trim()?.lowercase() ?: ""
        val filtered = if (query.isEmpty()) {
            allEntries   // No search active - show everything
        } else {
            allEntries.filter { entry ->
                // Resolve the human-readable category name for this entry
                val catName  = allCategories.find { it.id == entry.categoryId }?.name?.lowercase() ?: ""
                // Combine nickname and bank name into a single searchable label
                val accLabel = allAccounts.find { it.id == entry.bankAccountId }
                    ?.let { "${it.nickname} ${it.bankName}".lowercase() } ?: ""
                // Match if the query appears in any of the three fields
                entry.description.lowercase().contains(query) ||
                        catName.contains(query) ||
                        accLabel.contains(query)
            }
        }
        resubmitAdapter(filtered)
    }

    /**
     * Pushes [entries] to the adapter and toggles the empty-state view.
     * Defaults to [allEntries] when called without an argument (e.g. after an
     * accounts or categories update that doesn't change the entry list itself).
     *
     * @param entries The filtered or unfiltered list to display.
     */
    private fun resubmitAdapter(entries: List<ExpenseEntry> = allEntries) {
        adapter.submitData(entries, allCategories, allAccounts)
        val isEmpty = entries.isEmpty()
        // Toggle between the empty-state label and the transactions card
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.transactionsCard).visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    // -------------------------------------------------------------------------
    // Summary strip
    // Recalculates Income, Expenses, and Net totals from the provided entries.
    // Positive amounts are treated as income; negative amounts as expenses.
    // The Net value is coloured green when >= 0 and red when negative.
    // -------------------------------------------------------------------------
    private fun updateSummary(entries: List<ExpenseEntry>) {
        // Sum all positive amounts → total income for the period
        val income   = entries.filter { it.amount > 0 }.sumOf { it.amount }
        // Sum absolute values of all negative amounts → total expenditure
        val expenses = entries.filter { it.amount < 0 }.sumOf { abs(it.amount) }
        val net      = income - expenses

        summaryIncome.text   = "+ R %,.2f".format(income)
        summaryExpenses.text = "- R %,.2f".format(expenses)

        // Prefix negative net with a minus sign; positive net has no prefix
        summaryNet.text = if (net >= 0) "R %,.2f".format(net) else "- R %,.2f".format(abs(net))
        // Green for surplus, red for deficit
        summaryNet.setTextColor(
            if (net >= 0) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
        )
    }

    // -------------------------------------------------------------------------
    // Add Transaction button
    // Navigates to AddTransactionActivity, passing the current userId so the
    // new entry is saved under the correct account.
    // -------------------------------------------------------------------------
    private fun setupAddButton() {
        findViewById<MaterialButton>(R.id.addTransactionButton)?.setOnClickListener {
            startActivity(
                Intent(this, AddTransactionActivity::class.java)
                    .putExtra("USER_ID", userId)
            )
        }
    }
}