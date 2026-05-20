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
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// ForgotPasswordActivity -- now uses Firebase Auth's built-in password reset email.
//
// The original two-step flow (verify email → enter new password) is replaced by
// Firebase's standard flow: the user enters their email and Firebase sends a
// reset link. This is more secure and requires no manual password hashing.
//
// The Step 2 views (new password, confirm password) are kept in the layout but
// hidden -- Firebase handles that part entirely outside the app.
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var backArrow:       TextView
    private lateinit var tvStepIndicator: TextView
    private lateinit var tvStepTitle:     TextView
    private lateinit var tilEmail:        TextInputLayout
    private lateinit var etEmail:         TextInputEditText
    private lateinit var btnAction:       MaterialButton

    // Step 2 views -- kept but hidden (Firebase handles the actual reset)
    private lateinit var newPasswordLabel:     TextView
    private lateinit var tilNewPassword:       TextInputLayout
    private lateinit var etNewPassword:        TextInputEditText
    private lateinit var confirmPasswordLabel: TextView
    private lateinit var tilConfirmPassword:   TextInputLayout
    private lateinit var etConfirmPassword:    TextInputEditText
    private lateinit var tvPasswordHint:       TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // No ViewModel or Repository needed -- Firebase Auth is called directly here.

        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        backArrow           = findViewById(R.id.backArrow)
        tvStepIndicator     = findViewById(R.id.tvStepIndicator)
        tvStepTitle         = findViewById(R.id.tvStepTitle)
        tilEmail            = findViewById(R.id.tilEmail)
        etEmail             = findViewById(R.id.etEmail)
        btnAction           = findViewById(R.id.btnAction)

        // Step 2 views -- hidden because Firebase handles password reset externally
        newPasswordLabel    = findViewById(R.id.newPasswordLabel)
        tilNewPassword      = findViewById(R.id.tilNewPassword)
        etNewPassword       = findViewById(R.id.etNewPassword)
        confirmPasswordLabel= findViewById(R.id.confirmPasswordLabel)
        tilConfirmPassword  = findViewById(R.id.tilConfirmPassword)
        etConfirmPassword   = findViewById(R.id.etConfirmPassword)
        tvPasswordHint      = findViewById(R.id.tvPasswordHint)

        // Hide Step 2 views -- they are not needed with Firebase Auth reset
        newPasswordLabel.visibility     = View.GONE
        tilNewPassword.visibility       = View.GONE
        confirmPasswordLabel.visibility = View.GONE
        tilConfirmPassword.visibility   = View.GONE
        tvPasswordHint.visibility       = View.GONE

        // Update the step title to reflect the Firebase flow
        tvStepIndicator.text = "STEP 1 OF 1"
        tvStepTitle.text     = "Reset your password"
        btnAction.text       = "Send Reset Email"
    }

    private fun setupListeners() {
        backArrow.setOnClickListener { finish() }

        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilEmail.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnAction.setOnClickListener { handleSendResetEmail() }
    }

    private fun handleSendResetEmail() {
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
            try {
                // Firebase Auth sends a password reset email to the given address.
                // If no account exists for that email, Firebase still returns success
                // (by design, to avoid revealing whether an account exists).
                FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()

                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Reset email sent! Check your inbox.",
                    Toast.LENGTH_LONG
                ).show()

                // Return to the login screen after sending the reset email
                val intent = Intent(this@ForgotPasswordActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                btnAction.isEnabled = true
                tilEmail.error = "Could not send reset email. Please check the address and try again."
            }
        }
    }
}