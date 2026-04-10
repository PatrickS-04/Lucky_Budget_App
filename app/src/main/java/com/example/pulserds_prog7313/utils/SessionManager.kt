package com.example.pulserds_prog7313.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    // Called after a successful login — saves user data AND marks the session as active
    fun saveSession(userId: Int, username: String) {
        prefs.edit()
            .putInt("USER_ID", userId)
            .putString("USERNAME", username)
            .putBoolean("IS_LOGGED_IN", true)  // Session is now active
            .apply()
    }

    fun getUserId(): Int {
        return prefs.getInt("USER_ID", -1)
    }

    fun getUsername(): String? {
        return prefs.getString("USERNAME", null)
    }

    // Now checks the IS_LOGGED_IN flag instead of USER_ID
    // This means the session is considered invalid the moment clearLoginFlag() is called,
    // even though USER_ID and USERNAME are still stored in prefs.
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean("IS_LOGGED_IN", false)
    }

    // Full logout — called when the user explicitly taps "Log Out" in the app.
    // Wipes everything from SharedPreferences.
    fun logout() {
        prefs.edit().clear().apply()
    }

    // Soft logout — called automatically when the app is closed.
    // Only clears the IS_LOGGED_IN flag, leaving USER_ID and USERNAME intact.
    // This means the next launch forces a re-login, but you could later use
    // the stored data for a "Welcome back, [name]" hint on the login screen.
    fun clearLoginFlag() {
        prefs.edit().putBoolean("IS_LOGGED_IN", false).apply()
    }
}