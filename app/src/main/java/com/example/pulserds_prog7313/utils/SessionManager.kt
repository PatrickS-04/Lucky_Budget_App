package com.example.pulserds_prog7313.utils

import android.content.Context
import android.content.SharedPreferences

// SharedPreferences used to persist login session between app launches.
// [18] Google, "SharedPreferences -- save key-value data," Android Developers, 2024.
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    // -- Save session ----------------------------------------------------------

    // Called after a successful login - saves user data AND marks the session as active
    fun saveSession(userId: Int, fullName: String, email: String) {
        prefs.edit()
            .putInt("USER_ID", userId)
            .putString("FULL_NAME", fullName)
            .putString("EMAIL", email)
            .putBoolean("IS_LOGGED_IN", true)
            .apply()
    }

    // -- Getters ---------------------------------------------------------------
    fun getUserId(): Int = prefs.getInt("USER_ID", -1)

    fun getFullName(): String? = prefs.getString("FULL_NAME", null)

    fun getEmail(): String? = prefs.getString("EMAIL", null)

    fun isLoggedIn(): Boolean = prefs.getBoolean("IS_LOGGED_IN", false)

    // -- Logout ----------------------------------------------------------------

    // Full logout - wipes everything from SharedPreferences.
    fun logout() {
        prefs.edit().clear().apply()
    }

    // Soft logout - only clears the IS_LOGGED_IN flag.
    fun clearLoginFlag() {
        prefs.edit().putBoolean("IS_LOGGED_IN", false).apply()
    }
}