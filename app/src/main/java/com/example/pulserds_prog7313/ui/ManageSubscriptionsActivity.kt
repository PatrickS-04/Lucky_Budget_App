package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.Subscription
import com.example.pulserds_prog7313.repository.SubscriptionRepository
import com.example.pulserds_prog7313.ui.adapters.SubscriptionAdapter
import com.example.pulserds_prog7313.utils.CurrencyManager
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.SubscriptionViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton

/**
 * ManageSubscriptionsActivity
 *
 * Displays the user's subscriptions grouped by status, shows a monthly summary
 * card, supports filter chips (All / Active / Paused / Renewing Soon), and
 * provides a "renewing soon" alert banner.
 *
 * Architecture:
 *  - Observes [SubscriptionViewModel.subscriptions] (real-time Firestore stream)
 *  - Uses [SubscriptionAdapter] backed by a RecyclerView
 *  - Filter state is kept in [currentFilter] (no LiveData needed — it is purely
 *    a display concern that doesn't survive the process)
 *
 * Navigation:
 *  - Back arrow  → finish()
 *  - "+ Add" / "＋ Add New Subscription" → AddSubscriptionActivity
 */
class ManageSubscriptionsActivity : BaseActivity() {

    private lateinit var viewModel: SubscriptionViewModel
    private lateinit var adapter: SubscriptionAdapter

    private var userId = ""
    private var currentFilter = "All"   // "All" | "Active" | "Paused" | "Renewing Soon"
    private var allSubscriptions: List<Subscription> = emptyList()

    // -- Views -----------------------------------------------------------------

    private lateinit var summaryAmount:     TextView
    private lateinit var summaryYearly:     TextView
    private lateinit var activeCountText:   TextView
    private lateinit var pausedCountText:   TextView
    private lateinit var renewingSoonCount: TextView
    private lateinit var renewingAlertCard: View
    private lateinit var renewingAlertBody: TextView
    private lateinit var subsRecycler:      RecyclerView
    private lateinit var emptyText:         TextView

    // Filter chip views
    private lateinit var chipAll:          TextView
    private lateinit var chipActive:       TextView
    private lateinit var chipPaused:       TextView
    private lateinit var chipRenewingSoon: TextView

    // -- Lifecycle -------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_manage_subscriptions)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        userId = SessionManager(this).getUserId()

        setupViewModel()
        bindViews()
        setupRecycler()
        setupChips()
        setupButtons()
        observeData()

        // Note: this activity uses its own bottom-nav layout (not BaseActivity nav IDs),
        // so we do not call setupBottomNav() here to avoid a crash on missing view IDs.
    }

    // -- Setup -----------------------------------------------------------------

    private fun setupViewModel() {
        viewModel = ViewModelProvider(
            this,
            ViewModelFactory { SubscriptionViewModel(SubscriptionRepository()) }
        ).get(SubscriptionViewModel::class.java)
        viewModel.init(userId)
    }

    private fun bindViews() {
        summaryAmount     = findViewById(R.id.summaryAmount)
        summaryYearly     = findViewById(R.id.summaryYearly)
        // Stats row TextViews — look up by position inside the statsRow LinearLayout
        // (they are direct children: [0]=Active, [2]=Paused, [4]=RenewingSoon)
        val statsRow = findViewById<android.widget.LinearLayout>(R.id.statsRow)
        // Each "column" is a vertical LinearLayout; the number TextView is the 2nd child (index 1)
        activeCountText   = (statsRow.getChildAt(0) as android.widget.LinearLayout).getChildAt(1) as TextView
        pausedCountText   = (statsRow.getChildAt(2) as android.widget.LinearLayout).getChildAt(1) as TextView
        renewingSoonCount = (statsRow.getChildAt(4) as android.widget.LinearLayout).getChildAt(1) as TextView

        renewingAlertCard = findViewById(R.id.renewingAlertCard)
        // The alert body text is inside the nested LinearLayout
        renewingAlertBody = (renewingAlertCard as android.view.ViewGroup)
            .let { card ->
                // LinearLayout > LinearLayout > [TextView(bell), LinearLayout > [title, body]]
                val inner = (card as android.widget.LinearLayout)
                val textCol = inner.getChildAt(1) as android.widget.LinearLayout
                textCol.getChildAt(1) as TextView
            }

        subsRecycler = findViewById(R.id.subsRecycler)
        emptyText    = findViewById(R.id.subsEmptyText)

        chipAll          = findViewById(R.id.chipAll)
        chipActive       = findViewById(R.id.chipActive)
        chipPaused       = findViewById(R.id.chipPaused)
        chipRenewingSoon = findViewById(R.id.chipRenewingSoon)
    }

    private fun setupRecycler() {
        adapter = SubscriptionAdapter(
            onToggleStatus = { sub ->
                // Long-press: flip Active ↔ Paused with a confirmation dialog
                val action = if (sub.status == "Active") "pause" else "resume"
                AlertDialog.Builder(this)
                    .setTitle("${action.replaceFirstChar { it.uppercase() }} subscription?")
                    .setMessage("Do you want to $action \"${sub.serviceName}\"?")
                    .setPositiveButton("Yes") { _, _ -> viewModel.toggleStatus(sub, userId) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onDelete = { sub ->
                // Single-tap: show delete confirmation
                AlertDialog.Builder(this)
                    .setTitle("Delete subscription?")
                    .setMessage("Remove \"${sub.serviceName}\"? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteSubscription(sub, userId) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        subsRecycler.layoutManager = LinearLayoutManager(this)
        subsRecycler.adapter = adapter
        subsRecycler.isNestedScrollingEnabled = false
    }

    private fun setupChips() {
        val chips = mapOf(
            chipAll          to "All",
            chipActive       to "Active",
            chipPaused       to "Paused",
            chipRenewingSoon to "Renewing Soon"
        )
        for ((chip, filter) in chips) {
            chip.setOnClickListener {
                currentFilter = filter
                applyFilter(allSubscriptions)
                updateChipSelection(chip)
            }
        }
    }

    private fun setupButtons() {
        // Back arrow
        findViewById<TextView>(R.id.backArrow).setOnClickListener { finish() }

        // Header "+ Add" button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.addSubBtn)
            .setOnClickListener { openAddSubscription() }

        // Bottom large button
        findViewById<MaterialButton>(R.id.addSubscriptionButton)
            .setOnClickListener { openAddSubscription() }
    }

    private fun openAddSubscription() {
        startActivity(Intent(this, AddSubscriptionActivity::class.java))
    }

    // -- Observation -----------------------------------------------------------

    private fun observeData() {
        viewModel.subscriptions.observe(this) { list ->
            allSubscriptions = list
            updateSummaryCard(list)
            applyFilter(list)
        }

        viewModel.monthlyTotal.observe(this) { total ->
            val formatted = CurrencyManager.format(this, total)
            summaryAmount.text = formatted
        }
    }

    // -- UI update helpers -----------------------------------------------------

    private fun updateSummaryCard(list: List<Subscription>) {
        val activeList     = list.filter { it.status == "Active" }
        val pausedList     = list.filter { it.status == "Paused" }
        val renewingSoon   = activeList.filter { isRenewingWithinDays(it, 7) }

        activeCountText.text   = activeList.size.toString()
        pausedCountText.text   = pausedList.size.toString()
        renewingSoonCount.text = renewingSoon.size.toString()

        // Yearly equivalent across active subs
        val monthlyTotal = activeList.sumOf { sub ->
            when (sub.billingCycle) {
                "Weekly"  -> sub.amount * 4.33
                "Monthly" -> sub.amount
                "Yearly"  -> sub.amount / 12.0
                else      -> sub.amount
            }
        }
        val yearlyTotal = monthlyTotal * 12
        summaryYearly.text =
            "${CurrencyManager.format(this, yearlyTotal)} / year across ${activeList.size} subscriptions"

        // Renewing soon alert banner
        if (renewingSoon.isNotEmpty()) {
            renewingAlertCard.visibility = View.VISIBLE
            val names = renewingSoon.take(3).joinToString("  •  ") {
                "${it.serviceName} on ${it.nextRenewal}"
            }
            renewingAlertBody.text = names
        } else {
            renewingAlertCard.visibility = View.GONE
        }
    }

    private fun applyFilter(list: List<Subscription>) {
        val filtered = when (currentFilter) {
            "Active"        -> list.filter { it.status == "Active" }
            "Paused"        -> list.filter { it.status == "Paused" }
            "Renewing Soon" -> list.filter { it.status == "Active" && isRenewingWithinDays(it, 7) }
            else            -> list
        }
        adapter.submitList(filtered)

        // Show / hide empty state
        if (filtered.isEmpty()) {
            subsRecycler.visibility = View.GONE
            emptyText.visibility    = View.VISIBLE
            emptyText.text          = when (currentFilter) {
                "Active"        -> "No active subscriptions."
                "Paused"        -> "No paused subscriptions."
                "Renewing Soon" -> "No subscriptions renewing in the next 7 days."
                else            -> "No subscriptions yet. Tap '+ Add' to get started."
            }
        } else {
            subsRecycler.visibility = View.VISIBLE
            emptyText.visibility    = View.GONE
        }
    }

    private fun updateChipSelection(selected: TextView) {
        val activeColour  = 0xFF4ADE80.toInt()
        val inactiveColor = 0xFF122033.toInt()
        val activeText    = 0xFF0D1B2A.toInt()
        val inactiveText  = 0xFFFF9500.toInt()

        for (chip in listOf(chipAll, chipActive, chipPaused, chipRenewingSoon)) {
            val isSelected = chip == selected
            chip.setBackgroundColor(if (isSelected) activeColour else inactiveColor)
            chip.setTextColor(if (isSelected) activeText else inactiveText)
            chip.typeface = if (isSelected)
                android.graphics.Typeface.DEFAULT_BOLD
            else
                android.graphics.Typeface.DEFAULT
        }
    }

    /** Returns true if the subscription's next renewal is within [days] days from today. */
    private fun isRenewingWithinDays(sub: Subscription, days: Int): Boolean {
        return try {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
            val renewal = sdf.parse(sub.nextRenewal) ?: return false
            val today = java.util.Calendar.getInstance()
            val limit = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, days) }
            !renewal.before(today.time) && !renewal.after(limit.time)
        } catch (e: Exception) {
            false
        }
    }

    override fun onResume() {
        super.onResume()
        if (userId.isNotBlank()) viewModel.refreshMonthlyTotal(userId)
    }
}