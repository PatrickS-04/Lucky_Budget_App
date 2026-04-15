package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreateAccActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // Password validation rules — same set as login
    // -------------------------------------------------------------------------
    private val PASSWORD_RULES = listOf(
        PasswordRule("At least 8 characters")         { it.length >= 8 },
        PasswordRule("One uppercase letter (A–Z)")    { it.any { c -> c.isUpperCase() } },
        PasswordRule("One lowercase letter (a–z)")    { it.any { c -> c.isLowerCase() } },
        PasswordRule("One number (0–9)")               { it.any { c -> c.isDigit() } },
        PasswordRule("One special character (!@#\$…)") { it.any { c -> !c.isLetterOrDigit() } }
    )

    data class PasswordRule(val label: String, val check: (String) -> Boolean)

    // -------------------------------------------------------------------------
    // Views
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_acc)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupListeners()
    }

    // -------------------------------------------------------------------------
    // Bind view references
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
    }

    // -------------------------------------------------------------------------
    // Listeners
    // -------------------------------------------------------------------------
    private fun setupListeners() {

        // Clear errors as each field is edited
        etFullName.addTextChangedListener(clearErrorWatcher(tilFullName))
        etEmail.addTextChangedListener(clearErrorWatcher(tilEmail))

        // Live password strength feedback
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pw = s?.toString() ?: ""
                tilPassword.error = if (pw.isNotEmpty() && !isPasswordValid(pw)) {
                    buildPasswordError(pw)
                } else {
                    null
                }
                // Re-check confirm field if it already has input
                val confirm = etConfirmPassword.text?.toString() ?: ""
                if (confirm.isNotEmpty()) {
                    tilConfirmPassword.error = if (pw != confirm) "Passwords do not match" else null
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Confirm password live check
        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val confirm = s?.toString() ?: ""
                val pw      = etPassword.text?.toString() ?: ""
                tilConfirmPassword.error = when {
                    confirm.isEmpty() -> null
                    confirm != pw     -> "Passwords do not match"
                    else              -> null
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Create Account button
        btnCreateAccount.setOnClickListener {
            if (validateSignUpForm()) {
                // Account creation logic goes here (e.g. Firebase auth call)
                // On success, navigate to Dashboard
                startActivity(Intent(this, DashBoardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            }
        }

        // "Already have an account? Log in" link
        tvLogin.setOnClickListener {
            finish() // pop back to MainActivity (login screen)
        }
    }

    // -------------------------------------------------------------------------
    // Form validation
    // -------------------------------------------------------------------------
    private fun validateSignUpForm(): Boolean {
        var valid = true

        val fullName  = etFullName.text?.toString()?.trim() ?: ""
        val email     = etEmail.text?.toString()?.trim() ?: ""
        val password  = etPassword.text?.toString() ?: ""
        val confirm   = etConfirmPassword.text?.toString() ?: ""

        // Full name
        if (fullName.isEmpty()) {
            tilFullName.error = "Full name is required"
            valid = false
        } else {
            tilFullName.error = null
        }

        // Email
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

        // Password strength
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

        // Confirm password
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
    // Password helpers
    // -------------------------------------------------------------------------
    private fun isPasswordValid(password: String): Boolean =
        PASSWORD_RULES.all { it.check(password) }

    private fun buildPasswordError(password: String): String {
        val failing = PASSWORD_RULES
            .filterNot { it.check(password) }
            .joinToString("\n") { "• ${it.label}" }
        return "Password must include:\n$failing"
    }

    /** Returns a TextWatcher that simply clears the error on a given layout. */
    private fun clearErrorWatcher(layout: TextInputLayout) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            layout.error = null
        }
        override fun afterTextChanged(s: Editable?) {}
    }
}
