package com.example.pulserds_prog7313.data

import androidx.room.*

// Room DAO pattern
// [1] Google, "Save data in a local database using Room," Android Developers, 2024.
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(user: User)

    @Update
    suspend fun update(user: User)

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE email = :email AND passwordHash = :passwordHash LIMIT 1")
    suspend fun login(email: String, passwordHash: String): User?

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteById(userId: Int)
}