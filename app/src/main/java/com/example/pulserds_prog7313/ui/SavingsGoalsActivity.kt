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
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.SavingsGoalViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import kotlin.math.abs
import kotlin.math.roundToInt

class SavingsGoalsActivity : BaseActivity() {

    private lateinit var vm     : SavingsGoalViewModel
    private var userId = -1

    // Overview card views
    private lateinit var overallAmount      : TextView
    private lateinit var overallTarget      : TextView
    private lateinit var overallProgressBar : ProgressBar
    private lateinit var overallPercent     : TextView
    private lateinit var statActive         : TextView
    private lateinit var statCompleted      : TextView
    private lateinit var statThisMonth      : TextView

    // Goal containers
    private lateinit var activeGoalsLabel    : TextView
    private lateinit var activeGoalsContainer: LinearLayout
    private lateinit var emptyActiveText     : TextView
    private lateinit var completedLabel      : TextView
    private lateinit var completedContainer  : LinearLayout
    private lateinit var emptyCompletedText  : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_savings_goals)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        userId = SessionManager(this).getUserId()
        val db = AppDatabase.getDatabase(this)
        vm = ViewModelProvider(
            this, ViewModelFactory { SavingsGoalViewModel(SavingsGoalRepository(db.savingsGoalDao())) }
        )[SavingsGoalViewModel::class.java]

        bindViews()
        setupButtons()
        observeData()
        // FIX: observe saveResult ONCE here at activity level, not inside showAddFundsDialog
        observeSaveResult()
        setupBottomNav(R.id.navSettings)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshTotals(userId)
    }

    // ── Bind ──────────────────────────────────────────────────────────────────

    private fun bindViews() {
        overallAmount       = findViewById(R.id.overallAmount)
        overallTarget       = findViewById(R.id.overallTarget)
        overallProgressBar  = findViewById(R.id.overallProgressBar)
        overallPercent      = findViewById(R.id.overallPercent)
        statActive          = findViewById(R.id.statActive)
        statCompleted       = findViewById(R.id.statCompleted)
        statThisMonth       = findViewById(R.id.statThisMonth)
        activeGoalsLabel    = findViewById(R.id.activeGoalsLabel)
        activeGoalsContainer = findViewById(R.id.activeGoalsContainer)
        emptyActiveText     = findViewById(R.id.emptyActiveText)
        completedLabel      = findViewById(R.id.completedLabel)
        completedContainer  = findViewById(R.id.completedContainer)
        emptyCompletedText  = findViewById(R.id.emptyCompletedText)
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        val toCreate = View.OnClickListener {
            startActivity(Intent(this, CreateSavingsGoalActivity::class.java))
        }
        findViewById<View>(R.id.addGoalBtn)?.setOnClickListener(toCreate)
        findViewById<View>(R.id.addNewGoalButton)?.setOnClickListener(toCreate)
        findViewById<TextView>(R.id.backArrow)?.setOnClickListener { finish() }
    }

    // ── Observe ───────────────────────────────────────────────────────────────

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
            val pct = if (target > 0) ((saved / target) * 100).roundToInt().coerceIn(0, 100) else 0
            overallProgressBar.progress = pct
            overallPercent.text = "$pct% of your total goal achieved 🍀"
        }

        vm.refreshTotals(userId)
    }

    /**
     * FIX: saveResult is observed exactly ONCE at activity level.
     * Previously it was re-observed inside showAddFundsDialog(), which stacked
     * a new observer on every tap — causing multiple toasts, ANR-level event
     * flooding, and unpredictable navigation side-effects.
     */
    private fun observeSaveResult() {
        vm.saveResult.observe(this) { result ->
            when (result) {
                is SavingsGoalViewModel.GoalResult.FundsAdded ->
                    Toast.makeText(this, "Funds added! 💰", Toast.LENGTH_SHORT).show()
                is SavingsGoalViewModel.GoalResult.Error ->
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                else -> { /* Success belongs to CreateSavingsGoalActivity — SingleLiveEvent ensures it won't reach here */ }
            }
        }
    }

    // ── Render goals ──────────────────────────────────────────────────────────

    private fun renderGoals(goals: List<SavingsGoal>) {
        val active    = goals.filter { !it.isCompleted }
        val completed = goals.filter { it.isCompleted }

        // Stats row
        statActive.text    = active.size.toString()
        statCompleted.text = completed.size.toString()

        // This-month total: sum of savedAmount for goals created this calendar month
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val thisMonthMs = cal.timeInMillis
        val thisMonthSaved = active.filter { it.createdAt >= thisMonthMs }.sumOf { it.savedAmount }
        statThisMonth.text = "R %,.0f".format(thisMonthSaved)

        // Active goals section
        activeGoalsContainer.removeAllViews()
        if (active.isEmpty()) {
            emptyActiveText.visibility = View.VISIBLE
        } else {
            emptyActiveText.visibility = View.GONE
            active.forEach { goal -> activeGoalsContainer.addView(buildGoalCard(goal)) }
        }

        // Completed goals section
        completedContainer.removeAllViews()
        completedLabel.visibility = if (completed.isEmpty()) View.GONE else View.VISIBLE
        emptyCompletedText.visibility = View.GONE
        completed.forEach { goal -> completedContainer.addView(buildCompletedCard(goal)) }
    }

    // ── Active goal card ──────────────────────────────────────────────────────

    private fun buildGoalCard(goal: SavingsGoal): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_savings_goal, null, false)

        val pct  = if (goal.targetAmount > 0) ((goal.savedAmount / goal.targetAmount) * 100).roundToInt().coerceIn(0, 100) else 0
        val left = goal.targetAmount - goal.savedAmount

        val tintHex = when {
            pct >= 75 -> "#FBBF24"
            pct >= 40 -> "#60A5FA"
            else       -> "#4ADE80"
        }
        val tintColor = Color.parseColor(tintHex)

        card.findViewById<TextView>(R.id.goalCardIcon).text     = goal.icon
        card.findViewById<TextView>(R.id.goalCardName).text     = goal.name
        card.findViewById<TextView>(R.id.goalCardDate).text     = if (goal.targetDate.isNotBlank()) "Target: ${goal.targetDate}" else "No target date"
        card.findViewById<TextView>(R.id.goalCardSaved).text = "R ${"%.2f".format(goal.savedAmount)} saved"
        card.findViewById<TextView>(R.id.goalCardProgress).also {
            it.text = "$pct%  •  R ${"%.2f".format(abs(left))} to go"
            it.setTextColor(tintColor)
        }

        card.findViewById<View>(R.id.almostThereBadge).visibility =
            if (pct >= 80 && !goal.isCompleted) View.VISIBLE else View.GONE

        card.findViewById<ProgressBar>(R.id.goalCardProgressBar).also {
            it.progress = pct
            it.progressTintList = android.content.res.ColorStateList.valueOf(tintColor)
        }

        card.findViewById<MaterialButton>(R.id.goalCardAddFunds).also {
            it.backgroundTintList = android.content.res.ColorStateList.valueOf(tintColor)
            it.setOnClickListener { showAddFundsDialog(goal) }
        }

        card.findViewById<TextView>(R.id.goalCardMenu).setOnClickListener {
            showGoalMenu(goal, card)
        }

        return card
    }

    // ── Completed goal card ───────────────────────────────────────────────────

    private fun buildCompletedCard(goal: SavingsGoal): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_savings_goal_completed, null, false)

        card.findViewById<TextView>(R.id.completedGoalIcon).text = goal.icon
        card.findViewById<TextView>(R.id.completedGoalName).text = goal.name
        val subtitle = buildString {
            append("Completed")
            if (goal.completedDate.isNotBlank()) append(" ${goal.completedDate}")
            append("  •  R %,.2f".format(goal.targetAmount))
        }
        card.findViewById<TextView>(R.id.completedGoalSubtitle).text = subtitle

        card.findViewById<TextView>(R.id.completedGoalMenu).setOnClickListener {
            showGoalMenu(goal, card)
        }

        return card
    }

    // ── Add funds dialog ──────────────────────────────────────────────────────

    /**
     * FIX: removed vm.saveResult.observe() from inside this method.
     * The observer now lives in observeSaveResult() which is called once in onCreate().
     * This prevents observer accumulation on every dialog open.
     */
    private fun showAddFundsDialog(goal: SavingsGoal) {
        val remaining = goal.targetAmount - goal.savedAmount
        val editText = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "Amount (max R %,.2f)".format(remaining)
            setPadding(40, 24, 40, 24)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#7A99B0"))
        }

        AlertDialog.Builder(this)
            .setTitle("Add Funds to \"${goal.name}\"")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val amount = editText.text.toString().toDoubleOrNull() ?: 0.0
                if (amount <= 0) {
                    Toast.makeText(this, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                vm.addFunds(goal.id, amount.coerceAtMost(remaining), userId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Goal menu ─────────────────────────────────────────────────────────────

    private fun showGoalMenu(goal: SavingsGoal, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Delete Goal")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
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