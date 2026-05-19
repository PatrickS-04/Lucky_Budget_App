package com.example.pulserds_prog7313.data

// Firestore maps document fields to these properties by name automatically.
// 'id' stores the Firestore document ID (a String UID from Firebase Auth).
// All fields have default values so Firestore can deserialise documents
// using the no-argument constructor it requires.

data class User(
    val id: String = "",
    val fullName: String = "",
    val email: String = ""
    // passwordHash removed -- Firebase Auth owns password management entirely.
    // Storing Passwords is frowned upon
)