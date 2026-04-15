package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.pulserds_prog7313.R

class DashBoardActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dash_board)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // "See All" for Savings Goals → SavingsGoalsActivity
        findViewById<TextView>(R.id.savingsSeeAll)?.setOnClickListener {
            startActivity(Intent(this, SavingsGoalsActivity::class.java))
        }

        // "See All" for Transactions → TransactionsActivity
        findViewById<TextView>(R.id.transactionsSeeAll)?.setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java))
        }

        // Home is the current screen
        setupBottomNav(R.id.navHome)
    }
}