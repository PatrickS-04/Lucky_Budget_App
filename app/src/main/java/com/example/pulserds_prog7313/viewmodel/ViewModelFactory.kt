package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

// -- Generic ViewModel factory ------------------------------------------------
// Accepts a lambda creator so any ViewModel can be instantiated without
// writing a dedicated factory class for each one.
class ViewModelFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return creator() as T
    }
}