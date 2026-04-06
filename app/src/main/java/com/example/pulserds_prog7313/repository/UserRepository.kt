package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.data.UserDao

class UserRepository(private val userDao: UserDao) {

    suspend fun registerUser(user: User) {
        userDao.registerUser(user)
    }

    suspend fun login(email: String, password: String): User? {
        return userDao.login(email, password)
    }

    suspend fun getUserById(id: Int): User? {
        return userDao.getUserById(id)
    }
}