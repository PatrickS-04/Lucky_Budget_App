package com.example.pulserds_prog7313.data

import com.google.firebase.firestore.PropertyName

// 'id' and 'userId' are now Strings (Firestore document ID / Firebase Auth UID).
// All fields have default values for Firestore deserialisation.
data class BankAccount(
    val id: String = "",

    /** Firebase Auth UID of the owning user */
    val userId: String = "",

    /** One of: Cheque, Savings, Credit, Crypto, Invest, Wallet */
    val accountType: String = "",

    /** One of: Standard, FNB, Absa, Nedbank, Capitec, Investec, Binance, Other */
    val bankName: String = "",

    /** User-provided label, e.g. "My Cheque Account" */
    val nickname: String = "",

    /** Stored as String to preserve leading zeros and formatting */
    val accountNumber: String = "",

    /** Opening / current balance in Rand */
    val balance: Double = 0.0,

    /** Hex colour chosen by the user, e.g. "#4ADE80" */
    val colour: String = "#4ADE80",

    /** Whether this account is counted in the dashboard total */
    val includeInTotal: Boolean = true,

    /** Whether this is the default account for new transactions */
    // @PropertyName maps Kotlin's is-prefixed boolean to Firestore field "primary"
    @get:PropertyName("primary") @set:PropertyName("primary")
    var isPrimary: Boolean = false
)