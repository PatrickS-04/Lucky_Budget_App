package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.data.UserDao

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
}