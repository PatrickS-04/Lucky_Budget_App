package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.pulserds_prog7313.DonutChartView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton

class AccountsActivity : BaseActivity() {

    private lateinit var viewModel: BankAccountViewModel
    private var userId = -1

    // Views
    private lateinit var accountsContainer: LinearLayout
    private lateinit var emptyAccountsText: TextView
    private lateinit var legendRow: LinearLayout
    private lateinit var donutChart: DonutChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_accounts)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupViewModel()
        observeData()

        // "Add Account" button -> AddAccountActivity
        findViewById<MaterialButton>(R.id.addAccountButton)?.setOnClickListener {
            startActivity(Intent(this, AddAccountActivity::class.java))
        }

        setupBottomNav(R.id.navAccounts)
    }

    //  View binding

    private fun bindViews() {
        accountsContainer = findViewById(R.id.accountsContainer)
        emptyAccountsText = findViewById(R.id.emptyAccountsText)
        legendRow         = findViewById(R.id.legendRow)
        donutChart        = findViewById(R.id.donutChart)
    }

    //  ViewModel setup

    private fun setupViewModel() {
        userId = SessionManager(this).getUserId()
        val dao   = AppDatabase.getDatabase(this).bankAccountDao()
        val repo  = BankAccountRepository(dao)
        viewModel = ViewModelProvider(this, ViewModelFactory { BankAccountViewModel(repo) })
            .get(BankAccountViewModel::class.java)
        viewModel.init(userId)
    }

    //  Observers

    private fun observeData() {
        // Accounts list drives the card rows, legend, and donut chart
        viewModel.accounts.observe(this) { accounts ->
            renderAccountsList(accounts)
            updateLegend(accounts)
            updateDonut(accounts)
        }

        // Total balance (only accounts with includeInTotal = true)
        viewModel.totalBalance.observe(this) { total ->
            donutChart.setCenterLabel(formatRand(total), "ZAR *")
        }
    }

    //  Accounts list

    private fun renderAccountsList(accounts: List<BankAccount>) {
        accountsContainer.removeAllViews()

        if (accounts.isEmpty()) {
            emptyAccountsText.visibility = View.VISIBLE
            return
        }
        emptyAccountsText.visibility = View.GONE

        val inflater = LayoutInflater.from(this)

        accounts.forEachIndexed { index, account ->
            val row = inflater.inflate(R.layout.item_bank_account, accountsContainer, false)

            // Colour dot - use the account's stored hex colour
            val dotColour = safeParseColour(account.colour, "#4ADE80")
            row.findViewById<View>(R.id.accountDot).setBackgroundColor(dotColour)

            // Primary label: bank name
            row.findViewById<TextView>(R.id.accountBankName).text = account.bankName

            // Type tag coloured to match the dot
            val typeTag = row.findViewById<TextView>(R.id.accountTypeTag)
            typeTag.text = account.accountType
            typeTag.setTextColor(dotColour)

            // Balance
            row.findViewById<TextView>(R.id.accountBalance).text = formatRand(account.balance)

            // Masked account number - show last 4 digits
            val last4 = if (account.accountNumber.length >= 4)
                account.accountNumber.takeLast(4)
            else
                account.accountNumber
            row.findViewById<TextView>(R.id.accountMasked).text = "**** $last4"

            accountsContainer.addView(row)

            // Hairline divider between rows (not after the last one)
            if (index < accounts.lastIndex) {
                accountsContainer.addView(buildDivider())
            }
        }
    }

    //  Legend

    private fun updateLegend(accounts: List<BankAccount>) {
        legendRow.removeAllViews()
        // One legend entry per distinct account type, in the order they appear
        val seen = mutableSetOf<String>()
        accounts.forEach { account ->
            if (seen.add(account.accountType)) {
                legendRow.addView(buildLegendItem(account.accountType, account.colour))
            }
        }
    }

    private fun buildLegendItem(label: String, hexColour: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, dpToPx(16), 0) }
        }

        val dot = View(this).apply {
            val size     = dpToPx(10)
            layoutParams = LinearLayout.LayoutParams(size, size)
                .also { it.setMargins(0, 0, dpToPx(6), 0) }
            setBackgroundColor(safeParseColour(hexColour, "#4ADE80"))
        }

        val text = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            this.text = label
            setTextColor(Color.parseColor("#7A99B0"))
            textSize = 12f
        }

        item.addView(dot)
        item.addView(text)
        return item
    }

    //  Donut chart

    private fun updateDonut(accounts: List<BankAccount>) {
        val segments = accounts
            .filter { it.includeInTotal && it.balance > 0 }
            .groupBy { it.accountType }
            .map { (_, group) ->
                val typeTotal = group.sumOf { it.balance }.toFloat()
                val colour    = safeParseColour(group.first().colour, "#4ADE80")
                typeTotal to colour
            }
        if (segments.isNotEmpty()) donutChart.setSegments(segments)
    }

    //  Helpers

    private fun formatRand(amount: Double): String = "R %,.2f".format(amount)

    private fun safeParseColour(hex: String, fallback: String): Int =
        try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor(fallback) }

    private fun buildDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).also { it.setMargins(dpToPx(20), dpToPx(4), dpToPx(20), dpToPx(4)) }
        setBackgroundColor(Color.parseColor("#1A3248"))
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}