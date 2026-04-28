package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.CategoryDao

// Repository pattern as recommended by the Android Guide to App Architecture.
// [5] Google, "Guide to app architecture," Android Developers, 2024.
class CategoryRepository(private val categoryDao: CategoryDao) {

    suspend fun addCategory(category: Category) {
        categoryDao.insert(category)
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.delete(category)
    }

    suspend fun getCategoriesForUser(userId: Int): List<Category> {
        return categoryDao.getCategoriesForUser(userId)
    }

    fun getCategoriesForUserLive(userId: Int): LiveData<List<Category>> =
        categoryDao.getCategoriesForUserLive(userId)

    suspend fun countDefaultsForUser(userId: Int): Int =
        categoryDao.countDefaultsForUser(userId)
}