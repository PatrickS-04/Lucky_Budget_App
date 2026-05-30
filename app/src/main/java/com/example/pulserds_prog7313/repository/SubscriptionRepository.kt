package com.example.pulserds_prog7313.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.pulserds_prog7313.data.Subscription
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * SubscriptionRepository handles all Firestore reads and writes for
 * the "subscriptions" collection.
 *
 * Pattern mirrors BankAccountRepository:
 *  - getSubscriptionsForUser()  → real-time LiveData stream via snapshot listener
 *  - getSubscriptionsOnce()     → suspend one-shot fetch (for summary totals)
 *  - addSubscription()          → inserts and back-fills the document ID
 *  - updateSubscription()       → full document replace
 *  - deleteSubscription()       → hard delete
 */
class SubscriptionRepository {

    private val db  = FirebaseFirestore.getInstance()
    // All subscriptions live in a top-level "subscriptions" collection
    private val col get() = db.collection("subscriptions")

    // -- Real-time stream ------------------------------------------------------

    /**
     * Returns a LiveData that automatically updates whenever the user's
     * subscriptions change in Firestore.
     * Subscriptions are ordered: Active first, then alphabetically by serviceName.
     */
    fun getSubscriptionsForUser(userId: String): LiveData<List<Subscription>> {
        val liveData = MutableLiveData<List<Subscription>>()
        col.whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents
                        .mapNotNull { doc ->
                            doc.toObject(Subscription::class.java)?.copy(id = doc.id)
                        }
                        // Active subscriptions float to the top; ties broken by name
                        .sortedWith(
                            compareBy<Subscription> { if (it.status == "Active") 0 else 1 }
                                .thenBy { it.serviceName }
                        )
                    liveData.postValue(list)
                }
            }
        return liveData
    }

    // -- One-shot fetches ------------------------------------------------------

    suspend fun getSubscriptionsOnce(userId: String): List<Subscription> {
        val snapshot = col.whereEqualTo("userId", userId).get().await()
        return snapshot.documents
            .mapNotNull { doc -> doc.toObject(Subscription::class.java)?.copy(id = doc.id) }
    }

    // -- Write operations ------------------------------------------------------

    /**
     * Inserts a new subscription and back-fills the Firestore document ID
     * into the "id" field (consistent with how BankAccountRepository works).
     * Returns the new document ID, or "" on failure.
     */
    suspend fun addSubscription(subscription: Subscription): String {
        return try {
            val ref = col.add(subscription).await()
            // Store the document ID inside the document itself for easy retrieval
            col.document(ref.id).update("id", ref.id).await()
            ref.id
        } catch (e: Exception) {
            ""
        }
    }

    /** Replaces the entire subscription document (use for edits and status toggles). */
    suspend fun updateSubscription(subscription: Subscription) {
        col.document(subscription.id).set(subscription).await()
    }

    /** Hard-deletes a subscription. */
    suspend fun deleteSubscription(subscription: Subscription) {
        col.document(subscription.id).delete().await()
    }

    // -- Aggregates ------------------------------------------------------------

    /**
     * Returns the total monthly spend across all ACTIVE subscriptions.
     * Yearly subscriptions are divided by 12; weekly ones are multiplied by ~4.33.
     */
    suspend fun getMonthlyTotal(userId: String): Double {
        return getSubscriptionsOnce(userId)
            .filter { it.status == "Active" }
            .sumOf { sub ->
                when (sub.billingCycle) {
                    "Weekly"  -> sub.amount * 4.33
                    "Monthly" -> sub.amount
                    "Yearly"  -> sub.amount / 12.0
                    else      -> sub.amount
                }
            }
    }
}