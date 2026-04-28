package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SettingsActivity — Displays and manages all user-configurable settings for
 * the Pulser app, including profile info, security options, notification
 * preferences, app preferences, support links, and danger-zone actions
 * (sign out / delete account).
 *
 * Extends BaseActivity to inherit the shared bottom navigation bar setup.
 */
class SettingsActivity : BaseActivity() {

    // -------------------------------------------------------------------------
    // Session manager handles reading/writing the currently logged-in user's
    // data (name, email, ID) from encrypted SharedPreferences.
    // Repositories provide coroutine-based access to Room database tables.
    // -------------------------------------------------------------------------
    private lateinit var sessionManager: SessionManager
    private lateinit var savingsGoalRepo: SavingsGoalRepository
    private lateinit var userRepo: UserRepository

    /**
     * SharedPreferences file name and keys used to persist toggle switch states
     * across app sessions. Stored separately from session data so they survive
     * logout/login cycles.
     */
    companion object {
        private const val PREFS_SETTINGS = "app_settings"   // Prefs file name
        private const val KEY_2FA        = "two_factor_enabled"   // 2FA toggle
        private const val KEY_OVERSPEND  = "overspend_alerts"     // Overspend notification toggle
        private const val KEY_SUB_REMIND = "sub_reminders"        // Subscription reminder toggle
        private const val KEY_TX_ALERTS  = "tx_alerts"            // Transaction alert toggle
        private const val KEY_MILESTONES = "savings_milestones"   // Savings milestone toggle
        private const val KEY_DARK_MODE  = "dark_mode"            // Dark mode UI toggle
    }

    /**
     * Called when the activity is first created.
     * Sets up edge-to-edge display, initialises repositories, and wires up
     * every interactive element in the settings screen.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        // Adjust padding so content is not obscured by system bars (status/nav bars)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialise session manager and Room database repositories
        sessionManager   = SessionManager(this)
        val db           = AppDatabase.getDatabase(this)
        savingsGoalRepo  = SavingsGoalRepository(db.savingsGoalDao())
        userRepo         = UserRepository(db.userDao())

        // Load the settings SharedPreferences file
        val settingsPrefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)

        // ------------------------------------------------------------------
        // PROFILE CARD
        // Populate the profile card at the top of the screen with real data
        // pulled from the active session (name, email, plan label, initials).
        // ------------------------------------------------------------------
        val profileName  = findViewById<TextView>(R.id.profileName)
        val profileEmail = findViewById<TextView>(R.id.profileEmail)
        val profilePlan  = findViewById<TextView>(R.id.profilePlan)

        // Display the logged-in user's full name and email, with fallbacks
        profileName.text  = sessionManager.getFullName() ?: "No name set"
        profileEmail.text = sessionManager.getEmail()    ?: "No email set"

        // Build initials (up to 2 characters) from the user's full name and
        // render them inside the circular avatar CardView
        val avatarCard   = findViewById<androidx.cardview.widget.CardView>(R.id.avatarCard)
        val initialsView = avatarCard.getChildAt(0) as? TextView
        val fullName     = sessionManager.getFullName() ?: ""
        if (fullName.isNotBlank()) {
            val parts    = fullName.trim().split(" ")
            // Use first letter of first and last name if available; else just first letter
            val initials = if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}"
            else "${parts[0].first()}"
            initialsView?.text     = initials.uppercase()
            initialsView?.textSize = 22f
        }

        // Static plan label — premium tiers can be differentiated here in future
        profilePlan.text = "🍀 Pulser Free Plan"

        // Navigate to CreateAccActivity to allow the user to edit their profile details
        findViewById<MaterialButton>(R.id.editProfileBtn).setOnClickListener {
            startActivity(Intent(this, CreateAccActivity::class.java))
        }

        // ------------------------------------------------------------------
        // COMPLETED GOALS (REWARDS CARD)
        // Fetch the current user's completed savings goals from the database
        // and display the most recently completed one in the rewards card.
        // ------------------------------------------------------------------
        val userId = sessionManager.getUserId()
        loadCompletedGoals(userId)

        // ------------------------------------------------------------------
        // COLLABORATIVE ACCESS
        // "Invite Collaborator" button shows a dialog with the user's email
        // so they can share it manually. Full multi-user collaboration is
        // deferred to a future release.
        // ------------------------------------------------------------------
        val collaboratorsCard = findViewById<androidx.cardview.widget.CardView>(R.id.collaboratorsCard)

        findViewById<MaterialButton>(R.id.inviteCollaboratorBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Invite Collaborator")
                .setMessage(
                    "To invite a collaborator, share your Pulser account email with them.\n\n" +
                            "Your email: ${sessionManager.getEmail() ?: "unknown"}"
                )
                // Copy the user's email to the clipboard for easy sharing
                .setPositiveButton("Copy Email") { _, _ ->
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText(
                        "Pulser Email", sessionManager.getEmail() ?: ""
                    )
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Email copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ------------------------------------------------------------------
        // SECURITY — Change Password
        // Tapping this row opens a custom AlertDialog with three password
        // fields: current, new, and confirm.
        // ------------------------------------------------------------------
        findViewById<LinearLayout>(R.id.changePasswordRow).setOnClickListener {
            showChangePasswordDialog()
        }

        // ------------------------------------------------------------------
        // SECURITY — Two-Factor Authentication toggle
        // State is persisted in SharedPreferences so it survives app restarts.
        // ------------------------------------------------------------------
        val twoFactorSwitch = findViewById<Switch>(R.id.twoFactorSwitch)
        // Restore the previously saved toggle state (default: off)
        twoFactorSwitch.isChecked = settingsPrefs.getBoolean(KEY_2FA, false)

        twoFactorSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_2FA, isChecked).apply()
            val msg = if (isChecked) "Two-factor authentication enabled"
            else            "Two-factor authentication disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // ------------------------------------------------------------------
        // NOTIFICATIONS — Four independent toggles, each persisted in prefs.
        //   • Overspending alerts  — notify when a budget category is exceeded
        //   • Subscription reminders — remind before a subscription renews
        //   • Transaction alerts   — notify on every new transaction
        //   • Savings milestones   — notify when a savings goal milestone is hit
        // ------------------------------------------------------------------
        val overspendSwitch        = findViewById<Switch>(R.id.overspendSwitch)
        val subReminderSwitch      = findViewById<Switch>(R.id.subReminderSwitch)
        val transactionAlertSwitch = findViewById<Switch>(R.id.transactionAlertSwitch)
        val savingsMilestoneSwitch = findViewById<Switch>(R.id.savingsMilestoneSwitch)

        // Restore persisted states (sensible defaults shown below)
        overspendSwitch.isChecked        = settingsPrefs.getBoolean(KEY_OVERSPEND,  true)
        subReminderSwitch.isChecked      = settingsPrefs.getBoolean(KEY_SUB_REMIND, true)
        transactionAlertSwitch.isChecked = settingsPrefs.getBoolean(KEY_TX_ALERTS,  false)
        savingsMilestoneSwitch.isChecked = settingsPrefs.getBoolean(KEY_MILESTONES, true)

        // Persist each toggle's new state and show a brief confirmation toast
        overspendSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_OVERSPEND, isChecked).apply()
            Toast.makeText(this, if (isChecked) "Overspending alerts on" else "Overspending alerts off", Toast.LENGTH_SHORT).show()
        }
        subReminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_SUB_REMIND, isChecked).apply()
            Toast.makeText(this, if (isChecked) "Subscription reminders on" else "Subscription reminders off", Toast.LENGTH_SHORT).show()
        }
        transactionAlertSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_TX_ALERTS, isChecked).apply()
            Toast.makeText(this, if (isChecked) "Transaction alerts on" else "Transaction alerts off", Toast.LENGTH_SHORT).show()
        }
        savingsMilestoneSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_MILESTONES, isChecked).apply()
            Toast.makeText(this, if (isChecked) "Savings milestone alerts on" else "Savings milestone alerts off", Toast.LENGTH_SHORT).show()
        }

        // ------------------------------------------------------------------
        // PREFERENCES — Dark Mode, Currency, Language
        // ------------------------------------------------------------------

        // Dark mode toggle — actual theme switching is applied on next launch
        // via AppCompatDelegate.setDefaultNightMode()
        val darkModeSwitch = findViewById<Switch>(R.id.darkModeSwitch)
        darkModeSwitch.isChecked = settingsPrefs.getBoolean(KEY_DARK_MODE, true)
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            Toast.makeText(this, if (isChecked) "Dark mode enabled" else "Dark mode disabled", Toast.LENGTH_SHORT).show()
        }

        // Currency row — opens a single-choice dialog to select the display currency
        findViewById<LinearLayout>(R.id.currencyRow).setOnClickListener {
            showCurrencyDialog()
        }

        // Language row — currently locked to English (South Africa)
        findViewById<LinearLayout>(R.id.languageRow).setOnClickListener {
            Toast.makeText(this, "Language: English (South Africa)", Toast.LENGTH_SHORT).show()
        }

        // ------------------------------------------------------------------
        // SUPPORT & ABOUT — Three informational dialogs
        // ------------------------------------------------------------------

        // Help Centre: contact details and common FAQ topics
        findViewById<LinearLayout>(R.id.helpRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Help Centre")
                .setMessage(
                    "For support, contact us at:\nsupport@pulserapp.co.za\n\n" +
                            "Common topics:\n• Adding transactions\n• Setting savings goals\n• Managing subscriptions"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        // Privacy Policy: explains local-only data storage and AES-256 encryption
        findViewById<LinearLayout>(R.id.privacyRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Privacy Policy")
                .setMessage(
                    "Pulser stores all your financial data locally on your device.\n\n" +
                            "We do not share, sell, or transmit your personal data to any third parties.\n\n" +
                            "Your data is encrypted and protected with 256-bit AES encryption.\n\n" +
                            "Version: 1.0.0 | Last updated: April 2026"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        // Terms of Service: acceptable-use rules and liability disclaimer
        findViewById<LinearLayout>(R.id.termsRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Terms of Service")
                .setMessage(
                    "By using Pulser, you agree to:\n\n" +
                            "• Use the app for lawful personal finance management only.\n" +
                            "• Not attempt to reverse-engineer or tamper with the application.\n" +
                            "• Accept that Pulser provides financial tracking tools and is not a licensed financial advisor.\n\n" +
                            "For questions, contact support@pulserapp.co.za"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        // ------------------------------------------------------------------
        // DANGER ZONE — Irreversible account actions
        // ------------------------------------------------------------------

        // Sign Out: clears the session and returns the user to MainActivity
        findViewById<LinearLayout>(R.id.signOutRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    sessionManager.logout()
                    // Clear the back stack so the user cannot navigate back after signing out
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Delete Account: warns the user, clears the session, and redirects to login.
        // NOTE: Full DB record deletion requires UserDao.deleteById() — currently
        // only the session is cleared as a placeholder implementation.
        findViewById<LinearLayout>(R.id.deleteAccountRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage(
                    "This will permanently delete your account and all associated data.\n\n" +
                            "This action cannot be undone."
                )
                .setPositiveButton("Delete") { _, _ ->
                    sessionManager.logout()
                    Toast.makeText(this, "Account deleted. Goodbye!", Toast.LENGTH_LONG).show()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Mark Settings as the active bottom navigation destination
        setupBottomNav(R.id.navSettings)
    }

    // -------------------------------------------------------------------------
    // loadCompletedGoals
    // Queries the database on a background coroutine for all savings goals
    // belonging to [userId] that are marked as completed, then hands the list
    // to updateRewardsCard() on the main thread.
    // -------------------------------------------------------------------------
    private fun loadCompletedGoals(userId: Int) {
        lifecycleScope.launch {
            val completedGoals: List<SavingsGoal> = savingsGoalRepo
                .getAllGoalsOnce(userId)
                .filter { it.isCompleted }   // Client-side filter for completed goals

            // Switch back to the main thread before touching any Views
            runOnUiThread {
                updateRewardsCard(completedGoals)
            }
        }
    }

    // -------------------------------------------------------------------------
    // updateRewardsCard
    // Updates the Rewards section of the UI with the most recently completed
    // savings goal. Falls back to a "no goals yet" placeholder when the list
    // is empty. Uses view-hierarchy traversal because the inner card's child
    // TextViews do not have explicit resource IDs in the layout.
    // -------------------------------------------------------------------------
    private fun updateRewardsCard(completedGoals: List<SavingsGoal>) {
        val rewardsCard = findViewById<androidx.cardview.widget.CardView>(R.id.rewardsCard)
            ?: return   // Safety guard — exit if the card is not in the layout

        val goalCard = rewardsCard.findViewById<androidx.cardview.widget.CardView>(R.id.completedGoalCarCard)
            ?: return

        if (completedGoals.isEmpty()) {
            // Navigate the view hierarchy to reach the name and detail TextViews
            val nameView = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(1)
                    ?.let { it as? android.widget.LinearLayout }
                    ?.getChildAt(0) as? TextView
            }
            val detailView = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(1)
                    ?.let { it as? android.widget.LinearLayout }
                    ?.getChildAt(1) as? TextView
            }
            // Show placeholder text when the user has no completed goals
            nameView?.text   = "No completed goals yet"
            detailView?.text = "Keep saving — you're almost there!"
        } else {
            // Display the most recently completed goal (sorted by completedDate descending)
            val latest = completedGoals.sortedByDescending { it.completedDate }.first()

            // Traverse the view hierarchy to reach the three display elements
            val nameView = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(1)
                    ?.let { it as? android.widget.LinearLayout }
                    ?.getChildAt(0) as? TextView
            }
            val detailView = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(1)
                    ?.let { it as? android.widget.LinearLayout }
                    ?.getChildAt(1) as? TextView
            }
            // The emoji badge sits in the first child of the ConstraintLayout
            val emojiView = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(0)
                    ?.let { it as? androidx.cardview.widget.CardView }
                    ?.getChildAt(0) as? TextView
            }

            emojiView?.text  = latest.icon    // Goal-specific emoji icon
            nameView?.text   = latest.name    // Goal name (e.g. "Holiday Fund")
            // Format: "Completed DD/MM/YYYY · R X,XXX.XX"
            val dateLabel = if (latest.completedDate.isNotBlank()) "Completed ${latest.completedDate}" else "Completed"
            detailView?.text = "$dateLabel · R ${String.format("%,.2f", latest.targetAmount)}"
        }
    }

    // -------------------------------------------------------------------------
    // showChangePasswordDialog
    // Presents a custom AlertDialog with three EditText fields for changing the
    // user's password. Validates input length and match before hashing with
    // SHA-256 and persisting the updated User record via the repository.
    // -------------------------------------------------------------------------
    private fun showChangePasswordDialog() {
        // Build a programmatic LinearLayout to host the password fields
        // (avoids the need for a dedicated XML dialog layout file)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        // Current password field — used to verify identity before allowing change
        val currentPwInput = android.widget.EditText(this).apply {
            hint = "Current password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        // New password field
        val newPwInput = android.widget.EditText(this).apply {
            hint = "New password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        // Confirmation field — must match newPwInput exactly
        val confirmPwInput = android.widget.EditText(this).apply {
            hint = "Confirm new password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(currentPwInput)
        layout.addView(newPwInput)
        layout.addView(confirmPwInput)

        AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val currentPw = currentPwInput.text.toString()
                val newPw     = newPwInput.text.toString()
                val confirmPw = confirmPwInput.text.toString()

                when {
                    // Guard: all fields must be filled
                    currentPw.isBlank() || newPw.isBlank() || confirmPw.isBlank() ->
                        Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    // Guard: new password and confirmation must match
                    newPw != confirmPw ->
                        Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                    // Guard: enforce minimum password length
                    newPw.length < 8 ->
                        Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                    else -> {
                        // Hash both passwords before any DB interaction
                        val currentHash = hashPassword(currentPw)
                        val newHash     = hashPassword(newPw)
                        val email       = sessionManager.getEmail() ?: ""

                        lifecycleScope.launch {
                            // Verify current password by attempting a login lookup
                            val user = userRepo.login(email, currentHash)
                            if (user == null) {
                                // Current password did not match the stored hash
                                runOnUiThread {
                                    Toast.makeText(this@SettingsActivity, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Replace the password hash on the existing User entity and persist it
                                val updated = user.copy(passwordHash = newHash)
                                userRepo.updateUser(updated)
                                runOnUiThread {
                                    Toast.makeText(this@SettingsActivity, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // showCurrencyDialog
    // Shows a single-choice AlertDialog listing supported display currencies.
    // The selected index is persisted in SharedPreferences so the app can apply
    // the correct currency symbol across all screens.
    // -------------------------------------------------------------------------
    private fun showCurrencyDialog() {
        val currencies = arrayOf(
            "South African Rand (ZAR)",
            "US Dollar (USD)",
            "Euro (EUR)",
            "British Pound (GBP)",
            "Botswana Pula (BWP)"
        )
        val settingsPrefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
        // Restore the previously selected index (default: ZAR at index 0)
        val currentIndex  = settingsPrefs.getInt("currency_index", 0)

        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            // Radio-button list — highlights the currently active currency
            .setSingleChoiceItems(currencies, currentIndex) { dialog, which ->
                settingsPrefs.edit().putInt("currency_index", which).apply()
                Toast.makeText(this, "Currency set to: ${currencies[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()   // Close dialog immediately after selection
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // -------------------------------------------------------------------------
    // hashPassword
    // Computes a SHA-256 hex digest of the given plain-text password.
    // Must match the hashing logic in CreateAccActivity so that passwords
    // stored during registration can be verified here during login lookups.
    // -------------------------------------------------------------------------
    private fun hashPassword(password: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(password.toByteArray())
        // Convert each byte to a zero-padded 2-character hex string
        return bytes.joinToString("") { "%02x".format(it) }
    }
}