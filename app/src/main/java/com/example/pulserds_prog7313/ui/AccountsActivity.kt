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
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton

/**
 * AccountsActivity
 * ----------------
 * Displays all bank accounts linked to the logged-in user and provides
 * a visual overview of their balances.
 *
 * Shows:
 *   - A donut chart with one segment per account (balance as proportion).
 *   - A colour-coded legend matching each segment to its account.
 *   - A scrollable list of account cards with masked account numbers,
 *     bank name, account type, and current balance.
 *   - An "Add Account" button that navigates to [AddAccountActivity].
 *
 * Layout    : activity_accounts.xml
 * ViewModel : [BankAccountViewModel] -> accounts list and total balance
 */
class AccountsActivity : BaseActivity() {

    /** ViewModel that exposes the account list and aggregated total balance. */
    private lateinit var viewModel: BankAccountViewModel

    /** ID of the currently logged-in user; -1 if no session is active. */
    private var userId = -1

    // -- View references ------------------------------------------------------
    /** Container that holds dynamically inflated account card rows. */
    private lateinit var accountsContainer: LinearLayout

    /** Shown in place of account cards when the user has no linked accounts. */
    private lateinit var emptyAccountsText: TextView

    /** Horizontal row that holds one legend item per account. */
    private lateinit var legendRow: LinearLayout

    /** Donut chart that visualises each account's balance as a segment. */
    private lateinit var donutChart: DonutChartView

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_accounts)

        // Adjust root layout padding so content is not obscured by system bars
        // (status bar, navigation bar) on edge-to-edge displays.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupViewModel()
        observeData()

        // Navigate to the Add Account screen when the button is tapped.
        findViewById<MaterialButton>(R.id.addAccountButton)?.setOnClickListener {
            startActivity(Intent(this, AddAccountActivity::class.java))
        }

        setupBottomNav(R.id.navAccounts)
    }

    // -------------------------------------------------------------------------
    // View binding
    // Centralises all findViewById calls so onCreate stays clean and readable.
    // -------------------------------------------------------------------------

    private fun bindViews() {
        accountsContainer = findViewById(R.id.accountsContainer)
        emptyAccountsText = findViewById(R.id.emptyAccountsText)
        legendRow         = findViewById(R.id.legendRow)
        donutChart        = findViewById(R.id.donutChart)
    }

    // -------------------------------------------------------------------------
    // ViewModel setup
    // Build the dependency chain: Database -> DAO -> Repository -> ViewModel,
    // then initialise the ViewModel with the current user's ID.
    // -------------------------------------------------------------------------

    private fun setupViewModel() {
        userId = SessionManager(this).getUserId()
        val dao   = AppDatabase.getDatabase(this).bankAccountDao()
        val repo  = BankAccountRepository(dao)
        viewModel = ViewModelProvider(this, ViewModelFactory { BankAccountViewModel(repo) })
            .get(BankAccountViewModel::class.java)
        viewModel.init(userId) // Scopes all queries to the logged-in user.
    }

    // -------------------------------------------------------------------------
    // Data observation
    // -------------------------------------------------------------------------

    private fun observeData() {
        // Single source of truth: the accounts list drives the cards, legend,
        // AND donut chart so all three are always in sync with each other.
        viewModel.accounts.observe(this) { accounts ->
            renderAccountsList(accounts)
            updateLegend(accounts)
            updateDonut(accounts)
        }

        // The total balance is observed separately because it is pre-aggregated
        // by the DAO. It only updates the donut chart's centre label.
        viewModel.totalBalance.observe(this) { total ->
            donutChart.setCenterLabel(formatRand(total), "ZAR *")
        }
    }

    // -------------------------------------------------------------------------
    // Account card rows
    // -------------------------------------------------------------------------

    /**
     * Clears and rebuilds the account card list inside [accountsContainer].
     *
     * Each card is inflated from item_bank_account.xml and populated with:
     *   - Colour dot matching the account's assigned colour.
     *   - Bank name and account type tag.
     *   - Masked account number (last 4 digits visible).
     *   - Current balance formatted as Rand.
     *
     * Dividers are inserted between cards but not after the last one.
     * The empty-state message is shown when [accounts] is empty.
     */
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

            // Colour dot — fall back to green if the stored hex is invalid.
            val dotColour = safeParseColour(account.colour, "#4ADE80")
            row.findViewById<View>(R.id.accountDot).setBackgroundColor(dotColour)
            row.findViewById<TextView>(R.id.accountBankName).text = account.bankName

            // Account type tag inherits the same colour as the dot for visual consistency.
            val typeTag = row.findViewById<TextView>(R.id.accountTypeTag)
            typeTag.text = account.accountType
            typeTag.setTextColor(dotColour)

            row.findViewById<TextView>(R.id.accountBalance).text = formatRand(account.balance)

            // Mask all but the last 4 digits of the account number for security.
            val last4 = account.accountNumber.takeLast(4).padStart(4, '*')
            row.findViewById<TextView>(R.id.accountMasked).text = "**** $last4"

            accountsContainer.addView(row)

            // Add a divider after every card except the last one.
            if (index < accounts.lastIndex) {
                accountsContainer.addView(buildDivider())
            }
        }
    }

    // -------------------------------------------------------------------------
    // Legend
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the colour-coded legend to mirror the donut chart segments.
     *
     * Only accounts with includeInTotal = true AND balance > 0 are shown,
     * matching the same filter used by the donut chart and the DAO total.
     * If no accounts pass that filter, falls back to showing all accounts
     * so the legend is never empty when accounts exist.
     */
    private fun updateLegend(accounts: List<BankAccount>) {
        legendRow.removeAllViews()

        // One legend entry per account that appears in the donut chart.
        accounts
            .filter { it.includeInTotal && it.balance > 0 }
            .forEach { account ->
                legendRow.addView(buildLegendItem(account.nickname.ifBlank { account.bankName }, account.colour))
            }

        // Fall back to showing all accounts if none pass the filter above.
        if (legendRow.childCount == 0) {
            accounts.forEach { account ->
                legendRow.addView(buildLegendItem(account.nickname.ifBlank { account.bankName }, account.colour))
            }
        }
    }

    /**
     * Builds a single legend item: a coloured dot followed by a label.
     *
     * @param label      Text to display next to the dot (nickname or bank name).
     * @param hexColour  Hex colour string for the dot; falls back to green if invalid.
     * @return A [LinearLayout] containing the dot [View] and label [TextView].
     */
    private fun buildLegendItem(label: String, hexColour: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, dpToPx(16), 0) }
        }

        // Coloured dot representing this account in the legend.
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

    // -------------------------------------------------------------------------
    // Donut chart
    // -------------------------------------------------------------------------

    /**
     * Updates the donut chart segments from the current accounts list.
     *
     * Uses EXACTLY the same filter as BankAccountDao.getTotalBalance():
     *   includeInTotal = true  AND  balance > 0
     *
     * One segment per qualifying account; each segment's size is proportional
     * to its balance and coloured with the account's assigned colour.
     * Passing an empty list renders a grey placeholder ring.
     */
    private fun updateDonut(accounts: List<BankAccount>) {
        val segments = accounts
            .filter { it.includeInTotal && it.balance > 0 }
            .map { account ->
                account.balance.toFloat() to safeParseColour(account.colour, "#4ADE80")
            }

        // Always push to the chart — an empty list renders a grey placeholder ring.
        donutChart.setSegments(segments)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Formats [amount] as a South African Rand string with thousands separators
     * and two decimal places (e.g. "R 12,345.67").
     */
    private fun formatRand(amount: Double): String = "R %,.2f".format(amount)

    /**
     * Safely parses [hex] into an Android colour integer.
     * Returns the parsed [fallback] colour if [hex] is null, empty, or malformed.
     */
    private fun safeParseColour(hex: String, fallback: String): Int =
        try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor(fallback) }

    /**
     * Builds a thin horizontal divider line used to separate account card rows.
     * Margins give the divider an inset appearance consistent with the card layout.
     */
    private fun buildDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).also { it.setMargins(dpToPx(20), dpToPx(4), dpToPx(20), dpToPx(4)) }
        setBackgroundColor(Color.parseColor("#1A3248"))
    }

    /**
     * Converts a dp value to pixels using the device's current display density.
     * Used for programmatically set margins and dimensions to stay density-independent.
     */
    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}