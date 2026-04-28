package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.viewmodel.UserViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * ForgotPasswordActivity — Handles the two-step password reset flow.
 *
 * Step 1: The user enters their registered email address. The app checks
 *         whether an account exists in the local Room database.
 * Step 2: If the email is verified, the user is prompted to enter and
 *         confirm a new password. On success, the hashed password is
 *         updated in the database and the user is redirected to login.
 */
class ForgotPasswordActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // Password validation rules — mirrors the rules used in MainActivity and
    // CreateAccActivity to enforce a consistent password policy across the app.
    // Each rule pairs a human-readable label with a lambda that tests the input.
    // -------------------------------------------------------------------------
    private val PASSWORD_RULES = listOf(
        PasswordRule("At least 8 characters")         { it.length >= 8 },
        PasswordRule("One uppercase letter (A-Z)")    { it.any { c -> c.isUpperCase() } },
        PasswordRule("One lowercase letter (a-z)")    { it.any { c -> c.isLowerCase() } },
        PasswordRule("One number (0-9)")               { it.any { c -> c.isDigit() } },
        PasswordRule("One special character (!@#\$)") { it.any { c -> !c.isLetterOrDigit() } }
    )

    /**
     * Lightweight data class that pairs a user-facing rule description with
     * the boolean predicate used to evaluate it against a password string.
     */
    data class PasswordRule(val label: String, val check: (String) -> Boolean)

    // -------------------------------------------------------------------------
    // Step tracking — controls which portion of the UI is active and which
    // handler is invoked when the primary action button is tapped.
    // -------------------------------------------------------------------------

    /** Tracks the current step in the reset flow (1 = email verification, 2 = new password). */
    private var currentStep = 1

    /** Stores the email address after it has been successfully verified in Step 1. */
    private var verifiedEmail = ""

    // -------------------------------------------------------------------------
    // View references — bound in bindViews() after setContentView().
    // -------------------------------------------------------------------------

    /** Back-navigation arrow displayed in the top-left corner. */
    private lateinit var backArrow: TextView

    /** Small label showing "STEP X OF 2" above the step title. */
    private lateinit var tvStepIndicator: TextView

    /** Descriptive heading that changes between steps. */
    private lateinit var tvStepTitle: TextView

    // --- Step 1 views ---

    /** TextInputLayout wrapper for the email field (provides floating label & error display). */
    private lateinit var tilEmail: TextInputLayout

    /** EditText where the user types their registered email address. */
    private lateinit var etEmail: TextInputEditText

    // --- Step 2 views (hidden until Step 1 is completed) ---

    /** Label above the new-password input field. */
    private lateinit var newPasswordLabel: TextView

    /** TextInputLayout wrapper for the new-password field. */
    private lateinit var tilNewPassword: TextInputLayout

    /** EditText where the user types their desired new password. */
    private lateinit var etNewPassword: TextInputEditText

    /** Label above the confirm-password input field. */
    private lateinit var confirmPasswordLabel: TextView

    /** TextInputLayout wrapper for the confirm-password field. */
    private lateinit var tilConfirmPassword: TextInputLayout

    /** EditText where the user re-enters the new password to confirm it. */
    private lateinit var etConfirmPassword: TextInputEditText

    /** Hint text listing the password requirements for the user's reference. */
    private lateinit var tvPasswordHint: TextView

    /** Primary action button; label changes from "Continue" to "Reset Password" in Step 2. */
    private lateinit var btnAction: MaterialButton

    /** ViewModel that exposes user-related database operations via the repository. */
    private lateinit var userViewModel: UserViewModel

    // -------------------------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called when the activity is first created.
     * Sets up the layout, initialises the ViewModel, binds all view references,
     * and attaches event listeners.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        // Adjust the root view's padding so content is not obscured by system bars
        // (status bar at the top, navigation bar at the bottom).
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Build the dependency chain: Database → DAO → Repository → ViewModel.
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = UserRepository(db.userDao())
        userViewModel = ViewModelProvider(this, ViewModelFactory { UserViewModel(repository) })
            .get(UserViewModel::class.java)

        bindViews()
        setupListeners()
    }

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------

    /**
     * Binds every view reference to its corresponding XML element using findViewById.
     * Kept separate from onCreate() for readability.
     */
    private fun bindViews() {
        backArrow           = findViewById(R.id.backArrow)
        tvStepIndicator     = findViewById(R.id.tvStepIndicator)
        tvStepTitle         = findViewById(R.id.tvStepTitle)

        tilEmail            = findViewById(R.id.tilEmail)
        etEmail             = findViewById(R.id.etEmail)

        newPasswordLabel    = findViewById(R.id.newPasswordLabel)
        tilNewPassword      = findViewById(R.id.tilNewPassword)
        etNewPassword       = findViewById(R.id.etNewPassword)
        confirmPasswordLabel= findViewById(R.id.confirmPasswordLabel)
        tilConfirmPassword  = findViewById(R.id.tilConfirmPassword)
        etConfirmPassword   = findViewById(R.id.etConfirmPassword)
        tvPasswordHint      = findViewById(R.id.tvPasswordHint)

        btnAction           = findViewById(R.id.btnAction)
    }

    // -------------------------------------------------------------------------
    // Listener setup
    // -------------------------------------------------------------------------

    /**
     * Attaches click and text-change listeners to interactive views.
     *
     * - Back arrow: finishes the activity and returns to the previous screen.
     * - Email field watcher: clears the inline error as soon as the user edits.
     * - New-password watcher: clears the error once the password satisfies all rules.
     * - Confirm-password watcher: clears the mismatch error on any keystroke.
     * - Action button: delegates to handleStep1() or handleStep2() based on currentStep.
     */
    private fun setupListeners() {
        // Navigate back without making any changes
        backArrow.setOnClickListener { finish() }

        // Clear the email error whenever the user modifies the field
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilEmail.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Clear the password error only once the current input satisfies all rules
        etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isPasswordValid(s?.toString() ?: "")) tilNewPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Clear the confirmation error whenever the user types in the confirm field
        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilConfirmPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Route the button tap to the correct step handler
        btnAction.setOnClickListener {
            if (currentStep == 1) handleStep1() else handleStep2()
        }
    }

    // -------------------------------------------------------------------------
    // Step 1 — Email verification
    // -------------------------------------------------------------------------

    /**
     * Validates the entered email and queries the database for a matching account.
     *
     * Validation order:
     *  1. Empty check — shows an inline error and returns early.
     *  2. Format check — verifies the string matches Android's EMAIL_ADDRESS pattern.
     *  3. Database lookup (on a coroutine) — if no user is found, shows an error;
     *     otherwise stores the verified email and advances to Step 2.
     *
     * The action button is temporarily disabled during the async lookup to prevent
     * duplicate submissions.
     */
    private fun handleStep1() {
        val email = etEmail.text?.toString()?.trim() ?: ""

        // Guard: email must be present and well-formed before hitting the database
        when {
            email.isEmpty() -> {
                tilEmail.error = "Email is required"
                return
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                tilEmail.error = "Enter a valid email address"
                return
            }
        }

        // Disable the button to avoid concurrent coroutine launches
        btnAction.isEnabled = false
        lifecycleScope.launch {
            val user = userViewModel.getUserByEmail(email)
            btnAction.isEnabled = true  // Re-enable regardless of outcome

            if (user == null) {
                // No matching account — inform the user without revealing system details
                tilEmail.error = "No account found for this email"
            } else {
                // Account found — persist the email and move to Step 2
                verifiedEmail = email
                transitionToStep2()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 2 — New password entry and persistence
    // -------------------------------------------------------------------------

    /**
     * Validates the new password and its confirmation, then updates the database.
     *
     * Validation:
     *  - New password must be non-empty and pass all PASSWORD_RULES.
     *  - Confirm password must be non-empty and identical to the new password.
     *  - Both fields are evaluated independently so the user sees all errors at once.
     *
     * On success:
     *  - The user record is fetched again from the database (defensive re-fetch).
     *  - The password is hashed with SHA-256 before being stored.
     *  - A success Toast is shown and the user is redirected to MainActivity,
     *    with the back stack cleared so they cannot navigate back to this screen.
     */
    private fun handleStep2() {
        val newPassword     = etNewPassword.text?.toString() ?: ""
        val confirmPassword = etConfirmPassword.text?.toString() ?: ""
        var valid = true

        // Validate the new password field
        when {
            newPassword.isEmpty() -> {
                tilNewPassword.error = "Password is required"
                valid = false
            }
            !isPasswordValid(newPassword) -> {
                // Build a multi-line error listing every failing rule
                tilNewPassword.error = buildPasswordError(newPassword)
                valid = false
            }
            else -> tilNewPassword.error = null
        }

        // Validate the confirmation field (evaluated separately to show both errors)
        when {
            confirmPassword.isEmpty() -> {
                tilConfirmPassword.error = "Please confirm your password"
                valid = false
            }
            newPassword != confirmPassword -> {
                tilConfirmPassword.error = "Passwords do not match"
                valid = false
            }
            else -> tilConfirmPassword.error = null
        }

        // Abort if any field failed validation
        if (!valid) return

        // Disable button to prevent duplicate submissions during the async operation
        btnAction.isEnabled = false
        lifecycleScope.launch {
            // Defensive re-fetch: the user record should still exist, but handle the
            // edge case where it may have been deleted between Step 1 and Step 2.
            val user = userViewModel.getUserByEmail(verifiedEmail)
            if (user == null) {
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Something went wrong. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
                btnAction.isEnabled = true
                return@launch
            }

            // Hash the new password and persist the updated user record
            val updatedUser = user.copy(passwordHash = sha256(newPassword))
            userViewModel.updateUser(updatedUser)

            Toast.makeText(
                this@ForgotPasswordActivity,
                "Password reset successfully! Please sign in.",
                Toast.LENGTH_LONG
            ).show()

            // Return to the login screen. FLAG_ACTIVITY_CLEAR_TOP removes all
            // activities above MainActivity in the stack, and SINGLE_TOP reuses
            // the existing MainActivity instance instead of creating a new one.
            val intent = Intent(this@ForgotPasswordActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    // -------------------------------------------------------------------------
    // UI transition
    // -------------------------------------------------------------------------

    /**
     * Advances the UI from Step 1 to Step 2 by:
     *  - Disabling the email field so the verified address cannot be changed.
     *  - Making the password-related views visible (they are GONE by default in XML).
     *  - Updating the step indicator, heading, and button label to reflect Step 2.
     */
    private fun transitionToStep2() {
        currentStep = 2

        // Lock the email field — the account has been found and should not be changed
        etEmail.isEnabled = false
        tilEmail.isEnabled = false

        // Reveal the password input fields and the requirements hint
        newPasswordLabel.visibility     = View.VISIBLE
        tilNewPassword.visibility       = View.VISIBLE
        confirmPasswordLabel.visibility = View.VISIBLE
        tilConfirmPassword.visibility   = View.VISIBLE
        tvPasswordHint.visibility       = View.VISIBLE

        // Update the step indicator, title, and button label for Step 2
        tvStepIndicator.text = "STEP 2 OF 2"
        tvStepTitle.text     = "Choose a new password"
        btnAction.text       = "Reset Password"
    }

    // -------------------------------------------------------------------------
    // Helper functions
    // -------------------------------------------------------------------------

    /**
     * Returns true only if the given password satisfies every rule in PASSWORD_RULES.
     *
     * @param password The plain-text password string to evaluate.
     */
    private fun isPasswordValid(password: String): Boolean =
        PASSWORD_RULES.all { it.check(password) }

    /**
     * Builds a multi-line error message that lists all PASSWORD_RULES the given
     * password currently fails. Used to provide specific, actionable feedback.
     *
     * @param password The plain-text password string to evaluate.
     * @return A formatted string prefixed with "Password must include:" followed
     *         by a bullet list of failing rule labels.
     */
    private fun buildPasswordError(password: String): String {
        val failing = PASSWORD_RULES
            .filterNot { it.check(password) }
            .joinToString("\n") { "- ${it.label}" }
        return "Password must include:\n$failing"
    }

    /**
     * Produces a lowercase hexadecimal SHA-256 digest of the given input string.
     * Used to hash passwords before they are stored in the database, ensuring
     * that plain-text passwords are never persisted.
     *
     * @param input The plain-text string to hash.
     * @return A 64-character lowercase hex string representing the SHA-256 digest.
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}