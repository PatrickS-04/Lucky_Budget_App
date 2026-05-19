package com.example.pulserds_prog7313.ui

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.SavingsGoalViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SavingsGoalsActivity displays the user's savings goals dashboard.
 *
 * It shows:
 *  - An overview card summarising total saved, total target, and overall progress.
 *  - A stats row showing the count of active goals, completed goals, and
 *    the amount saved this calendar month.
 *  - A scrollable list of active goal cards (each with an "Add Funds" button).
 *  - A scrollable list of completed goal cards.
 *
 * Architecture: MVVM — all data operations go through [SavingsGoalViewModel];
 * the activity only observes LiveData and updates the UI.
 */
class SavingsGoalsActivity : BaseActivity() {

    // ViewModel that owns all savings-goal business logic and Room interactions.
    private lateinit var vm: SavingsGoalViewModel

    // ID of the currently logged-in user, resolved from SessionManager.
    private var userId = -1

    // Overview card views

    /** Displays the cumulative amount saved across all active goals. */
    private lateinit var overallAmount: TextView

    /** Displays the cumulative target amount across all active goals. */
    private lateinit var overallTarget: TextView

    /** Horizontal progress bar representing overall savings completion percentage. */
    private lateinit var overallProgressBar: ProgressBar

    /** Text label showing the overall percentage achieved (e.g. "42% of your total goal achieved 🍀"). */
    private lateinit var overallPercent: TextView

    // Quick-stat TextViews (the three stat tiles beneath the progress bar)

    /** Shows the number of currently active (incomplete) goals. */
    private lateinit var statActive: TextView

    /** Shows the number of completed goals. */
    private lateinit var statCompleted: TextView

    /** Shows the total amount saved this calendar month across all active goals. */
    private lateinit var statThisMonth: TextView

    // Goal list containers

    /** Section header label for the active goals list. */
    private lateinit var activeGoalsLabel: TextView

    /** LinearLayout into which active goal cards are programmatically inflated. */
    private lateinit var activeGoalsContainer: LinearLayout

    /** Placeholder message shown when the user has no active goals. */
    private lateinit var emptyActiveText: TextView

    /** Section header label for the completed goals list. */
    private lateinit var completedLabel: TextView

    /** LinearLayout into which completed goal cards are programmatically inflated. */
    private lateinit var completedContainer: LinearLayout

    /** Placeholder message shown when the user has no completed goals. */
    private lateinit var emptyCompletedText: TextView

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_savings_goals)

        // Ensure the root layout respects system bar insets (status bar / nav bar).
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Retrieve the logged-in user's ID so all DB queries are scoped to them.
        userId = SessionManager(this).getUserId()

        // Build the ViewModel backed by a Room DAO via the repository layer.
        val db = AppDatabase.getDatabase(this)
        vm = ViewModelProvider(
            this, ViewModelFactory { SavingsGoalViewModel(SavingsGoalRepository(db.savingsGoalDao())) }
        )[SavingsGoalViewModel::class.java]

        bindViews()          // Wire XML views to Kotlin properties.
        setupButtons()       // Attach click listeners for navigation buttons.
        observeData()        // Start observing LiveData from the ViewModel.
        observeSaveResult()  // Observe fund-add / error results exactly once.
        setupBottomNav(R.id.navSettings) // Highlight the correct bottom nav tab.
    }

    /**
     * Refresh totals every time the activity returns to the foreground.
     * This handles cases where the user navigates to [CreateSavingsGoalActivity],
     * adds a goal, and then presses back — the overview card will reflect the
     * latest DB state without needing a full re-create.
     */
    override fun onResume() {
        super.onResume()
        vm.refreshTotals(userId)
    }

    // View binding

    /**
     * Maps each XML view ID to its corresponding Kotlin property.
     * Called once from [onCreate] after [setContentView].
     */
    private fun bindViews() {
        overallAmount        = findViewById(R.id.overallAmount)
        overallTarget        = findViewById(R.id.overallTarget)
        overallProgressBar   = findViewById(R.id.overallProgressBar)
        overallPercent       = findViewById(R.id.overallPercent)
        statActive           = findViewById(R.id.statActive)
        statCompleted        = findViewById(R.id.statCompleted)
        statThisMonth        = findViewById(R.id.statThisMonth)
        activeGoalsLabel     = findViewById(R.id.activeGoalsLabel)
        activeGoalsContainer = findViewById(R.id.activeGoalsContainer)
        emptyActiveText      = findViewById(R.id.emptyActiveText)
        completedLabel       = findViewById(R.id.completedLabel)
        completedContainer   = findViewById(R.id.completedContainer)
        emptyCompletedText   = findViewById(R.id.emptyCompletedText)
    }

    // Button setup

    /**
     * Attaches click listeners to the two "Add Goal" entry points and the back arrow.
     *
     * Both [R.id.addGoalBtn] (the FAB-style button) and [R.id.addNewGoalButton]
     * (the empty-state CTA button) navigate to [CreateSavingsGoalActivity].
     */
    private fun setupButtons() {
        val toCreate = View.OnClickListener {
            startActivity(Intent(this, CreateSavingsGoalActivity::class.java))
        }
        findViewById<View>(R.id.addGoalBtn)?.setOnClickListener(toCreate)
        findViewById<View>(R.id.addNewGoalButton)?.setOnClickListener(toCreate)
        // Back arrow dismisses this activity and returns to the previous screen.
        findViewById<TextView>(R.id.backArrow)?.setOnClickListener { finish() }
    }

    // LiveData observers

    /**
     * Subscribes to the three main LiveData streams exposed by the ViewModel:
     *  1. The full list of goals — drives [renderGoals].
     *  2. [SavingsGoalViewModel.totalSaved] — updates the overview "amount saved" label.
     *  3. [SavingsGoalViewModel.totalTarget] — updates the overview target label and
     *     calculates the overall progress percentage.
     *
     * Also triggers an initial totals refresh so the overview card is populated
     * before any goal-list update arrives.
     */
    private fun observeData() {
        vm.getAllGoalsLive(userId).observe(this) { goals ->
            renderGoals(goals ?: emptyList())
        }

        vm.totalSaved.observe(this) { saved ->
            overallAmount.text = "R %,.0f".format(saved)
        }

        vm.totalTarget.observe(this) { target ->
            val saved = vm.totalSaved.value ?: 0.0
            overallTarget.text = "of R %,.0f total target".format(target)
            // Clamp to 0–100 so the ProgressBar never overflows.
            val pct = if (target > 0) ((saved / target) * 100).roundToInt().coerceIn(0, 100) else 0
            overallProgressBar.progress = pct
            overallPercent.text = "$pct% of your total goal achieved 🍀"
        }

        vm.refreshTotals(userId)
    }

    /**
     * Observes [SavingsGoalViewModel.saveResult] exactly once at the activity level.
     *
     * WHY THIS IS IMPORTANT:
     * Previously this observer was registered inside [showAddFundsDialog]. Every time
     * the dialog was opened a NEW observer was added to the same LiveData — causing:
     *  - Multiple simultaneous Toast messages per save operation.
     *  - An ANR-level event flood as stacked observers fired together.
     *  - Unpredictable navigation side-effects.
     *
     * By moving the observer here, it is registered exactly once per activity instance.
     * [SavingsGoalViewModel.GoalResult.Success] events (emitted by [CreateSavingsGoalActivity])
     * are suppressed by a SingleLiveEvent wrapper in the ViewModel so they do not
     * accidentally trigger a toast in this screen.
     */
    private fun observeSaveResult() {
        vm.saveResult.observe(this) { result ->
            when (result) {
                is SavingsGoalViewModel.GoalResult.FundsAdded ->
                    Toast.makeText(this, "Funds added! 💰", Toast.LENGTH_SHORT).show()
                is SavingsGoalViewModel.GoalResult.Error ->
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                else -> {
                    // GoalResult.Success is handled by CreateSavingsGoalActivity.
                    // SingleLiveEvent guarantees it will not reach this observer.
                }
            }
        }
    }

    // Goal list rendering

    /**
     * Splits the full goal list into active and completed buckets, then
     * rebuilds both goal-card containers and updates the stats row.
     *
     * @param goals The complete list of [SavingsGoal] objects for the current user.
     */
    private fun renderGoals(goals: List<SavingsGoal>) {
        val active    = goals.filter { !it.isCompleted }
        val completed = goals.filter { it.isCompleted }

        // Stats row
        statActive.text    = active.size.toString()
        statCompleted.text = completed.size.toString()

        // Calculate the start-of-current-month timestamp to filter "this month" goals.
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val thisMonthMs = cal.timeInMillis

        // Sum savedAmount for active goals whose createdAt falls in the current month.
        val thisMonthSaved = active.filter { it.createdAt >= thisMonthMs }.sumOf { it.savedAmount }
        statThisMonth.text = "R %,.0f".format(thisMonthSaved)

        // Active goals section
        activeGoalsContainer.removeAllViews() // Clear stale cards before re-inflating.
        if (active.isEmpty()) {
            emptyActiveText.visibility = View.VISIBLE
        } else {
            emptyActiveText.visibility = View.GONE
            active.forEach { goal -> activeGoalsContainer.addView(buildGoalCard(goal)) }
        }

        // Completed Goal section
        completedContainer.removeAllViews() // Clear stale cards before re-inflating.
        // Hide the "Completed" header entirely when there are no completed goals.
        completedLabel.visibility = if (completed.isEmpty()) View.GONE else View.VISIBLE
        emptyCompletedText.visibility = View.GONE
        completed.forEach { goal -> completedContainer.addView(buildCompletedCard(goal)) }
    }

    // Card builders

    /**
     * Inflates and populates an active goal card (item_savings_goal layout).
     *
     * The card's accent colour is chosen dynamically based on progress:
     *  - < 40 % → green  (#4ADE80) — encouragement to keep going.
     *  - 40–74 % → blue  (#60A5FA) — steady progress.
     *  - ≥ 75 % → amber  (#FBBF24) — almost there!
     *
     * An "Almost There" badge is shown when progress reaches 80 % or above.
     *
     * @param goal The [SavingsGoal] data object to render.
     * @return     The fully populated card [View] ready to be added to [activeGoalsContainer].
     */
    private fun buildGoalCard(goal: SavingsGoal): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_savings_goal, null, false)

        // Calculate percentage saved, clamped to 0–100, and the remaining amount.
        val pct  = if (goal.targetAmount > 0) ((goal.savedAmount / goal.targetAmount) * 100).roundToInt().coerceIn(0, 100) else 0
        val left = goal.targetAmount - goal.savedAmount

        // Choose accent colour based on progress bracket.
        val tintHex = when {
            pct >= 75 -> "#FBBF24" // Amber — nearing completion.
            pct >= 40 -> "#60A5FA" // Blue  — good progress.
            else       -> "#4ADE80" // Green — early stage.
        }
        val tintColor = Color.parseColor(tintHex)

        // Populate text fields.
        card.findViewById<TextView>(R.id.goalCardIcon).text  = goal.icon
        card.findViewById<TextView>(R.id.goalCardName).text  = goal.name
        card.findViewById<TextView>(R.id.goalCardDate).text  =
            if (goal.targetDate.isNotBlank()) "Target: ${goal.targetDate}" else "No target date"
        card.findViewById<TextView>(R.id.goalCardSaved).text =
            "R ${"%.2f".format(goal.savedAmount)} saved"

        // Progress summary line — shows percentage and remaining amount in the accent colour.
        card.findViewById<TextView>(R.id.goalCardProgress).also {
            it.text = "$pct%  •  R ${"%.2f".format(abs(left))} to go"
            it.setTextColor(tintColor)
        }

        // "Almost There" badge — only visible when ≥ 80 % complete and goal is still active.
        card.findViewById<View>(R.id.almostThereBadge).visibility =
            if (pct >= 80 && !goal.isCompleted) View.VISIBLE else View.GONE

        // Style the progress bar with the accent colour.
        card.findViewById<ProgressBar>(R.id.goalCardProgressBar).also {
            it.progress = pct
            it.progressTintList = android.content.res.ColorStateList.valueOf(tintColor)
        }

        // "Add Funds" button — tinted to match the accent colour; opens the funds dialog.
        card.findViewById<MaterialButton>(R.id.goalCardAddFunds).also {
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(tintColor)
            it.setOnClickListener { showAddFundsDialog(goal) }
        }

        // Three-dot overflow menu for goal-level actions (currently: delete).
        card.findViewById<TextView>(R.id.goalCardMenu).setOnClickListener {
            showGoalMenu(goal, card)
        }

        return card
    }

    /**
     * Inflates and populates a completed goal card (item_savings_goal_completed layout).
     *
     * The subtitle line combines the completion date (if recorded) and the final
     * target amount, e.g. "Completed 12 Apr 2025  •  R 5,000.00".
     *
     * @param goal The completed [SavingsGoal] data object to render.
     * @return     The fully populated card [View] ready to be added to [completedContainer].
     */
    private fun buildCompletedCard(goal: SavingsGoal): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_savings_goal_completed, null, false)

        card.findViewById<TextView>(R.id.completedGoalIcon).text = goal.icon
        card.findViewById<TextView>(R.id.completedGoalName).text = goal.name

        // Build the subtitle string, appending the completion date only when available.
        val subtitle = buildString {
            append("Completed")
            if (goal.completedDate.isNotBlank()) append(" ${goal.completedDate}")
            append("  •  R %,.2f".format(goal.targetAmount))
        }
        card.findViewById<TextView>(R.id.completedGoalSubtitle).text = subtitle

        // Three-dot overflow menu — same delete action as active goal cards.
        card.findViewById<TextView>(R.id.completedGoalMenu).setOnClickListener {
            showGoalMenu(goal, card)
        }

        return card
    }

    // Dialogs

    /**
     * Shows a modal dialog that lets the user deposit funds into [goal].
     *
     * Validation rules enforced before the dialog is dismissed:
     *  1. Amount must be a valid positive number.
     *  2. Amount must not exceed the remaining balance (targetAmount − savedAmount).
     *
     * The dialog is intentionally prevented from auto-dismissing on the positive
     * button press so validation errors can be shown inline via [EditText.setError].
     * Only a valid submission closes the dialog and triggers [SavingsGoalViewModel.addFunds].
     *
     * @param goal The [SavingsGoal] to add funds to.
     */
    private fun showAddFundsDialog(goal: SavingsGoal) {
        val remaining = goal.targetAmount - goal.savedAmount

        // Custom EditText for amount entry — decimal numeric keyboard.
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount (max R %,.2f)".format(remaining)
            setPadding(40, 24, 40, 24)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.parseColor("#7A99B0"))
        }

        // Build the dialog without a positive-button callback so we can prevent auto-dismiss.
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Funds to \"${goal.name}\"")
            .setView(editText)
            .setNegativeButton("Cancel", null)
            .create()

        // Register the "Add" button with an empty lambda — real logic is applied below
        // via setOnClickListener AFTER show(), which overrides AlertDialog's auto-dismiss.
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Add") { _, _ -> }

        dialog.show()

        // Override the positive button's click behaviour post-show to control dismissal manually.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
            when {
                amount <= 0 -> {
                    // Show inline error; keep dialog open for the user to correct input.
                    editText.error = "Please enter a valid amount"
                }
                amount > remaining -> {
                    // Prevent over-funding — cap at the remaining balance.
                    editText.error = "Max R %,.2f".format(remaining)
                }
                else -> {
                    // Dismiss before writing to DB to avoid UI jank if the write is slow.
                    dialog.dismiss()
                    // Delegates the Room insert to the ViewModel; LiveData re-renders the card.
                    vm.addFunds(goal.id, amount, userId)
                }
            }
        }
    }

    /**
     * Shows a [PopupMenu] anchored to [anchor] with goal management options.
     *
     * Currently the only available action is "Delete Goal", which presents a
     * confirmation [AlertDialog] before calling [SavingsGoalViewModel.deleteGoal].
     *
     * @param goal   The [SavingsGoal] the menu applies to.
     * @param anchor The [View] the popup should be anchored to (typically the ⋮ icon).
     */
    private fun showGoalMenu(goal: SavingsGoal, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Delete Goal")

        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                // Require explicit confirmation before permanently deleting the goal.
                AlertDialog.Builder(this)
                    .setTitle("Delete \"${goal.name}\"?")
                    .setMessage("This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ ->
                        vm.deleteGoal(goal, userId)
                        Toast.makeText(this, "Goal deleted.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
        }
        popup.show()
    }
}