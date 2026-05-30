package com.example.pulserds_prog7313.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.repository.BankAccountRepository
import com.example.pulserds_prog7313.repository.SubscriptionRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.BankAccountViewModel
import com.example.pulserds_prog7313.viewmodel.SubscriptionViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar

/**
 * AddSubscriptionActivity
 *
 * Form screen that lets the user add a new subscription. Mirrors the design
 * in the provided wireframe:
 *   - Quick-pick tiles pre-fill the service name + icon
 *   - Details card: service name, amount, billing cycle, category, linked account, start date, note
 *   - Settings card: Auto-Pay toggle, Renewal Reminder toggle + reminder-days selector
 *   - Live preview card updates as the user types
 *   - "Save Subscription" validates and persists via SubscriptionViewModel
 *
 * The BankAccountViewModel is used only to populate the linked-account dropdown
 * with the user's real accounts from Firestore.
 */
class AddSubscriptionActivity : BaseActivity() {

    // -- ViewModels ------------------------------------------------------------
    private lateinit var subscriptionVM: SubscriptionViewModel
    private lateinit var bankAccountVM:  BankAccountViewModel

    private var userId = ""

    // -- State -----------------------------------------------------------------
    private var selectedBillingCycle = "Monthly"
    private var selectedReminderDays = 3
    private var selectedIcon         = "📋"

    // Map of account nicknames → account IDs for the dropdown
    private val accountMap = mutableMapOf<String, String>()   // nickname → id

    // -- Form views ------------------------------------------------------------
    private lateinit var serviceNameEditText: TextInputEditText
    private lateinit var amountEditText:      TextInputEditText
    private lateinit var categoryDropdown:    AutoCompleteTextView
    private lateinit var accountDropdown:     AutoCompleteTextView
    private lateinit var startDateEditText:   TextInputEditText
    private lateinit var noteEditText:        TextInputEditText
    private lateinit var autoPaySwitch:       Switch
    private lateinit var reminderSwitch:      Switch
    private lateinit var reminderDaysRow:     LinearLayout

    // Cycle selector chips
    private lateinit var cycleWeekly:  TextView
    private lateinit var cycleMonthly: TextView
    private lateinit var cycleYearly:  TextView

    // Reminder day chips
    private lateinit var remind1Day:  TextView
    private lateinit var remind3Days: TextView
    private lateinit var remind7Days: TextView

    // Preview views
    private lateinit var previewIcon:     TextView
    private lateinit var previewName:     TextView
    private lateinit var previewCategory: TextView
    private lateinit var previewRenewal:  TextView
    private lateinit var previewAmount:   TextView
    private lateinit var previewYearly:   TextView

    // -- Lifecycle -------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_subscription)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userId = SessionManager(this).getUserId()

        setupViewModels()
        bindViews()
        setupQuickPick()
        setupBillingCycleChips()
        setupReminderToggle()
        setupReminderDayChips()
        setupCategoryDropdown()
        setupDatePicker()
        setupPreviewUpdates()
        setupSaveButton()
        observeData()

        // Navigation
        findViewById<TextView>(R.id.backArrow).setOnClickListener { finish() }
        findViewById<TextView>(R.id.cancelButton).setOnClickListener { finish() }
    }

    // -- Setup -----------------------------------------------------------------

    private fun setupViewModels() {
        subscriptionVM = ViewModelProvider(
            this,
            ViewModelFactory { SubscriptionViewModel(SubscriptionRepository()) }
        ).get(SubscriptionViewModel::class.java)

        bankAccountVM = ViewModelProvider(
            this,
            ViewModelFactory { BankAccountViewModel(BankAccountRepository()) }
        ).get(BankAccountViewModel::class.java)
        bankAccountVM.init(userId)
    }

    private fun bindViews() {
        serviceNameEditText = findViewById(R.id.serviceNameEditText)
        amountEditText      = findViewById(R.id.amountEditText)
        categoryDropdown    = findViewById(R.id.categoryDropdown)
        accountDropdown     = findViewById(R.id.accountDropdown)
        startDateEditText   = findViewById(R.id.startDateEditText)
        noteEditText        = findViewById(R.id.noteEditText)
        autoPaySwitch       = findViewById(R.id.autoPaySwitch)
        reminderSwitch      = findViewById(R.id.reminderSwitch)
        reminderDaysRow     = findViewById(R.id.reminderDaysRow)

        cycleWeekly  = findViewById(R.id.cycleWeekly)
        cycleMonthly = findViewById(R.id.cycleMonthly)
        cycleYearly  = findViewById(R.id.cycleYearly)

        remind1Day  = findViewById(R.id.remind1Day)
        remind3Days = findViewById(R.id.remind3Days)
        remind7Days = findViewById(R.id.remind7Days)

        previewIcon     = findViewById(R.id.previewIcon)
        previewName     = findViewById(R.id.previewName)
        previewCategory = findViewById(R.id.previewCategory)
        previewRenewal  = findViewById(R.id.previewRenewal)
        previewAmount   = findViewById(R.id.previewAmount)
        previewYearly   = findViewById(R.id.previewYearly)
    }

    /** Maps popular service names to their icon emoji and pre-fills the form. */
    private fun setupQuickPick() {
        // (quickPickId, serviceName, icon)
        val quickPicks = listOf(
            Triple(R.id.quickNetflix,  "Netflix",               "📺"),
            Triple(R.id.quickSpotify,  "Spotify",               "🎵"),
            Triple(R.id.quickYoutube,  "YouTube Premium",       "▶️"),
            Triple(R.id.quickDisney,   "Disney+",               "🌟"),
            Triple(R.id.quickAdobe,    "Adobe Creative Cloud",  "🎨"),
            Triple(R.id.quickApple,    "Apple One",             "🍎"),
            Triple(R.id.quickAmazon,   "Amazon Prime",          "📦"),
            Triple(R.id.quickCustom,   "",                      "📋")
        )
        for ((id, name, icon) in quickPicks) {
            val view = findViewById<LinearLayout>(id) ?: continue
            view.setOnClickListener {
                selectedIcon = icon
                if (name.isNotBlank()) {
                    serviceNameEditText.setText(name)
                } else {
                    serviceNameEditText.setText("")
                    serviceNameEditText.requestFocus()
                }
                previewIcon.text = icon
                updatePreview()
            }
        }
    }

    private fun setupBillingCycleChips() {
        val chips = mapOf(cycleWeekly to "Weekly", cycleMonthly to "Monthly", cycleYearly to "Yearly")
        for ((chip, cycle) in chips) {
            chip.setOnClickListener {
                selectedBillingCycle = cycle
                selectBillingChip(chip)
                updatePreview()
            }
        }
        selectBillingChip(cycleMonthly) // default: Monthly
    }

    private fun setupReminderToggle() {
        reminderDaysRow.visibility = if (reminderSwitch.isChecked) View.VISIBLE else View.GONE
        reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            reminderDaysRow.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun setupReminderDayChips() {
        val chips = mapOf(remind1Day to 1, remind3Days to 3, remind7Days to 7)
        for ((chip, days) in chips) {
            chip.setOnClickListener {
                selectedReminderDays = days
                selectReminderChip(chip)
            }
        }
        selectReminderChip(remind3Days) // default: 3 days
    }

    private fun setupCategoryDropdown() {
        val categories = listOf(
            "Entertainment", "Streaming", "Music", "Gaming",
            "News & Media", "Design & Creative", "Productivity",
            "Cloud Storage", "Education", "Health & Fitness",
            "Finance", "App", "Other"
        )
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        categoryDropdown.setAdapter(catAdapter)
        categoryDropdown.setOnItemClickListener { _, _, _, _ -> updatePreview() }
    }

    /** Opens a DatePickerDialog when the start-date field is tapped. */
    private fun setupDatePicker() {
        startDateEditText.setOnClickListener { showDatePicker() }
        // Also handle end-icon tap on the TextInputLayout
        val layout = startDateEditText.parent?.parent
        if (layout is com.google.android.material.textfield.TextInputLayout) {
            layout.setEndIconOnClickListener { showDatePicker() }
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val dateStr = "%02d/%02d/%04d".format(day, month + 1, year)
                startDateEditText.setText(dateStr)
                updatePreview()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** Wires text-watchers on the main fields so the preview card stays live. */
    private fun setupPreviewUpdates() {
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = updatePreview()
        }
        serviceNameEditText.addTextChangedListener(watcher)
        amountEditText.addTextChangedListener(watcher)
        autoPaySwitch.setOnCheckedChangeListener { _, _ -> updatePreview() }
    }

    private fun setupSaveButton() {
        findViewById<MaterialButton>(R.id.saveSubscriptionButton).setOnClickListener {
            saveSubscription()
        }
    }

    // -- Observe bank accounts for the account dropdown ------------------------

    private fun observeData() {
        bankAccountVM.accounts.observe(this) { accounts ->
            accountMap.clear()
            accounts.forEach { accountMap[it.nickname] = it.id }

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                accountMap.keys.toList()
            )
            accountDropdown.setAdapter(adapter)
        }

        subscriptionVM.saveResult.observe(this) { result ->
            when (result) {
                is SubscriptionViewModel.SaveResult.Success -> {
                    Toast.makeText(this, "Subscription saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                is SubscriptionViewModel.SaveResult.Error -> {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // -- Save ------------------------------------------------------------------

    private fun saveSubscription() {
        val serviceName   = serviceNameEditText.text?.toString()?.trim() ?: ""
        val amountStr     = amountEditText.text?.toString()?.trim() ?: ""
        val category      = categoryDropdown.text?.toString()?.trim() ?: ""
        val accountNick   = accountDropdown.text?.toString()?.trim() ?: ""
        val startDate     = startDateEditText.text?.toString()?.trim() ?: ""
        val note          = noteEditText.text?.toString()?.trim() ?: ""

        val amount        = amountStr.toDoubleOrNull() ?: 0.0
        val bankAccountId = accountMap[accountNick] ?: ""

        subscriptionVM.addSubscription(
            userId        = userId,
            serviceName   = serviceName,
            amount        = amount,
            billingCycle  = selectedBillingCycle,
            category      = category,
            bankAccountId = bankAccountId,
            startDate     = startDate,
            note          = note,
            isAutoPay     = autoPaySwitch.isChecked,
            isReminderOn  = reminderSwitch.isChecked,
            reminderDays  = selectedReminderDays,
            icon          = selectedIcon
        )
    }

    // -- Live preview ----------------------------------------------------------

    /**
     * Refreshes the preview card at the bottom of the form to reflect the
     * current field values — giving the user a "what it will look like" preview.
     */
    private fun updatePreview() {
        val name     = serviceNameEditText.text?.toString()?.ifBlank { "Service Name" } ?: "Service Name"
        val amountD  = amountEditText.text?.toString()?.toDoubleOrNull() ?: 0.0
        val category = categoryDropdown.text?.toString()?.ifBlank { "Category" } ?: "Category"
        val account  = accountDropdown.text?.toString()?.ifBlank { "Account" } ?: "Account"
        val startDate = startDateEditText.text?.toString() ?: ""

        previewIcon.text = selectedIcon
        previewName.text = name
        previewCategory.text = "$category  •  $account"

        val autoPayStr = if (autoPaySwitch.isChecked) "Auto-pay ON" else "Auto-pay OFF"
        previewRenewal.text = "$selectedBillingCycle  •  $autoPayStr"

        val cycleLabel = when (selectedBillingCycle) {
            "Weekly"  -> "wk"
            "Yearly"  -> "yr"
            else      -> "mo"
        }
        previewAmount.text = "R %.2f/%s".format(amountD, cycleLabel)

        // Compute yearly equivalent for the preview
        val yearlyEquiv = when (selectedBillingCycle) {
            "Weekly"  -> amountD * 52
            "Monthly" -> amountD * 12
            "Yearly"  -> amountD
            else      -> amountD * 12
        }
        previewYearly.text = "R %.2f / year".format(yearlyEquiv)

        // Show next renewal in preview if start date is set
        if (startDate.isNotBlank()) {
            val nextRenewal = subscriptionVM.computeNextRenewal(startDate, selectedBillingCycle)
            previewRenewal.text = "Renews $nextRenewal  •  $autoPayStr"
        }
    }

    // -- Chip selection helpers ------------------------------------------------

    private fun selectBillingChip(selected: TextView) {
        for (chip in listOf(cycleWeekly, cycleMonthly, cycleYearly)) {
            if (chip == selected) {
                chip.setBackgroundColor(0xFF4ADE80.toInt())
                chip.setTextColor(0xFFFF9500.toInt())
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                chip.setBackgroundColor(0xFF0D1B2A.toInt())
                chip.setTextColor(0xFFFF9500.toInt())
                chip.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }

    private fun selectReminderChip(selected: TextView) {
        for (chip in listOf(remind1Day, remind3Days, remind7Days)) {
            if (chip == selected) {
                chip.setBackgroundColor(0xFF4ADE80.toInt())
                chip.setTextColor(0xFFFF9500.toInt())
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                chip.setBackgroundColor(0xFF0D1B2A.toInt())
                chip.setTextColor(0xFFFF9500.toInt())
                chip.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }
}