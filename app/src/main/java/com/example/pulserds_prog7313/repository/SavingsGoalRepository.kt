package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pulserds_prog7313.data.SavingsGoal
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// SavingsGoalRepository now reads/writes to Firestore collection "savings_goals".
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

class SavingsGoalRepository {

    private val db  = FirebaseFirestore.getInstance()
    private val col get() = db.collection("savings_goals")

    // -- LiveData stream -------------------------------------------------------

    // Observes all goals for a user (active first, then completed, newest first).
    fun getAllGoalsLive(userId: String): LiveData<List<SavingsGoal>> {
        val liveData = MutableLiveData<List<SavingsGoal>>()
        col.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents
                        .mapNotNull { doc -> doc.toObject(SavingsGoal::class.java)?.copy(id = doc.id) }
                        .sortedWith(
                            compareBy<SavingsGoal> { it.isCompleted }
                                .thenByDescending { it.createdAt }
                        )
                    liveData.postValue(list)
                }
            }
        return liveData
    }

    // -- One-shot fetches ------------------------------------------------------

    suspend fun getAllGoalsOnce(userId: String): List<SavingsGoal> {
        val snapshot = col.whereEqualTo("userId", userId).get().await()
        return snapshot.documents
            .mapNotNull { doc -> doc.toObject(SavingsGoal::class.java)?.copy(id = doc.id) }
            .sortedWith(
                compareBy<SavingsGoal> { it.isCompleted }
                    .thenByDescending { it.createdAt }
            )
    }

    suspend fun getById(goalId: String): SavingsGoal? {
        val doc = col.document(goalId).get().await()
        return doc.toObject(SavingsGoal::class.java)?.copy(id = doc.id)
    }

    // -- Write operations ------------------------------------------------------

    // Inserts a new goal and returns the Firestore-generated document ID.
    suspend fun insert(goal: SavingsGoal): String {
        val ref = col.add(goal).await()
        col.document(ref.id).update("id", ref.id).await()
        return ref.id
    }

    suspend fun update(goal: SavingsGoal) {
        col.document(goal.id).set(goal).await()
    }

    suspend fun delete(goal: SavingsGoal) {
        col.document(goal.id).delete().await()
    }

    // Atomically adds [delta] to savedAmount and marks the goal complete if reached.
    suspend fun addFunds(goalId: String, delta: Double, completedDate: String) {
        val ref = col.document(goalId)
        db.runTransaction { transaction ->
            val snapshot    = transaction.get(ref)
            val goal        = snapshot.toObject(SavingsGoal::class.java) ?: return@runTransaction
            val newSaved    = minOf(goal.savedAmount + delta, goal.targetAmount)
            val nowComplete = newSaved >= goal.targetAmount
            transaction.update(ref, mapOf(
                "savedAmount"   to newSaved,
                "isCompleted"   to nowComplete,
                "completedDate" to if (nowComplete) completedDate else goal.completedDate
            ))
        }.await()
    }

    // -- Aggregates ------------------------------------------------------------

    suspend fun getTotalSaved(userId: String): Double =
        getAllGoalsOnce(userId).filter { !it.isCompleted }.sumOf { it.savedAmount }

    suspend fun getTotalTarget(userId: String): Double =
        getAllGoalsOnce(userId).filter { !it.isCompleted }.sumOf { it.targetAmount }
}