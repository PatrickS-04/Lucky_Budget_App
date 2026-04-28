package com.example.pulserds_prog7313.data

import androidx.lifecycle.LiveData
import androidx.room.*

// Room DAO pattern
// [1] Google, "Save data in a local database using Room," Android Developers, 2024.
@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY isDefault DESC, name ASC")
    suspend fun getCategoriesForUser(userId: Int): List<Category>

    /** LiveData stream so UI refreshes automatically */
    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY isDefault DESC, name ASC")
    fun getCategoriesForUserLive(userId: Int): LiveData<List<Category>>

    @Query("SELECT COUNT(*) FROM categories WHERE userId = :userId AND isDefault = 1")
    suspend fun countDefaultsForUser(userId: Int): Int
}