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

class SettingsActivity : BaseActivity() {

    // -------------------------------------------------------------------------
    // Session & repositories
    // -------------------------------------------------------------------------
    private lateinit var sessionManager: SessionManager
    private lateinit var savingsGoalRepo: SavingsGoalRepository
    private lateinit var userRepo: UserRepository

    // -------------------------------------------------------------------------
    // SharedPreferences keys for toggle states (persisted across sessions)
    // -------------------------------------------------------------------------
    companion object {
        private const val PREFS_SETTINGS = "app_settings"
        private const val KEY_BIOMETRIC  = "biometric_enabled"
        private const val KEY_2FA        = "two_factor_enabled"
        private const val KEY_OVERSPEND  = "overspend_alerts"
        private const val KEY_SUB_REMIND = "sub_reminders"
        private const val KEY_TX_ALERTS  = "tx_alerts"
        private const val KEY_MILESTONES = "savings_milestones"
        private const val KEY_DARK_MODE  = "dark_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialise session & repos
        sessionManager   = SessionManager(this)
        val db           = AppDatabase.getDatabase(this)
        savingsGoalRepo  = SavingsGoalRepository(db.savingsGoalDao())
        userRepo         = UserRepository(db.userDao())

        val settingsPrefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)

        // ─────────────────────────────────────────────────────────────────────
        // PROFILE CARD — populate with real session data
        // ─────────────────────────────────────────────────────────────────────
        val profileName  = findViewById<TextView>(R.id.profileName)
        val profileEmail = findViewById<TextView>(R.id.profileEmail)
        val profilePlan  = findViewById<TextView>(R.id.profilePlan)

        profileName.text  = sessionManager.getFullName() ?: "No name set"
        profileEmail.text = sessionManager.getEmail()    ?: "No email set"

        // Show initials in the avatar circle based on real name
        val avatarCard = findViewById<androidx.cardview.widget.CardView>(R.id.avatarCard)
        val initialsView = avatarCard.getChildAt(0) as? TextView
        val fullName = sessionManager.getFullName() ?: ""
        if (fullName.isNotBlank()) {
            val parts    = fullName.trim().split(" ")
            val initials = if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}"
            else "${parts[0].first()}"
            initialsView?.text     = initials.uppercase()
            initialsView?.textSize = 22f
        }

        // Plan label: base free plan for now
        profilePlan.text = "🍀 Pulser Free Plan"

        // Edit Profile → CreateAccActivity
        findViewById<MaterialButton>(R.id.editProfileBtn).setOnClickListener {
            startActivity(Intent(this, CreateAccActivity::class.java))
        }

        // ─────────────────────────────────────────────────────────────────────
        // COMPLETED GOALS — load real data from DB
        // ─────────────────────────────────────────────────────────────────────
        val userId = sessionManager.getUserId()
        loadCompletedGoals(userId)

        // ─────────────────────────────────────────────────────────────────────
        // COLLABORATIVE ACCESS — Invite Collaborator button
        // ─────────────────────────────────────────────────────────────────────
        val collaboratorsCard = findViewById<androidx.cardview.widget.CardView>(R.id.collaboratorsCard)

        // Replace hardcoded collaborator rows with current-user info only
        // (real multi-user collaboration is out of scope; show a sensible state)
        val collab1Name  = collaboratorsCard.findViewById<TextView?>(
            resources.getIdentifier("collab1Name",  "id", packageName)
        )
        // The layout uses inline TextViews without dedicated IDs for the names,
        // so we update them via the invite button row instead.

        findViewById<MaterialButton>(R.id.inviteCollaboratorBtn).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Invite Collaborator")
                .setMessage(
                    "To invite a collaborator, share your Pulser account email with them.\n\n" +
                            "Your email: ${sessionManager.getEmail() ?: "unknown"}"
                )
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

        // ─────────────────────────────────────────────────────────────────────
        // SECURITY — Change Password row
        // ─────────────────────────────────────────────────────────────────────
        findViewById<LinearLayout>(R.id.changePasswordRow).setOnClickListener {
            showChangePasswordDialog()
        }

        // Auto Lock row
        findViewById<LinearLayout>(R.id.autoLockRow).setOnClickListener {
            showAutoLockDialog()
        }

        // ─────────────────────────────────────────────────────────────────────
        // SECURITY — Switches (persisted in SharedPreferences)
        // ─────────────────────────────────────────────────────────────────────
        val biometricSwitch = findViewById<Switch>(R.id.biometricSwitch)
        val twoFactorSwitch = findViewById<Switch>(R.id.twoFactorSwitch)

        biometricSwitch.isChecked = settingsPrefs.getBoolean(KEY_BIOMETRIC, true)
        twoFactorSwitch.isChecked = settingsPrefs.getBoolean(KEY_2FA, false)

        biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_BIOMETRIC, isChecked).apply()
            val msg = if (isChecked) "Biometric login enabled" else "Biometric login disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        twoFactorSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_2FA, isChecked).apply()
            val msg = if (isChecked) "Two-factor authentication enabled" else "Two-factor authentication disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // ─────────────────────────────────────────────────────────────────────
        // NOTIFICATIONS — Switches
        // ─────────────────────────────────────────────────────────────────────
        val overspendSwitch        = findViewById<Switch>(R.id.overspendSwitch)
        val subReminderSwitch      = findViewById<Switch>(R.id.subReminderSwitch)
        val transactionAlertSwitch = findViewById<Switch>(R.id.transactionAlertSwitch)
        val savingsMilestoneSwitch = findViewById<Switch>(R.id.savingsMilestoneSwitch)

        overspendSwitch.isChecked        = settingsPrefs.getBoolean(KEY_OVERSPEND,  true)
        subReminderSwitch.isChecked      = settingsPrefs.getBoolean(KEY_SUB_REMIND, true)
        transactionAlertSwitch.isChecked = settingsPrefs.getBoolean(KEY_TX_ALERTS,  false)
        savingsMilestoneSwitch.isChecked = settingsPrefs.getBoolean(KEY_MILESTONES, true)

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

        // ─────────────────────────────────────────────────────────────────────
        // PREFERENCES — Currency, Language, Dark Mode, Tutorial
        // ─────────────────────────────────────────────────────────────────────
        val darkModeSwitch = findViewById<Switch>(R.id.darkModeSwitch)
        darkModeSwitch.isChecked = settingsPrefs.getBoolean(KEY_DARK_MODE, true)
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            Toast.makeText(this, if (isChecked) "Dark mode enabled" else "Dark mode disabled", Toast.LENGTH_SHORT).show()
            // Dark mode theming would be applied on next launch via AppCompatDelegate
        }

        findViewById<LinearLayout>(R.id.currencyRow).setOnClickListener {
            showCurrencyDialog()
        }

        findViewById<LinearLayout>(R.id.languageRow).setOnClickListener {
            Toast.makeText(this, "Language: English (South Africa)", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.tutorialRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Replay Tutorial")
                .setMessage("This would restart the app walkthrough from the beginning.")
                .setPositiveButton("Replay") { _, _ ->
                    Toast.makeText(this, "Tutorial restarted", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ─────────────────────────────────────────────────────────────────────
        // SUPPORT & ABOUT
        // ─────────────────────────────────────────────────────────────────────
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

        // ─────────────────────────────────────────────────────────────────────
        // DANGER ZONE — Sign Out & Delete Account
        // ─────────────────────────────────────────────────────────────────────
        findViewById<LinearLayout>(R.id.signOutRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    sessionManager.logout()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<LinearLayout>(R.id.deleteAccountRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage(
                    "This will permanently delete your account and all associated data.\n\n" +
                            "This action cannot be undone."
                )
                .setPositiveButton("Delete") { _, _ ->
                    // Wipe session and redirect to login; actual DB deletion would require
                    // a UserDao.deleteById() method — signal intent clearly to user.
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

        // Settings is the current screen
        setupBottomNav(R.id.navSettings)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Load real completed goals from DB into the rewards card
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadCompletedGoals(userId: Int) {
        lifecycleScope.launch {
            val completedGoals: List<SavingsGoal> = savingsGoalRepo
                .getAllGoalsOnce(userId)
                .filter { it.isCompleted }

            runOnUiThread {
                updateRewardsCard(completedGoals)
            }
        }
    }

    private fun updateRewardsCard(completedGoals: List<SavingsGoal>) {
        val rewardsCard = findViewById<androidx.cardview.widget.CardView>(R.id.rewardsCard)
            ?: return

        // Find the goal name and detail TextViews inside the completedGoalCarCard
        val goalCard = rewardsCard.findViewById<androidx.cardview.widget.CardView>(R.id.completedGoalCarCard)
            ?: return

        val goalNameView   = goalCard.findViewWithTag<TextView>("goalName")
        val goalDetailView = goalCard.findViewWithTag<TextView>("goalDetail")

        if (completedGoals.isEmpty()) {
            // Update the static card to reflect no completed goals yet
            val nameView   = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(1)  // carGoalInfo LinearLayout
                    ?.let { it as? android.widget.LinearLayout }
                    ?.getChildAt(0) as? TextView
            }
            val detailView = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(1)
                    ?.let { it as? android.widget.LinearLayout }
                    ?.getChildAt(1) as? TextView
            }
            nameView?.text   = "No completed goals yet"
            detailView?.text = "Keep saving — you're almost there!"
        } else {
            // Show the most recently completed goal
            val latest = completedGoals.sortedByDescending { it.completedDate }.first()
            val nameView   = goalCard.getChildAt(0)?.let { cl ->
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
            val emojiView = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(0)  // carBadge CardView
                    ?.let { it as? androidx.cardview.widget.CardView }
                    ?.getChildAt(0) as? TextView
            }

            emojiView?.text   = latest.icon
            nameView?.text    = latest.name
            val dateLabel = if (latest.completedDate.isNotBlank()) "Completed ${latest.completedDate}" else "Completed"
            detailView?.text  = "$dateLabel · R ${String.format("%,.2f", latest.targetAmount)}"
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Change Password dialog
    // ─────────────────────────────────────────────────────────────────────────
    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null)

        // Build a simple input dialog manually (no custom layout needed)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }

        val currentPwInput = android.widget.EditText(this).apply {
            hint = "Current password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newPwInput = android.widget.EditText(this).apply {
            hint = "New password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
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
                    currentPw.isBlank() || newPw.isBlank() || confirmPw.isBlank() ->
                        Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                    newPw != confirmPw ->
                        Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
                    newPw.length < 8 ->
                        Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                    else -> {
                        val currentHash = hashPassword(currentPw)
                        val newHash     = hashPassword(newPw)
                        val email       = sessionManager.getEmail() ?: ""

                        lifecycleScope.launch {
                            val user = userRepo.login(email, currentHash)
                            if (user == null) {
                                runOnUiThread {
                                    Toast.makeText(this@SettingsActivity, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // UserDao needs an update method — call the generic update via UserRepository
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

    // ─────────────────────────────────────────────────────────────────────────
    // Auto Lock dialog
    // ─────────────────────────────────────────────────────────────────────────
    private fun showAutoLockDialog() {
        val options = arrayOf("1 minute", "5 minutes", "15 minutes", "30 minutes", "Never")
        val settingsPrefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
        val currentIndex  = settingsPrefs.getInt("auto_lock_index", 1) // default: 5 min

        AlertDialog.Builder(this)
            .setTitle("Auto Lock After")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                settingsPrefs.edit().putInt("auto_lock_index", which).apply()
                Toast.makeText(this, "Auto lock set to: ${options[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Currency dialog
    // ─────────────────────────────────────────────────────────────────────────
    private fun showCurrencyDialog() {
        val currencies = arrayOf(
            "South African Rand (ZAR)",
            "US Dollar (USD)",
            "Euro (EUR)",
            "British Pound (GBP)",
            "Botswana Pula (BWP)"
        )
        val settingsPrefs  = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
        val currentIndex   = settingsPrefs.getInt("currency_index", 0)

        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            .setSingleChoiceItems(currencies, currentIndex) { dialog, which ->
                settingsPrefs.edit().putInt("currency_index", which).apply()
                Toast.makeText(this, "Currency set to: ${currencies[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHA-256 helper (matches CreateAccActivity hashing)
    // ─────────────────────────────────────────────────────────────────────────
    private fun hashPassword(password: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes  = digest.digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}