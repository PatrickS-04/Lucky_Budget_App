package com.example.pulserds_prog7313.data

/**
 * Represents a recurring subscription (e.g. Netflix, Spotify).
 *
 * Fields mirror Firestore document structure; all have default values so
 * Firestore can deserialise the document via toObject() without reflection errors.
 *
 * @param id            Firestore document ID (populated after write).
 * @param userId        Firebase Auth UID of the owning user.
 * @param serviceName   Display name of the subscription service.
 * @param amount        Amount charged per billing cycle.
 * @param billingCycle  One of: "Weekly", "Monthly", "Yearly".
 * @param category      User-defined category label (e.g. "Entertainment").
 * @param bankAccountId ID of the linked BankAccount used for auto-pay.
 * @param startDate     Start date in "dd/MM/yyyy" format.
 * @param nextRenewal   Computed next renewal date in "dd/MM/yyyy" format.
 * @param note          Optional user note.
 * @param isAutoPay     Whether the subscription deducts automatically on renewal.
 * @param isReminderOn  Whether to notify the user before renewal.
 * @param reminderDays  Days before renewal to send a reminder (1, 3, or 7).
 * @param status        One of: "Active", "Paused".
 * @param icon          Emoji icon representing the service (e.g. "📺").
 */
data class Subscription(
    val id: String = "",
    val userId: String = "",
    val serviceName: String = "",
    val amount: Double = 0.0,
    val billingCycle: String = "Monthly",
    val category: String = "",
    val bankAccountId: String = "",
    val startDate: String = "",
    val nextRenewal: String = "",
    val note: String = "",
    val isAutoPay: Boolean = true,
    val isReminderOn: Boolean = true,
    val reminderDays: Int = 3,
    val status: String = "Active",
    val icon: String = "📋"
)