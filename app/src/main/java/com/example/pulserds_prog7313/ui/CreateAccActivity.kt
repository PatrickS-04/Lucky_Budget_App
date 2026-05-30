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
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.UserViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import kotlinx.coroutines.launch

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
//[11] Google, Material Design Components for Android, ver. 1.12.0. [Software]. Google, 2024.
// Available: https://github.com/material-components/material-components-android.
//[15] Google, "View Binding," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/view-binding.
// [Accessed: 28 Apr. 2026].
//[24] 	Firebase, "Authenticate with Google on Android," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth/android/google-signin.
// [Accessed 30 May 2026].
//[27] 	Firebase, "Firebase Authentication," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth?authuser=0&_gl=1*10potxh*_ga*MTk4MDgwMzQ0MC4xNzcxODc3ODAz*_ga_CW55HF8NVT*czE3Nzk2NDE1OTIkbzExJGcxJHQxNzc5NjQyMzc2JGo1NCRsMCRoMA..#multi-factor.
// [Accessed 30 May 2026].

class CreateAccActivity : AppCompatActivity() {

    private val PASSWORD_RULES = listOf(
        PasswordRule("At least 8 characters")         { it.length >= 8 },
        PasswordRule("One uppercase letter (A-Z)")    { it.any { c -> c.isUpperCase() } },
        PasswordRule("One lowercase letter (a-z)")    { it.any { c -> c.isLowerCase() } },
        PasswordRule("One number (0-9)")               { it.any { c -> c.isDigit() } },
        PasswordRule("One special character (!@#\$)") { it.any { c -> !c.isLetterOrDigit() } }
    )

    data class PasswordRule(val label: String, val check: (String) -> Boolean)

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
        backArrowButton.setOnClickListener { finish() }

        val signInButton = findViewById<TextView>(R.id.signInText)
        signInButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Firebase-backed repository -- no database instance needed
        val repository = UserRepository()
        userViewModel  = ViewModelProvider(this, ViewModelFactory { UserViewModel(repository) })
            .get(UserViewModel::class.java)

        sessionManager = SessionManager(this)

        bindViews()
        setupListeners()
    }

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

    private fun setupListeners() {
        backArrow.setOnClickListener { finish() }

        etFullName.addTextChangedListener(clearErrorWatcher(tilFullName))
        etEmail.addTextChangedListener(clearErrorWatcher(tilEmail))

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

        btnCreateAccount.setOnClickListener {
            if (!termsCheckBox.isChecked) {
                Toast.makeText(this, "Please agree to the Terms of Service to continue", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (validateSignUpForm()) {
                val fullName = etFullName.text?.toString()?.trim() ?: ""
                val email    = etEmail.text?.toString()?.trim() ?: ""
                val password = etPassword.text?.toString() ?: ""

                // Pass plain-text password directly to Firebase Auth via the ViewModel.
                // No manual SHA-256 hashing -- Firebase Auth handles credential security.
                lifecycleScope.launch {
                    try {
                        val savedUser = userViewModel.register(email, password, fullName)
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
                    } catch (e: FirebaseAuthUserCollisionException) {
                        tilEmail.error = "An account with this email already exists"
                    } catch (e: FirebaseAuthWeakPasswordException) {
                        tilPassword.error = e.reason ?: "Password is too weak"
                    } catch (e: FirebaseAuthInvalidCredentialsException) {
                        tilEmail.error = "Invalid email address"
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@CreateAccActivity,
                            "Registration failed: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        tvLogin.setOnClickListener { finish() }
        signInText.setOnClickListener { finish() }
    }

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
}