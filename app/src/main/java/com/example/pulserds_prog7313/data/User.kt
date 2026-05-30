package com.example.pulserds_prog7313.data

// Firestore maps document fields to these properties by name automatically.
// 'id' stores the Firestore document ID (a String UID from Firebase Auth).
// All fields have default values so Firestore can deserialise documents
// using the no-argument constructor it requires.
//[26] 	Firebase, "Firebase Authentication," Google, 27 May 2026. [Online].
// Available: https://firebase.google.com/docs/auth?authuser=0&_gl=1*10potxh*_ga*MTk4MDgwMzQ0MC4xNzcxODc3ODAz*_ga_CW55HF8NVT*czE3Nzk2NDE1OTIkbzExJGcxJHQxNzc5NjQyMzc2JGo1NCRsMCRoMA..#multi-factor.
// [Accessed 30 May 2026].

data class User(
    val id: String = "",
    val fullName: String = "",
    val email: String = ""
    // passwordHash removed -- Firebase Auth owns password management entirely.
    // Storing Passwords is frowned upon
)