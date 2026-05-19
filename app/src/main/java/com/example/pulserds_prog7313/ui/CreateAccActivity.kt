package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.UserViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * CreateAccActivity handles the user registration screen.
 *
 * Responsibilities:
 * - Collects user input: full name, email, password, and confirm password.
 * - Validates all fields live (as the user types) and on form submission.
 * - Enforces password strength rules defined in [PASSWORD_RULES].
 * - Hashes the password with SHA-256 before persisting it to the database.
 * - Checks for duplicate email addresses before inserting a new user.
 * - Saves the authenticated session via [SessionManager] upon successful registration.
 * - Navigates to [DashBoardActivity] after a successful account creation.
 */
class CreateAccActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // Password validation rules
    // Each PasswordRule pairs a human-readable label with a predicate (lambda)
    // that returns true when the rule is satisfied for a given password string.
    // -------------------------------------------------------------------------
    private val PASSWORD_RULES = listOf(
        PasswordRule("At least 8 characters")         { it.length >= 8 },
        PasswordRule("One uppercase letter (A-Z)")    { it.any { c -> c.isUpperCase() } },
        PasswordRule("One lowercase letter (a-z)")    { it.any { c -> c.isLowerCase() } },
        PasswordRule("One number (0-9)")               { it.any { c -> c.isDigit() } },
        PasswordRule("One special character (!@#\$)") { it.any { c -> !c.isLetterOrDigit() } }
    )

    /**
     * Represents a single password validation rule.
     *
     * @property label Human-readable description shown to the user when the rule fails.
     * @property check Predicate that returns true when the password satisfies this rule.
     */
    data class PasswordRule(val label: String, val check: (String) -> Boolean)

    // -------------------------------------------------------------------------
    // Views — declared here so they are accessible across all methods in the
    // activity without needing to call findViewById() repeatedly.
    // -------------------------------------------------------------------------
    private lateinit var tilFullName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnCreateAccount: Button
    private lateinit var tvLogin: TextView
    private lateinit var backArrow: TextView
    private lateinit var signInText: TextView
    private lateinit var termsCheckBox: android.widget.CheckBox

    /** ViewModel that exposes suspend functions for user-related DB operations. */
    private lateinit var userViewModel: UserViewModel

    /** Manages saving and clearing the logged-in user's session in SharedPreferences. */
    private lateinit var sessionManager: SessionManager

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Extend content behind system bars for immersive UI.
        setContentView(R.layout.activity_create_acc)

        // Navigate back to the previous screen (login) when the back arrow is tapped.
        val backArrowButton = findViewById<TextView>(R.id.backArrow)
        backArrowButton.setOnClickListener {
            finish()
        }

        // Navigate to the login screen when the "Sign in" text link is tapped.
        val signInButton = findViewById<TextView>(R.id.signInText)
        signInButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // Adjust the root layout's padding so content is not obscured by system bars
        // (status bar, navigation bar) on edge-to-edge displays.
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

        sessionManager = SessionManager(this)

        bindViews()      // Resolve all view references from the layout.
        setupListeners() // Attach click and text-change listeners.
    }

    // -------------------------------------------------------------------------
    // Bind view references
    // Centralises all findViewById calls so onCreate stays clean and readable.
    // -------------------------------------------------------------------------
    private fun bindViews() {
        tilFullName        = findViewById(R.id.tilFullName)
        tilEmail           = findViewById(R.id.tilEmail)
        tilPassword        = findViewById(R.id.tilPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        etFullName         = findViewById(R.id.etFullName)
        etEmail            = findViewById(R.id.etEmail)
        etPassword         = findViewById(R.id.etPassword)
        etConfirmPassword  = findViewById(R.id.etConfirmPassword)
        btnCreateAccount   = findViewById(R.id.btnCreateAccount)
        tvLogin            = findViewById(R.id.tvLogin)
        backArrow          = findViewById(R.id.backArrow)
        signInText         = findViewById(R.id.signInText)
        termsCheckBox      = findViewById(R.id.termsCheckBox)
    }

    // -------------------------------------------------------------------------
    // Listeners
    // -------------------------------------------------------------------------
    private fun setupListeners() {

        // Back arrow — navigate back to the login screen by finishing this activity.
        backArrow.setOnClickListener {
            finish()
        }

        // Clear any existing error on the Full Name and Email fields as the user types,
        // so stale error messages don't persist after the user starts correcting input.
        etFullName.addTextChangedListener(clearErrorWatcher(tilFullName))
        etEmail.addTextChangedListener(clearErrorWatcher(tilEmail))

        // Live password strength feedback:
        // Show which rules are failing while the user is still typing the password.
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pw = s?.toString() ?: ""

                // Show a detailed error listing every failing rule, or clear the error
                // when the password field is empty or fully valid.
                tilPassword.error = if (pw.isNotEmpty() && !isPasswordValid(pw)) {
                    buildPasswordError(pw)
                } else null

                // Keep the confirm-password error in sync if the user edits the
                // primary password field after already filling in the confirm field.
                val confirm = etConfirmPassword.text?.toString() ?: ""
                if (confirm.isNotEmpty()) {
                    tilConfirmPassword.error = if (pw != confirm) "Passwords do not match" else null
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Confirm password live check:
        // Immediately inform the user if the two passwords no longer match.
        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val confirm = s?.toString() ?: ""
                val pw      = etPassword.text?.toString() ?: ""
                tilConfirmPassword.error = when {
                    confirm.isEmpty() -> null               // Don't nag on an empty field.
                    confirm != pw     -> "Passwords do not match"
                    else              -> null               // Passwords match — no error.
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Create Account button:
        // 1. Guard behind Terms of Service acceptance.
        // 2. Run full form validation.
        // 3. Hash the password and persist the new user to the local Room database.
        // 4. Save the session so downstream screens can read the current userId.
        // 5. Navigate to the Dashboard.
        btnCreateAccount.setOnClickListener {
            // Block registration if the user hasn't agreed to the Terms of Service.
            if (!termsCheckBox.isChecked) {
                Toast.makeText(this, "Please agree to the Terms of Service to continue", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (validateSignUpForm()) {
                val fullName = etFullName.text?.toString()?.trim() ?: ""
                val email    = etEmail.text?.toString()?.trim() ?: ""
                val password = etPassword.text?.toString() ?: ""
                val hash     = sha256(password) // Never store plain-text passwords.

                // All Room operations must run off the main thread; lifecycleScope
                // ties the coroutine to this Activity's lifecycle automatically.
                lifecycleScope.launch {
                    // Prevent duplicate accounts for the same email address.
                    val existing = userViewModel.getUserByEmail(email)
                    if (existing != null) {
                        tilEmail.error = "An account with this email already exists"
                        return@launch
                    }

                    // Insert the new user and wait for it to complete.
                    val newUser = User(fullName = fullName, email = email, passwordHash = hash)
                    userViewModel.register(newUser)

                    // Re-fetch the user after insert so we have the auto-generated
                    // primary key (userId). This also acts as an insert confirmation:
                    // if the row is missing, registration failed for some reason.
                    // NOTE: The DB insert is guaranteed complete before this line
                    // runs, so getUserByEmail will always return a valid userId —
                    // preventing a FOREIGN KEY crash when DashBoardActivity seeds
                    // default categories.
                    val savedUser = userViewModel.getUserByEmail(email)
                    if (savedUser == null) {
                        Toast.makeText(
                            this@CreateAccActivity,
                            "Registration failed. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    // Persist the session before navigating so DashBoardActivity
                    // immediately reads a valid userId when it calls seedDefaultCategories().
                    sessionManager.saveSession(savedUser.id, savedUser.fullName, savedUser.email)

                    Toast.makeText(
                        this@CreateAccActivity,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Navigate to the Dashboard and clear the back-stack so the
                    // user cannot press Back to return to the registration screen.
                    startActivity(Intent(this@CreateAccActivity, DashBoardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            }
        }

        // "Already have an account? Log in" link — go back to the login screen.
        tvLogin.setOnClickListener {
            finish()
        }

        // "Sign in" bold link next to tvLogin — same navigation action.
        signInText.setOnClickListener {
            finish()
        }
    }

    // -------------------------------------------------------------------------
    // Form validation
    // Checks all fields for completeness and correctness.
    // Returns true only when every field passes its respective check.
    // -------------------------------------------------------------------------
    private fun validateSignUpForm(): Boolean {
        var valid = true

        val fullName = etFullName.text?.toString()?.trim() ?: ""
        val email    = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        val confirm  = etConfirmPassword.text?.toString() ?: ""

        // Full name must not be blank.
        if (fullName.isEmpty()) {
            tilFullName.error = "Full name is required"
            valid = false
        } else {
            tilFullName.error = null
        }

        // Email must be present and match Android's built-in email pattern.
        when {
            email.isEmpty() -> {
                tilEmail.error = "Email is required"
                valid = false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                tilEmail.error = "Enter a valid email address"
                valid = false
            }
            else -> tilEmail.error = null
        }

        // Password must be present and satisfy all PASSWORD_RULES.
        when {
            password.isEmpty() -> {
                tilPassword.error = "Password is required"
                valid = false
            }
            !isPasswordValid(password) -> {
                tilPassword.error = buildPasswordError(password)
                valid = false
            }
            else -> tilPassword.error = null
        }

        // Confirm password must be present and identical to the primary password.
        when {
            confirm.isEmpty() -> {
                tilConfirmPassword.error = "Please confirm your password"
                valid = false
            }
            confirm != password -> {
                tilConfirmPassword.error = "Passwords do not match"
                valid = false
            }
            else -> tilConfirmPassword.error = null
        }

        return valid
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if [password] satisfies every rule in [PASSWORD_RULES].
     */
    private fun isPasswordValid(password: String): Boolean =
        PASSWORD_RULES.all { it.check(password) }

    /**
     * Builds a multi-line error string listing every [PASSWORD_RULES] entry
     * that [password] currently fails, prefixed with a bullet dash.
     */
    private fun buildPasswordError(password: String): String {
        val failing = PASSWORD_RULES
            .filterNot { it.check(password) }
            .joinToString("\n") { "- ${it.label}" }
        return "Password must include:\n$failing"
    }

    /**
     * Returns a [TextWatcher] that clears the error on [layout] as soon as the
     * user starts typing. Avoids stale error messages after input is corrected.
     */
    private fun clearErrorWatcher(layout: TextInputLayout) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            layout.error = null
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    /**
     * Computes the SHA-256 hash of [input] and returns it as a lowercase hex string.
     *
     * SHA-256 is a one-way function - the plain-text password is never stored.
     * The same algorithm must be used on the login screen when comparing credentials.
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}