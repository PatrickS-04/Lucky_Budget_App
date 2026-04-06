package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.data.UserDao

class UserRepository(private val userDao: UserDao) {

    suspend fun register(user: User) {
        userDao.insert(user)
    }

    suspend fun login(username: String, passwordHash: String): User? {
        return userDao.login(username, passwordHash)
    }

    suspend fun getUserByUsername(username: String): User? {
        return userDao.getUserByUsername(username)
    }
}