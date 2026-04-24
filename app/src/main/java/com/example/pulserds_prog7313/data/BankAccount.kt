package com.example.pulserds_prog7313.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity → table name: bank_accounts
 *
 * Each account belongs to a [User] (via [userId]).
 * Cascading delete: removing a user also removes all their accounts.
 */
@Entity(
    tableName = "bank_accounts",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class BankAccount(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    /** FK → users.id — set from SessionManager */
    val userId: Int,

    /** One of: Cheque, Savings, Credit, Crypto, Invest, Wallet */
    val accountType: String,

    /** One of: Standard, FNB, Absa, Nedbank, Capitec, Investec, Binance, Other */
    val bankName: String,

    /** User-provided label, e.g. "My Cheque Account" (max 30 chars) */
    val nickname: String,

    /** Stored as String to preserve leading zeros and formatting */
    val accountNumber: String,

    /** Opening / current balance in Rand */
    val balance: Double = 0.0,

    /** Hex colour chosen by the user, e.g. "#4ADE80" */
    val colour: String = "#4ADE80",

    /** Whether this account is counted in the dashboard total */
    val includeInTotal: Boolean = true,

    /** Whether this is the default account for new transactions */
    val isPrimary: Boolean = false
)