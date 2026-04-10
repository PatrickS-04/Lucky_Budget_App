package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.databinding.ActivityMainBinding
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.UserViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userViewModel: UserViewModel
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init dependencies
        val db = AppDatabase.getDatabase(this)
        val repo = UserRepository(db.userDao())
        userViewModel = ViewModelProvider(
            this,
            ViewModelFactory { UserViewModel(repo) }
        )[UserViewModel::class.java]
        sessionManager = SessionManager(this)

        // If already logged in, skip straight to the dashboard
        if (sessionManager.isLoggedIn()) {
            navigateToDashboard()
            return
        }

        setupListeners()
    }

    private fun setupListeners() {

        // Sign In button
        binding.loginButton.setOnClickListener {
            val email    = binding.emailEditText.text?.toString()?.trim() ?: ""
            val password = binding.passwordEditText.text?.toString() ?: ""

            if (!validateLoginInputs(email, password)) return@setOnClickListener

            binding.loginButton.isEnabled = false

            lifecycleScope.launch {
                val hash = sha256(password)
                val user = userViewModel.login(email, hash)

                runOnUiThread {
                    binding.loginButton.isEnabled = true
                    if (user != null) {
                        sessionManager.saveSession(user.id, user.username)
                        navigateToDashboard()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Invalid email or password. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // Navigate to Create Account screen
        binding.signUpText.setOnClickListener {
            startActivity(Intent(this, CreateAccActivity::class.java))
        }

        // Forgot Password (placeholder – extend as needed)
        binding.forgotPasswordText.setOnClickListener {
            Toast.makeText(this, "Password reset coming soon.", Toast.LENGTH_SHORT).show()
        }

        // Google Sign-In (placeholder – wire up Google Auth SDK as needed)
        binding.googleSignInButton.setOnClickListener {
            Toast.makeText(this, "Google Sign-In coming soon.", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateLoginInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "Email is required"
            return false
        }
        binding.emailInputLayout.error = null

        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Password is required"
            return false
        }
        binding.passwordInputLayout.error = null

        return true
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun navigateToDashboard() {
        startActivity(Intent(this, DashBoardActivity::class.java))
        finish()
    }

    companion object {
        /** SHA-256 hash helper – matches the hash stored at registration. */
        fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}