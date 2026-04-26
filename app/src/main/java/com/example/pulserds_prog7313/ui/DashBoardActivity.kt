package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
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
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import com.example.pulserds_prog7313.ui.adapters.CategoryTileAdapter
import com.example.pulserds_prog7313.ui.adapters.DashSavingsAdapter
import com.example.pulserds_prog7313.ui.adapters.TransactionAdapter
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ExpenseEntryViewModel
import com.example.pulserds_prog7313.viewmodel.SavingsGoalViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs

class DashBoardActivity : BaseActivity() {

    // ViewModels
    private lateinit var categoryVM: CategoryViewModel
    private lateinit var expenseVM: ExpenseEntryViewModel
    private lateinit var savingsVM: SavingsGoalViewModel
    private lateinit var bankAccountVM: BankAccountViewModel

    // Adapters
    private lateinit var tileAdapter: CategoryTileAdapter
    private lateinit var savingsAdapter: DashSavingsAdapter
    private lateinit var txAdapter: TransactionAdapter

    // FIX: use -1 as the "not yet loaded" sentinel — userId=1 is a perfectly valid user
    private var userId = -1

    // Latest cached lists so we can cross-reference them in each adapter update
    private var latestCategories: List<Category>    = emptyList()
    private var latestAccounts:   List<BankAccount> = emptyList()
    private var latestEntries:    List<ExpenseEntry> = emptyList()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dash_board)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val session = SessionManager(this)
        userId = session.getUserId()

        // Personalised greeting using stored first name
        val firstName = session.getFullName()?.split(" ")?.firstOrNull() ?: "there"
        findViewById<TextView>(R.id.greetingText).text = "Hi, $firstName! 👋"

        setupViewModels()
        setupRecyclers()
        observeLiveData()

        // "See All" navigation
        findViewById<TextView>(R.id.savingsSeeAll)?.setOnClickListener {
            startActivity(Intent(this, SavingsGoalsActivity::class.java))
        }
        findViewById<TextView>(R.id.transactionsSeeAll)?.setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java))
        }

        setupBottomNav(R.id.navHome)
    }

    override fun onResume() {
        super.onResume()
        // Refresh every time the user navigates back to the dashboard so that
        // data added on other screens (Transactions, Accounts) shows up immediately.
        if (userId != -1) {
            loadAggregateData()
            bankAccountVM.refreshTotal(userId)
        }
    }

    // Setup

    private fun setupViewModels() {
        val db = AppDatabase.getDatabase(applicationContext)

        categoryVM = ViewModelProvider(
            this,
            ViewModelFactory { CategoryViewModel(CategoryRepository(db.categoryDao())) }
        )[CategoryViewModel::class.java]

        expenseVM = ViewModelProvider(
            this,
            ViewModelFactory { ExpenseEntryViewModel(ExpenseEntryRepository(db.expenseEntryDao())) }
        )[ExpenseEntryViewModel::class.java]

        savingsVM = ViewModelProvider(
            this,
            ViewModelFactory { SavingsGoalViewModel(SavingsGoalRepository(db.savingsGoalDao())) }
        )[SavingsGoalViewModel::class.java]

        bankAccountVM = ViewModelProvider(
            this,
            ViewModelFactory { BankAccountViewModel(BankAccountRepository(db.bankAccountDao())) }
        )[BankAccountViewModel::class.java]

        // Only seed if we have a valid logged-in user
        if (userId != -1) {                              // ← add this guard
            categoryVM.seedDefaultCategories(userId)
        }

        bankAccountVM.init(userId)
    }

    private fun setupRecyclers() {
        // Category tiles — 2-column grid; layoutManager declared in XML
        tileAdapter = CategoryTileAdapter()
        findViewById<RecyclerView>(R.id.dashCategoryTilesRecycler).apply {
            adapter = tileAdapter
            isNestedScrollingEnabled = false
        }

        // Savings goals — compact vertical list
        savingsAdapter = DashSavingsAdapter()
        findViewById<RecyclerView>(R.id.dashSavingsRecycler).apply {
            layoutManager = LinearLayoutManager(this@DashBoardActivity)
            adapter = savingsAdapter
            isNestedScrollingEnabled = false
        }

        // Recent transactions — vertical list, same adapter as TransactionsActivity
        txAdapter = TransactionAdapter()
        findViewById<RecyclerView>(R.id.dashTransactionsRecycler).apply {
            layoutManager = LinearLayoutManager(this@DashBoardActivity)
            adapter = txAdapter
            isNestedScrollingEnabled = false
        }
    }

    // ── LiveData observers ────────────────────────────────────────────────────

    private fun observeLiveData() {
        // FIX: guard against -1, not 1 — userId=1 is a real, valid user
        if (userId == -1) return

        // Total balance — sum of all included bank account balances (same DAO query as AccountsActivity)
        bankAccountVM.totalBalance.observe(this) { balance ->
            showBalanceAmount(balance)
        }

        // Bank accounts list — needed to label each transaction row's subtitle
        bankAccountVM.accounts.observe(this) { accounts ->
            latestAccounts = accounts
            refreshTransactionsAdapter()
        }

        // Category list — triggers a tile refresh whenever categories are added/removed
        categoryVM.getCategoriesLive(userId).observe(this) { categories ->
            latestCategories = categories
            // Recalculate monthly totals against the updated category list
            loadAggregateData()
        }

        // All expense entries ordered by date DESC, id DESC — same ordering as TransactionsActivity.
        // Drives both the Recent Transactions strip AND the category spending tile amounts.
        expenseVM.getAllEntriesLive(userId).observe(this) { entries ->
            latestEntries = entries
            refreshTransactionsAdapter()
            // Recalculate monthly category totals whenever any entry changes
            loadAggregateData()
        }

        // Savings goals — show top 3 active (incomplete) goals
        savingsVM.getAllGoalsLive(userId).observe(this) { goals ->
            val activeGoals = goals.filter { !it.isCompleted }.take(3)
            savingsAdapter.submitList(activeGoals)

            val hasGoals = activeGoals.isNotEmpty()
            findViewById<View>(R.id.dashSavingsRecycler).visibility =
                if (hasGoals) View.VISIBLE else View.GONE
            findViewById<View>(R.id.dashSavingsEmpty).visibility =
                if (hasGoals) View.GONE else View.VISIBLE
        }
    }

    /**
     * Pushes the 3 most-recent entries to the transactions adapter whenever
     * either the entries list or the accounts/categories list changes.
     * Uses the same TransactionAdapter as TransactionsActivity so rows look identical.
     */
    private fun refreshTransactionsAdapter() {
        val recent = latestEntries.take(3)
        txAdapter.submitData(recent, latestCategories, latestAccounts)

        val hasData = recent.isNotEmpty()
        findViewById<View>(R.id.dashTransactionsRecycler).visibility =
            if (hasData) View.VISIBLE else View.GONE
        findViewById<View>(R.id.dashTransactionsEmpty).visibility =
            if (hasData) View.GONE else View.VISIBLE
    }

    // ── Aggregate data ────────────────────────────────────────────────────────

    private fun loadAggregateData() {
        if (userId == -1) return

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).expenseEntryDao()

            // All-time income / expense totals for the Income and Expenses header boxes
            val totalIncome   = dao.getTotalIncome(userId)   // positive sum
            val totalExpenses = dao.getTotalExpenses(userId) // negative or zero
            showIncomeAmount(totalIncome)
            showExpenseAmount(abs(totalExpenses))

            // Current and previous month boundaries (format Room uses: "dd/MM/yyyy")
            val (curStart, curEnd)   = currentMonthRange()
            val (prevStart, prevEnd) = lastMonthRange()

            // Current-month entries — drives net calculation, overspending alert, and tile amounts
            val curEntries = dao.getEntriesForPeriod(userId, curStart, curEnd)
            val curIncome  = curEntries.filter { it.amount > 0 }.sumOf { it.amount }
            val curExpense = curEntries.filter { it.amount < 0 }.sumOf { abs(it.amount) }
            val curNet     = curIncome - curExpense

            // Previous-month entries — only used for the balance-change percentage
            val prevEntries = dao.getEntriesForPeriod(userId, prevStart, prevEnd)
            val prevIncome  = prevEntries.filter { it.amount > 0 }.sumOf { it.amount }
            val prevExpense = prevEntries.filter { it.amount < 0 }.sumOf { abs(it.amount) }
            val prevNet     = prevIncome - prevExpense

            showBalanceChange(curNet, prevNet)
            showOverspendingAlert(curIncome, curExpense, curEntries)

            // Per-category spending totals for the current month → budget category tiles
            val monthlyTotals = curEntries
                .filter { it.amount < 0 }
                .groupBy { it.categoryId }
                .mapValues { (_, es) -> es.sumOf { abs(it.amount) } }

            tileAdapter.submitCategories(latestCategories, monthlyTotals)
        }
    }

    // ── UI update helpers ─────────────────────────────────────────────────────

    private fun showBalanceAmount(balance: Double) {
        findViewById<TextView>(R.id.balanceAmount).text = "R %,.2f".format(balance)
    }

    private fun showIncomeAmount(income: Double) {
        findViewById<TextView>(R.id.incomeAmount).text = "R %,.2f".format(income)
    }

    private fun showExpenseAmount(expenses: Double) {
        findViewById<TextView>(R.id.expenseAmount).text = "R %,.2f".format(expenses)
    }

    private fun showBalanceChange(curNet: Double, prevNet: Double) {
        val tv = findViewById<TextView>(R.id.balanceChange)
        when {
            prevNet == 0.0 && curNet == 0.0 -> {
                tv.text = "No activity recorded yet"
                tv.setTextColor(Color.parseColor("#7A99B0"))
            }
            prevNet == 0.0 -> {
                tv.text = "▲ First month on record"
                tv.setTextColor(Color.parseColor("#4ADE80"))
            }
            else -> {
                val pct      = ((curNet - prevNet) / abs(prevNet)) * 100.0
                val positive = pct >= 0
                val arrow    = if (positive) "▲" else "▼"
                val color    = if (positive) "#4ADE80" else "#F87171"
                tv.text = "$arrow ${"%.1f".format(abs(pct))}% vs last month"
                tv.setTextColor(Color.parseColor(color))
            }
        }
    }

    /**
     * Shows the overspending alert card when the current month's expenses exceed income.
     * Names the highest-spending category so the user knows where to cut back.
     * Hides the card when everything is within budget.
     */
    private fun showOverspendingAlert(
        income: Double,
        expenses: Double,
        entries: List<ExpenseEntry>
    ) {
        val card = findViewById<View>(R.id.overspendingCard)
        val body = findViewById<TextView>(R.id.alertBody)

        if (expenses == 0.0 || expenses <= income) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        val over = expenses - income

        // Identify the highest-spending category this month
        val topCategoryId = entries
            .filter { it.amount < 0 }
            .groupBy { it.categoryId }
            .maxByOrNull { (_, es) -> es.sumOf { abs(it.amount) } }
            ?.key

        val catName = latestCategories.find { it.id == topCategoryId }?.name ?: "a category"
        body.text = "You've spent R ${"%.2f".format(over)} more than you earned " +
                "this month. Your highest spend is in $catName."
    }

    // ── Date helpers ──────────────────────────────────────────────────────────

    /** Returns "01/MM/YYYY" to "DD/MM/YYYY" for the current calendar month. */
    private fun currentMonthRange(): Pair<String, String> {
        val cal   = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)
        val last  = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return "01/%02d/%d".format(month, year) to "%02d/%02d/%d".format(last, month, year)
    }

    /**
     * Returns "01/MM/YYYY" to "DD/MM/YYYY" for the *previous* calendar month.
     * FIX: was incorrectly adding +1 (next month); corrected to -1 (last month).
     */
    private fun lastMonthRange(): Pair<String, String> {
        val cal   = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)   // ← was +1, now correctly -1
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)
        val last  = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return "01/%02d/%d".format(month, year) to "%02d/%02d/%d".format(last, month, year)
    }
}