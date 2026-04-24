package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.utils.SessionManager
import com.google.android.material.button.MaterialButton

class TransactionsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_transactions)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // "Add Transaction" button → AddTransactionActivity
        findViewById<MaterialButton>(R.id.addTransactionButton)?.setOnClickListener {
            val userId = SessionManager(this).getUserId()
            val intent = Intent(this, AddTransactionActivity::class.java)
                .putExtra("USER_ID", userId)
            startActivity(intent)
        }

        // Transactions is the current screen
        setupBottomNav(R.id.navTransactions)
    }
}