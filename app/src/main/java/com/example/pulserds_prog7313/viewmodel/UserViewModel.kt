package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.ViewModel
import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.repository.UserRepository

// UserViewModel now delegates to the Firebase-backed UserRepository.
// Key changes vs the Room version:
//   - register() takes email + password + fullName separately (Firebase Auth
//     needs the plain-text password; we no longer hash it ourselves).
//   - login() takes email + password (plain-text); Firebase Auth verifies it.
//   - deleteUserById() now takes a String (Firebase Auth UID).
class UserViewModel(private val userRepository: UserRepository) : ViewModel() {

    // Registers a new user via Firebase Auth and stores the profile in Firestore.
    // Throws on failure (FirebaseAuthUserCollisionException, network errors, etc.).
    suspend fun register(email: String, password: String, fullName: String): User {
        return userRepository.register(email, password, fullName)
    }

    // Authenticates the user via Firebase Auth and returns their Firestore profile.
    // Returns null if the credentials are incorrect or no profile exists.
    suspend fun login(email: String, password: String): User? {
        return userRepository.login(email, password)
    }

    suspend fun loginWithGoogle(idToken: String): User? {
        return userRepository.loginWithGoogle(idToken)
    }

    // Looks up a user by email address in Firestore.
    suspend fun getUserByEmail(email: String): User? {
        return userRepository.getUserByEmail(email)
    }

    // Persists changes to an existing user's Firestore profile document.
    suspend fun updateUser(user: User) {
        userRepository.updateUser(user)
    }

    // Deletes a user's Firestore profile document by their UID.
    suspend fun deleteUserById(userId: String) {
        userRepository.deleteUserById(userId)
    }
}