package com.example.pulserds_prog7313.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_session", Context.MODE_PRIVATE)

    fun saveSession(userId: Int, username: String) {
        prefs.edit()
            .putInt("USER_ID", userId)
            .putString("USERNAME", username)
            .apply()
    }

    fun getUserId(): Int {
        return prefs.getInt("USER_ID", -1)
    }

    fun getUsername(): String? {
        return prefs.getString("USERNAME", null)
    }

    fun isLoggedIn(): Boolean {
        return getUserId() != -1
    }

    fun logout() {
        prefs.edit().clear().apply()
    }
}