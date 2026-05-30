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
import com.example.pulserds_prog7313.repository.SubscriptionRepository
import com.example.pulserds_prog7313.viewmodel.SubscriptionViewModel

//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
// [Accessed: 28 Apr. 2026].
//[3] Google, "LiveData overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/livedata.
// [Accessed: 28 Apr. 2026].
//[4] JetBrains, "Coroutines guide," Kotlin Documentation, 2024. [Online].
// Available: https://kotlinlang.org/docs/coroutines-guide.html.
// [Accessed: 28 Apr. 2026].
//[8] JetBrains, kotlinx-coroutines-android, ver. 1.8.1. [Software]. JetBrains, 2024.
// Available: https://github.com/Kotlin/kotlinx.coroutines.
//[9] Google, AndroidX Core KTX, ver. 1.13.1. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/core.
//[10] Google, AndroidX AppCompat, ver. 1.7.0. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/appcompat.
//[25] 	F. van Puffelen, "Save a Room Database file in Firebase," Stack Exchange Inc, 22 August 2021. [Online].
// Available: https://stackoverflow.com/questions/68878022/save-a-room-database-file-in-firebase.
// [Accessed 30 May 2026].
//[26] 	A. Mamo, "Android Room and Firebase in a MVVM Architecture - Offline First Application," Stack Exchange Inc, 18 November 2022. [Online].
// Available: https://stackoverflow.com/questions/74484239/android-room-and-firebase-in-a-mvvm-architecture-offline-first-application.
// [Accessed 30 May 2026].

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
    private lateinit var subscriptionVM: SubscriptionViewModel

    private var latestCategories = mutableListOf<Category>()
    private var latestAccounts:   List<BankAccount> = emptyList()
    private var latestEntries:    List<ExpenseEntry> = emptyList()
    private var latestGoals:      List<com.example.pulserds_prog7313.data.SavingsGoal> = emptyList()

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

        // Subscriptions "Manage" button on dashboard
        findViewById<android.widget.TextView>(R.id.subManageLink)?.setOnClickListener {
            startActivity(Intent(this, ManageSubscriptionsActivity::class.java))
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

        subscriptionVM = ViewModelProvider(
            this,
            ViewModelFactory { SubscriptionViewModel(SubscriptionRepository()) }
        )[SubscriptionViewModel::class.java]
        subscriptionVM.init(userId)
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

        observeSubscriptions()

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
            latestGoals = goals.filter { !it.isCompleted }
            val activeGoals = latestGoals.take(3)
            savingsAdapter.submitList(activeGoals)

            val hasGoals = activeGoals.isNotEmpty()
            findViewById<View>(R.id.dashSavingsRecycler).visibility =
                if (hasGoals) View.VISIBLE else View.GONE
            findViewById<View>(R.id.dashSavingsEmpty).visibility =
                if (hasGoals) View.GONE else View.VISIBLE
        }
    }

    private fun observeSubscriptions() {
        subscriptionVM.subscriptions.observe(this) { subs ->
            val activeSubs = subs.filter { it.status == "Active" }.take(3)
            val container = findViewById<android.widget.LinearLayout>(R.id.dashSubsContainer)
                ?: return@observe
            container.removeAllViews()
            if (activeSubs.isEmpty()) {
                val empty = android.widget.TextView(this).apply {
                    text = "No active subscriptions."
                    setTextColor(0xFF7A99B0.toInt())
                    textSize = 13f
                    setPadding(40, 24, 40, 24)
                }
                container.addView(empty)
            } else {
                activeSubs.forEach { sub ->
                    val row = android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(40, 20, 40, 20)
                    }
                    val icon = android.widget.TextView(this).apply {
                        text = sub.icon; textSize = 20f; setPadding(0, 0, 20, 0)
                    }
                    val info = android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    info.addView(android.widget.TextView(this).apply {
                        text = sub.serviceName
                        setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    })
                    info.addView(android.widget.TextView(this).apply {
                        text = "Renews ${sub.nextRenewal}"
                        setTextColor(0xFF7A99B0.toInt()); textSize = 12f
                    })
                    val lbl = when(sub.billingCycle) { "Weekly"->"wk"; "Yearly"->"yr"; else->"mo" }
                    val amt = android.widget.TextView(this).apply {
                        text = "${CurrencyManager.format(this@DashBoardActivity, sub.amount)}/$lbl"
                        setTextColor(0xFFF87171.toInt()); textSize = 13f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }
                    row.addView(icon); row.addView(info); row.addView(amt)
                    container.addView(row)
                }
            }
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

        if (expenses == 0.0) {
            card.visibility = View.GONE
            return
        }

        val spentByCategory = entries
            .filter { it.amount < 0 }
            .groupBy { it.categoryId }
            .mapValues { (_, es) -> es.sumOf { abs(it.amount) } }

        if (spentByCategory.isEmpty()) {
            card.visibility = View.GONE
            return
        }

        // Match each savings goal to a category by name (case-insensitive)
        data class BudgetBreach(val catName: String, val spent: Double, val budget: Double)

        val breaches = mutableListOf<BudgetBreach>()
        for ((catId, spent) in spentByCategory) {
            val catName = latestCategories.find { it.id == catId }?.name ?: continue
            val goal = latestGoals.find { it.name.trim().lowercase() == catName.trim().lowercase() }
            if (goal != null && goal.targetAmount > 0 && spent > goal.targetAmount) {
                breaches.add(BudgetBreach(catName.replaceFirstChar { it.uppercase() }, spent, goal.targetAmount))
            }
        }

        card.visibility = View.VISIBLE

        if (breaches.isNotEmpty()) {
            // Show the worst breach first
            val worst = breaches.maxByOrNull { it.spent - it.budget }!!
            val overBy = worst.spent - worst.budget

            // Suggest the next-highest spending category as a place to cut back
            val otherCatName = spentByCategory.entries
                .sortedByDescending { it.value }
                .map { (id, _) -> latestCategories.find { it.id == id }?.name }
                .firstOrNull { it?.lowercase() != worst.catName.lowercase() }
                ?.replaceFirstChar { it.uppercase() }

            val suggestion = if (otherCatName != null)
                " Consider shifting funds from $otherCatName." else ""
            body.text = "You've exceeded your ${worst.catName} savings goal by " +
                    "${CurrencyManager.format(this, overBy)}.$suggestion"
        } else {
            // No goal breached — show top spending category as a general insight
            val topCatId = spentByCategory.maxByOrNull { it.value }!!.key
            val topCatName = latestCategories.find { it.id == topCatId }
                ?.name?.replaceFirstChar { it.uppercase() } ?: "a category"
            val topSpent = spentByCategory[topCatId] ?: 0.0

            val secondCatName = spentByCategory.entries
                .sortedByDescending { it.value }
                .drop(1).firstOrNull()?.key
                ?.let { id -> latestCategories.find { it.id == id }?.name?.replaceFirstChar { it.uppercase() } }

            val suggestion = if (secondCatName != null)
                " Keep an eye on your $secondCatName spending too." else ""
            body.text = "Your highest spend this month is $topCatName at " +
                    "${CurrencyManager.format(this, topSpent)}.$suggestion"
        }
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