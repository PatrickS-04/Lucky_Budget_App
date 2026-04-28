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
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fully self-contained — does NOT use SavingsGoalViewModel or any LiveData
 * for its create flow. Validation + DB insert + navigation all happen inside
 * a single lifecycleScope.launch block, so there is no shared observer state
 * that can fire finish() unexpectedly when the keyboard appears.
 */
class CreateSavingsGoalActivity : AppCompatActivity() {

    private lateinit var bankVm : BankAccountViewModel
    private lateinit var dao    : com.example.pulserds_prog7313.data.SavingsGoalDao

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
    private lateinit var createGoalButton   : MaterialButton

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
        dao    = db.savingsGoalDao()

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
        createGoalButton    = findViewById(R.id.createGoalButton)
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
        try {
            val name    = goalNameEdit.text.toString().trim().ifBlank { "Your Goal Name" }
            val target  = targetAmountEdit.text.toString().toDoubleOrNull() ?: 0.0
            val deposit = initialDepositEdit.text.toString().toDoubleOrNull() ?: 0.0
            val date    = targetDateEdit.text.toString().trim().ifBlank { "-" }

            previewIcon.text    = selectedIcon
            previewName.text    = name
            previewDetails.text = String.format(java.util.Locale.US, "Target: R %.2f  •  By: %s", target, date)

            val pct = if (target > 0) ((deposit / target) * 100).toInt().coerceIn(0, 100) else 0
            previewProgressBar.progress = pct
            previewProgressText.text    = String.format(java.util.Locale.US, "%d%% saved  •  R %.2f initial deposit", pct, deposit)
        } catch (_: Exception) {
            // Swallow any formatting exception so typing never crashes the screen
        }
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        findViewById<TextView>(R.id.backArrow)?.setOnClickListener { finish() }
        findViewById<TextView>(R.id.cancelButton)?.setOnClickListener { finish() }
        createGoalButton.setOnClickListener { attemptCreate() }
    }

    // ── Create goal ───────────────────────────────────────────────────────────

    /**
     * Validates input, inserts directly via the DAO inside a lifecycleScope
     * coroutine, then calls finish() on success — all in one self-contained
     * block with zero LiveData observers involved.
     */
    private fun attemptCreate() {
        val name    = goalNameEdit.text.toString().trim()
        val target  = targetAmountEdit.text.toString().toDoubleOrNull() ?: 0.0
        val deposit = initialDepositEdit.text.toString().toDoubleOrNull() ?: 0.0
        val date    = targetDateEdit.text.toString().trim()
        val autoOn  = autoSaveSwitch.isChecked
        val autoAmt = autoSaveAmountEdit.text.toString().toDoubleOrNull() ?: 0.0

        // Validate on the main thread before touching the DB
        val error = when {
            name.isBlank()                           -> "Please enter a goal name."
            target <= 0                              -> "Target amount must be greater than R 0."
            deposit < 0                              -> "Initial deposit cannot be negative."
            deposit > target                         -> "Initial deposit cannot exceed the target amount."
            autoOn && autoAmt <= 0                   -> "Please enter an auto-save amount."
            else                                     -> null
        }
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }

        createGoalButton.isEnabled = false

        val now       = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
        val completed = deposit >= target

        val goal = SavingsGoal(
            userId            = userId,
            name              = name,
            icon              = selectedIcon,
            targetAmount      = target,
            savedAmount       = deposit,
            targetDate        = date,
            linkedAccountId   = selectedAccountId,
            autoSaveEnabled   = autoOn,
            autoSaveAmount    = autoAmt,
            autoSaveFrequency = selectedFrequency,
            isCompleted       = completed,
            completedDate     = if (completed) now else ""
        )

        lifecycleScope.launch {
            val id = dao.insert(goal)
            // Switch back to the main thread to update UI
            runOnUiThread {
                if (id > 0) {
                    Toast.makeText(this@CreateSavingsGoalActivity, "Goal created! 🎯", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    createGoalButton.isEnabled = true
                    Toast.makeText(this@CreateSavingsGoalActivity, "Could not save goal. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}