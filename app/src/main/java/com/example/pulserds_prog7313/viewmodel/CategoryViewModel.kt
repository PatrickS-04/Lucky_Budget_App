package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.repository.CategoryRepository
import kotlinx.coroutines.launch

class CategoryViewModel(private val categoryRepository: CategoryRepository) : ViewModel() {

    private val _categories = MutableLiveData<List<Category>>()
    val categories: LiveData<List<Category>> get() = _categories

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
}