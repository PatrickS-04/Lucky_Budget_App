package com.example.pulserds_prog7313.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named savings goal that the user works toward over time.
 *
 * [savedAmount] grows whenever the user taps "Add Funds".
 * [isCompleted] is set to true (and [completedDate] recorded) when
 * savedAmount >= targetAmount.
 */
@Entity(
    tableName = "savings_goals",
    foreignKeys = [ForeignKey(
        entity = User::class,
        parentColumns = ["id"],
        childColumns = ["userId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("userId")]
)
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,

    /** Display name, e.g. "Emergency Fund" */
    val name: String,

    /** Emoji character chosen from the icon picker */
    val icon: String = "🏦",

    /** Amount the user wants to reach */
    val targetAmount: Double,

    /** Amount saved so far */
    val savedAmount: Double = 0.0,

    /** Target date as "dd MMM yyyy", e.g. "31 Dec 2026" */
    val targetDate: String = "",

    /** Id of the linked BankAccount (0 = no link) */
    val linkedAccountId: Int = 0,

    /** Whether auto-save is enabled */
    val autoSaveEnabled: Boolean = false,

    /** Auto-save amount per period */
    val autoSaveAmount: Double = 0.0,

    /** "WEEKLY", "MONTHLY", or "YEARLY" */
    val autoSaveFrequency: String = "MONTHLY",

    /** True once savedAmount >= targetAmount */
    val isCompleted: Boolean = false,

    /** "dd MMM yyyy" completion date, populated when isCompleted becomes true */
    val completedDate: String = "",

    /** Creation timestamp */
    val createdAt: Long = System.currentTimeMillis()
)
