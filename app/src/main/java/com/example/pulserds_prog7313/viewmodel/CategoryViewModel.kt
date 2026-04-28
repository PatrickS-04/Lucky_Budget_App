package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.repository.CategoryRepository
import kotlinx.coroutines.launch

// MVVM ViewModel — based off the official Google documents.
// [2] Google, "ViewModel overview," Android Developers, 2024.
// [7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.

/**
 * CategoryViewModel
 * -----------------
 * Manages all category data for the logged-in user and exposes it to
 * the UI layer via observable LiveData.
 *
 * Responsibilities:
 *   - Exposes a LiveData list of [Category] objects for UI observation.
 *   - Provides a Room-backed LiveData stream for screens that need
 *     automatic updates without manual reloads.
 *   - Handles adding and deleting categories, refreshing the list afterwards.
 *   - Seeds a set of sensible default categories on first login so new
 *     users have a ready-to-use category list without manual setup.
 *
 * @param categoryRepository Repository that abstracts all Room operations for categories.
 */
class CategoryViewModel(private val categoryRepository: CategoryRepository) : ViewModel() {

    // -- Observed by category-aware screens -----------------------------------

    /**
     * Internal mutable list; updated manually after load, add, and delete operations.
     * Exposed publicly as an immutable [LiveData] via [categories].
     */
    private val _categories = MutableLiveData<List<Category>>()

    /**
     * Immutable LiveData observed by screens such as CategoryTotalsActivity
     * and AnalyticsActivity. Updated by [loadCategoriesForUser].
     */
    val categories: LiveData<List<Category>> get() = _categories

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns a Room-backed LiveData stream for [userId]'s categories.
     *
     * Unlike [loadCategoriesForUser], this stream updates automatically
     * whenever the underlying database table changes — no manual reload needed.
     * Preferred for screens that display categories in real time (e.g. AddExpenseActivity).
     *
     * @param userId The ID of the logged-in user.
     * @return A [LiveData] list that Room keeps up to date automatically.
     */
    fun getCategoriesLive(userId: Int): LiveData<List<Category>> =
        categoryRepository.getCategoriesForUserLive(userId)

    /**
     * Fetches all categories for [userId] from the database and posts the
     * result to [categories].
     *
     * Called after add/delete operations and on period changes to ensure
     * [categories] always reflects the current database state.
     *
     * @param userId The ID of the logged-in user.
     */
    fun loadCategoriesForUser(userId: Int) {
        viewModelScope.launch {
            _categories.value = categoryRepository.getCategoriesForUser(userId)
        }
    }

    /**
     * Inserts [category] into the database and refreshes [categories]
     * so the UI reflects the addition immediately.
     *
     * @param category The [Category] to insert; must have a valid userId set.
     */
    fun addCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.addCategory(category)
            loadCategoriesForUser(category.userId) // Refresh so the new category appears in the list.
        }
    }

    /**
     * Deletes [category] from the database and refreshes [categories]
     * so the UI reflects the removal immediately.
     *
     * @param category The [Category] to delete; must have a valid userId set.
     */
    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
            loadCategoriesForUser(category.userId) // Refresh so the deleted category disappears from the list.
        }
    }

    // -------------------------------------------------------------------------
    // Default category seeding
    // -------------------------------------------------------------------------

    /**
     * Seeds a predefined set of default categories for [userId] on first login.
     *
     * Guards:
     *   - Returns immediately if [userId] is -1 (no active session).
     *   - Returns immediately if defaults have already been seeded for this user,
     *     preventing duplicate entries across logins or app reinstalls.
     *
     * Default categories cover common EXPENSE, INCOME, and BOTH types so new
     * users have a useful starting set without any manual setup.
     *
     * This function is a suspend function and must be called from a coroutine
     * or another suspend function (e.g. inside a lifecycleScope.launch block).
     *
     * @param userId The ID of the newly registered user to seed categories for.
     */
    suspend fun seedDefaultCategories(userId: Int) {
        if (userId == -1) return // No active session — bail out early.

        // Skip seeding if defaults already exist to prevent duplicates.
        val existingDefaults = categoryRepository.countDefaultsForUser(userId)
        if (existingDefaults > 0) return

        val defaults = listOf(
            // -- Expense categories -------------------------------------------
            Category(userId = userId, name = "Groceries",     type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Dining Out",    type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Transport",     type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Entertainment", type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Subscriptions", type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Utilities",     type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Healthcare",    type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Shopping",      type = "EXPENSE", isDefault = true),
            // -- Income categories --------------------------------------------
            Category(userId = userId, name = "Salary",        type = "INCOME",  isDefault = true),
            Category(userId = userId, name = "Investment",    type = "INCOME",  isDefault = true),
            Category(userId = userId, name = "Freelance",     type = "INCOME",  isDefault = true),
            // -- Both (income or expense) categories --------------------------
            Category(userId = userId, name = "Gift",          type = "BOTH",    isDefault = true)
        )

        // Insert each default category individually via the repository.
        defaults.forEach { categoryRepository.addCategory(it) }
    }
}