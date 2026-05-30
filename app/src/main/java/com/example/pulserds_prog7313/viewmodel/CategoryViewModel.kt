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
//[1] Google, "Save data in a local database using Room," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/data-storage/room.
// [Accessed: 28 Apr. 2026].
//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
// [Accessed: 28 Apr. 2026].
//[3] Google, "LiveData overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/livedata.
// [Accessed: 28 Apr. 2026].
//[4] JetBrains, "Coroutines guide," Kotlin Documentation, 2024. [Online].
// Available: https://kotlinlang.org/docs/coroutines-guide.html.
// [Accessed: 28 Apr. 2026].
//[5] Google, "Guide to app architecture," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/architecture.
// [Accessed: 28 Apr. 2026].
//[6] Google, AndroidX Room, ver. 2.7.0-alpha11. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/room.
//[7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/lifecycle.
//[26] 	A. Mamo, "Android Room and Firebase in a MVVM Architecture - Offline First Application," Stack Exchange Inc, 18 November 2022. [Online].
// Available: https://stackoverflow.com/questions/74484239/android-room-and-firebase-in-a-mvvm-architecture-offline-first-application.
// [Accessed 30 May 2026].

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

        val currentCategories = categoryRepository.getCategoriesForUser(userId)

        val existingDefaults = currentCategories.map { it.name.lowercase().trim() }.toSet()

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
        defaults.forEach { defaultCategory ->
            if (!existingDefaults.contains(defaultCategory.name.lowercase().trim()))
            {
                categoryRepository.addCategory(defaultCategory)
            }
        }

        loadCategoriesForUser(userId)
    }
}