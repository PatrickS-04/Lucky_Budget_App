package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.databinding.ActivityCreateAccBinding
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.UserViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

class CreateAccActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateAccBinding
    private lateinit var userViewModel: UserViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateAccBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init dependencies
        val db   = AppDatabase.getDatabase(this)
        val repo = UserRepository(db.userDao())
        userViewModel = ViewModelProvider(
            this,
            ViewModelFactory { UserViewModel(repo) }
        )[UserViewModel::class.java]
        sessionManager = SessionManager(this)

        setupListeners()
    }

    private fun setupListeners() {

        // Back arrow
        binding.backArrow.setOnClickListener { finish() }

        // Already have an account → go to login
        binding.signInText.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Create Account button
        binding.createAccountButton.setOnClickListener {
            val name            = binding.nameEditText.text?.toString()?.trim() ?: ""
            val email           = binding.emailEditText.text?.toString()?.trim() ?: ""
            val password        = binding.passwordEditText.text?.toString() ?: ""
            val confirmPassword = binding.confirmPasswordEditText.text?.toString() ?: ""
            val termsAccepted   = binding.termsCheckBox.isChecked

            if (!validateInputs(name, email, password, confirmPassword, termsAccepted)) return@setOnClickListener

            binding.createAccountButton.isEnabled = false

            lifecycleScope.launch {
                // Check if username (email) already exists
                val existing = userViewModel.getUserByUsername(email)

                runOnUiThread {
                    if (existing != null) {
                        binding.createAccountButton.isEnabled = true
                        binding.emailInputLayout.error = "An account with this email already exists"
                        return@runOnUiThread
                    }

                    // Proceed with registration
                    lifecycleScope.launch {
                        try {
                            val hash = MainActivity.sha256(password)
                            val newUser = User(username = email, passwordHash = hash)
                            userViewModel.register(newUser)

                            // Fetch the saved user to get the auto-generated ID
                            val savedUser = userViewModel.getUserByUsername(email)

                            runOnUiThread {
                                if (savedUser != null) {
                                    sessionManager.saveSession(savedUser.id, savedUser.username)
                                    Toast.makeText(
                                        this@CreateAccActivity,
                                        "Welcome, $name! Your account has been created.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    navigateToDashboard()
                                } else {
                                    binding.createAccountButton.isEnabled = true
                                    Toast.makeText(
                                        this@CreateAccActivity,
                                        "Registration failed. Please try again.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                binding.createAccountButton.isEnabled = true
                                Toast.makeText(
                                    this@CreateAccActivity,
                                    "Registration failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateInputs(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
        termsAccepted: Boolean
    ): Boolean {
        var valid = true

        if (name.isEmpty()) {
            binding.nameInputLayout.error = "Full name is required"
            valid = false
        } else {
            binding.nameInputLayout.error = null
        }

        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Email is required"
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Enter a valid email address"
            valid = false
        } else {
            binding.emailInputLayout.error = null
        }

        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Password is required"
            valid = false
        } else if (password.length < 6) {
            binding.passwordInputLayout.error = "Password must be at least 6 characters"
            valid = false
        } else {
            binding.passwordInputLayout.error = null
        }

        if (confirmPassword != password) {
            binding.confirmPasswordInputLayout.error = "Passwords do not match"
            valid = false
        } else {
            binding.confirmPasswordInputLayout.error = null
        }

        if (!termsAccepted) {
            Toast.makeText(this, "You must accept the Terms of Service to continue.", Toast.LENGTH_SHORT).show()
            valid = false
        }

        return valid
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashBoardActivity::class.java))
        finish()
    }
}