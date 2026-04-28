package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.ViewModel
import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.repository.UserRepository

class UserViewModel(private val userRepository: UserRepository) : ViewModel() {

    suspend fun register(user: User) {
        userRepository.register(user)
    }

    suspend fun login(email: String, passwordHash: String): User? {
        return userRepository.login(email, passwordHash)
    }

    suspend fun getUserByEmail(email: String): User? {
        return userRepository.getUserByEmail(email)
    }

    suspend fun updateUser(user: User) {
        userRepository.updateUser(user)
    }
}