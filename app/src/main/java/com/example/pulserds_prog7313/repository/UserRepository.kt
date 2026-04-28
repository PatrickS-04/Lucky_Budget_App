package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.data.UserDao

// Repository pattern as recommended by the Android Guide to App Architecture.
// [5] Google, "Guide to app architecture," Android Developers, 2024.
class UserRepository(private val userDao: UserDao) {

    suspend fun register(user: User) {
        userDao.insert(user)
    }

    suspend fun login(email: String, passwordHash: String): User? {
        return userDao.login(email, passwordHash)
    }

    suspend fun getUserByEmail(email: String): User? {
        return userDao.getUserByEmail(email)
    }

    suspend fun updateUser(user: User) {
        userDao.update(user)
    }

    suspend fun deleteUserById(userId: Int) {
        userDao.deleteById(userId)
    }
}