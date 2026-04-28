package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.ViewModel
import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.repository.UserRepository

// MVVM ViewModel — based off the official Google documents.
// [2] Google, "ViewModel overview," Android Developers, 2024.
// [7] Google, AndroidX Lifecycle ViewModel KTX, ver. 2.8.4. [Software]. Android Developers, 2024.

/**
 * UserViewModel
 * -------------
 * Manages all user account operations and acts as the bridge between
 * the UI layer and [UserRepository].
 *
 * Responsibilities:
 *   - Registering new user accounts.
 *   - Authenticating existing users via email and hashed password.
 *   - Looking up a user record by email address.
 *   - Updating an existing user's stored details.
 *
 * All functions are suspend functions and must be called from a coroutine
 * or another suspend function (e.g. inside a lifecycleScope.launch block).
 *
 * @param userRepository Repository that abstracts all Room database operations for users.
 */
class UserViewModel(private val userRepository: UserRepository) : ViewModel() {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Inserts a new [User] record into the database.
     *
     * Called during account creation after all form validation has passed
     * and the password has been hashed. Does not return the generated row ID —
     * use [getUserByEmail] immediately after to retrieve the saved user with
     * its auto-generated primary key.
     *
     * @param user The [User] to insert; userId should be 0 so Room auto-generates it.
     */
    suspend fun register(user: User) {
        userRepository.register(user)
    }

    /**
     * Attempts to authenticate a user with the given credentials.
     *
     * Delegates to the repository which queries the database for a user
     * matching both [email] and [passwordHash]. The password must already
     * be hashed (SHA-256) before this function is called — plain-text
     * passwords are never passed to the database layer.
     *
     * @param email        The email address entered by the user on the login screen.
     * @param passwordHash SHA-256 hash of the password entered by the user.
     * @return The matching [User] if credentials are correct, or null if not found.
     */
    suspend fun login(email: String, passwordHash: String): User? {
        return userRepository.login(email, passwordHash)
    }

    /**
     * Looks up a user record by [email] address.
     *
     * Used after [register] to retrieve the newly inserted user with its
     * auto-generated userId, which is required before saving the session
     * and seeding default categories.
     *
     * @param email The email address to search for.
     * @return The matching [User] if found, or null if no account exists for that email.
     */
    suspend fun getUserByEmail(email: String): User? {
        return userRepository.getUserByEmail(email)
    }

    /**
     * Persists changes to an existing [User] record in the database.
     *
     * Called from profile or settings screens when the user updates their
     * stored details (e.g. full name or email address). The [user] object
     * must carry the correct userId so Room matches and updates the right row.
     *
     * @param user The [User] with updated fields to write back to the database.
     */
    suspend fun updateUser(user: User) {
        userRepository.updateUser(user)
    }
}