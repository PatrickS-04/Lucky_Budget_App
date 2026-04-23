package com.example.pulserds_prog7313.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.ui.adapters.CategoryTileAdapter
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory

class DashBoardActivity : BaseActivity() {

    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var tileAdapter: CategoryTileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dash_board)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViewModel()
        setupCategoryTilesRecycler()
        observeCategories()
        loadCategoriesForCurrentUser()

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

    private fun setupViewModel() {
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = CategoryRepository(db.categoryDao())
        categoryViewModel = ViewModelProvider(
            this,
            ViewModelFactory { CategoryViewModel(repository) }
        )[CategoryViewModel::class.java]
    }

    private fun setupCategoryTilesRecycler() {
        tileAdapter = CategoryTileAdapter()
        findViewById<RecyclerView>(R.id.dashCategoryTilesRecycler).apply {
            adapter = tileAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeCategories() {
        categoryViewModel.categories.observe(this) { categories ->
            tileAdapter.submitList(categories)
        }
    }

    private fun loadCategoriesForCurrentUser() {
        val userId = SessionManager(this).getUserId()
        if (userId != -1) {
            categoryViewModel.loadCategoriesForUser(userId)
        }
    }
}