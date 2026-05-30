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
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.repository.SavingsGoalRepository
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.CurrencyManager
import com.example.pulserds_prog7313.utils.SessionManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
// [Accessed: 28 Apr. 2026].
//[4] JetBrains, "Coroutines guide," Kotlin Documentation, 2024. [Online].
// Available: https://kotlinlang.org/docs/coroutines-guide.html.
// [Accessed: 28 Apr. 2026].
//[8] JetBrains, kotlinx-coroutines-android, ver. 1.8.1. [Software]. JetBrains, 2024.
// Available: https://github.com/Kotlin/kotlinx.coroutines.
//[9] Google, AndroidX Core KTX, ver. 1.13.1. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/core.
//[10] Google, AndroidX AppCompat, ver. 1.7.0. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/appcompat.
//[12] Google, AndroidX ConstraintLayout, ver. 2.2.0. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/constraintlayout.
//[18] Google, "SharedPreferences — save key-value data," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/data-storage/shared-preferences.
// [Accessed: 28 Apr. 2026].
//[27] 	Firebase, "Firebase Authentication," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth?authuser=0&_gl=1*10potxh*_ga*MTk4MDgwMzQ0MC4xNzcxODc3ODAz*_ga_CW55HF8NVT*czE3Nzk2NDE1OTIkbzExJGcxJHQxNzc5NjQyMzc2JGo1NCRsMCRoMA..#multi-factor.
// [Accessed 30 May 2026].

class SettingsActivity : BaseActivity() {

    private lateinit var sessionManager:  SessionManager
    private lateinit var savingsGoalRepo: SavingsGoalRepository
    private lateinit var userRepo:        UserRepository

    companion object {
        private const val PREFS_SETTINGS = "app_settings"
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

        val navBarContainer = findViewById<LinearLayout>(R.id.containerForBottomNavBar)

        ViewCompat.setOnApplyWindowInsetsListener(navBarContainer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val basePadding = (16 * resources.displayMetrics.density).toInt()
            v.setPadding(bars.left, bars.top, bars.right, basePadding + bars.bottom)
            insets
        }

        sessionManager  = SessionManager(this)
        savingsGoalRepo = SavingsGoalRepository()
        userRepo        = UserRepository()

        val settingsPrefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)

        val profileName  = findViewById<TextView>(R.id.profileName)
        val profileEmail = findViewById<TextView>(R.id.profileEmail)
        val profilePlan  = findViewById<TextView>(R.id.profilePlan)

        profileName.text  = sessionManager.getFullName() ?: "No name set"
        profileEmail.text = sessionManager.getEmail()    ?: "No email set"

        val avatarCard   = findViewById<androidx.cardview.widget.CardView>(R.id.avatarCard)
        val initialsView = avatarCard.getChildAt(0) as? TextView
        val fullName     = sessionManager.getFullName() ?: ""
        if (fullName.isNotBlank()) {
            val parts    = fullName.trim().split(" ")
            val initials = if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}"
            else "${parts[0].first()}"
            initialsView?.text     = initials.uppercase()
            initialsView?.textSize = 22f
        }

        profilePlan.text = "🍀 Pulser Free Plan"

        findViewById<MaterialButton>(R.id.editProfileBtn).setOnClickListener {
            startActivity(Intent(this, CreateAccActivity::class.java))
        }

        val userId = sessionManager.getUserId()
        loadCompletedGoals(userId)

        val collaboratorsCard = findViewById<androidx.cardview.widget.CardView>(R.id.collaboratorsCard)
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

        findViewById<LinearLayout>(R.id.changePasswordRow).setOnClickListener {
            showChangePasswordDialog()
        }

<<<<<<< Updated upstream
=======
        //[28] 	Firebase, "Add multi-factor authentication to your Android app," Google, 27 May 2026. [Online].
        // Available: https://firebase.google.com/docs/auth/android/multi-factor.
        // [Accessed 30 May 2026].
        val twoFactorSwitch = findViewById<Switch>(R.id.twoFactorSwitch)
        twoFactorSwitch.isChecked = settingsPrefs.getBoolean(KEY_2FA, false)
        twoFactorSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_2FA, isChecked).apply()
            val msg = if (isChecked) "Two-factor authentication enabled"
            else "Two-factor authentication disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

>>>>>>> Stashed changes
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

        val darkModeSwitch = findViewById<Switch>(R.id.darkModeSwitch)
        darkModeSwitch.isChecked = settingsPrefs.getBoolean(KEY_DARK_MODE, true)
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            settingsPrefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            Toast.makeText(this, if (isChecked) "Dark mode enabled" else "Dark mode disabled", Toast.LENGTH_SHORT).show()
        }

        // ↓ CHANGED: showCurrencyDialog() now handles rate refresh (see below)
        findViewById<LinearLayout>(R.id.currencyRow).setOnClickListener { showCurrencyDialog() }
        findViewById<LinearLayout>(R.id.languageRow).setOnClickListener {
            Toast.makeText(this, "Language: English (South Africa)", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.helpRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Help Centre")
                .setMessage("For support, contact us at:\nsupport@pulserapp.co.za\n\nCommon topics:\n• Adding transactions\n• Setting savings goals\n• Managing subscriptions")
                .setPositiveButton("OK", null).show()
        }

        findViewById<LinearLayout>(R.id.privacyRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Privacy Policy")
                .setMessage("Pulser stores your financial data securely in Firebase.\n\nWe do not share, sell, or transmit your personal data to any third parties.\n\nVersion: 1.0.0 | Last updated: April 2026")
                .setPositiveButton("OK", null).show()
        }

        findViewById<LinearLayout>(R.id.termsRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Terms of Service")
                .setMessage("By using Pulser, you agree to:\n\n• Use the app for lawful personal finance management only.\n• Not attempt to reverse-engineer or tamper with the application.\n• Accept that Pulser provides financial tracking tools and is not a licensed financial advisor.\n\nFor questions, contact support@pulserapp.co.za")
                .setPositiveButton("OK", null).show()
        }

        findViewById<LinearLayout>(R.id.signOutRow).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
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
                .setMessage("This will permanently delete your account and all associated data.\n\nThis action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    val currentUserId = sessionManager.getUserId()
                    lifecycleScope.launch {
                        if (currentUserId.isNotBlank()) {
                            userRepo.deleteUserById(currentUserId)
                            FirebaseAuth.getInstance().currentUser?.delete()
                        }
                        sessionManager.logout()
                        runOnUiThread {
                            Toast.makeText(this@SettingsActivity, "Account deleted. Goodbye!", Toast.LENGTH_LONG).show()
                            val intent = Intent(this@SettingsActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        setupBottomNav(R.id.navSettings)
    }

    private fun loadCompletedGoals(userId: String) {
        lifecycleScope.launch {
            val completedGoals: List<SavingsGoal> = savingsGoalRepo
                .getAllGoalsOnce(userId)
                .filter { it.isCompleted }
            runOnUiThread { updateRewardsCard(completedGoals) }
        }
    }

    private fun updateRewardsCard(completedGoals: List<SavingsGoal>) {
        val rewardsCard = findViewById<androidx.cardview.widget.CardView>(R.id.rewardsCard) ?: return
        val goalCard = rewardsCard.findViewById<androidx.cardview.widget.CardView>(R.id.completedGoalCarCard) ?: return

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

        if (completedGoals.isEmpty()) {
            nameView?.text   = "No completed goals yet"
            detailView?.text = "Keep saving - you're almost there!"
        } else {
            val latest = completedGoals.sortedByDescending { it.completedDate }.first()
            val emojiView = goalCard.getChildAt(0)?.let { cl ->
                (cl as? androidx.constraintlayout.widget.ConstraintLayout)
                    ?.getChildAt(0)
                    ?.let { it as? androidx.cardview.widget.CardView }
                    ?.getChildAt(0) as? TextView
            }
            emojiView?.text  = latest.icon
            nameView?.text   = latest.name
            val dateLabel = if (latest.completedDate.isNotBlank()) "Completed ${latest.completedDate}" else "Completed"
            detailView?.text = "$dateLabel · ${CurrencyManager.format(this, latest.savedAmount)} / ${CurrencyManager.format(this, latest.targetAmount)}"
        }
    }

    private fun showChangePasswordDialog() {
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 20)
        }
        val currentPwInput = android.widget.EditText(this).apply {
            hint = "Current password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val newPwInput = android.widget.EditText(this).apply {
            hint = "New password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val confirmPwInput = android.widget.EditText(this).apply {
            hint = "Confirm new password"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
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
                        val user  = FirebaseAuth.getInstance().currentUser
                        val email = sessionManager.getEmail() ?: ""
                        if (user == null || email.isBlank()) {
                            Toast.makeText(this, "Not signed in", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }
                        lifecycleScope.launch {
                            try {
                                val credential = com.google.firebase.auth.EmailAuthProvider
                                    .getCredential(email, currentPw)
                                user.reauthenticate(credential).await()
                                user.updatePassword(newPw).await()
                                runOnUiThread {
                                    Toast.makeText(this@SettingsActivity, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(this@SettingsActivity, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCurrencyDialog() {
        // Matches the order of CurrencyManager.CURRENCIES exactly
        val currencies    = CurrencyManager.CURRENCIES.map { "${it.label} (${it.code})" }.toTypedArray()
        val settingsPrefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
        val currentIndex  = settingsPrefs.getInt("currency_index", 0)

        AlertDialog.Builder(this)
            .setTitle("Select Currency")
            .setSingleChoiceItems(currencies, currentIndex) { dialog, which ->
                settingsPrefs.edit().putInt("currency_index", which).apply()

                // ↓ CHANGED: invalidate the cached rate so the next fetch
                //   retrieves a rate for the newly selected currency
                settingsPrefs.edit()
                    .remove("cached_exchange_rate")
                    .remove("cached_exchange_time")
                    .apply()

                // ↓ CHANGED: kick off a background fetch for the new rate
                lifecycleScope.launch {
                    CurrencyManager.refreshRateIfNeeded(this@SettingsActivity)
                }

                Toast.makeText(this, "Currency set to: ${currencies[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}