package com.example.pulserds_prog7313

import com.example.pulserds_prog7313.data.AccountSpending
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import com.example.pulserds_prog7313.data.SavingsGoal
import com.example.pulserds_prog7313.data.User
import com.example.pulserds_prog7313.service.SpendingInsight
import com.example.pulserds_prog7313.utils.CurrencyManager
import org.junit.Assert.*
import org.junit.Test

// -------------------------------------------------------------------
// User model tests
// -------------------------------------------------------------------
class UserModelTest {

    // Verifies that the default id assigned to a new User is 0
    @Test
    fun `user has correct default id of empty string`() {
        val user = User(fullName = "Jane Doe", email = "jane@example.com")
        assertEquals("", user.id)
    }

    // Two users with identical fields but different ids must not be considered equal
    @Test
    fun `two users with same data but different ids are not equal`() {
        val user1 = User(id = "uid_1", fullName = "Jane Doe", email = "jane@example.com")
        val user2 = User(id = "uid_2", fullName = "Jane Doe", email = "jane@example.com")
        assertNotEquals(user1, user2)
    }

    // copy() must return a new instance with the changed field while leaving the original intact
    @Test
    fun `user copy produces independent instance with updated field`() {
        val original = User(id = "uid_1", fullName = "Jane Doe", email = "jane@example.com")
        val updated = original.copy(email = "newemail@example.com")
        assertEquals("newemail@example.com", updated.email)
        assertEquals("jane@example.com", original.email) // original unchanged
    }
}

// -------------------------------------------------------------------
// BankAccount model tests
// -------------------------------------------------------------------
class BankAccountModelTest {

    // -- Helper ---------------------------------------------------------------
    private fun makeAccount(
        balance: Double = 1000.0,
        includeInTotal: Boolean = true,
        isPrimary: Boolean = false
    ) = BankAccount(
        userId = "uid_1",
        accountType = "Cheque",
        bankName = "FNB",
        nickname = "My Cheque",
        accountNumber = "1234567890",
        balance = balance,
        includeInTotal = includeInTotal,
        isPrimary = isPrimary
    )

    // -- Default value tests --------------------------------------------------

    @Test
    fun `default balance is zero when not provided`() {
        val account = BankAccount(
            userId = "uid_1", accountType = "Savings", bankName = "Absa",
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

    // -- Business logic tests -------------------------------------------------

    // Only accounts with includeInTotal = true should contribute to the dashboard total
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

    // Exactly one account in the list should be flagged as primary
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

// -------------------------------------------------------------------
// SavingsGoal model tests
// -------------------------------------------------------------------
class SavingsGoalModelTest {

    // -- Helper ---------------------------------------------------------------
    private fun makeGoal(
        targetAmount: Double = 10_000.0,
        savedAmount: Double = 0.0,
        isCompleted: Boolean = false
    ) = SavingsGoal(
        userId = "uid_1",
        name = "Emergency Fund",
        targetAmount = targetAmount,
        savedAmount = savedAmount,
        isCompleted = isCompleted
    )

    // -- Progress calculation tests -------------------------------------------

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

    // Progress must be capped at 100 even when savedAmount exceeds targetAmount
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

    // -- Default value tests --------------------------------------------------

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

// -------------------------------------------------------------------
// ExpenseEntry model tests
// -------------------------------------------------------------------
class ExpenseEntryModelTest {

    // -- Helper ---------------------------------------------------------------
    private fun makeEntry(amount: Double = 150.0, categoryId: String = "cat_1") = ExpenseEntry(
        userId = "uid_1",
        categoryId = categoryId,
        amount = amount,
        date = "01/06/2025",
        startTime = "08:00",
        endTime = "09:00",
        description = "Test expense"
    )


    // -- Default value tests --------------------------------------------------

    @Test
    fun `photoPath is null by default`() {
        val entry = makeEntry()
        assertNull(entry.photoPath)
    }

    @Test
    fun `default bankAccountId is empty string when not provided`() {
        val entry = makeEntry()
        assertEquals("", entry.bankAccountId)
    }

    // -- Aggregation tests ----------------------------------------------------

    @Test
    fun `total of multiple expenses sums correctly`() {
        val entries = listOf(makeEntry(50.0), makeEntry(75.50), makeEntry(24.50))
        val total = entries.sumOf { it.amount }
        assertEquals(150.0, total, 0.001)
    }

    // Filtering by categoryId and summing should return only matching entries
    @Test
    fun `filtering entries by category returns correct subset`() {
        val entries = listOf(
            makeEntry(amount = 100.0, categoryId = "cat_1"),
            makeEntry(amount = 200.0, categoryId = "cat_2"),
            makeEntry(amount = 300.0, categoryId = "cat_1")
        )
        val cat1Total = entries.filter { it.categoryId == "cat_1" }.sumOf { it.amount }
        assertEquals(400.0, cat1Total, 0.001)
    }
}

// -------------------------------------------------------------------
// Category model tests
// -------------------------------------------------------------------
class CategoryModelTest {

    // -- Default value tests --------------------------------------------------

    @Test
    fun `default category type is BOTH`() {
        val cat = Category(userId = "uid_1", name = "General")
        assertEquals("BOTH", cat.type)
    }

    @Test
    fun `isDefault is false for user-created categories`() {
        val cat = Category(userId = "uid_1", name = "Side Hustle", type = "INCOME")
        assertFalse(cat.isDefault)
    }

    // -- Filtering tests ------------------------------------------------------

    // Only categories flagged isDefault = true should be returned by the default filter
    @Test
    fun `only default categories are filtered correctly`() {
        val categories = listOf(
            Category(userId = "uid_1", name = "Food", isDefault = true),
            Category(userId = "uid_1", name = "Transport", isDefault = true),
            Category(userId = "uid_1", name = "My Custom Cat", isDefault = false)
        )
        val defaultCats = categories.filter { it.isDefault }
        assertEquals(2, defaultCats.size)
    }

    // EXPENSE and BOTH categories should both appear in the expense dropdown
    @Test
    fun `expense categories are filtered correctly`() {
        val categories = listOf(
            Category(userId = "uid_1", name = "Groceries", type = "EXPENSE"),
            Category(userId = "uid_1", name = "Salary", type = "INCOME"),
            Category(userId = "uid_1", name = "Mixed", type = "BOTH")
        )
        val expenseCats = categories.filter { it.type == "EXPENSE" || it.type == "BOTH" }
        assertEquals(2, expenseCats.size)
    }

    // Category.isDefault is mutable (var) so it can be set post-construction
    @Test
    fun `isDefault can be mutated via var assignment`() {
        val cat = Category(userId = "uid_1", name = "Food")
        cat.isDefault = true
        assertTrue(cat.isDefault)
    }
}

// -------------------------------------------------------------------
// SavingsGoal – spending-band fields (new in Spending-Oversight branch)
// -------------------------------------------------------------------
class SavingsGoalSpendingBandTest {

    private fun makeGoal(
        minSpendingGoal: Double = 0.0,
        maxSpendingGoal: Double = 0.0
    ) = SavingsGoal(
        userId = "uid_1",
        name = "Groceries Goal",
        targetAmount = 5_000.0,
        minSpendingGoal = minSpendingGoal,
        maxSpendingGoal = maxSpendingGoal
    )

    @Test
    fun `minSpendingGoal defaults to zero`() {
        val goal = makeGoal()
        assertEquals(0.0, goal.minSpendingGoal, 0.001)
    }

    @Test
    fun `maxSpendingGoal defaults to zero`() {
        val goal = makeGoal()
        assertEquals(0.0, goal.maxSpendingGoal, 0.001)
    }

    @Test
    fun `minSpendingGoal and maxSpendingGoal are stored correctly`() {
        val goal = makeGoal(minSpendingGoal = 500.0, maxSpendingGoal = 2_000.0)
        assertEquals(500.0, goal.minSpendingGoal, 0.001)
        assertEquals(2_000.0, goal.maxSpendingGoal, 0.001)
    }

    @Test
    fun `spending band is valid when max is greater than min`() {
        val goal = makeGoal(minSpendingGoal = 300.0, maxSpendingGoal = 1_500.0)
        assertTrue(goal.maxSpendingGoal > goal.minSpendingGoal)
    }

    // A goal with no band set (both == 0) should be treated as "not configured"
    @Test
    fun `spending band is considered unset when both values are zero`() {
        val goal = makeGoal(minSpendingGoal = 0.0, maxSpendingGoal = 0.0)
        val bandActive = goal.minSpendingGoal > 0 || goal.maxSpendingGoal > 0
        assertFalse(bandActive)
    }
}

// -------------------------------------------------------------------
// SpendingGoalChartView – bar colour classification logic
// (mirrors the when-expression inside onDraw without Android dependencies)
// -------------------------------------------------------------------
class SpendingGoalChartColorTest {

    private enum class BarColor { OK, LOW, HIGH }

    private fun classifyBar(amount: Double, minGoal: Double, maxGoal: Double): BarColor = when {
        maxGoal > 0 && amount > maxGoal -> BarColor.HIGH
        minGoal > 0 && amount < minGoal && amount > 0 -> BarColor.LOW
        else -> BarColor.OK
    }

    @Test
    fun `amount above maxGoal is classified as HIGH`() {
        assertEquals(BarColor.HIGH, classifyBar(amount = 3_000.0, minGoal = 500.0, maxGoal = 2_000.0))
    }

    @Test
    fun `amount below minGoal is classified as LOW`() {
        assertEquals(BarColor.LOW, classifyBar(amount = 100.0, minGoal = 500.0, maxGoal = 2_000.0))
    }

    @Test
    fun `amount within band is classified as OK`() {
        assertEquals(BarColor.OK, classifyBar(amount = 1_000.0, minGoal = 500.0, maxGoal = 2_000.0))
    }

    @Test
    fun `zero amount with minGoal set is classified as OK (not LOW)`() {
        // Zero spend days should not flash amber — the condition requires amount > 0
        assertEquals(BarColor.OK, classifyBar(amount = 0.0, minGoal = 500.0, maxGoal = 2_000.0))
    }

    @Test
    fun `amount exactly at maxGoal is classified as OK`() {
        assertEquals(BarColor.OK, classifyBar(amount = 2_000.0, minGoal = 500.0, maxGoal = 2_000.0))
    }

    @Test
    fun `amount exactly at minGoal is classified as OK`() {
        assertEquals(BarColor.OK, classifyBar(amount = 500.0, minGoal = 500.0, maxGoal = 2_000.0))
    }

    // When no band is configured (maxGoal == 0) all bars should default to OK
    @Test
    fun `any amount is OK when maxGoal is zero`() {
        assertEquals(BarColor.OK, classifyBar(amount = 99_999.0, minGoal = 0.0, maxGoal = 0.0))
    }
}

// -------------------------------------------------------------------
// CurrencyManager – context-free tests (CURRENCIES list & Currency model)
// -------------------------------------------------------------------
class CurrencyManagerTest {

    @Test
    fun `CURRENCIES list contains exactly five entries`() {
        assertEquals(5, CurrencyManager.CURRENCIES.size)
    }

    @Test
    fun `first currency is ZAR with symbol R`() {
        val zar = CurrencyManager.CURRENCIES[0]
        assertEquals("ZAR", zar.code)
        assertEquals("R", zar.symbol)
    }

    @Test
    fun `all currency codes are unique`() {
        val codes = CurrencyManager.CURRENCIES.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `all currency symbols are non-empty`() {
        CurrencyManager.CURRENCIES.forEach { currency ->
            assertTrue("Symbol for ${currency.code} is blank", currency.symbol.isNotBlank())
        }
    }

    @Test
    fun `USD entry has correct symbol`() {
        val usd = CurrencyManager.CURRENCIES.first { it.code == "USD" }
        assertEquals("$", usd.symbol)
    }

    @Test
    fun `BWP entry is present`() {
        val bwp = CurrencyManager.CURRENCIES.find { it.code == "BWP" }
        assertNotNull(bwp)
        assertEquals("P", bwp!!.symbol)
    }
}

// -------------------------------------------------------------------
// SpendingInsight – data class & GeminiSpendingService helper logic
// -------------------------------------------------------------------
class SpendingInsightTest {

    @Test
    fun `SpendingInsight stores all fields correctly`() {
        val insight = SpendingInsight(
            summary = "Good month.",
            overspendingWarnings = listOf("Dining out too much"),
            budgetSuggestions = listOf("Cut subscriptions"),
            savingsAdvice = "Stay on track.",
            topRisk = "Credit card debt"
        )
        assertEquals("Good month.", insight.summary)
        assertEquals(1, insight.overspendingWarnings.size)
        assertEquals(1, insight.budgetSuggestions.size)
        assertEquals("Stay on track.", insight.savingsAdvice)
        assertEquals("Credit card debt", insight.topRisk)
    }

    @Test
    fun `SpendingInsight supports empty warning and suggestion lists`() {
        val insight = SpendingInsight(
            summary = "No data.",
            overspendingWarnings = emptyList(),
            budgetSuggestions = emptyList(),
            savingsAdvice = "",
            topRisk = ""
        )
        assertTrue(insight.overspendingWarnings.isEmpty())
        assertTrue(insight.budgetSuggestions.isEmpty())
    }

    // Mirror the income/expense/saved calculation used in GeminiSpendingService.analyzeSpending
    @Test
    fun `income and expense split from entries is calculated correctly`() {
        val amounts = listOf(5_000.0, -1_200.0, -800.0, 200.0)
        val income     = amounts.filter { it > 0 }.sum()
        val totalSpend = amounts.filter { it < 0 }.sumOf { kotlin.math.abs(it) }
        val saved      = (income - totalSpend).coerceAtLeast(0.0)

        assertEquals(5_200.0, income, 0.001)
        assertEquals(2_000.0, totalSpend, 0.001)
        assertEquals(3_200.0, saved, 0.001)
    }

    @Test
    fun `saved is clamped to zero when expenses exceed income`() {
        val amounts = listOf(1_000.0, -4_000.0)
        val income     = amounts.filter { it > 0 }.sum()
        val totalSpend = amounts.filter { it < 0 }.sumOf { kotlin.math.abs(it) }
        val saved      = (income - totalSpend).coerceAtLeast(0.0)

        assertEquals(0.0, saved, 0.001)
    }

    @Test
    fun `savings rate is zero when income is zero`() {
        val income = 0.0
        val saved  = 0.0
        val rate   = if (income > 0) saved / income * 100 else 0.0
        assertEquals(0.0, rate, 0.001)
    }
}

// -------------------------------------------------------------------
// AccountSpending – data class tests
// -------------------------------------------------------------------
class AccountSpendingTest {

    @Test
    fun `AccountSpending stores bankAccountId and total correctly`() {
        val spend = AccountSpending(bankAccountId = "acc_1", total = 3_500.0)
        assertEquals("acc_1", spend.bankAccountId)
        assertEquals(3_500.0, spend.total, 0.001)
    }

    @Test
    fun `summing AccountSpending list gives correct total`() {
        val spendings = listOf(
            AccountSpending("acc_1", 1_000.0),
            AccountSpending("acc_2", 2_500.0),
            AccountSpending("acc_3", 500.0)
        )
        val grandTotal = spendings.sumOf { it.total }
        assertEquals(4_000.0, grandTotal, 0.001)
    }

    @Test
    fun `filtering AccountSpending by account id works correctly`() {
        val spendings = listOf(
            AccountSpending("acc_1", 1_000.0),
            AccountSpending("acc_2", 2_500.0)
        )
        val found = spendings.find { it.bankAccountId == "acc_2" }
        assertNotNull(found)
        assertEquals(2_500.0, found!!.total, 0.001)
    }
}