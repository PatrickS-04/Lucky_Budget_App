package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.repository.CategoryRepository
import kotlinx.coroutines.launch

// MVVM ViewModel — based off the official google documents
// [2] Google, "ViewModel overview," Android Developers, 2024.
// [7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.
class CategoryViewModel(private val categoryRepository: CategoryRepository) : ViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> get() = _categories

    fun getCategoriesLive(userId: Int): LiveData<List<Category>> =
        categoryRepository.getCategoriesForUserLive(userId)

    fun loadCategoriesForUser(userId: Int) {
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

    suspend fun seedDefaultCategories(userId: Int) {
        if (userId == -1) return
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