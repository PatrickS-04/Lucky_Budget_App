package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
import com.example.pulserds_prog7313.data.AppDatabase
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
 * Fully functional Transactions screen:
 *  - Observes Room LiveData - list refreshes automatically after every add/delete
 *  - Period filter chips (All / Today / This Week / This Month / This Year)
 *  - Real-time text search across description, category, and account
 *  - Summary strip shows live Income / Expenses / Net for the active filter
 *  - RecyclerView replaces all the hard-coded placeholder rows
 */
class TransactionsActivity : BaseActivity() {

    //  View references
    private lateinit var recycler          : RecyclerView
    private lateinit var emptyText         : TextView
    private lateinit var summaryIncome     : TextView
    private lateinit var summaryExpenses   : TextView
    private lateinit var summaryNet        : TextView
    private lateinit var searchInput       : TextInputEditText

    private lateinit var chipAll           : TextView
    private lateinit var chipToday         : TextView
    private lateinit var chipWeek          : TextView
    private lateinit var chipMonth         : TextView
    private lateinit var chipYear          : TextView

    //  Data
    private lateinit var adapter           : TransactionAdapter
    private lateinit var entryViewModel    : ExpenseEntryViewModel
    private lateinit var bankViewModel     : BankAccountViewModel
    private lateinit var categoryViewModel : CategoryViewModel

    private var userId = -1

    // Full unfiltered list for search
    private var allEntries    : List<ExpenseEntry> = emptyList()
    private var allCategories : List<Category>     = emptyList()
    private var allAccounts   : List<BankAccount>  = emptyList()

    // Active period filter - null means "All time"
    private var activeStartDate: String? = null
    private var activeEndDate  : String? = null
    private var activePeriodChip: TextView? = null

    // Active entry LiveData (swapped when period changes)
    private var entriesLiveData: LiveData<List<ExpenseEntry>>? = null

    //  Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transactions)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
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

        // Default - show all transactions
        selectChip(chipAll)
        applyAllFilter()
    }

    //  View binding
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

    //  RecyclerView
    private fun setupRecycler() {
        adapter = TransactionAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter
        recycler.isNestedScrollingEnabled = false
    }

    //  ViewModels
    private fun setupViewModels() {
        val db = AppDatabase.getDatabase(this)

        entryViewModel = ViewModelProvider(
            this,
            ViewModelFactory { ExpenseEntryViewModel(ExpenseEntryRepository(db.expenseEntryDao())) }
        )[ExpenseEntryViewModel::class.java]

        bankViewModel = ViewModelProvider(
            this,
            ViewModelFactory { BankAccountViewModel(BankAccountRepository(db.bankAccountDao())) }
        )[BankAccountViewModel::class.java]
        bankViewModel.init(userId)

        categoryViewModel = ViewModelProvider(
            this,
            ViewModelFactory { CategoryViewModel(CategoryRepository(db.categoryDao())) }
        )[CategoryViewModel::class.java]

        if (userId != -1) {
            lifecycleScope.launch {
                categoryViewModel.seedDefaultCategories(userId)
            }
        }

        // Observe accounts
        bankViewModel.accounts.observe(this) { accounts ->
            allAccounts = accounts
            resubmitAdapter()
        }

        // Observe categories
        categoryViewModel.getCategoriesLive(userId).observe(this) { cats ->
            allCategories = cats
            resubmitAdapter()
        }
    }

    //  Period chips
    private fun setupChips() {
        chipAll.setOnClickListener   { selectChip(chipAll);   applyAllFilter() }
        chipToday.setOnClickListener { selectChip(chipToday); applyTodayFilter() }
        chipWeek.setOnClickListener  { selectChip(chipWeek);  applyWeekFilter() }
        chipMonth.setOnClickListener { selectChip(chipMonth); applyMonthFilter() }
        chipYear.setOnClickListener  { selectChip(chipYear);  applyYearFilter() }
    }

    private fun selectChip(chip: TextView) {
        val activeColour   = "#4ADE80"
        val inactiveColour = resources.getColor(R.color.backgroundOrange, theme)
        val activeText   = "#0D1B2A"
        val inactiveText = resources.getColor(R.color.very_darkGreen, theme)

        listOf(chipAll, chipToday, chipWeek, chipMonth, chipYear).forEach { c ->
            if (c == chip) {
                c.setBackgroundColor(Color.parseColor(activeColour))
                c.setTextColor(Color.parseColor(activeText))
                c.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                c.setBackgroundColor(inactiveColour)
                c.setTextColor(inactiveText)
                c.typeface = android.graphics.Typeface.DEFAULT
            }
        }
        activePeriodChip = chip
    }

    //  Filter helpers
    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private fun applyAllFilter() {
        activeStartDate = null
        activeEndDate   = null
        observeEntries(entryViewModel.getAllEntriesLive(userId))
    }

    private fun applyTodayFilter() {
        val today = fmt.format(Date())
        activeStartDate = today
        activeEndDate   = today
        observeEntries(entryViewModel.getEntriesForPeriodLive(userId, today, today))
    }

    private fun applyWeekFilter() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        val start = fmt.format(cal.time)
        val end   = fmt.format(Date())
        activeStartDate = start
        activeEndDate   = end
        observeEntries(entryViewModel.getEntriesForPeriodLive(userId, start, end))
    }

    private fun applyMonthFilter() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = fmt.format(cal.time)
        val end   = fmt.format(Date())
        activeStartDate = start
        activeEndDate   = end
        observeEntries(entryViewModel.getEntriesForPeriodLive(userId, start, end))
    }

    private fun applyYearFilter() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        val start = fmt.format(cal.time)
        val end   = fmt.format(Date())
        activeStartDate = start
        activeEndDate   = end
        observeEntries(entryViewModel.getEntriesForPeriodLive(userId, start, end))
    }

    /** Replace the active LiveData observation with the new source */
    private fun observeEntries(liveData: LiveData<List<ExpenseEntry>>) {
        entriesLiveData?.removeObservers(this)
        entriesLiveData = liveData
        liveData.observe(this) { entries ->
            allEntries = entries ?: emptyList()
            applySearchFilter()
            updateSummary(allEntries)
        }
    }

    //  Search
    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { applySearchFilter() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun applySearchFilter() {
        val query = searchInput.text?.toString()?.trim()?.lowercase() ?: ""
        val filtered = if (query.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { entry ->
                val catName  = allCategories.find { it.id == entry.categoryId }?.name?.lowercase() ?: ""
                val accLabel = allAccounts.find { it.id == entry.bankAccountId }
                    ?.let { "${it.nickname} ${it.bankName}".lowercase() } ?: ""
                entry.description.lowercase().contains(query) ||
                        catName.contains(query) ||
                        accLabel.contains(query)
            }
        }
        resubmitAdapter(filtered)
    }

    private fun resubmitAdapter(entries: List<ExpenseEntry> = allEntries) {
        adapter.submitData(entries, allCategories, allAccounts)
        val isEmpty = entries.isEmpty()
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.transactionsCard).visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    //  Summary strip
    private fun updateSummary(entries: List<ExpenseEntry>) {
        val income   = entries.filter { it.amount > 0 }.sumOf { it.amount }
        val expenses = entries.filter { it.amount < 0 }.sumOf { abs(it.amount) }
        val net      = income - expenses

        summaryIncome.text   = "+ R %,.2f".format(income)
        summaryExpenses.text = "- R %,.2f".format(expenses)

        summaryNet.text = if (net >= 0) "R %,.2f".format(net) else "- R %,.2f".format(abs(net))
        summaryNet.setTextColor(
            if (net >= 0) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
        )
    }

    //  Add button
    private fun setupAddButton() {
        findViewById<MaterialButton>(R.id.addTransactionButton)?.setOnClickListener {
            startActivity(
                Intent(this, AddTransactionActivity::class.java)
                    .putExtra("USER_ID", userId)
            )
        }
    }
}