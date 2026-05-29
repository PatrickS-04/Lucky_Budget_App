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
 * CreateSavingsGoalActivity - Provides a form for creating a new savings goal.
 *
 * Fully self-contained - does NOT use SavingsGoalViewModel or any LiveData
 * for its create flow. Validation + DB insert + navigation all happen inside
 * a single lifecycleScope.launch block, so there is no shared observer state
 * that can fire finish() unexpectedly when the keyboard appears.
 *
 * The screen includes:
 * - An emoji icon picker for personalising the goal.
 * - Fields for the goal name, target amount, initial deposit, and target date.
 * - An optional linked bank account dropdown.
 * - An optional auto-save configuration section (amount + frequency).
 * - A live preview card that updates as the user types.
 */
class CreateSavingsGoalActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // ViewModel and DAO
    // -------------------------------------------------------------------------

    /** ViewModel for loading and observing the user's linked bank accounts. */
    private lateinit var bankVm: BankAccountViewModel

    /** Repository used for the fire-and-forget goal insert in attemptCreate(). */
    private lateinit var goalRepo: com.example.pulserds_prog7313.repository.SavingsGoalRepository

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** The ID of the currently signed-in user; -1 indicates no active session. */
    private var userId = ""

    /** The emoji character currently selected in the icon picker. Defaults to a bank. */
    private var selectedIcon = "🏦"

    /** The ID of the bank account selected in the dropdown; 0 means no linked account. */
    private var selectedAccountId = ""

    /** The auto-save recurrence interval selected by the user. Defaults to monthly. */
    private var selectedFrequency = "MONTHLY"

    /** Cached list of the user's bank accounts populated when the LiveData emits. */
    private var accountsList: List<BankAccount> = emptyList()

    // -------------------------------------------------------------------------
    // Icon picker mapping - maps each icon container view ID to its emoji string
    // -------------------------------------------------------------------------

    /**
     * Maps each icon LinearLayout view ID to its corresponding emoji character.
     * Used in setupIconPicker() to attach click listeners and in highlightIcon()
     * to apply the selected / unselected background colours.
     */
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

    // -------------------------------------------------------------------------
    // View references - bound in bindViews() after setContentView()
    // -------------------------------------------------------------------------

    /** EditText for the user to enter the name of their savings goal. */
    private lateinit var goalNameEdit: TextInputEditText

    /** EditText for the total amount the user wants to save. */
    private lateinit var targetAmountEdit: TextInputEditText

    /** EditText for the amount the user is depositing immediately when creating the goal. */
    private lateinit var initialDepositEdit: TextInputEditText

    /** EditText that displays the chosen target date; opens a DatePickerDialog on tap. */
    private lateinit var targetDateEdit: TextInputEditText

    /** Auto-complete dropdown that lists the user's linked bank accounts. */
    private lateinit var accountDropdown: AutoCompleteTextView

    /** Toggle switch that shows or hides the auto-save configuration options. */
    private lateinit var autoSaveSwitch: Switch

    /** Container view for the auto-save amount and frequency fields; hidden by default. */
    private lateinit var autoSaveOptions: View

    /** EditText for the recurring auto-save amount when auto-save is enabled. */
    private lateinit var autoSaveAmountEdit: TextInputEditText

    /** EditText for the minimum monthly spending goal (optional, used by Analytics chart). */
    private lateinit var minSpendingEdit: TextInputEditText

    /** EditText for the maximum monthly spending goal (optional, used by Analytics chart). */
    private lateinit var maxSpendingEdit: TextInputEditText

    /** Chip/TextView for selecting a weekly auto-save frequency. */
    private lateinit var freqWeekly: TextView

    /** Chip/TextView for selecting a monthly auto-save frequency (default). */
    private lateinit var freqMonthly: TextView

    /** Chip/TextView for selecting a yearly auto-save frequency. */
    private lateinit var freqYearly: TextView

    /** TextView in the preview card that displays the selected emoji icon. */
    private lateinit var previewIcon: TextView

    /** TextView in the preview card that displays the goal name. */
    private lateinit var previewName: TextView

    /** TextView in the preview card that displays the target amount and date. */
    private lateinit var previewDetails: TextView

    /** ProgressBar in the preview card showing the initial deposit as a percentage of the target. */
    private lateinit var previewProgressBar: ProgressBar

    /** TextView in the preview card showing the percentage saved and the initial deposit amount. */
    private lateinit var previewProgressText: TextView

    /** Primary action button that triggers validation and goal creation. */
    private lateinit var createGoalButton: MaterialButton

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called when the activity is first created.
     * Enables edge-to-edge rendering, resolves the signed-in user from the session,
     * initialises the DAO and bank account ViewModel, then wires all UI sections
     * in a logical top-to-bottom order.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_savings_goal)

        // Inset the root view so content is not obscured by status / navigation bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Resolve the signed-in user and obtain a direct DAO reference for inserts
        userId = SessionManager(this).getUserId()
        goalRepo = com.example.pulserds_prog7313.repository.SavingsGoalRepository()

        bankVm = ViewModelProvider(
            this, ViewModelFactory { BankAccountViewModel(BankAccountRepository()) }
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

    // -------------------------------------------------------------------------
    // Bind
    // -------------------------------------------------------------------------

    /**
     * Binds every view reference to its corresponding XML element using findViewById.
     * Kept separate from onCreate() for readability.
     */
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
        minSpendingEdit     = findViewById(R.id.minSpendingEditText)
        maxSpendingEdit     = findViewById(R.id.maxSpendingEditText)
    }

    // -------------------------------------------------------------------------
    // Icon picker
    // -------------------------------------------------------------------------

    /**
     * Initialises the emoji icon picker by pre-selecting the bank icon and
     * attaching a click listener to every icon container in iconMap.
     * Each tap updates selectedIcon, refreshes the highlight, and syncs the
     * preview card immediately.
     */
    private fun setupIconPicker() {
        // Pre-select the default icon so the picker has a visible selection on launch
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

    /**
     * Applies a highlighted background to the selected icon container and resets
     * all other containers to the unselected background colour.
     *
     * @param selectedId The view ID of the icon that should appear selected.
     */
    private fun highlightIcon(selectedId: Int) {
        for (viewId in iconMap.keys) {
            // Dark green for the active selection; dark blue for all others
            val bg = if (viewId == selectedId) "#0D2A1A" else "#1A2A38"
            try {
                val v = findViewById<LinearLayout>(viewId) ?: continue
                v.setBackgroundColor(android.graphics.Color.parseColor(bg))
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------------------
    // Account dropdown
    // -------------------------------------------------------------------------

    /**
     * Observes the bank account LiveData and populates the dropdown adapter
     * whenever the account list changes. The first item is always "No linked account"
     * (mapped to selectedAccountId = 0); subsequent items correspond to real accounts.
     * An item-click listener updates selectedAccountId to the chosen account's ID.
     */
    private fun setupAccountDropdown() {
        bankVm.accounts.observe(this) { accounts ->
            accountsList = accounts
            // Prepend a "no account" option so index 0 is always a safe default
            val labels = listOf("No linked account") + accounts.map { "${it.nickname} (${it.bankName})" }
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, labels)
            accountDropdown.setAdapter(adapter)
            accountDropdown.setText("No linked account", false)
            selectedAccountId = ""
            accountDropdown.setOnItemClickListener { _, _, position, _ ->
                // Position 0 is the "no account" sentinel; offset by 1 for real accounts
                selectedAccountId = if (position == 0) "" else accounts[position - 1].id
            }
        }
        // Ensure the dropdown opens on a direct tap of the field (not just the arrow)
        accountDropdown.setOnClickListener { accountDropdown.showDropDown() }
    }

    // -------------------------------------------------------------------------
    // Date picker
    // -------------------------------------------------------------------------

    /**
     * Configures the target date field to open a DatePickerDialog on tap instead
     * of showing a software keyboard. The minimum selectable date is today so the
     * user cannot choose a date in the past. On confirmation, the selected date is
     * formatted and written into the field, and the preview card is refreshed.
     */
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
        // Disable direct keyboard input so the field only receives values from the picker
        targetDateEdit.isFocusable = false
        targetDateEdit.isFocusableInTouchMode = false
        targetDateEdit.setOnClickListener(openPicker)
        // Also open the picker when the surrounding layout container is tapped
        findViewById<View>(R.id.targetDateLayout)?.setOnClickListener(openPicker)
    }

    // -------------------------------------------------------------------------
    // Auto-save toggle
    // -------------------------------------------------------------------------

    /**
     * Attaches a checked-change listener to the auto-save switch.
     * Shows the auto-save amount and frequency section when enabled,
     * and hides it when disabled to keep the form concise.
     */
    private fun setupAutoSaveToggle() {
        autoSaveSwitch.setOnCheckedChangeListener { _, checked ->
            autoSaveOptions.visibility = if (checked) View.VISIBLE else View.GONE
        }
    }

    // -------------------------------------------------------------------------
    // Frequency chips
    // -------------------------------------------------------------------------

    /**
     * Pre-selects the monthly frequency chip and attaches click listeners to all
     * three chips. Each tap delegates to selectFreq() which updates the selection
     * state and repaints the chip visuals accordingly.
     */
    private fun setupFrequencyChips() {
        // Default selection matches the selectedFrequency field initialised above
        selectFreq(freqMonthly, "MONTHLY")
        freqWeekly.setOnClickListener  { selectFreq(freqWeekly,  "WEEKLY")  }
        freqMonthly.setOnClickListener { selectFreq(freqMonthly, "MONTHLY") }
        freqYearly.setOnClickListener  { selectFreq(freqYearly,  "YEARLY")  }
    }

    /**
     * Updates selectedFrequency and repaints all three frequency chips so only
     * the chosen chip appears highlighted.
     *
     * @param chip The TextView that was tapped and should be styled as active.
     * @param freq The frequency string constant to store (e.g. "WEEKLY").
     */
    private fun selectFreq(chip: TextView, freq: String) {
        selectedFrequency = freq
        listOf(freqWeekly, freqMonthly, freqYearly).forEach { c ->
            if (c == chip) {
                // Active chip - green background, dark text, bold typeface
                c.setBackgroundColor(android.graphics.Color.parseColor("#4ADE80"))
                c.setTextColor(android.graphics.Color.parseColor("#0D1B2A"))
                c.typeface = android.graphics.Typeface.DEFAULT_BOLD
            } else {
                // Inactive chip - dark background, muted text, regular typeface
                c.setBackgroundColor(android.graphics.Color.parseColor("#0D1B2A"))
                c.setTextColor(android.graphics.Color.parseColor("#7A99B0"))
                c.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }

    // -------------------------------------------------------------------------
    // Live preview
    // -------------------------------------------------------------------------

    /**
     * Attaches a shared TextWatcher to the goal name, target amount, and initial
     * deposit fields so that updatePreview() is called after every keystroke,
     * keeping the preview card in sync with the form at all times.
     */
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

    /**
     * Reads the current form values and updates all elements of the live preview
     * card - icon, name, target/date details, progress bar, and progress label.
     * Parsing errors are swallowed silently so incomplete input never crashes the UI.
     */
    private fun updatePreview() {
        try {
            // Use placeholder text when fields are empty so the card always looks complete
            val name    = goalNameEdit.text.toString().trim().ifBlank { "Your Goal Name" }
            val target  = targetAmountEdit.text.toString().toDoubleOrNull() ?: 0.0
            val deposit = initialDepositEdit.text.toString().toDoubleOrNull() ?: 0.0
            val date    = targetDateEdit.text.toString().trim().ifBlank { "-" }

            previewIcon.text    = selectedIcon
            previewName.text    = name
            previewDetails.text = String.format(java.util.Locale.US, "Target: R %.2f  •  By: %s", target, date)

            // Calculate deposit as a percentage of the target, clamped between 0 and 100
            val pct = if (target > 0) ((deposit / target) * 100).toInt().coerceIn(0, 100) else 0
            previewProgressBar.progress = pct
            previewProgressText.text    = String.format(java.util.Locale.US, "%d%% saved  •  R %.2f initial deposit", pct, deposit)
        } catch (_: Exception) {
            // Swallow any formatting exception so typing never crashes the screen
        }
    }

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    /**
     * Attaches click listeners to the back arrow, cancel button, and the primary
     * create button. Both the back arrow and cancel simply finish the activity
     * without saving. The create button delegates to attemptCreate().
     */
    private fun setupButtons() {
        findViewById<TextView>(R.id.backArrow)?.setOnClickListener { finish() }
        findViewById<TextView>(R.id.cancelButton)?.setOnClickListener { finish() }
        createGoalButton.setOnClickListener { attemptCreate() }
    }

    // -------------------------------------------------------------------------
    // Create goal
    // -------------------------------------------------------------------------

    /**
     * Validates the form input on the main thread, then inserts the new goal
     * directly via the DAO inside a lifecycleScope coroutine and calls finish()
     * on success - all in one self-contained block with zero LiveData observers
     * involved to avoid unexpected lifecycle interactions.
     *
     * Validation rules (checked in order, first failure shown as a Toast):
     * - Goal name must not be blank.
     * - Target amount must be greater than R 0.
     * - Initial deposit must be non-negative.
     * - Initial deposit must not exceed the target amount.
     * - If auto-save is enabled, the auto-save amount must be greater than R 0.
     *
     * On a successful insert (DAO returns a positive row ID), a confirmation Toast
     * is shown and the activity is finished. On failure, the button is re-enabled
     * and an error Toast is shown so the user can retry.
     */
    private fun attemptCreate() {
        // Read and sanitise all form values before any validation
        val name    = goalNameEdit.text.toString().trim()
        val target  = targetAmountEdit.text.toString().toDoubleOrNull() ?: 0.0
        val deposit = initialDepositEdit.text.toString().toDoubleOrNull() ?: 0.0
        val date    = targetDateEdit.text.toString().trim()
        val autoOn  = autoSaveSwitch.isChecked
        val autoAmt = autoSaveAmountEdit.text.toString().toDoubleOrNull() ?: 0.0
        val minSpend = minSpendingEdit.text.toString().toDoubleOrNull() ?: 0.0
        val maxSpend = maxSpendingEdit.text.toString().toDoubleOrNull() ?: 0.0

        // Validate on the main thread before touching the DB; return early on the first error
        val error = when {
            name.isBlank()         -> "Please enter a goal name."
            target <= 0            -> "Target amount must be greater than R 0."
            deposit < 0            -> "Initial deposit cannot be negative."
            deposit > target       -> "Initial deposit cannot exceed the target amount."
            autoOn && autoAmt <= 0 -> "Please enter an auto-save amount."
            else                   -> null
        }
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }

        // Disable the button to prevent duplicate inserts while the coroutine runs
        createGoalButton.isEnabled = false

        val now       = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

        // Mark the goal as immediately completed if the initial deposit meets the target
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
            completedDate     = if (completed) now else "",   // Only set if goal is already met
            minSpendingGoal   = minSpend,
            maxSpendingGoal   = if (maxSpend > minSpend) maxSpend else 0.0
        )

        lifecycleScope.launch {
            val id = goalRepo.insert(goal)
            runOnUiThread {
                if (id.isNotBlank()) {
                    // Positive row ID confirms a successful insert
                    Toast.makeText(this@CreateSavingsGoalActivity, "Goal created! 🎯", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    // Insert failed - re-enable the button so the user can retry
                    createGoalButton.isEnabled = true
                    Toast.makeText(this@CreateSavingsGoalActivity, "Could not save goal. Please try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}