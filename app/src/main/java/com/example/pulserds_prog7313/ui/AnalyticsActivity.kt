package com.example.pulserds_prog7313.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R
import com.google.android.material.button.MaterialButton

class AnalyticsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_analytics)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // "Export" button → toast placeholder (no dedicated export screen yet)
        findViewById<MaterialButton>(R.id.exportButton)?.setOnClickListener {
            Toast.makeText(this, "Export coming soon", Toast.LENGTH_SHORT).show()
        }

        // Analytics is the current screen
        setupBottomNav(R.id.navAnalytics)
    }
}