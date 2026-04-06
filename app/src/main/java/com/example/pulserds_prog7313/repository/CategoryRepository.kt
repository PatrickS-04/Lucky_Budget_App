package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.CategoryDao

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
}