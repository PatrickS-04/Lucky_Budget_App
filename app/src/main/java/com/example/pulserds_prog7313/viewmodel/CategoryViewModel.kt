package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.repository.CategoryRepository
import kotlinx.coroutines.launch

// Only change from the Room version: userId parameters are now String instead of Int.
// All business logic and LiveData wiring is identical.
class CategoryViewModel(private val categoryRepository: CategoryRepository) : ViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> get() = _categories

    fun getCategoriesLive(userId: String): LiveData<List<Category>> =
        categoryRepository.getCategoriesForUserLive(userId)

    fun loadCategoriesForUser(userId: String) {
        viewModelScope.launch {
            _categories.value = categoryRepository.getCategoriesForUser(userId)
        }
    }

    fun addCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.addCategory(category)
            loadCategoriesForUser(category.userId)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
            loadCategoriesForUser(category.userId)
        }
    }

    suspend fun seedDefaultCategories(userId: String) {
        if (userId.isBlank()) return

        val existingDefaults = categoryRepository.countDefaultsForUser(userId)
        if (existingDefaults > 0) return

        val defaults = listOf(
            Category(userId = userId, name = "Groceries",     type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Dining Out",    type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Transport",     type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Entertainment", type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Subscriptions", type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Utilities",     type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Healthcare",    type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Shopping",      type = "EXPENSE", isDefault = true),
            Category(userId = userId, name = "Salary",        type = "INCOME",  isDefault = true),
            Category(userId = userId, name = "Investment",    type = "INCOME",  isDefault = true),
            Category(userId = userId, name = "Freelance",     type = "INCOME",  isDefault = true),
            Category(userId = userId, name = "Gift",          type = "BOTH",    isDefault = true)
        )
        defaults.forEach { categoryRepository.addCategory(it) }
    }
}