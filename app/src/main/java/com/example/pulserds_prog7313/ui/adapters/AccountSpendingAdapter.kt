package com.example.pulserds_prog7313.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.AccountSpending
import com.example.pulserds_prog7313.data.BankAccount

/**
 * Displays per-[BankAccount] expense totals in the Spending by Account card.
 *
 * Call [submitData] with the full account list and the per-account spending map
 * (bankAccountId → absolute spend amount) whenever either changes.
 */
class AccountSpendingAdapter :
    ListAdapter<BankAccount, AccountSpendingAdapter.AccountViewHolder>(DIFF_CALLBACK) {

    /** bankAccountId → absolute amount spent this period */
    private var spending: Map<Int, Double> = emptyMap()
    private var grandTotal: Double = 1.0

    fun submitData(accounts: List<BankAccount>, accountSpending: List<AccountSpending>) {
        spending = accountSpending.associate { it.bankAccountId to kotlin.math.abs(it.total) }
        grandTotal = spending.values.sumOf { it }.takeIf { it > 0.0 } ?: 1.0
        // Only show accounts that have at least some spending this period, sorted descending
        val active = accounts
            .filter { (spending[it.id] ?: 0.0) > 0.0 }
            .sortedByDescending { spending[it.id] ?: 0.0 }
        submitList(active)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_account_spending_row, parent, false)
        return AccountViewHolder(view)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        val account = getItem(position)
        val spent = spending[account.id] ?: 0.0
        val pct = ((spent / grandTotal) * 100).toInt().coerceIn(0, 100)
        holder.bind(account, spent, pct)
    }

    inner class AccountViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dot: View        = itemView.findViewById(R.id.accountColourDot)
        private val name: TextView   = itemView.findViewById(R.id.accountName)
        private val amount: TextView = itemView.findViewById(R.id.accountAmount)
        private val bar: ProgressBar = itemView.findViewById(R.id.accountProgress)

        fun bind(account: BankAccount, spent: Double, pct: Int) {
            val colour = try { Color.parseColor(account.colour) } catch (e: Exception) { Color.parseColor("#1A3A2A") }

            dot.setBackgroundColor(colour)
            name.text = if (account.nickname.isNotBlank()) "${account.bankName} · ${account.nickname}"
            else account.bankName
            amount.text = "R ${ "%,.0f".format(spent) }"
            bar.progress = pct
            bar.progressTintList = android.content.res.ColorStateList.valueOf(colour)
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BankAccount>() {
            override fun areItemsTheSame(old: BankAccount, new: BankAccount) = old.id == new.id
            override fun areContentsTheSame(old: BankAccount, new: BankAccount) = old == new
        }
    }
}