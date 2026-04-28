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

class ForgotPasswordActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // Password validation — same rules as MainActivity / CreateAccActivity
    // -------------------------------------------------------------------------
    private val PASSWORD_RULES = listOf(
        PasswordRule("At least 8 characters")         { it.length >= 8 },
        PasswordRule("One uppercase letter (A-Z)")    { it.any { c -> c.isUpperCase() } },
        PasswordRule("One lowercase letter (a-z)")    { it.any { c -> c.isLowerCase() } },
        PasswordRule("One number (0-9)")               { it.any { c -> c.isDigit() } },
        PasswordRule("One special character (!@#\$)") { it.any { c -> !c.isLetterOrDigit() } }
    )

    data class PasswordRule(val label: String, val check: (String) -> Boolean)

    // -------------------------------------------------------------------------
    // Step tracking
    // -------------------------------------------------------------------------
    private var currentStep = 1          // 1 = verify email, 2 = set new password
    private var verifiedEmail = ""       // set after step 1 succeeds

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------
    private lateinit var backArrow: TextView
    private lateinit var tvStepIndicator: TextView
    private lateinit var tvStepTitle: TextView

    // Step 1
    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText

    // Step 2
    private lateinit var newPasswordLabel: TextView
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var confirmPasswordLabel: TextView
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tvPasswordHint: TextView

    private lateinit var btnAction: MaterialButton

    private lateinit var userViewModel: UserViewModel

    // -------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // ViewModel
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = UserRepository(db.userDao())
        userViewModel = ViewModelProvider(this, ViewModelFactory { UserViewModel(repository) })
            .get(UserViewModel::class.java)

        bindViews()
        setupListeners()
    }

    // -------------------------------------------------------------------------
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
    private fun setupListeners() {
        backArrow.setOnClickListener { finish() }

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilEmail.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isPasswordValid(s?.toString() ?: "")) tilNewPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilConfirmPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnAction.setOnClickListener {
            if (currentStep == 1) handleStep1() else handleStep2()
        }
    }

    // -------------------------------------------------------------------------
    // Step 1: verify that the email exists in the database
    // -------------------------------------------------------------------------
    private fun handleStep1() {
        val email = etEmail.text?.toString()?.trim() ?: ""

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

        btnAction.isEnabled = false
        lifecycleScope.launch {
            val user = userViewModel.getUserByEmail(email)
            btnAction.isEnabled = true

            if (user == null) {
                tilEmail.error = "No account found for this email"
            } else {
                verifiedEmail = email
                transitionToStep2()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Step 2: validate the new password and save it
    // -------------------------------------------------------------------------
    private fun handleStep2() {
        val newPassword     = etNewPassword.text?.toString() ?: ""
        val confirmPassword = etConfirmPassword.text?.toString() ?: ""
        var valid = true

        when {
            newPassword.isEmpty() -> {
                tilNewPassword.error = "Password is required"
                valid = false
            }
            !isPasswordValid(newPassword) -> {
                tilNewPassword.error = buildPasswordError(newPassword)
                valid = false
            }
            else -> tilNewPassword.error = null
        }

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

        if (!valid) return

        btnAction.isEnabled = false
        lifecycleScope.launch {
            val user = userViewModel.getUserByEmail(verifiedEmail)
            if (user == null) {
                // Shouldn't happen, but handle gracefully
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Something went wrong. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
                btnAction.isEnabled = true
                return@launch
            }

            val updatedUser = user.copy(passwordHash = sha256(newPassword))
            userViewModel.updateUser(updatedUser)

            Toast.makeText(
                this@ForgotPasswordActivity,
                "Password reset successfully! Please sign in.",
                Toast.LENGTH_LONG
            ).show()

            // Go back to login screen (clear back stack so the user must log in)
            val intent = Intent(this@ForgotPasswordActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }

    // -------------------------------------------------------------------------
    // Show step 2 UI
    // -------------------------------------------------------------------------
    private fun transitionToStep2() {
        currentStep = 2

        // Disable email field (the account is found; user shouldn't change it)
        etEmail.isEnabled = false
        tilEmail.isEnabled = false

        // Show password fields
        newPasswordLabel.visibility     = View.VISIBLE
        tilNewPassword.visibility       = View.VISIBLE
        confirmPasswordLabel.visibility = View.VISIBLE
        tilConfirmPassword.visibility   = View.VISIBLE
        tvPasswordHint.visibility       = View.VISIBLE

        // Update step indicator text
        tvStepIndicator.text = "STEP 2 OF 2"
        tvStepTitle.text     = "Choose a new password"
        btnAction.text       = "Reset Password"
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun isPasswordValid(password: String): Boolean =
        PASSWORD_RULES.all { it.check(password) }

    private fun buildPasswordError(password: String): String {
        val failing = PASSWORD_RULES
            .filterNot { it.check(password) }
            .joinToString("\n") { "- ${it.label}" }
        return "Password must include:\n$failing"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}