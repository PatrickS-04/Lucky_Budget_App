package com.example.pulserds_prog7313

import android.app.Application
import android.app.Activity
import android.os.Bundle
import com.example.pulserds_prog7313.utils.SessionManager
import kotlinx.coroutines.launch
import com.example.pulserds_prog7313.utils.CurrencyManager

// ActivityLifecycleCallbacks used to track foreground/background state for auto-logout.
// [2] Google, "ViewModel overview," Android Developers, 2024.
class App : Application() {

    // Counts how many activities are currently visible/started.
    // When this drops to 0, no screen in the app is open -> user has left the app.
    private var activityCount = 0

    override fun onCreate() {
        super.onCreate()

        kotlinx.coroutines.GlobalScope.launch {
            CurrencyManager.refreshRateIfNeeded(this@App)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {

            override fun onActivityStarted(activity: Activity) {
                // Increment each time any activity becomes visible
                activityCount++
            }

            override fun onActivityStopped(activity: Activity) {
                // Decrement each time any activity is no longer visible
                activityCount--

                // If no activities remain visible, the user has fully left the app
                // (closed it, swiped it away, or the emulator was stopped).
                // This is the safe point to clear the login flag.
                if (activityCount == 0) {
                    SessionManager(applicationContext).clearLoginFlag()
                }
            }

            // -- Required interface methods -- no logic needed here ------------
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}