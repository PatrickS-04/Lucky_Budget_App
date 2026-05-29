package com.example.pulserds_prog7313.data

import com.google.firebase.firestore.PropertyName

// 'id' is now a String (Firestore auto-generated document ID).
// 'userId' is now a String (Firebase Auth UID) instead of an Int.
// All fields have default values for Firestore deserialisation.
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