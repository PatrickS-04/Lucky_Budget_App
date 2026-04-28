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
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.UserViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * MainActivity is the app's entry point and serves as the login screen.
 *
 * Responsibilities:
 *  - Auto-redirect to [DashBoardActivity] if a valid session already exists.
 *  - Validate the email and password fields before attempting a DB lookup.
 *  - Hash the entered password with SHA-256 and compare it against the stored hash.
 *  - Persist the user session via [SessionManager] on successful login.
 *  - Navigate to [CreateAccActivity] (sign-up) or [ForgotPasswordActivity] as needed.
 *
 * Architecture: MVVM — the actual DB query is delegated to [UserViewModel];
 * this activity only drives UI state and reacts to the query result.
 */
class MainActivity : AppCompatActivity() {

    // Password validation rules

    /**
     * Ordered list of rules that a password must satisfy for the login form to pass
     * client-side validation. Each rule pairs a human-readable label (shown in the
     * error message) with a predicate applied to the raw password string.
     *
     * NOTE: These rules mirror the constraints enforced during registration in
     * [CreateAccActivity] so the error feedback is consistent across both screens.
     */
    private val PASSWORD_RULES = listOf(
        PasswordRule("At least 8 characters")         { it.length >= 8 },
        PasswordRule("One uppercase letter (A-Z)")    { it.any { c -> c.isUpperCase() } },
        PasswordRule("One lowercase letter (a-z)")    { it.any { c -> c.isLowerCase() } },
        PasswordRule("One number (0-9)")               { it.any { c -> c.isDigit() } },
        PasswordRule("One special character (!@#\$)") { it.any { c -> !c.isLetterOrDigit() } }
    )

    /**
     * Pairs a human-readable [label] with a [check] predicate so that failed rules
     * can be identified by name when building the inline error message.
     *
     * @property label Human-readable description shown in the password error hint.
     * @property check Predicate that returns `true` when the password satisfies this rule.
     */
    data class PasswordRule(val label: String, val check: (String) -> Boolean)

    // Views

    /** Wrapping layout for the email field — used to display inline error messages. */
    private lateinit var tilEmail: TextInputLayout

    /** Wrapping layout for the password field — used to display inline error messages. */
    private lateinit var tilPassword: TextInputLayout

    /** Raw text input for the user's email address. */
    private lateinit var etEmail: TextInputEditText

    /** Raw text input for the user's password (input is masked by the layout). */
    private lateinit var etPassword: TextInputEditText

    /** Primary action button that triggers form validation and the login flow. */
    // ViewBinding enabled in build.gradle — eliminates findViewById calls.
    // [15] Google, "View Binding," Android Developers, 2024.
    private lateinit var btnLogin: Button

    /** Tappable label that navigates to the account creation screen. */
    private lateinit var tvSignUp: TextView

    /** Tappable label that navigates to the password-reset screen. */
    private lateinit var tvForgotPassword: TextView

    // ViewModel that owns the login DB query and any related business logic.
    private lateinit var userViewModel: UserViewModel

    // Manages the persisted login session (read on launch; written on successful login).
    private lateinit var sessionManager: SessionManager

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Ensure the root layout respects system bar insets (status bar / nav bar).
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Build the ViewModel backed by a Room DAO through the repository layer.
        val db         = AppDatabase.getDatabase(applicationContext)
        val repository = UserRepository(db.userDao())
        userViewModel  = ViewModelProvider(this, ViewModelFactory { UserViewModel(repository) })
            .get(UserViewModel::class.java)

        sessionManager = SessionManager(this)

        // Skip the login UI entirely if the user is already authenticated.
        // Calling finish() prevents the user from navigating back to this screen.
        if (sessionManager.isLoggedIn()) {
            goToDashboard()
            return
        }

        bindViews()       // Wire XML view IDs to Kotlin properties.
        setupListeners()  // Attach text watchers and click handlers.
    }

    // View Binding

    /**
     * Maps each XML view ID to its corresponding Kotlin property.
     * Called once from [onCreate] after [setContentView] and the session check.
     */
    private fun bindViews() {
        tilEmail         = findViewById(R.id.tilEmail)
        tilPassword      = findViewById(R.id.tilPassword)
        etEmail          = findViewById(R.id.etEmail)
        etPassword       = findViewById(R.id.etPassword)
        btnLogin         = findViewById(R.id.btnLogin)
        tvSignUp         = findViewById(R.id.tvSignUp)
        tvForgotPassword = findViewById(R.id.forgotPasswordText)
    }

    // Listeners

    /**
     * Attaches all interactive listeners to the login form:
     *
     * - **Email TextWatcher** — clears the email error as soon as the user starts typing,
     *   so stale error messages don't linger after a correction attempt.
     * - **Password TextWatcher** — clears the password error once all rules are satisfied,
     *   providing positive real-time feedback without surfacing new errors mid-type.
     * - **Login button** — validates the form, hashes the password, and performs the DB
     *   lookup inside a [lifecycleScope] coroutine.
     * - **Sign-up label** — navigates to [CreateAccActivity].
     * - **Forgot password label** — navigates to [ForgotPasswordActivity].
     */
    private fun setupListeners() {

        // Clear the email error immediately when the user edits the field so they
        // are not discouraged from correcting a previous mistake.
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilEmail.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Clear the password error only once all rules pass, giving the user a clear
        // signal that their password now meets every requirement.
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isPasswordValid(s?.toString() ?: "")) tilPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Login button — runs client-side validation first, then queries the DB.
        btnLogin.setOnClickListener {
            if (validateLoginForm()) {
                val email    = etEmail.text?.toString()?.trim() ?: ""
                val password = etPassword.text?.toString() ?: ""

                // Hash the password before sending it to the ViewModel.
                // The stored password is also a SHA-256 hash, so a plaintext
                // comparison is never performed.
                val hash = sha256(password)

                // Run the DB lookup on the coroutine dispatcher managed by lifecycleScope.
                // The coroutine is automatically cancelled if the activity is destroyed.
                lifecycleScope.launch {
                    val user = userViewModel.login(email, hash)
                    if (user != null) {
                        // Persist the session so subsequent app launches skip login.
                        sessionManager.saveSession(user.id, user.fullName, user.email)
                        goToDashboard()
                    } else {
                        // Show both a Toast and an inline field error for clarity.
                        Toast.makeText(
                            this@MainActivity,
                            "Invalid email or password",
                            Toast.LENGTH_SHORT
                        ).show()
                        tilPassword.error = "Invalid email or password"
                    }
                }
            }
        }

        // Navigate to the account-creation flow.
        tvSignUp.setOnClickListener {
            startActivity(Intent(this, CreateAccActivity::class.java))
        }

        // Navigate to the password-reset flow.
        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    // Form Validation

    /**
     * Validates both the email and password fields before a login attempt.
     *
     * Validation rules checked (in order):
     * **Email:**
     *  1. Must not be blank.
     *  2. Must match Android's [android.util.Patterns.EMAIL_ADDRESS] pattern.
     *
     * **Password:**
     *  1. Must not be blank.
     *  2. Must satisfy all rules in [PASSWORD_RULES] (checked via [isPasswordValid]).
     *
     * Errors are shown inline via [TextInputLayout.setError]. The function collects
     * all errors rather than short-circuiting so the user sees every problem at once.
     *
     * @return `true` if both fields pass all checks; `false` otherwise.
     */
    private fun validateLoginForm(): Boolean {
        var valid = true

        val email    = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""

        // Email validation — checked sequentially; only the first failing rule is shown.
        when {
            email.isEmpty() -> {
                tilEmail.error = "Email is required"
                valid = false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                tilEmail.error = "Enter a valid email address"
                valid = false
            }
            else -> tilEmail.error = null  // Clear any previous error if the field is now valid.
        }

        // Password validation — [buildPasswordError] lists every failing rule so
        // the user knows exactly what to fix without multiple submission attempts.
        when {
            password.isEmpty() -> {
                tilPassword.error = "Password is required"
                valid = false
            }
            !isPasswordValid(password) -> {
                tilPassword.error = buildPasswordError(password)
                valid = false
            }
            else -> tilPassword.error = null  // Clear any previous error.
        }

        return valid
    }

    // Helpers

    /**
     * Navigates to [DashBoardActivity] and removes this activity from the back stack.
     *
     * [Intent.FLAG_ACTIVITY_CLEAR_TOP] and [Intent.FLAG_ACTIVITY_SINGLE_TOP] together
     * ensure that if a DashBoardActivity instance already exists in the stack it is
     * reused rather than duplicated, preventing stacked login → dashboard chains.
     */
    private fun goToDashboard() {
        startActivity(Intent(this, DashBoardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish() // Remove MainActivity from the back stack so the user cannot press back to return.
    }

    /**
     * Returns `true` if the given [password] satisfies every rule in [PASSWORD_RULES].
     *
     * @param password The raw (unhashed) password string to evaluate.
     */
    private fun isPasswordValid(password: String): Boolean =
        PASSWORD_RULES.all { it.check(password) }

    /**
     * Builds a multi-line error string listing only the [PASSWORD_RULES] that
     * the given [password] currently fails.
     *
     * Example output:
     * ```
     * Password must include:
     * - At least 8 characters
     * - One special character (!@#$)
     * ```
     *
     * @param password The raw password string to evaluate against each rule.
     * @return         A formatted error string ready to be set on [tilPassword].
     */
    private fun buildPasswordError(password: String): String {
        val failing = PASSWORD_RULES
            .filterNot { it.check(password) }  // Keep only the rules this password fails.
            .joinToString("\n") { "- ${it.label}" }
        return "Password must include:\n$failing"
    }

    /**
     * Produces a lowercase hex-encoded SHA-256 hash of the given [input] string.
     *
     * This hash is used to compare the entered password against the hash stored
     * in Room during registration — plaintext passwords are never persisted or
     * transmitted. The algorithm and encoding must match exactly what
     * [CreateAccActivity] uses when the account is first created.
     *
     * @param input The raw plaintext string to hash (typically the password field value).
     * @return      A 64-character lowercase hex string representing the SHA-256 digest.
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}