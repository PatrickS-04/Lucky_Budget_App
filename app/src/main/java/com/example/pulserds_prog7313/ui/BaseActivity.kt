package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.pulserds_prog7313.R

/**
 * BaseActivity wires up the bottom navigation bar for every screen.
 * All activities that have a bottom nav should extend this instead of AppCompatActivity.
 *
 * Each layout must have these IDs on the five nav LinearLayouts:
 *   navHome, navAccounts, navTransactions, navAnalytics, navSettings
 */
//[9] Google, AndroidX Core KTX, ver. 1.13.1. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/core.
//[10] Google, AndroidX AppCompat, ver. 1.7.0. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/appcompat.

abstract class BaseActivity : AppCompatActivity() {

    /**
     * Call this after setContentView() in each activity's onCreate().
     * Pass the ID of the nav item that represents the current screen —
     * that one will be skipped (already here, no need to navigate).
     */
    protected fun setupBottomNav(currentNavId: Int) {
        val navItems = mapOf(
            R.id.navHome         to DashBoardActivity::class.java,
            R.id.navAccounts     to AccountsActivity::class.java,
            R.id.navTransactions to TransactionsActivity::class.java,
            R.id.navAnalytics    to AnalyticsActivity::class.java,
            R.id.navSettings     to SettingsActivity::class.java
        )

        for ((viewId, activityClass) in navItems) {
            val navView = findViewById<LinearLayout>(viewId) ?: continue
            if (viewId == currentNavId) {
                // Already on this screen — no click needed
                continue
            }
            navView.setOnClickListener {
                val intent = Intent(this, activityClass)
                // Clear the back stack so pressing Back doesn't loop through nav history
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(intent)
            }
        }
    }
}
