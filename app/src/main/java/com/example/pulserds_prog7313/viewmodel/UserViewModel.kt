package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.repository.UserRepository
import kotlinx.coroutines.launch

class UserViewModel(private val userRepository: UserRepository) : ViewModel() {

    suspend fun register(user: User) {
        userRepository.register(user)
    }

    suspend fun login(username: String, passwordHash: String): User? {
        return userRepository.login(username, passwordHash)
    }

    suspend fun getUserByUsername(username: String): User? {
        return userRepository.getUserByUsername(username)
    }
}