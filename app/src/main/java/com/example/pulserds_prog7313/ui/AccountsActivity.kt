package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R
import com.google.android.material.button.MaterialButton

class AccountsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_accounts)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // "Add Account" button → AddAccountActivity
        findViewById<MaterialButton>(R.id.addAccountButton)?.setOnClickListener {
            startActivity(Intent(this, AddAccountActivity::class.java))
        }

        // "See All" for Subscriptions → ManageSubscriptionsActivity
        findViewById<TextView>(R.id.subscriptionsSeeAll)?.setOnClickListener {
            startActivity(Intent(this, ManageSubscriptionsActivity::class.java))
        }

        // Accounts is the current screen
        setupBottomNav(R.id.navAccounts)
    }
}