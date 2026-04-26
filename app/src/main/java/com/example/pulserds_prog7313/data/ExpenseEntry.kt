package com.example.pulserds_prog7313.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expense_entries",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_DEFAULT
        ),
        ForeignKey(
            entity = BankAccount::class,
            parentColumns = ["id"],
            childColumns = ["bankAccountId"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ],
    indices = [Index("bankAccountId")]
)
data class ExpenseEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val categoryId: Int,
    val bankAccountId: Int = 0,   // FK -> bank_accounts.id (0 = unknown/legacy)
    val amount: Double,
    val date: String,             // "dd/MM/yyyy"
    val startTime: String,
    val endTime: String,
    val description: String,
    val photoPath: String? = null
)
