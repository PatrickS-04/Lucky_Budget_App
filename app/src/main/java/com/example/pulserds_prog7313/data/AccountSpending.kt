package com.example.pulserds_prog7313.data

// Previously defined at the bottom of ExpenseEntryDao.kt.
// Moved here now that the DAO file is deleted.
// Holds the per-account spending total returned by
// ExpenseEntryRepository.getExpensesByAccount().
data class AccountSpending(val bankAccountId: String, val total: Double)