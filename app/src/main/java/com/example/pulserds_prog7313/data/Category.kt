package com.example.pulserds_prog7313.data

import com.google.firebase.firestore.PropertyName

// 'id' is now a String (Firestore auto-generated document ID).
// 'userId' is now a String (Firebase Auth UID) instead of an Int.
// All fields have default values for Firestore deserialisation.
//[26] 	Firebase, "Firebase Authentication," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth?authuser=0&_gl=1*10potxh*_ga*MTk4MDgwMzQ0MC4xNzcxODc3ODAz*_ga_CW55HF8NVT*czE3Nzk2NDE1OTIkbzExJGcxJHQxNzc5NjQyMzc2JGo1NCRsMCRoMA..#multi-factor.
// [Accessed 30 May 2026].

data class Category(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    /** "EXPENSE", "INCOME", or "BOTH" */
    val type: String = "BOTH",
    /** true = built-in default; false = user-created */
    // @PropertyName maps Kotlin's is-prefixed boolean to Firestore field "default"
    @get:PropertyName("default") @set:PropertyName("default")
    var isDefault: Boolean = false
)