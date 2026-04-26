package com.example.pulserds_prog7313.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SavingsGoalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: SavingsGoal): Long

    @Update
    suspend fun update(goal: SavingsGoal)

    @Delete
    suspend fun delete(goal: SavingsGoal)

    /** Stream all goals for a user, active first then completed, newest first within each group. */
    @Query("""
        SELECT * FROM savings_goals
        WHERE userId = :userId
        ORDER BY isCompleted ASC, createdAt DESC
    """)
    fun getAllGoalsLive(userId: Int): LiveData<List<SavingsGoal>>

    /** One-shot - for coroutine use. */
    @Query("SELECT * FROM savings_goals WHERE userId = :userId ORDER BY isCompleted ASC, createdAt DESC")
    suspend fun getAllGoalsOnce(userId: Int): List<SavingsGoal>

    @Query("SELECT * FROM savings_goals WHERE id = :goalId LIMIT 1")
    suspend fun getById(goalId: Int): SavingsGoal?

    /** Atomically add [delta] to savedAmount and set isCompleted / completedDate when done. */
    @Query("""
        UPDATE savings_goals
        SET savedAmount    = MIN(savedAmount + :delta, targetAmount),
            isCompleted    = CASE WHEN savedAmount + :delta >= targetAmount THEN 1 ELSE 0 END,
            completedDate  = CASE WHEN savedAmount + :delta >= targetAmount THEN :completedDate ELSE completedDate END
        WHERE id = :goalId
    """)
    suspend fun addFunds(goalId: Int, delta: Double, completedDate: String)

    /** Sum of savedAmount across all active goals for the summary strip. */
    @Query("SELECT COALESCE(SUM(savedAmount), 0.0) FROM savings_goals WHERE userId = :userId AND isCompleted = 0")
    suspend fun getTotalSaved(userId: Int): Double

    /** Sum of targetAmount across all active goals. */
    @Query("SELECT COALESCE(SUM(targetAmount), 0.0) FROM savings_goals WHERE userId = :userId AND isCompleted = 0")
    suspend fun getTotalTarget(userId: Int): Double
}
