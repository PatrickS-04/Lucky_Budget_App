package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pulserds_prog7313.data.Category
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

// CategoryRepository now reads/writes to Firestore collection "categories".
// Documents are scoped per user via the "userId" field.
//[1] Google, "Save data in a local database using Room," Android Developers, 2024. [Online].
// Available: https://developer.android.com/training/data-storage/room.
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
//[8] JetBrains, kotlinx-coroutines-android, ver. 1.8.1. [Software]. JetBrains, 2024.
// Available: https://github.com/Kotlin/kotlinx.coroutines.
//[14] Google, "KSP quickstart," Android Developers, 2024. [Online].
// Available: https://developer.android.com/build/migrate-to-ksp.
// [Accessed: 28 Apr. 2026].
//[25] 	F. van Puffelen, "Save a Room Database file in Firebase," Stack Exchange Inc, 22 August 2021. [Online].
// Available: https://stackoverflow.com/questions/68878022/save-a-room-database-file-in-firebase.
// [Accessed 30 May 2026].

class CategoryRepository {

    private val db = FirebaseFirestore.getInstance()
    private val col get() = db.collection("categories")

    // -- Write operations ------------------------------------------------------

    suspend fun addCategory(category: Category) {
        if (category.id.isBlank()) {
            // New category -- let Firestore generate the document ID
            val ref = col.add(category).await()
            // Optionally write the generated ID back into the document
            col.document(ref.id).update("id", ref.id).await()
        } else {
            col.document(category.id).set(category).await()
        }
    }

    suspend fun deleteCategory(category: Category) {
        col.document(category.id).delete().await()
    }

    // -- Read operations -------------------------------------------------------

    // One-shot fetch -- returns the current list and returns immediately.
    // Sorting is done in-memory to avoid requiring a composite Firestore index.
    suspend fun getCategoriesForUser(userId: String): List<Category> {
        return try {
            val snapshot = col
                .whereEqualTo("userId", userId)
                .get()
                .await()
            snapshot.documents
                .mapNotNull { doc -> doc.toObject(Category::class.java)?.copy(id = doc.id) }
                .sortedByDescending { it.isDefault }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Real-time LiveData stream -- the returned LiveData updates automatically
    // whenever the Firestore collection changes. Call removeListener() on the
    // returned ListenerRegistration when the observer is no longer needed
    // (e.g. in onDestroy) to avoid memory leaks.
    fun getCategoriesForUserLive(userId: String): LiveData<List<Category>> {
        val liveData = MutableLiveData<List<Category>>()
        col.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Category::class.java)?.copy(id = doc.id)
                    }.sortedByDescending { it.isDefault }
                    liveData.postValue(list)
                }
            }
        return liveData
    }

    // Returns the count of default (seeded) categories for the user.
    // Used to guard against re-seeding on subsequent logins.
    suspend fun countDefaultsForUser(userId: String): Int {
        val snapshot = col
            .whereEqualTo("userId", userId)
            .whereEqualTo("isDefault", true)
            .get()
            .await()
        return snapshot.size()
    }
}