package com.example.pulserds_prog7313.utils

import android.content.Context
import android.content.SharedPreferences

// SharedPreferences used to persist login session between app launches.
// userId is now a String (Firebase Auth UID) instead of an Int.
//[18] Google, "SharedPreferences — save key-value data," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/data-storage/shared-preferences.
// [Accessed: 28 Apr. 2026].
//[26] 	Firebase, "Firebase Authentication," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth?authuser=0&_gl=1*10potxh*_ga*MTk4MDgwMzQ0MC4xNzcxODc3ODAz*_ga_CW55HF8NVT*czE3Nzk2NDE1OTIkbzExJGcxJHQxNzc5NjQyMzc2JGo1NCRsMCRoMA..#multi-factor.
// [Accessed 30 May 2026].
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    // -- Save session ----------------------------------------------------------

    // Called after a successful login or registration.
    // userId is the Firebase Auth UID (e.g. "abc123xyz").
    fun saveSession(userId: String, fullName: String, email: String) {
        prefs.edit()
            .putString("USER_ID", userId)       // String, not Int
            .putString("FULL_NAME", fullName)
            .putString("EMAIL", email)
            .putBoolean("IS_LOGGED_IN", true)
            .apply()
    }

    // -- Getters ---------------------------------------------------------------

    // Returns the Firebase Auth UID, or "" if no session exists.
    fun getUserId(): String = prefs.getString("USER_ID", "") ?: ""

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