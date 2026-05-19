package com.example.pulserds_prog7313.data

// 'id', 'userId', 'categoryId', 'bankAccountId' are now Strings (Firestore IDs / Auth UIDs).
// All fields have default values for Firestore deserialisation.
data class ExpenseEntry(
    val id: String = "",
    val userId: String = "",
    val categoryId: String = "",
    val bankAccountId: String = "",
    val amount: Double = 0.0,
    val date: String = "",          // "dd/MM/yyyy"
    val startTime: String = "",
    val endTime: String = "",
    val description: String = "",
    val photoPath: String? = null
)