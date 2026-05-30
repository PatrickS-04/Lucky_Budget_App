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
//[1] Google, "Save data in a local database using Room," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/data-storage/room.
// [Accessed: 28 Apr. 2026].
//[2] Google, "ViewModel overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/viewmodel.
// [Accessed: 28 Apr. 2026].
//[3] Google, "LiveData overview," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/libraries/architecture/livedata.
// [Accessed: 28 Apr. 2026].
//[4] JetBrains, "Coroutines guide," Kotlin Documentation, 2024. [Online].
// Available: https://kotlinlang.org/docs/coroutines-guide.html.
// [Accessed: 28 Apr. 2026].
//[5] Google, "Guide to app architecture," Android Developers, 2024. [Online].
// Available: https://developer.android.com/topic/architecture.
// [Accessed: 28 Apr. 2026].
//[6] Google, AndroidX Room, ver. 2.7.0-alpha11. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/room.
//[7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.
// Available: https://developer.android.com/jetpack/androidx/releases/lifecycle.
//[26] 	A. Mamo, "Android Room and Firebase in a MVVM Architecture - Offline First Application," Stack Exchange Inc, 18 November 2022. [Online].
// Available: https://stackoverflow.com/questions/74484239/android-room-and-firebase-in-a-mvvm-architecture-offline-first-application.
// [Accessed 30 May 2026].

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