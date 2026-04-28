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
import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.UserViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.security.MessageDigest

class CreateAccActivity : AppCompatActivity() {

    // -------------------------------------------------------------------------
    // Password validation rules
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
    private lateinit var backArrow: TextView
    private lateinit var signInText: TextView
    private lateinit var termsCheckBox: android.widget.CheckBox

    private lateinit var userViewModel: UserViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_acc)

        val backArrowButton = findViewById<TextView>(R.id.backArrow)
        backArrowButton.setOnClickListener {
            finish()
        }

        val signInButton = findViewById<TextView>(R.id.signInText)
        signInButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = UserRepository(db.userDao())
        userViewModel = ViewModelProvider(this, ViewModelFactory { UserViewModel(repository) })
            .get(UserViewModel::class.java)

        sessionManager = SessionManager(this)

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
        backArrow          = findViewById(R.id.backArrow)
        signInText         = findViewById(R.id.signInText)
        termsCheckBox      = findViewById(R.id.termsCheckBox)
    }

    // -------------------------------------------------------------------------
    // Listeners
    // -------------------------------------------------------------------------
    private fun setupListeners() {

        // Back arrow — navigate back to login screen
        backArrow.setOnClickListener {
            finish()
        }

        etFullName.addTextChangedListener(clearErrorWatcher(tilFullName))
        etEmail.addTextChangedListener(clearErrorWatcher(tilEmail))

        // Live password strength feedback
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pw = s?.toString() ?: ""
                tilPassword.error = if (pw.isNotEmpty() && !isPasswordValid(pw)) {
                    buildPasswordError(pw)
                } else null
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

        // Create Account button - validate, save to DB, then navigate
        btnCreateAccount.setOnClickListener {
            if (!termsCheckBox.isChecked) {
                Toast.makeText(this, "Please agree to the Terms of Service to continue", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (validateSignUpForm()) {
                val fullName = etFullName.text?.toString()?.trim() ?: ""
                val email    = etEmail.text?.toString()?.trim() ?: ""
                val password = etPassword.text?.toString() ?: ""
                val hash     = sha256(password)

                lifecycleScope.launch {
                    // Check if email is already registered
                    val existing = userViewModel.getUserByEmail(email)
                    if (existing != null) {
                        tilEmail.error = "An account with this email already exists"
                        return@launch
                    }

                    // Insert the new user and wait for it to complete
                    val newUser = User(fullName = fullName, email = email, passwordHash = hash)
                    userViewModel.register(newUser)

                    // the DB insert is
                    // guaranteed to be done before this line runs, so getUserByEmail
                    // will always find the row and return a valid userId — preventing
                    // the FOREIGN KEY crash when DashBoardActivity seeds categories.
                    val savedUser = userViewModel.getUserByEmail(email)
                    if (savedUser == null) {
                        Toast.makeText(
                            this@CreateAccActivity,
                            "Registration failed. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    // Session is fully saved before we navigate, so DashBoardActivity
                    // reads a valid userId the moment it calls seedDefaultCategories()
                    sessionManager.saveSession(savedUser.id, savedUser.fullName, savedUser.email)

                    Toast.makeText(
                        this@CreateAccActivity,
                        "Account created successfully!",
                        Toast.LENGTH_SHORT
                    ).show()

                    startActivity(Intent(this@CreateAccActivity, DashBoardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            }
        }

        // "Already have an account? Log in" link
        tvLogin.setOnClickListener {
            finish()
        }

        // "Sign in" bold link next to tvLogin — same action
        signInText.setOnClickListener {
            finish()
        }
    }

    // -------------------------------------------------------------------------
    // Form validation
    // -------------------------------------------------------------------------
    private fun validateSignUpForm(): Boolean {
        var valid = true

        val fullName = etFullName.text?.toString()?.trim() ?: ""
        val email    = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""
        val confirm  = etConfirmPassword.text?.toString() ?: ""

        if (fullName.isEmpty()) {
            tilFullName.error = "Full name is required"
            valid = false
        } else {
            tilFullName.error = null
        }

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
    private fun isPasswordValid(password: String): Boolean =
        PASSWORD_RULES.all { it.check(password) }

    private fun buildPasswordError(password: String): String {
        val failing = PASSWORD_RULES
            .filterNot { it.check(password) }
            .joinToString("\n") { "- ${it.label}" }
        return "Password must include:\n$failing"
    }

    private fun clearErrorWatcher(layout: TextInputLayout) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            layout.error = null
        }
        override fun afterTextChanged(s: Editable?) {}
    }

    /** SHA-256 hash - same algorithm used on the login screen. */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}