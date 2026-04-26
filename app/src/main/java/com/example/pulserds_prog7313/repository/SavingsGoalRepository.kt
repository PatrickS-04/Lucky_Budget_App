package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.data.SavingsGoalDao

class SavingsGoalRepository(private val dao: SavingsGoalDao) {

    fun getAllGoalsLive(userId: Int): LiveData<List<SavingsGoal>> = dao.getAllGoalsLive(userId)

    suspend fun getAllGoalsOnce(userId: Int): List<SavingsGoal> = dao.getAllGoalsOnce(userId)

    suspend fun insert(goal: SavingsGoal): Long = dao.insert(goal)

    suspend fun update(goal: SavingsGoal) = dao.update(goal)

    suspend fun delete(goal: SavingsGoal) = dao.delete(goal)

    suspend fun getById(goalId: Int): SavingsGoal? = dao.getById(goalId)

    suspend fun addFunds(goalId: Int, delta: Double, completedDate: String) =
        dao.addFunds(goalId, delta, completedDate)

    suspend fun getTotalSaved(userId: Int): Double = dao.getTotalSaved(userId)

    suspend fun getTotalTarget(userId: Int): Double = dao.getTotalTarget(userId)
}
