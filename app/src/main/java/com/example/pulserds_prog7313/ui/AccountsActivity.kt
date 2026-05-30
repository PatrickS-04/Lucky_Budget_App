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

//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
// [Accessed: 28 Apr. 2026].
//[3] Google, "LiveData overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/livedata.
// [Accessed: 28 Apr. 2026].
//[9] Google, AndroidX Core KTX, ver. 1.13.1. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/core.
//[10] Google, AndroidX AppCompat, ver. 1.7.0. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/appcompat.
//[15] Google, "View Binding," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/view-binding.
// [Accessed: 28 Apr. 2026].
//[20] Google, "Canvas and drawables," Android Developers, 2024. [Online].
// Available: https://developer.android.com/develop/ui/views/graphics/drawables.
// [Accessed: 28 Apr. 2026].
//[25] 	F. van Puffelen, "Save a Room Database file in Firebase," Stack Exchange Inc, 22 August 2021. [Online].
// Available: https://stackoverflow.com/questions/68878022/save-a-room-database-file-in-firebase.
// [Accessed 30 May 2026].
//[26] 	A. Mamo, "Android Room and Firebase in a MVVM Architecture - Offline First Application," Stack Exchange Inc, 18 November 2022. [Online].
// Available: https://stackoverflow.com/questions/74484239/android-room-and-firebase-in-a-mvvm-architecture-offline-first-application.
// [Accessed 30 May 2026].

class AccountsActivity : BaseActivity() {

    private lateinit var viewModel: BankAccountViewModel
    private var userId = ""   // String UID

    private lateinit var accountsContainer: LinearLayout
    private lateinit var emptyAccountsText: TextView
    private lateinit var legendRow: LinearLayout
    private lateinit var donutChart: DonutChartView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_accounts)

        val navBarContainer = findViewById<LinearLayout>(R.id.containerForBottomNavBar)

        ViewCompat.setOnApplyWindowInsetsListener(navBarContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val basePadding = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(bars.left, bars.top, bars.right, basePadding + bars.bottom)
            insets
        }

        bindViews()
        setupViewModel()
        observeData()

        findViewById<MaterialButton>(R.id.addAccountButton)?.setOnClickListener {
            startActivity(Intent(this, AddAccountActivity::class.java))
        }

        setupBottomNav(R.id.navAccounts)
    }

    private fun bindViews() {
        accountsContainer = findViewById(R.id.accountsContainer)
        emptyAccountsText = findViewById(R.id.emptyAccountsText)
        legendRow         = findViewById(R.id.legendRow)
        donutChart        = findViewById(R.id.donutChart)
    }

    private fun setupViewModel() {
        userId = SessionManager(this).getUserId()
        // No AppDatabase -- repository uses Firebase directly
        viewModel = ViewModelProvider(this, ViewModelFactory { BankAccountViewModel(BankAccountRepository()) })
            .get(BankAccountViewModel::class.java)
        viewModel.init(userId)
    }

    private fun observeData() {
        viewModel.accounts.observe(this) { accounts ->
            renderAccountsList(accounts)
            updateLegend(accounts)
            updateDonut(accounts)
        }
        viewModel.totalBalance.observe(this) { total ->
            donutChart.setCenterLabel(formatRand(total), "ZAR *")
        }
    }

    private fun renderAccountsList(accounts: List<BankAccount>) {
        accountsContainer.removeAllViews()
        if (accounts.isEmpty()) { emptyAccountsText.visibility = View.VISIBLE; return }
        emptyAccountsText.visibility = View.GONE
        val inflater = LayoutInflater.from(this)
        accounts.forEachIndexed { index, account ->
            val row = inflater.inflate(R.layout.item_bank_account, accountsContainer, false)
            val dotColour = safeParseColour(account.colour, "#4ADE80")
            row.findViewById<View>(R.id.accountDot).setBackgroundColor(dotColour)
            row.findViewById<TextView>(R.id.accountBankName).text = account.bankName
            val typeTag = row.findViewById<TextView>(R.id.accountTypeTag)
            typeTag.text = account.accountType
            typeTag.setTextColor(dotColour)
            row.findViewById<TextView>(R.id.accountBalance).text = formatRand(account.balance)
            val last4 = account.accountNumber.takeLast(4).padStart(4, '*')
            row.findViewById<TextView>(R.id.accountMasked).text = "**** $last4"
            accountsContainer.addView(row)
            if (index < accounts.lastIndex) accountsContainer.addView(buildDivider())
        }
    }

    private fun updateLegend(accounts: List<BankAccount>) {
        legendRow.removeAllViews()
        accounts.filter { it.includeInTotal && it.balance > 0 }.forEach { account ->
            legendRow.addView(buildLegendItem(account.nickname.ifBlank { account.bankName }, account.colour))
        }
        if (legendRow.childCount == 0) {
            accounts.forEach { account ->
                legendRow.addView(buildLegendItem(account.nickname.ifBlank { account.bankName }, account.colour))
            }
        }
    }

    private fun buildLegendItem(label: String, hexColour: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(0, 0, dpToPx(16), 0) }
        }
        val dot = View(this).apply {
            val size = dpToPx(10)
            layoutParams = LinearLayout.LayoutParams(size, size).also { it.setMargins(0, 0, dpToPx(6), 0) }
            setBackgroundColor(safeParseColour(hexColour, "#4ADE80"))
        }
        val text = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            this.text = label
            setTextColor(Color.parseColor("#7A99B0"))
            textSize = 12f
        }
        item.addView(dot); item.addView(text)
        return item
    }

    private fun updateDonut(accounts: List<BankAccount>) {
        val segments = accounts.filter { it.includeInTotal && it.balance > 0 }
            .map { account -> account.balance.toFloat() to safeParseColour(account.colour, "#4ADE80") }
        donutChart.setSegments(segments)
    }

    private fun formatRand(amount: Double): String = "R %,.2f".format(amount)
    private fun safeParseColour(hex: String, fallback: String): Int =
        try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor(fallback) }
    private fun buildDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            .also { it.setMargins(dpToPx(20), dpToPx(4), dpToPx(20), dpToPx(4)) }
        setBackgroundColor(Color.parseColor("#1A3248"))
    }
    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
