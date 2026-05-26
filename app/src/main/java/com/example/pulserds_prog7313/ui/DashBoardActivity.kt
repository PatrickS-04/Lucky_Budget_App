package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
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
import com.example.pulserds_prog7313.utils.CurrencyManager

class DashBoardActivity : BaseActivity() {

    private lateinit var categoryVM:    CategoryViewModel
    private lateinit var expenseVM:     ExpenseEntryViewModel
    private lateinit var savingsVM:     SavingsGoalViewModel
    private lateinit var bankAccountVM: BankAccountViewModel

    private lateinit var tileAdapter:   CategoryTileAdapter
    private lateinit var savingsAdapter:DashSavingsAdapter
    private lateinit var txAdapter:     TransactionAdapter

    // userId is now a String (Firebase Auth UID). "" means no session.
    private var userId = ""

    private var latestCategories = mutableListOf<Category>()
    private var latestAccounts:   List<BankAccount> = emptyList()
    private var latestEntries:    List<ExpenseEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dash_board)

        val navBarContainer = findViewById<LinearLayout>(R.id.containerForBottomNavBar)

        ViewCompat.setOnApplyWindowInsetsListener(navBarContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val basePadding = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(bars.left, bars.top, bars.right, basePadding + bars.bottom)
            insets
        }

        val session = SessionManager(this)
        userId = session.getUserId()   // Now returns String

        val firstName = session.getFullName()?.split(" ")?.firstOrNull() ?: "there"
        findViewById<TextView>(R.id.greetingText).text = "Hi, $firstName! 👋"

        setupViewModels()
        setupRecyclers()
        observeLiveData()

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
        if (userId.isNotBlank()) {
            loadAggregateData(latestCategories)
            bankAccountVM.refreshTotal(userId)
        }
    }

    private fun setupViewModels() {
        // No AppDatabase needed -- repositories use Firebase directly
        categoryVM = ViewModelProvider(
            this,
            ViewModelFactory { CategoryViewModel(CategoryRepository()) }
        )[CategoryViewModel::class.java]

        expenseVM = ViewModelProvider(
            this,
            ViewModelFactory { ExpenseEntryViewModel(ExpenseEntryRepository()) }
        )[ExpenseEntryViewModel::class.java]

        savingsVM = ViewModelProvider(
            this,
            ViewModelFactory { SavingsGoalViewModel(SavingsGoalRepository()) }
        )[SavingsGoalViewModel::class.java]

        bankAccountVM = ViewModelProvider(
            this,
            ViewModelFactory { BankAccountViewModel(BankAccountRepository()) }
        )[BankAccountViewModel::class.java]

        if (userId.isNotBlank()) {
            lifecycleScope.launch {
                categoryVM.seedDefaultCategories(userId)
            }
        }

        bankAccountVM.init(userId)
    }

    private fun setupRecyclers() {
        tileAdapter = CategoryTileAdapter()
        findViewById<RecyclerView>(R.id.dashCategoryTilesRecycler).apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@DashBoardActivity, 2)
            adapter = tileAdapter
            isNestedScrollingEnabled = false
        }

        savingsAdapter = DashSavingsAdapter()
        findViewById<RecyclerView>(R.id.dashSavingsRecycler).apply {
            layoutManager = LinearLayoutManager(this@DashBoardActivity)
            adapter = savingsAdapter
            isNestedScrollingEnabled = false
        }

        txAdapter = TransactionAdapter()
        findViewById<RecyclerView>(R.id.dashTransactionsRecycler).apply {
            layoutManager = LinearLayoutManager(this@DashBoardActivity)
            adapter = txAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeLiveData() {
        if (userId.isBlank()) return

        bankAccountVM.totalBalance.observe(this) { balance -> showBalanceAmount(balance) }

        bankAccountVM.accounts.observe(this) { accounts ->
            latestAccounts = accounts
            refreshTransactionsAdapter()
        }

        categoryVM.getCategoriesLive(userId).observe(this) { newCategory ->
            if (newCategory != null)
            {
                latestCategories.clear()

                val defaultCategories = setOf(
                    "groceries", "dining out", "transport", "entertainment",
                    "subscriptions", "utilities", "healthcare", "shopping",
                    "salary", "investment", "freelance", "gift"
                )

                val fixedCategories = newCategory.map { category ->
                    val cleanName = category.name.lowercase().trim()
                    if (defaultCategories.contains(cleanName)) {
                        category.copy(name = cleanName, isDefault = true)
                    } else {
                        category.copy(name = cleanName)
                    }
                }

                val differentCategories = fixedCategories.distinctBy { it.name.lowercase().trim() }
                latestCategories.addAll(differentCategories)
                refreshTransactionsAdapter()
                loadAggregateData(differentCategories.toList())
            }
        }

        expenseVM.getAllEntriesLive(userId).observe(this) { entries ->
            latestEntries = entries
            refreshTransactionsAdapter()
            loadAggregateData(latestCategories)
        }

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

    private fun refreshTransactionsAdapter() {
        val recent = latestEntries.take(3)
        txAdapter.submitData(recent, latestCategories, latestAccounts)

        val hasData = recent.isNotEmpty()
        findViewById<View>(R.id.dashTransactionsRecycler).visibility =
            if (hasData) View.VISIBLE else View.GONE
        findViewById<View>(R.id.dashTransactionsEmpty).visibility =
            if (hasData) View.GONE else View.VISIBLE
    }

    private fun loadAggregateData(renderCategories: List<Category>) {
        if (userId.isBlank()) return

        lifecycleScope.launch {
            try {
                val repo = ExpenseEntryRepository()

                val totalIncome   = repo.getTotalIncome(userId)
                val totalExpenses = repo.getTotalExpenses(userId)
                showIncomeAmount(totalIncome)
                showExpenseAmount(abs(totalExpenses))

                val (curStart, curEnd)   = currentMonthRange()
                val (prevStart, prevEnd) = lastMonthRange()

                val curEntries = repo.getEntriesForPeriod(userId, curStart, curEnd)
                val curIncome  = curEntries.filter { it.amount > 0 }.sumOf { it.amount }
                val curExpense = curEntries.filter { it.amount < 0 }.sumOf { abs(it.amount) }
                val curNet     = curIncome - curExpense

                val prevEntries = repo.getEntriesForPeriod(userId, prevStart, prevEnd)
                val prevIncome  = prevEntries.filter { it.amount > 0 }.sumOf { it.amount }
                val prevExpense = prevEntries.filter { it.amount < 0 }.sumOf { abs(it.amount) }
                val prevNet     = prevIncome - prevExpense

                showBalanceChange(curNet, prevNet)
                showOverspendingAlert(curIncome, curExpense, curEntries)

                val monthlyTotals = curEntries
                    .filter { it.amount < 0 }
                    .groupBy { it.categoryId }
                    .mapValues { (_, es) -> es.sumOf { abs(it.amount) } }

                tileAdapter.submitCategories(renderCategories, monthlyTotals)
            } catch (_: Exception) {
                // Firestore unavailable — leave existing UI state as-is
            }
        }
    }

    private fun showBalanceAmount(balance: Double) {
        findViewById<TextView>(R.id.balanceAmount).text =
            CurrencyManager.format(this, balance)
    }

    private fun showIncomeAmount(income: Double) {
        findViewById<TextView>(R.id.incomeAmount).text =
            CurrencyManager.format(this, income)
    }

    private fun showExpenseAmount(expenses: Double) {
        findViewById<TextView>(R.id.expenseAmount).text =
            CurrencyManager.format(this, expenses)
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

    private fun showOverspendingAlert(income: Double, expenses: Double, entries: List<ExpenseEntry>) {
        val card = findViewById<View>(R.id.overspendingCard)
        val body = findViewById<TextView>(R.id.alertBody)

        if (expenses == 0.0 || expenses <= income) {
            card.visibility = View.GONE
            return
        }

        card.visibility = View.VISIBLE
        val over = expenses - income

        val topCategoryId = entries
            .filter { it.amount < 0 }
            .groupBy { it.categoryId }
            .maxByOrNull { (_, es) -> es.sumOf { abs(it.amount) } }
            ?.key

        val catName = latestCategories.find { it.id == topCategoryId }?.name ?: "a category"

        body.text = "You've spent ${CurrencyManager.format(this, over)} more than " +
                "you earned this month. Your highest spend is in $catName."
    }

    private fun currentMonthRange(): Pair<String, String> {
        val cal   = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)
        val last  = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return "01/%02d/%d".format(month, year) to "%02d/%02d/%d".format(last, month, year)
    }

    private fun lastMonthRange(): Pair<String, String> {
        val cal   = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        val month = cal.get(Calendar.MONTH) + 1
        val year  = cal.get(Calendar.YEAR)
        val last  = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        return "01/%02d/%d".format(month, year) to "%02d/%02d/%d".format(last, month, year)
    }
}