package com.example.pulserds_prog7313.data

// 'id', 'userId', 'categoryId', 'bankAccountId' are now Strings (Firestore IDs / Auth UIDs).
// All fields have default values for Firestore deserialisation.
//[26] 	Firebase, "Firebase Authentication," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth?authuser=0&_gl=1*10potxh*_ga*MTk4MDgwMzQ0MC4xNzcxODc3ODAz*_ga_CW55HF8NVT*czE3Nzk2NDE1OTIkbzExJGcxJHQxNzc5NjQyMzc2JGo1NCRsMCRoMA..#multi-factor.
// [Accessed 30 May 2026].

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