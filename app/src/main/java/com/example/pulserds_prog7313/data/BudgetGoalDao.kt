package com.example.pulserds_prog7313.data

import androidx.room.*

@Dao
interface BudgetGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(goal: BudgetGoal)

    @Query("SELECT * FROM budget_goals WHERE userId = :userId AND month = :month AND year = :year LIMIT 1")
    suspend fun getGoalForMonth(userId: Int, month: Int, year: Int): BudgetGoal?
}