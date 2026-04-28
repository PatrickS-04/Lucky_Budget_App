package com.example.pulserds_prog7313

import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.data.User
import org.junit.Assert.*
import org.junit.Test

//-------------------------------------------------------------------
// User model tests
//-------------------------------------------------------------------
class UserModelTest {

    @Test
    fun `user has correct default id of zero`() {
        val user = User(fullName = "Jane Doe", email = "jane@example.com", passwordHash = "hashed123")
        assertEquals(0, user.id)
    }

    @Test
    fun `two users with same data but different ids are not equal`() {
        val user1 = User(id = 1, fullName = "Jane Doe", email = "jane@example.com", passwordHash = "hashed123")
        val user2 = User(id = 2, fullName = "Jane Doe", email = "jane@example.com", passwordHash = "hashed123")
        assertNotEquals(user1, user2)
    }

    @Test
    fun `user copy produces independent instance with updated field`() {
        val original = User(id = 1, fullName = "Jane Doe", email = "jane@example.com", passwordHash = "hash")
        val updated = original.copy(email = "newemail@example.com")
        assertEquals("newemail@example.com", updated.email)
        assertEquals("jane@example.com", original.email) // original unchanged
    }
}

//-------------------------------------------------------------------
// BankAccount model tests
//-------------------------------------------------------------------
class BankAccountModelTest {

    private fun makeAccount(
        balance: Double = 1000.0,
        includeInTotal: Boolean = true,
        isPrimary: Boolean = false
    ) = BankAccount(
        userId = 1,
        accountType = "Cheque",
        bankName = "FNB",
        nickname = "My Cheque",
        accountNumber = "1234567890",
        balance = balance,
        includeInTotal = includeInTotal,
        isPrimary = isPrimary
    )

    @Test
    fun `default balance is zero when not provided`() {
        val account = BankAccount(
            userId = 1, accountType = "Savings", bankName = "Absa",
            nickname = "Savings", accountNumber = "0000000001"
        )
        assertEquals(0.0, account.balance, 0.001)
    }

    @Test
    fun `default colour is green hex`() {
        val account = makeAccount()
        assertEquals("#4ADE80", account.colour)
    }

    @Test
    fun `includeInTotal defaults to true`() {
        val account = makeAccount()
        assertTrue(account.includeInTotal)
    }

    @Test
    fun `isPrimary defaults to false`() {
        val account = makeAccount()
        assertFalse(account.isPrimary)
    }

    @Test
    fun `sum of included accounts matches dashboard total`() {
        val accounts = listOf(
            makeAccount(balance = 500.0, includeInTotal = true),
            makeAccount(balance = 1500.0, includeInTotal = true),
            makeAccount(balance = 9999.0, includeInTotal = false) // excluded
        )
        val dashboardTotal = accounts.filter { it.includeInTotal }.sumOf { it.balance }
        assertEquals(2000.0, dashboardTotal, 0.001)
    }

    @Test
    fun `only one account should be primary`() {
        val accounts = listOf(
            makeAccount(isPrimary = true),
            makeAccount(isPrimary = false),
            makeAccount(isPrimary = false)
        )
        val primaryCount = accounts.count { it.isPrimary }
        assertEquals(1, primaryCount)
    }
}

//-------------------------------------------------------------------
// SavingsGoal model tests
//-------------------------------------------------------------------
class SavingsGoalModelTest {

    private fun makeGoal(
        targetAmount: Double = 10_000.0,
        savedAmount: Double = 0.0,
        isCompleted: Boolean = false
    ) = SavingsGoal(
        userId = 1,
        name = "Emergency Fund",
        targetAmount = targetAmount,
        savedAmount = savedAmount,
        isCompleted = isCompleted
    )

    @Test
    fun `progress percentage is zero when nothing saved`() {
        val goal = makeGoal(savedAmount = 0.0)
        val progress = (goal.savedAmount / goal.targetAmount) * 100
        assertEquals(0.0, progress, 0.001)
    }

    @Test
    fun `progress percentage is 50 when half saved`() {
        val goal = makeGoal(targetAmount = 10_000.0, savedAmount = 5_000.0)
        val progress = (goal.savedAmount / goal.targetAmount) * 100
        assertEquals(50.0, progress, 0.001)
    }

    @Test
    fun `progress percentage does not exceed 100 when over-funded`() {
        val goal = makeGoal(targetAmount = 1_000.0, savedAmount = 1_500.0)
        val progress = minOf((goal.savedAmount / goal.targetAmount) * 100, 100.0)
        assertEquals(100.0, progress, 0.001)
    }

    @Test
    fun `remaining amount is zero when goal completed`() {
        val goal = makeGoal(targetAmount = 5_000.0, savedAmount = 5_000.0, isCompleted = true)
        val remaining = maxOf(goal.targetAmount - goal.savedAmount, 0.0)
        assertEquals(0.0, remaining, 0.001)
    }

    @Test
    fun `default icon is bank emoji`() {
        val goal = makeGoal()
        assertEquals("🏦", goal.icon)
    }

    @Test
    fun `default autoSaveFrequency is MONTHLY`() {
        val goal = makeGoal()
        assertEquals("MONTHLY", goal.autoSaveFrequency)
    }

    @Test
    fun `isCompleted is false by default`() {
        val goal = makeGoal()
        assertFalse(goal.isCompleted)
    }
}

//-------------------------------------------------------------------
// ExpenseEntry model tests
//-------------------------------------------------------------------
class ExpenseEntryModelTest {

    private fun makeEntry(amount: Double = 150.0, categoryId: Int = 1) = ExpenseEntry(
        userId = 1,
        categoryId = categoryId,
        amount = amount,
        date = "01/06/2025",
        startTime = "08:00",
        endTime = "09:00",
        description = "Test expense"
    )

    @Test
    fun `photoPath is null by default`() {
        val entry = makeEntry()
        assertNull(entry.photoPath)
    }

    @Test
    fun `default bankAccountId is zero (unknown)`() {
        val entry = makeEntry()
        assertEquals(0, entry.bankAccountId)
    }

    @Test
    fun `total of multiple expenses sums correctly`() {
        val entries = listOf(makeEntry(50.0), makeEntry(75.50), makeEntry(24.50))
        val total = entries.sumOf { it.amount }
        assertEquals(150.0, total, 0.001)
    }

    @Test
    fun `filtering entries by category returns correct subset`() {
        val entries = listOf(
            makeEntry(amount = 100.0, categoryId = 1),
            makeEntry(amount = 200.0, categoryId = 2),
            makeEntry(amount = 300.0, categoryId = 1)
        )
        val cat1Total = entries.filter { it.categoryId == 1 }.sumOf { it.amount }
        assertEquals(400.0, cat1Total, 0.001)
    }
}

//-------------------------------------------------------------------
// Category model tests
//-------------------------------------------------------------------
class CategoryModelTest {

    @Test
    fun `default category type is BOTH`() {
        val cat = Category(userId = 1, name = "General")
        assertEquals("BOTH", cat.type)
    }

    @Test
    fun `isDefault is false for user-created categories`() {
        val cat = Category(userId = 1, name = "Side Hustle", type = "INCOME")
        assertFalse(cat.isDefault)
    }

    @Test
    fun `only default categories are filtered correctly`() {
        val categories = listOf(
            Category(userId = 1, name = "Food", isDefault = true),
            Category(userId = 1, name = "Transport", isDefault = true),
            Category(userId = 1, name = "My Custom Cat", isDefault = false)
        )
        val defaultCats = categories.filter { it.isDefault }
        assertEquals(2, defaultCats.size)
    }

    @Test
    fun `expense categories are filtered correctly`() {
        val categories = listOf(
            Category(userId = 1, name = "Groceries", type = "EXPENSE"),
            Category(userId = 1, name = "Salary", type = "INCOME"),
            Category(userId = 1, name = "Mixed", type = "BOTH")
        )
        val expenseCats = categories.filter { it.type == "EXPENSE" || it.type == "BOTH" }
        assertEquals(2, expenseCats.size)
    }
}