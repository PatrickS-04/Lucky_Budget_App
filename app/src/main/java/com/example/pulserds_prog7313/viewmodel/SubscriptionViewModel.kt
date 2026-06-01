package com.example.pulserds_prog7313.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulserds_prog7313.data.Subscription
import com.example.pulserds_prog7313.repository.SubscriptionRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * SubscriptionViewModel owns all subscription-related state for the UI.
 *
 * Follows the same pattern as BankAccountViewModel:
 *  - init(userId)      kicks off the real-time LiveData stream
 *  - addSubscription() validates input before writing to the repository
 *  - toggleStatus()    flips between "Active" and "Paused"
 *  - delete()          removes a subscription
 *  - saveResult        single-use event indicating success or failure
 */
class SubscriptionViewModel(
    private val repo: SubscriptionRepository
) : ViewModel() {

    // -- Exposed state ---------------------------------------------------------

    /** Real-time list of all subscriptions for the current user. */
    lateinit var subscriptions: LiveData<List<Subscription>>
        private set

    /** Monthly total spend across active subscriptions. */
    private val _monthlyTotal = MutableLiveData(0.0)
    val monthlyTotal: LiveData<Double> get() = _monthlyTotal

    /** One-shot result event emitted after add/update operations. */
    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> get() = _saveResult

    // -- Initialisation --------------------------------------------------------

    fun init(userId: String) {
        if (!::subscriptions.isInitialized) {
            subscriptions = repo.getSubscriptionsForUser(userId)
        }
        refreshMonthlyTotal(userId)
    }

    fun refreshMonthlyTotal(userId: String) {
        viewModelScope.launch {
            _monthlyTotal.value = repo.getMonthlyTotal(userId)
        }
    }

    // -- Mutations -------------------------------------------------------------

    /**
     * Validates form inputs, computes the next renewal date, then persists
     * the subscription to Firestore.
     *
     * On success → posts SaveResult.Success.
     * On validation or write failure → posts SaveResult.Error with a message.
     */
    fun addSubscription(
        userId: String,
        serviceName: String,
        amount: Double,
        billingCycle: String,
        category: String,
        bankAccountId: String,
        startDate: String,
        note: String,
        autoPay: Boolean,
        reminderOn: Boolean,
        reminderDays: Int,
        icon: String
    ) {
        // -- Input validation --
        val error = validate(serviceName, amount, billingCycle, category, bankAccountId, startDate)
        if (error != null) {
            _saveResult.value = SaveResult.Error(error)
            return
        }

        val nextRenewal = computeNextRenewal(startDate, billingCycle)

        viewModelScope.launch {
            val subscription = Subscription(
                userId        = userId,
                serviceName   = serviceName.trim(),
                amount        = amount,
                billingCycle  = billingCycle,
                category      = category,
                bankAccountId = bankAccountId,
                startDate     = startDate,
                nextRenewal   = nextRenewal,
                note          = note.trim(),
                autoPay      = autoPay,
                reminderOn   = reminderOn,
                reminderDays  = reminderDays,
                status        = "Active",
                icon          = icon
            )
            val newId = repo.addSubscription(subscription)
            if (newId.isNotBlank()) {
                refreshMonthlyTotal(userId)
                _saveResult.value = SaveResult.Success
            } else {
                _saveResult.value = SaveResult.Error("Could not save subscription. Please try again.")
            }
        }
    }

    /** Toggles a subscription between "Active" and "Paused". */
    fun toggleStatus(subscription: Subscription, userId: String) {
        val newStatus = if (subscription.status == "Active") "Paused" else "Active"
        viewModelScope.launch {
            repo.updateSubscription(subscription.copy(status = newStatus))
            refreshMonthlyTotal(userId)
        }
    }

    /** Hard-deletes a subscription. */
    fun deleteSubscription(subscription: Subscription, userId: String) {
        viewModelScope.launch {
            repo.deleteSubscription(subscription)
            refreshMonthlyTotal(userId)
        }
    }

    // -- Helpers ---------------------------------------------------------------

    /**
     * Validates subscription form fields. Returns an error message string, or
     * null if everything is valid.
     */
    private fun validate(
        serviceName: String,
        amount: Double,
        billingCycle: String,
        category: String,
        bankAccountId: String,
        startDate: String
    ): String? = when {
        serviceName.isBlank()   -> "Please enter a service name."
        amount <= 0             -> "Amount must be greater than zero."
        billingCycle.isBlank()  -> "Please select a billing cycle."
        category.isBlank()      -> "Please select a category."
        bankAccountId.isBlank() -> "Please select a linked account."
        startDate.isBlank()     -> "Please select a start date."
        else                    -> null
    }

    /**
     * Given a [startDate] in "dd/MM/yyyy" format and a [billingCycle],
     * computes the next renewal date from today.
     *
     * Logic:
     *  1. Parse the start date.
     *  2. Advance by one billing cycle at a time until the date is >= today.
     *  3. Return the resulting date as "dd/MM/yyyy".
     */
    fun computeNextRenewal(startDate: String, billingCycle: String): String {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val start = sdf.parse(startDate) ?: return startDate
            val cal = Calendar.getInstance().apply { time = start }
            val today = Calendar.getInstance()

            // Advance until we're in the future relative to today
            while (!cal.after(today)) {
                when (billingCycle) {
                    "Weekly"  -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    "Monthly" -> cal.add(Calendar.MONTH, 1)
                    "Yearly"  -> cal.add(Calendar.YEAR, 1)
                }
            }
            sdf.format(cal.time)
        } catch (e: Exception) {
            startDate
        }
    }

    // -- Result type -----------------------------------------------------------

    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}