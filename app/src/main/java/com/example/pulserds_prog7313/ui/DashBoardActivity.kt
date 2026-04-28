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

/**
 * DashBoardActivity — The main landing screen shown after a user signs in.
 *
 * Displays a personalised greeting, an aggregate balance summary (total balance,
 * income, expenses, and a month-over-month change indicator), an overspending
 * alert card, a horizontal row of category spend tiles, a short list of active
 * savings goals, and the three most recent transactions.
 *
 * All data is loaded from the local Room database via ViewModels and observed
 * through LiveData so the UI stays in sync with any background changes.
 */
class DashBoardActivity : BaseActivity() {

    // -------------------------------------------------------------------------
    // ViewModels — one per data domain, each backed by its own repository
    // -------------------------------------------------------------------------

    /** Handles category data including seeding defaults for new users. */
    private lateinit var categoryVM: CategoryViewModel

    /** Handles expense and income entry data for the signed-in user. */
    private lateinit var expenseVM: ExpenseEntryViewModel

    /** Handles savings goal data, used to populate the goals summary section. */
    private lateinit var savingsVM: SavingsGoalViewModel

    /** Handles bank account data and exposes a live total balance. */
    private lateinit var bankAccountVM: BankAccountViewModel

    // -------------------------------------------------------------------------
    // Adapters — bound to their respective RecyclerViews in setupRecyclers()
    // -------------------------------------------------------------------------

    /** Populates the horizontal row of category spend tiles. */
    private lateinit var tileAdapter: CategoryTileAdapter

    /** Populates the condensed savings goals list on the dashboard. */
    private lateinit var savingsAdapter: DashSavingsAdapter

    /** Populates the recent transactions list on the dashboard. */
    private lateinit var txAdapter: TransactionAdapter

    /** The ID of the currently signed-in user; -1 indicates no active session. */
    private var userId = -1

    // -------------------------------------------------------------------------
    // Cached lists — stored so that each adapter can be refreshed whenever any
    // one of the three underlying datasets changes, without re-fetching the others.
    // -------------------------------------------------------------------------

    /** Most recently emitted list of categories for this user. */
    private var latestCategories: List<Category>     = emptyList()

    /** Most recently emitted list of bank accounts for this user. */
    private var latestAccounts:   List<BankAccount>  = emptyList()

    /** Most recently emitted list of expense/income entries for this user. */
    private var latestEntries:    List<ExpenseEntry>  = emptyList()

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called when the activity is first created.
     * Enables edge-to-edge rendering, resolves the signed-in user from the session,
     * sets up ViewModels, RecyclerViews, and LiveData observers, and wires
     * navigation listeners for the "See all" links and the bottom nav bar.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dash_board)

        // Inset the root view so content is not obscured by status / navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Retrieve the signed-in user's ID and display name from the session store
        val session = SessionManager(this)
        userId = session.getUserId()

        // Use only the first name for a friendly greeting; fall back to "there"
        // if the full name is unavailable or cannot be split
        val firstName = session.getFullName()?.split(" ")?.firstOrNull() ?: "there"
        findViewById<TextView>(R.id.greetingText).text = "Hi, $firstName! 👋"

        setupViewModels()
        setupRecyclers()
        observeLiveData()

        // Navigate to the full savings goals screen when "See all" is tapped
        findViewById<TextView>(R.id.savingsSeeAll)?.setOnClickListener {
            startActivity(Intent(this, SavingsGoalsActivity::class.java))
        }

        // Navigate to the full transactions screen when "See all" is tapped
        findViewById<TextView>(R.id.transactionsSeeAll)?.setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java))
        }

        // Highlight the Home item in the shared bottom navigation bar
        setupBottomNav(R.id.navHome)
    }

    /**
     * Called each time the activity returns to the foreground (e.g. after the
     * user navigates back from SavingsGoalsActivity or TransactionsActivity).
     * Re-fetches aggregate data and refreshes the total balance so the dashboard
     * always reflects the latest state without requiring a full restart.
     */
    override fun onResume() {
        super.onResume()
        if (userId != -1) {
            loadAggregateData()
            bankAccountVM.refreshTotal(userId)
        }
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Instantiates all four ViewModels using a custom ViewModelFactory so that
     * constructor arguments (repositories) can be injected at creation time.
     *
     * Also seeds the default expense categories for the current user if they do
     * not already exist, and initialises the bank account ViewModel with the
     * current user ID so it can begin loading account data.
     */
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

        // FIX: seedDefaultCategories is now a suspend function, so it must be
        // called inside a coroutine — this ensures it fully completes (and the
        // user row is confirmed present in the DB) before any category inserts run.
        if (userId != -1) {
            lifecycleScope.launch {
                categoryVM.seedDefaultCategories(userId)
            }
        }

        // Pass the user ID so the ViewModel can start observing the correct accounts
        bankAccountVM.init(userId)
    }

    /**
     * Creates and attaches adapters to the three RecyclerViews on the dashboard.
     * Nested scrolling is disabled on all three so they behave correctly inside
     * the parent ScrollView without consuming scroll events unexpectedly.
     */
    private fun setupRecyclers() {
        // Category tile strip — uses its own horizontal layout manager internally
        tileAdapter = CategoryTileAdapter()
        findViewById<RecyclerView>(R.id.dashCategoryTilesRecycler).apply {
            adapter = tileAdapter
            isNestedScrollingEnabled = false
        }

        // Savings goals preview list — vertical, shows up to 3 active goals
        savingsAdapter = DashSavingsAdapter()
        findViewById<RecyclerView>(R.id.dashSavingsRecycler).apply {
            layoutManager = LinearLayoutManager(this@DashBoardActivity)
            adapter = savingsAdapter
            isNestedScrollingEnabled = false
        }

        // Recent transactions list — vertical, shows the 3 most recent entries
        txAdapter = TransactionAdapter()
        findViewById<RecyclerView>(R.id.dashTransactionsRecycler).apply {
            layoutManager = LinearLayoutManager(this@DashBoardActivity)
            adapter = txAdapter
            isNestedScrollingEnabled = false
        }
    }

    // -------------------------------------------------------------------------
    // LiveData observers
    // -------------------------------------------------------------------------

    // LiveData used to observe database changes on the UI thread safely.
    // [3] Google, "LiveData overview," Android Developers, 2024.

    /**
     * Registers observers for all LiveData streams required by the dashboard.
     * Each observer updates the relevant cached list and triggers a UI refresh.
     * No-ops immediately if there is no active user session.
     */
    private fun observeLiveData() {
        if (userId == -1) return

        // Update the balance card whenever the computed total changes
        bankAccountVM.totalBalance.observe(this) { balance ->
            showBalanceAmount(balance)
        }

        // Cache the latest accounts list so the transaction adapter can resolve
        // account names without a separate database query
        bankAccountVM.accounts.observe(this) { accounts ->
            latestAccounts = accounts
            refreshTransactionsAdapter()
        }

        // Cache the latest categories and recalculate aggregate spend data
        // whenever a category is added, renamed, or removed
        categoryVM.getCategoriesLive(userId).observe(this) { categories ->
            latestCategories = categories
            loadAggregateData()
        }

        // Cache the latest entries, refresh the transaction list, and recalculate
        // aggregate totals whenever any entry is created, edited, or deleted
        expenseVM.getAllEntriesLive(userId).observe(this) { entries ->
            latestEntries = entries
            refreshTransactionsAdapter()
            loadAggregateData()
        }

        // Populate the savings goals section; show an empty-state view when there
        // are no active (incomplete) goals to display
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
     * Passes the three most recent entries to the transaction adapter along with
     * the cached category and account lists so each row can display resolved names.
     * Also toggles the empty-state placeholder when there are no entries.
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

    // -------------------------------------------------------------------------
    // Aggregate data
    // -------------------------------------------------------------------------

    /**
     * Queries the database for all data needed to populate the summary cards
     * and the category tile strip, then pushes the results to the relevant
     * UI helpers. Runs inside a coroutine because all DAO calls are suspend functions.
     *
     * Computed values include:
     * - All-time total income and expenses (shown in the summary cards).
     * - Current and previous month net figures (used for the trend indicator).
     * - An overspending alert if current-month expenses exceed income.
     * - Per-category spend totals for the current month (fed to the tile adapter).
     */
    private fun loadAggregateData() {
        if (userId == -1) return

        lifecycleScope.launch {
            val dao = AppDatabase.getDatabase(applicationContext).expenseEntryDao()

            // All-time totals shown in the income and expense summary cards
            val totalIncome   = dao.getTotalIncome(userId)
            val totalExpenses = dao.getTotalExpenses(userId)
            showIncomeAmount(totalIncome)
            showExpenseAmount(abs(totalExpenses))

            // Date ranges for the current and previous calendar months
            val (curStart, curEnd)   = currentMonthRange()
            val (prevStart, prevEnd) = lastMonthRange()

            // Current month — split entries into income and expense buckets
            val curEntries = dao.getEntriesForPeriod(userId, curStart, curEnd)
            val curIncome  = curEntries.filter { it.amount > 0 }.sumOf { it.amount }
            val curExpense = curEntries.filter { it.amount < 0 }.sumOf { abs(it.amount) }
            val curNet     = curIncome - curExpense

            // Previous month — same bucketing for the month-over-month comparison
            val prevEntries = dao.getEntriesForPeriod(userId, prevStart, prevEnd)
            val prevIncome  = prevEntries.filter { it.amount > 0 }.sumOf { it.amount }
            val prevExpense = prevEntries.filter { it.amount < 0 }.sumOf { abs(it.amount) }
            val prevNet     = prevIncome - prevExpense

            showBalanceChange(curNet, prevNet)
            showOverspendingAlert(curIncome, curExpense, curEntries)

            // Aggregate current-month expenses by category for the tile strip
            val monthlyTotals = curEntries
                .filter { it.amount < 0 }
                .groupBy { it.categoryId }
                .mapValues { (_, es) -> es.sumOf { abs(it.amount) } }

            tileAdapter.submitCategories(latestCategories, monthlyTotals)
        }
    }

    // -------------------------------------------------------------------------
    // UI update helpers
    // -------------------------------------------------------------------------

    /**
     * Formats and displays the user's current total bank balance.
     *
     * @param balance The raw balance value retrieved from the ViewModel.
     */
    private fun showBalanceAmount(balance: Double) {
        findViewById<TextView>(R.id.balanceAmount).text = "R %,.2f".format(balance)
    }

    /**
     * Formats and displays the user's all-time total income.
     *
     * @param income The sum of all positive entries for this user.
     */
    private fun showIncomeAmount(income: Double) {
        findViewById<TextView>(R.id.incomeAmount).text = "R %,.2f".format(income)
    }

    /**
     * Formats and displays the user's all-time total expenses as a positive value.
     *
     * @param expenses The absolute value of the sum of all negative entries.
     */
    private fun showExpenseAmount(expenses: Double) {
        findViewById<TextView>(R.id.expenseAmount).text = "R %,.2f".format(expenses)
    }

    /**
     * Updates the balance-change indicator below the total balance card.
     * Compares the current month's net (income minus expenses) against the
     * previous month's net and displays a coloured percentage change with an
     * up or down arrow. Handles the edge cases where one or both months have
     * no recorded activity.
     *
     * @param curNet  Net amount (income - expenses) for the current calendar month.
     * @param prevNet Net amount (income - expenses) for the previous calendar month.
     */
    private fun showBalanceChange(curNet: Double, prevNet: Double) {
        val tv = findViewById<TextView>(R.id.balanceChange)
        when {
            // No activity in either month — show a neutral placeholder
            prevNet == 0.0 && curNet == 0.0 -> {
                tv.text = "No activity recorded yet"
                tv.setTextColor(Color.parseColor("#7A99B0"))
            }
            // First month on record — no previous baseline to compare against
            prevNet == 0.0 -> {
                tv.text = "▲ First month on record"
                tv.setTextColor(Color.parseColor("#4ADE80"))
            }
            // Standard case — compute the percentage change relative to last month
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
     * Shows or hides the overspending alert card based on whether current-month
     * expenses exceed current-month income. When visible, the card displays how
     * much the user has overspent and names the category with the highest spend
     * to help them identify where to cut back.
     *
     * @param income   Total income recorded for the current calendar month.
     * @param expenses Total expenses recorded for the current calendar month (positive value).
     * @param entries  All expense/income entries for the current calendar month,
     *                 used to determine the top spending category.
     */
    private fun showOverspendingAlert(
        income: Double,
        expenses: Double,
        entries: List<ExpenseEntry>
    ) {
        val card = findViewById<View>(R.id.overspendingCard)
        val body = findViewById<TextView>(R.id.alertBody)

        // Hide the card when there are no expenses or the user is within budget
        if (expenses == 0.0 || expenses <= income) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        val over = expenses - income

        // Find the category ID that accounts for the largest share of expenses
        val topCategoryId = entries
            .filter { it.amount < 0 }
            .groupBy { it.categoryId }
            .maxByOrNull { (_, es) -> es.sumOf { abs(it.amount) } }
            ?.key

        // Resolve the category name from the cached list, defaulting gracefully
        val catName = latestCategories.find { it.id == topCategoryId }?.name ?: "a category"
        body.text = "You've spent R ${"%.2f".format(over)} more than you earned " +
                "this month. Your highest spend is in $catName."
    }

    // -------------------------------------------------------------------------
    // Date helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the start and end date strings for the current calendar month,
     * formatted as "dd/MM/yyyy" to match the date format used in the database.
     *
     * @return A Pair where first is the first day and second is the last day
     *         of the current month.
     */
    private fun currentMonthRange(): Pair<String, String> {
        val cal   = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1   // Calendar.MONTH is zero-based
        val year  = cal.get(Calendar.YEAR)
        val last  = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return "01/%02d/%d".format(month, year) to "%02d/%02d/%d".format(last, month, year)
    }

    /**
     * Returns the start and end date strings for the previous calendar month,
     * formatted as "dd/MM/yyyy" to match the date format used in the database.
     * Handles year boundaries automatically via Calendar.add().
     *
     * @return A Pair where first is the first day and second is the last day
     *         of the previous month.
     */
    private fun lastMonthRange(): Pair<String, String> {
        val cal   = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)               // Roll back one month (handles year wrap)
        val month = cal.get(Calendar.MONTH) + 1   // Calendar.MONTH is zero-based
        val year  = cal.get(Calendar.YEAR)
        val last  = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return "01/%02d/%d".format(month, year) to "%02d/%02d/%d".format(last, month, year)
    }
}