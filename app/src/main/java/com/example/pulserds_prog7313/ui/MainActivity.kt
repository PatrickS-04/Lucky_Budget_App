package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.repository.UserRepository
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.UserViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val PASSWORD_RULES = listOf(
        PasswordRule("At least 8 characters")         { it.length >= 8 },
        PasswordRule("One uppercase letter (A-Z)")    { it.any { c -> c.isUpperCase() } },
        PasswordRule("One lowercase letter (a-z)")    { it.any { c -> c.isLowerCase() } },
        PasswordRule("One number (0-9)")               { it.any { c -> c.isDigit() } },
        PasswordRule("One special character (!@#\$)") { it.any { c -> !c.isLetterOrDigit() } }
    )

    data class PasswordRule(val label: String, val check: (String) -> Boolean)

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnGoogleSignIn: MaterialButton  // maps to R.id.googleSignInButton
    private lateinit var tvSignUp: TextView
    private lateinit var tvForgotPassword: TextView
    private lateinit var userViewModel: UserViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val repository = UserRepository()
        userViewModel = ViewModelProvider(this, ViewModelFactory { UserViewModel(repository) })
            .get(UserViewModel::class.java)

        sessionManager = SessionManager(this)
        credentialManager = CredentialManager.create(this)

        if (sessionManager.isLoggedIn()) {
            goToDashboard()
            return
        }

        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        tilEmail         = findViewById(R.id.tilEmail)
        tilPassword      = findViewById(R.id.tilPassword)
        etEmail          = findViewById(R.id.etEmail)
        etPassword       = findViewById(R.id.etPassword)
        btnLogin         = findViewById(R.id.btnLogin)
        btnGoogleSignIn  = findViewById(R.id.googleSignInButton)  // matches your XML id
        tvSignUp         = findViewById(R.id.tvSignUp)
        tvForgotPassword = findViewById(R.id.forgotPasswordText)
    }

    private fun setupListeners() {
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tilEmail.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isPasswordValid(s?.toString() ?: "")) tilPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnLogin.setOnClickListener {
            if (validateLoginForm()) {
                val email    = etEmail.text?.toString()?.trim() ?: ""
                val password = etPassword.text?.toString() ?: ""

                lifecycleScope.launch {
                    val user = userViewModel.login(email, password)
                    if (user != null) {
                        sessionManager.saveSession(user.id, user.fullName, user.email)
                        goToDashboard()
                    } else {
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

        btnGoogleSignIn.setOnClickListener {
            launchGoogleSignIn()
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, CreateAccActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun launchGoogleSignIn() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId("1094146633756-bki1015ktnhnafcjberjc2n5hgabijte.apps.googleusercontent.com")
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity
                )
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                Log.e("GoogleSignIn", "GetCred@entialException", e)
                Toast.makeText(
                    this@MainActivity,
                    "Google sign-in failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun handleGoogleCredential(credential: androidx.credentials.Credential) {
        if (credential is CustomCredential &&
            credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCredential.idToken

            val user = userViewModel.loginWithGoogle(idToken)
            if (user != null) {
                sessionManager.saveSession(user.id, user.fullName, user.email)
                goToDashboard()
            } else {
                Toast.makeText(
                    this,
                    "Google sign-in failed. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Log.w("GoogleSignIn", "Credential is not of type Google ID!")
            Toast.makeText(this, "Unexpected credential type.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateLoginForm(): Boolean {
        var valid = true
        val email    = etEmail.text?.toString()?.trim() ?: ""
        val password = etPassword.text?.toString() ?: ""

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

        return valid
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashBoardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    private fun isPasswordValid(password: String): Boolean =
        PASSWORD_RULES.all { it.check(password) }

    private fun buildPasswordError(password: String): String {
        val failing = PASSWORD_RULES
            .filterNot { it.check(password) }
            .joinToString("\n") { "- ${it.label}" }
        return "Password must include:\n$failing"
    }
}