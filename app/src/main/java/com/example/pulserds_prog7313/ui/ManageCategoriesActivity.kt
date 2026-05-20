package com.example.pulserds_prog7313.ui

import android.os.Bundle
import android.view.View
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.repository.CategoryRepository
import com.example.pulserds_prog7313.ui.adapters.CategoryRowAdapter
import com.example.pulserds_prog7313.utils.SessionManager
import com.example.pulserds_prog7313.viewmodel.CategoryViewModel
import com.example.pulserds_prog7313.viewmodel.ViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/**
 * ManageCategoriesActivity allows the logged-in user to view, add, and delete
 * spending/income categories.
 *
 * Categories are split into two groups:
 *  - **Default categories** - seeded automatically for every new user; cannot be deleted.
 *  - **Custom categories**  - created by the user; can be deleted individually.
 *
 * Architecture: MVVM - all data operations are delegated to [CategoryViewModel];
 * the activity only observes LiveData and updates the UI in response.
 */
class ManageCategoriesActivity : AppCompatActivity() {

    // ViewModel that owns all category business logic and Room interactions.
    private lateinit var categoryViewModel: CategoryViewModel

    // RecyclerView adapter for the read-only default categories list.
    private lateinit var defaultAdapter: CategoryRowAdapter

    // RecyclerView adapter for the user-managed custom categories list (shows delete icons).
    private lateinit var customAdapter: CategoryRowAdapter

    // ID of the currently logged-in user, used to scope all DB queries.
    private var loggedInUserId: String = ""

    // Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_categories)

        // Prefer the user ID passed via Intent extras (e.g. from a parent activity).
        // Fall back to SessionManager if the Intent did not include it.
        loggedInUserId = SessionManager(this).getUserId()

        // Build the ViewModel backed by a Room DAO through the repository layer.
        val repo = CategoryRepository()
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CategoryViewModel(repo) as T
            }
        }
        categoryViewModel = ViewModelProvider(this, factory)[CategoryViewModel::class.java]

        // Seed default categories for this user if they don't already exist.
        // NOTE: seedDefaultCategories is a suspend function and must be called
        // inside a coroutine - lifecycleScope is used here so the coroutine is
        // automatically cancelled if the activity is destroyed before it completes.
        if (loggedInUserId.isNotBlank()) {
            lifecycleScope.launch {
                categoryViewModel.seedDefaultCategories(loggedInUserId)
            }
        }

        setupRecyclerViews()  // Configure both RecyclerViews and their adapters.
        observeCategories()   // Start observing the combined category LiveData.
        setupAddButton()      // Wire up the "Add Category" button with validation.

        // Back arrow navigates back to the previous screen without a result.
        findViewById<TextView>(R.id.backArrow).setOnClickListener { finish() }
    }

    // RecyclerView setup

    /**
     * Initialises the two [RecyclerView]s and their respective [CategoryRowAdapter]s.
     *
     * - [defaultAdapter] has delete functionality disabled (`showDelete = false`)
     *   because default categories are system-level and must not be removed.
     * - [customAdapter] has delete functionality enabled (`showDelete = true`) and
     *   triggers [CategoryViewModel.deleteCategory] followed by a confirmation Toast.
     *
     * Nested scrolling is disabled on both RecyclerViews so they expand fully
     * inside their parent ScrollView without conflicting scroll gestures.
     */
    private fun setupRecyclerViews() {
        // Default categories - no delete action needed, so the lambda is a no-op.
        defaultAdapter = CategoryRowAdapter(showDelete = false) { }

        // Custom categories - delete action removes the category from Room and
        // shows a brief confirmation Toast to the user.
        customAdapter = CategoryRowAdapter(showDelete = true) { cat ->
            categoryViewModel.deleteCategory(cat)
            Toast.makeText(this, "\"${cat.name}\" deleted", Toast.LENGTH_SHORT).show()
        }

        // Attach the default categories adapter to its RecyclerView.
        findViewById<RecyclerView>(R.id.defaultCategoriesRecycler).apply {
            layoutManager = LinearLayoutManager(this@ManageCategoriesActivity)
            adapter = defaultAdapter
            // Disable nested scrolling so the parent ScrollView handles all vertical scroll.
            isNestedScrollingEnabled = false
        }

        // Attach the custom categories adapter to its RecyclerView.
        findViewById<RecyclerView>(R.id.customCategoriesRecycler).apply {
            layoutManager = LinearLayoutManager(this@ManageCategoriesActivity)
            adapter = customAdapter
            isNestedScrollingEnabled = false
        }
    }

    // LiveData observation

    /**
     * Observes the combined list of all categories for the logged-in user and
     * routes them into the correct adapter based on the [Category.isDefault] flag.
     *
     * Also controls the visibility of [R.id.emptyCustomLabel] - a placeholder
     * message shown when the user has not yet created any custom categories.
     */
    private fun observeCategories() {
        categoryViewModel.getCategoriesLive(loggedInUserId).observe(this) { all ->
            // Split the full list into default and user-created categories.
            val defaults = all.filter { it.isDefault }
            val custom   = all.filter { !it.isDefault }

            // Push the filtered lists to their respective adapters.
            // DiffUtil inside ListAdapter handles efficient incremental updates.
            defaultAdapter.submitList(defaults)
            customAdapter.submitList(custom)

            // Show the empty-state label only when the custom list is empty.
            val emptyLabel = findViewById<TextView>(R.id.emptyCustomLabel)
            emptyLabel.visibility = if (custom.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // Add category

    /**
     * Wires up the "Add Category" button with input validation and category creation.
     *
     * Validation rules (checked in order before any DB write):
     *  1. Name must not be blank.
     *  2. Name must be 30 characters or fewer.
     *
     * The category type is resolved from the [RadioGroup] selection:
     *  - "INCOME"  - income-only category.
     *  - "EXPENSE" - expense-only category (default when nothing is selected).
     *  - "BOTH"    - applies to both income and expense transactions.
     *
     * On success the name field is cleared and a confirmation Toast is shown.
     */
    private fun setupAddButton() {
        val nameInput = findViewById<TextInputEditText>(R.id.categoryNameInput)
        val typeGroup = findViewById<RadioGroup>(R.id.categoryTypeGroup)
        val addBtn    = findViewById<MaterialButton>(R.id.addCategoryBtn)

        addBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()

            // Validation 1: name must not be empty.
            if (name.isEmpty()) {
                nameInput.error = "Please enter a category name"
                nameInput.requestFocus()
                return@setOnClickListener
            }

            // Validation 2: name length cap to keep the UI readable.
            if (name.length > 30) {
                nameInput.error = "Name must be 30 characters or less"
                nameInput.requestFocus()
                return@setOnClickListener
            }

            // Map the selected radio button to the category type string stored in Room.
            val type = when (typeGroup.checkedRadioButtonId) {
                R.id.radioIncome -> "INCOME"
                R.id.radioBoth   -> "BOTH"
                else             -> "EXPENSE" // Default fallback when no radio is selected.
            }

            // Construct the new Category entity - isDefault is always false for
            // user-created categories so they appear in the deletable custom list.
            val newCategory = Category(
                userId    = loggedInUserId,
                name      = name,
                type      = type,
                isDefault = false
            )

            // Persist the new category via the ViewModel; Room triggers LiveData,
            // which will automatically update the custom RecyclerView.
            categoryViewModel.addCategory(newCategory)

            // Clear the input field so the user can immediately add another category.
            nameInput.setText("")

            Toast.makeText(this, "Category \"$name\" added!", Toast.LENGTH_SHORT).show()
        }
    }
}