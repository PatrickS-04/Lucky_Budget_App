package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// UserRepository now uses Firebase Authentication for login/register/password reset,
// and Firestore to store the user's profile (name, email).
// The constructor no longer takes a DAO -- Firebase singletons are used directly.
class UserRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    // -- Register --------------------------------------------------------------

    // Creates a new Firebase Auth account, then stores the user's profile in
    // Firestore under /users/{uid}. Returns the saved User on success, null on failure.
    suspend fun register(email: String, password: String, fullName: String): User? {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid    = result.user?.uid ?: return null
        val user   = User(id = uid, fullName = fullName, email = email)
        db.collection("users").document(uid).set(user).await()
        return user
    }

    // -- Login -----------------------------------------------------------------

    // Signs the user in with Firebase Auth and returns the matching User profile
    // from Firestore. Returns null if credentials are wrong or the profile is missing.
    suspend fun login(email: String, password: String): User? {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid    = result.user?.uid ?: return null
        val doc    = db.collection("users").document(uid).get().await()
        return doc.toObject(User::class.java)?.copy(id = uid)
    }

    // -- Get user by email -----------------------------------------------------

    // Queries Firestore for a user document matching the given email.
    // Used after register() to confirm the insert and retrieve the generated UID.
    suspend fun getUserByEmail(email: String): User? {
        val snapshot = db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .await()
        if (snapshot.isEmpty) return null
        val doc = snapshot.documents[0]
        return doc.toObject(User::class.java)?.copy(id = doc.id)
    }

    // -- Update user -----------------------------------------------------------

    // Writes updated fields back to the user's Firestore document.
    suspend fun updateUser(user: User) {
        db.collection("users").document(user.id).set(user).await()
    }

    // -- Delete user -----------------------------------------------------------

    // Deletes the Firestore profile document for the given userId (UID).
    // Note: to also delete the Firebase Auth account itself, call
    // FirebaseAuth.getInstance().currentUser?.delete() separately.
    suspend fun deleteUserById(userId: String) {
        db.collection("users").document(userId).delete().await()
    }
}