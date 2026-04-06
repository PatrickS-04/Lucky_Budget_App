package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.BudgetGoal
import com.example.pulserds_prog7313.data.BudgetGoalDao

class BudgetGoalRepository(private val budgetGoalDao: BudgetGoalDao) {

    suspend fun saveGoal(goal: BudgetGoal) {
        budgetGoalDao.insertOrUpdate(goal)
    }

    suspend fun getGoalForMonth(userId: Int, month: Int, year: Int): BudgetGoal? {
        return budgetGoalDao.getGoalForMonth(userId, month, year)
    }
}