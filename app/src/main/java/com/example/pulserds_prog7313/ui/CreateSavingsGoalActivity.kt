package com.example.pulserds_prog7313.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.SavingsGoalViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class CreateSavingsGoalActivity : AppCompatActivity() {

    private lateinit var savingsVm : SavingsGoalViewModel
    private lateinit var bankVm    : BankAccountViewModel

    private var userId            = -1
    private var selectedIcon      = "🏦"
    private var selectedAccountId = 0
    private var selectedFrequency = "MONTHLY"
    private var accountsList      : List<BankAccount> = emptyList()

    private val iconMap = mapOf(
        R.id.iconBank   to "🏦",
        R.id.iconPlane  to "✈️",
        R.id.iconLaptop to "💻",
        R.id.iconCar    to "🚗",
        R.id.iconHouse  to "🏠",
        R.id.iconRing   to "💍",
        R.id.iconGrad   to "🎓",
        R.id.iconChart  to "📈",
        R.id.iconBaby   to "👶",
        R.id.iconGift   to "🎁"
    )

    private lateinit var goalNameEdit       : TextInputEditText
    private lateinit var targetAmountEdit   : TextInputEditText
    private lateinit var initialDepositEdit : TextInputEditText
    private lateinit var targetDateEdit     : TextInputEditText
    private lateinit var accountDropdown    : AutoCompleteTextView
    private lateinit var autoSaveSwitch     : Switch
    private lateinit var autoSaveOptions    : View
    private lateinit var autoSaveAmountEdit : TextInputEditText
    private lateinit var freqWeekly         : TextView
    private lateinit var freqMonthly        : TextView
    private lateinit var freqYearly         : TextView
    private lateinit var previewIcon        : TextView
    private lateinit var previewName        : TextView
    private lateinit var previewDetails     : TextView
    private lateinit var previewProgressBar : ProgressBar
    private lateinit var previewProgressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_savings_goal)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        userId = SessionManager(this).getUserId()
        val db = AppDatabase.getDatabase(this)

        savingsVm = ViewModelProvider(
            this, ViewModelFactory { SavingsGoalViewModel(SavingsGoalRepository(db.savingsGoalDao())) }
        )[SavingsGoalViewModel::class.java]

        bankVm = ViewModelProvider(
            this, ViewModelFactory { BankAccountViewModel(BankAccountRepository(db.bankAccountDao())) }
        )[BankAccountViewModel::class.java]
        bankVm.init(userId)

        bindViews()
        setupIconPicker()
        setupAccountDropdown()
        setupDatePicker()
        setupAutoSaveToggle()
        setupFrequencyChips()
        setupLivePreview()
        setupButtons()
        observeResults()
    }

    // ── Bind ──────────────────────────────────────────────────────────────────
    private fun bindViews() {
        goalNameEdit        = findViewById(R.id.goalNameEditText)
        targetAmountEdit    = findViewById(R.id.targetAmountEditText)
        initialDepositEdit  = findViewById(R.id.initialDepositEditText)
        targetDateEdit      = findViewById(R.id.targetDateEditText)
        accountDropdown     = findViewById(R.id.linkedAccountDropdown)
        autoSaveSwitch      = findViewById(R.id.autoSaveSwitch)
        autoSaveOptions     = findViewById(R.id.autoSaveOptions)
        autoSaveAmountEdit  = findViewById(R.id.autoSaveAmountEditText)
        freqWeekly          = findViewById(R.id.freqWeekly)
        freqMonthly         = findViewById(R.id.freqMonthly)
        freqYearly          = findViewById(R.id.freqYearly)
        previewIcon         = findViewById(R.id.previewIcon)
        previewName         = findViewById(R.id.previewName)
        previewDetails      = findViewById(R.id.previewDetails)
        previewProgressBar  = findViewById(R.id.previewProgressBar)
        previewProgressText = findViewById(R.id.previewProgressText)
    }

    // ── Icon picker ───────────────────────────────────────────────────────────
    private fun setupIconPicker() {
        highlightIcon(R.id.iconBank)
        for ((viewId, emoji) in iconMap) {
            val view = findViewById<LinearLayout>(viewId) ?: continue
            view.setOnClickListener {
                selectedIcon = emoji
                highlightIcon(viewId)
                previewIcon.text = emoji
            }
        }
    }

    private fun highlightIcon(selectedId: Int) {
        for (viewId in iconMap.keys) {
            val bg = if (viewId == selectedId) "#0D2A1A" else "#1A2A38"
            try {
                val v = findViewById<LinearLayout>(viewId) ?: continue
                v.setBackgroundColor(android.graphics.Color.parseColor(bg))
            } catch (_: Exception) {}
        }
    }

    // ── Account dropdown ──────────────────────────────────────────────────────
    private fun setupAccountDropdown() {
        bankVm.accounts.observe(this) { accounts ->
            accountsList = accounts
            val labels = listOf("No linked account") + accounts.map { "${it.nickname} (${it.bankName})" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
            accountDropdown.setAdapter(adapter)
            accountDropdown.setText("No linked account", false)
            selectedAccountId = 0
            accountDropdown.setOnItemClickListener { _, _, position, _ ->
                selectedAccountId = if (position == 0) 0 else accounts[position - 1].id
            }
        }
        accountDropdown.setOnClickListener { accountDropdown.showDropDown() }
    }

    // ── Date picker ───────────────────────────────────────────────────────────
    private fun setupDatePicker() {
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val openPicker = View.OnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    targetDateEdit.setText(fmt.format(cal.time))
                    updatePreview()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).also { it.datePicker.minDate = System.currentTimeMillis() }.show()
        }
        // Prevent keyboard; only the picker should open
        targetDateEdit.isFocusable = false
        targetDateEdit.isFocusableInTouchMode = false
        targetDateEdit.setOnClickListener(openPicker)
        findViewById<View>(R.id.targetDateLayout)?.setOnClickListener(openPicker)
    }

    // ── Auto-save toggle ──────────────────────────────────────────────────────
    private fun setupAutoSaveToggle() {
        autoSaveSwitch.setOnCheckedChangeListener { _, checked ->
            autoSaveOptions.visibility = if (checked) View.VISIBLE else View.GONE
        }
    }

    // ── Frequency chips ───────────────────────────────────────────────────────
    private fun setupFrequencyChips() {
        selectFreq(freqMonthly, "MONTHLY")
        freqWeekly.setOnClickListener  { selectFreq(freqWeekly,  "WEEKLY")  }
        freqMonthly.setOnClickListener { selectFreq(freqMonthly, "MONTHLY") }
        freqYearly.setOnClickListener  { selectFreq(freqYearly,  "YEARLY")  }
    }

    private fun selectFreq(chip: TextView, freq: String) {
        selectedFrequency = freq
        listOf(freqWeekly, freqMonthly, freqYearly).forEach { c ->
            if (c == chip) {
                c.setBackgroundColor(android.graphics.Color.parseColor("#4ADE80"))
                c.setTextColor(android.graphics.Color.parseColor("#0D1B2A"))
                c.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                c.setBackgroundColor(android.graphics.Color.parseColor("#0D1B2A"))
                c.setTextColor(android.graphics.Color.parseColor("#7A99B0"))
                c.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    // ── Live preview ──────────────────────────────────────────────────────────
    private fun setupLivePreview() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updatePreview() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        goalNameEdit.addTextChangedListener(watcher)
        targetAmountEdit.addTextChangedListener(watcher)
        initialDepositEdit.addTextChangedListener(watcher)
    }

    private fun updatePreview() {
        val name    = goalNameEdit.text.toString().trim().ifBlank { "Your Goal Name" }
        val target  = targetAmountEdit.text.toString().toDoubleOrNull() ?: 0.0
        val deposit = initialDepositEdit.text.toString().toDoubleOrNull() ?: 0.0
        val date    = targetDateEdit.text.toString().trim().ifBlank { "-" }

        previewIcon.text    = selectedIcon
        previewName.text    = name
        previewDetails.text = "Target: R %,.2f  •  By: %s".format(target, date)

        val pct = if (target > 0) ((deposit / target) * 100).toInt().coerceIn(0, 100) else 0
        previewProgressBar.progress = pct
        previewProgressText.text    = "$pct% saved  •  R %,.2f initial deposit".format(deposit)
    }

    // ── Buttons ───────────────────────────────────────────────────────────────
    private fun setupButtons() {
        findViewById<TextView>(R.id.backArrow)?.setOnClickListener { finish() }
        findViewById<TextView>(R.id.cancelButton)?.setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.createGoalButton)?.setOnClickListener { attemptCreate() }
    }

    private fun attemptCreate() {
        val name    = goalNameEdit.text.toString()
        val target  = targetAmountEdit.text.toString().toDoubleOrNull() ?: 0.0
        val deposit = initialDepositEdit.text.toString().toDoubleOrNull() ?: 0.0
        val date    = targetDateEdit.text.toString().trim()
        val autoOn  = autoSaveSwitch.isChecked
        val autoAmt = autoSaveAmountEdit.text.toString().toDoubleOrNull() ?: 0.0

        savingsVm.createGoal(
            userId          = userId,
            name            = name,
            icon            = selectedIcon,
            targetAmount    = target,
            initialDeposit  = deposit,
            targetDate      = date,
            linkedAccountId = selectedAccountId,
            autoSaveEnabled = autoOn,
            autoSaveAmount  = autoAmt,
            autoSaveFreq    = selectedFrequency
        )
    }

    // ── Observe results ───────────────────────────────────────────────────────
    private fun observeResults() {
        savingsVm.saveResult.observe(this) { result ->
            // null = no event pending; ignore (covers startup and post-consume state)
            result ?: return@observe

            savingsVm.consumeResult() // always clear first so it can never re-fire

            when (result) {
                is SavingsGoalViewModel.GoalResult.Success -> {
                    Toast.makeText(this, "Goal created! 🎯", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is SavingsGoalViewModel.GoalResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
                else -> { /* FundsAdded not relevant here */ }
            }
        }
    }
}