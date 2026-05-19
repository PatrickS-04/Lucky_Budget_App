package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pulserds_prog7313.data.Category
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

// CategoryRepository now reads/writes to Firestore collection "categories".
// Documents are scoped per user via the "userId" field.
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
    suspend fun getCategoriesForUser(userId: String): List<Category> {
        val snapshot = col
            .whereEqualTo("userId", userId)
            .orderBy("isDefault", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Category::class.java)?.copy(id = doc.id)
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