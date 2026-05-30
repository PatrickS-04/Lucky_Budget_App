package com.example.pulserds_prog7313.data

// 'id', 'userId', 'linkedAccountId' are now Strings.
// All fields have default values for Firestore deserialisation.
//[26] 	Firebase, "Firebase Authentication," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth?authuser=0&_gl=1*10potxh*_ga*MTk4MDgwMzQ0MC4xNzcxODc3ODAz*_ga_CW55HF8NVT*czE3Nzk2NDE1OTIkbzExJGcxJHQxNzc5NjQyMzc2JGo1NCRsMCRoMA..#multi-factor.
// [Accessed 30 May 2026].

data class SavingsGoal(
    val id: String = "",
    val userId: String = "",

    /** Display name, e.g. "Emergency Fund" */
    val name: String = "",

    /** Emoji character chosen from the icon picker */
    val icon: String = "🏦",

    /** Amount the user wants to reach */
    val targetAmount: Double = 0.0,

    /** Amount saved so far */
    val savedAmount: Double = 0.0,

    /** Target date as "dd MMM yyyy", e.g. "31 Dec 2026" */
    val targetDate: String = "",

    /** Firestore document ID of the linked BankAccount ("" = no link) */
    val linkedAccountId: String = "",

    /** Whether auto-save is enabled */
    val autoSaveEnabled: Boolean = false,

    /** Auto-save amount per period */
    val autoSaveAmount: Double = 0.0,

    /** "WEEKLY", "MONTHLY", or "YEARLY" */
    val autoSaveFrequency: String = "MONTHLY",

    /** True once savedAmount >= targetAmount */
    val isCompleted: Boolean = false,

    /** "dd MMM yyyy" completion date */
    val completedDate: String = "",

    /** Creation timestamp */
    val createdAt: Long = System.currentTimeMillis(),

    // ---- Monthly spending-goal band (used by the Analytics spending-goals chart) ----

    /** Minimum monthly spending target for this category (0 = not set) */
    val minSpendingGoal: Double = 0.0,

    /** Maximum monthly spending target for this category (0 = not set) */
    val maxSpendingGoal: Double = 0.0
)