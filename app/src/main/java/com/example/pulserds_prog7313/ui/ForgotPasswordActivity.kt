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
import com.google.firebase.auth.ActionCodeSettings
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
//[27] 	Firebase, "Firebase Authentication," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth?authuser=0&_gl=1*10potxh*_ga*MTk4MDgwMzQ0MC4xNzcxODc3ODAz*_ga_CW55HF8NVT*czE3Nzk2NDE1OTIkbzExJGcxJHQxNzc5NjQyMzc2JGo1NCRsMCRoMA..#multi-factor.
// [Accessed 30 May 2026].

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
        // Normalise to lowercase so the address always matches the Firebase Auth
        // account regardless of how the user capitalised it at registration.
        val email = etEmail.text?.toString()?.trim()?.lowercase() ?: ""

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
                // ActionCodeSettings sets a continueUrl so Firebase's email template
                // includes a proper redirect — this improves deliverability with
                // institutional mail servers that block the bare firebaseapp.com links.
                //[28] 	Firebase, "Add multi-factor authentication to your Android app," Google, 27 May 2026. [Online].
                // Available: https://firebase.google.com/docs/auth/android/multi-factor.
                // [Accessed 30 May 2026].
                val actionCodeSettings = ActionCodeSettings.newBuilder()
                    .setUrl("https://prog7313-poe-g2-pulser-ds.firebaseapp.com")
                    .setHandleCodeInApp(false)
                    .setAndroidPackageName(
                        "com.example.pulserds_prog7313",
                        /* installIfNotAvailable = */ true,
                        /* minimumVersion = */ null
                    )
                    .build()

                FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(email, actionCodeSettings)
                    .await()

                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Reset email sent! Check your inbox — if you don't see it, check your Spam or Junk folder.",
                    Toast.LENGTH_LONG
                ).show()

                // Return to the login screen after sending the reset email
                val intent = Intent(this@ForgotPasswordActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                android.util.Log.e("ForgotPassword", "Reset email failed: ${e.message}", e)
                btnAction.isEnabled = true
                val msg = when {
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "No internet connection. Please check your network and try again."
                    e.message?.contains("blocked", ignoreCase = true) == true ||
                    e.message?.contains("too-many-requests", ignoreCase = true) == true ->
                        "Too many requests. Please wait a few minutes and try again."
                    e.message?.contains("invalid-email", ignoreCase = true) == true ->
                        "Invalid email address. Please check and try again."
                    else ->
                        "Could not send reset email. Please check the address and try again."
                }
                tilEmail.error = msg
                Toast.makeText(this@ForgotPasswordActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}