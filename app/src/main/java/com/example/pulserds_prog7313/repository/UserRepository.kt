package com.example.pulserds_prog7313.repository

import com.example.pulserds_prog7313.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.GoogleAuthProvider

// UserRepository now uses Firebase Authentication for login/register/password reset,
// and Firestore to store the user's profile (name, email).
// The constructor no longer takes a DAO -- Firebase singletons are used directly.
class UserRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    // -- Register --------------------------------------------------------------

    // Creates a new Firebase Auth account, then stores the user's profile in
    // Firestore under /users/{uid}. Throws on failure so callers can show specific errors.
    suspend fun register(email: String, password: String, fullName: String): User {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid    = result.user?.uid ?: throw Exception("Auth succeeded but no UID returned")
        val user   = User(id = uid, fullName = fullName, email = email)
        db.collection("users").document(uid).set(user).await()
        return user
    }

    // -- Login -----------------------------------------------------------------

    // Signs the user in with Firebase Auth and returns the matching User profile
    // from Firestore. Returns null if credentials are wrong or the profile is missing.
    suspend fun login(email: String, password: String): User? {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid    = result.user?.uid ?: return null
            val doc    = db.collection("users").document(uid).get().await()
            doc.toObject(User::class.java)?.copy(id = uid)
        } catch (e: Exception) {
            null
        }
    }

    // -- Login with Google-----------------------------------------------------------------

    suspend fun loginWithGoogle(idToken: String): User? {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user ?: return null
            val uid = firebaseUser.uid

            // Check if a Firestore profile already exists for this Google user
            val doc = db.collection("users").document(uid).get().await()
            if (doc.exists()) {
                // Returning Google user — load their profile
                doc.toObject(User::class.java)?.copy(id = uid)
            } else {
                // First-time Google user — create a Firestore profile automatically
                val user = User(
                    id = uid,
                    fullName = firebaseUser.displayName ?: "",
                    email = firebaseUser.email ?: ""
                )
                db.collection("users").document(uid).set(user).await()
                user
            }
        } catch (e: Exception) {
            null
        }
    }

    // -- Get user by email -----------------------------------------------------

    // Queries Firestore for a user document matching the given email.
    // Used after register() to confirm the insert and retrieve the generated UID.
    suspend fun getUserByEmail(email: String): User? {
        return try {
            val snapshot = db.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()
            if (snapshot.isEmpty) return null
            val doc = snapshot.documents[0]
            doc.toObject(User::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
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