package com.example.pulserds_prog7313.ui

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AppDatabase
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.ui.adapters.CategoryRowAdapter
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ManageCategoriesActivity : AppCompatActivity() {

    private lateinit var categoryViewModel: CategoryViewModel
    private lateinit var defaultAdapter: CategoryRowAdapter
    private lateinit var customAdapter: CategoryRowAdapter
    private var loggedInUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_categories)

        loggedInUserId = intent.getIntExtra("USER_ID", -1)
        if (loggedInUserId == -1) {
            loggedInUserId = SessionManager(this).getUserId()
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val repo = CategoryRepository(db.categoryDao())
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(repo) as T
            }
        }
        categoryViewModel = ViewModelProvider(this, factory)[CategoryViewModel::class.java]

        // Seed default categories for this user if not yet done
        categoryViewModel.seedDefaultCategories(loggedInUserId)

        setupRecyclerViews()
        observeCategories()
        setupAddButton()

        findViewById<TextView>(R.id.backArrow).setOnClickListener { finish() }
    }

    private fun setupRecyclerViews() {
        defaultAdapter = CategoryRowAdapter(showDelete = false) { }
        customAdapter  = CategoryRowAdapter(showDelete = true) { cat ->
            categoryViewModel.deleteCategory(cat)
            Toast.makeText(this, "\"${cat.name}\" deleted", Toast.LENGTH_SHORT).show()
        }

        findViewById<RecyclerView>(R.id.defaultCategoriesRecycler).apply {
            layoutManager = LinearLayoutManager(this@ManageCategoriesActivity)
            adapter = defaultAdapter
            isNestedScrollingEnabled = false
        }

        findViewById<RecyclerView>(R.id.customCategoriesRecycler).apply {
            layoutManager = LinearLayoutManager(this@ManageCategoriesActivity)
            adapter = customAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeCategories() {
        categoryViewModel.getCategoriesLive(loggedInUserId).observe(this) { all ->
            val defaults = all.filter { it.isDefault }
            val custom   = all.filter { !it.isDefault }

            defaultAdapter.submitList(defaults)
            customAdapter.submitList(custom)

            val emptyLabel = findViewById<TextView>(R.id.emptyCustomLabel)
            emptyLabel.visibility = if (custom.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun setupAddButton() {
        val nameInput   = findViewById<TextInputEditText>(R.id.categoryNameInput)
        val typeGroup   = findViewById<RadioGroup>(R.id.categoryTypeGroup)
        val addBtn      = findViewById<MaterialButton>(R.id.addCategoryBtn)

        addBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                nameInput.error = "Please enter a category name"
                nameInput.requestFocus()
                return@setOnClickListener
            }
            if (name.length > 30) {
                nameInput.error = "Name must be 30 characters or less"
                nameInput.requestFocus()
                return@setOnClickListener
            }

            val type = when (typeGroup.checkedRadioButtonId) {
                R.id.radioIncome  -> "INCOME"
                R.id.radioBoth    -> "BOTH"
                else              -> "EXPENSE"
            }

            val newCategory = Category(
                userId    = loggedInUserId,
                name      = name,
                type      = type,
                isDefault = false
            )
            categoryViewModel.addCategory(newCategory)
            nameInput.setText("")
            Toast.makeText(this, "Category \"$name\" added!", Toast.LENGTH_SHORT).show()
        }
    }
}