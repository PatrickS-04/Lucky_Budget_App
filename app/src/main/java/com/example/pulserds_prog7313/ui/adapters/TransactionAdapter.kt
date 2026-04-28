package com.example.pulserds_prog7313.ui.adapters

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.BankAccount
import com.example.pulserds_prog7313.data.Category
import com.example.pulserds_prog7313.data.ExpenseEntry
import kotlin.math.abs

/**
 * RecyclerView adapter that renders [ExpenseEntry] rows.
 * Call [submitData] whenever the entries, categories, or accounts lists change.
 */
class TransactionAdapter : ListAdapter<ExpenseEntry, TransactionAdapter.TxViewHolder>(DIFF) {

    // -- State -----------------------------------------------------------------
    private var categories: Map<Int, Category> = emptyMap()
    private var accounts: Map<Int, BankAccount> = emptyMap()

    // -- Data submission -------------------------------------------------------
    fun submitData(
        entries: List<ExpenseEntry>,
        categories: List<Category>,
        accounts: List<BankAccount>
    ) {
        this.categories = categories.associateBy { it.id }
        this.accounts   = accounts.associateBy { it.id }
        submitList(entries)
    }

    // -- ViewHolder creation ---------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TxViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction_row, parent, false)
        return TxViewHolder(view)
    }

    // -- ViewHolder binding ----------------------------------------------------
    override fun onBindViewHolder(holder: TxViewHolder, position: Int) {
        holder.bind(getItem(position), categories, accounts)
    }

    // -- ViewHolder ------------------------------------------------------------
    inner class TxViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon        : TextView  = itemView.findViewById(R.id.txIcon)
        private val description : TextView  = itemView.findViewById(R.id.txDescription)
        private val subtitle    : TextView  = itemView.findViewById(R.id.txSubtitle)
        private val amount      : TextView  = itemView.findViewById(R.id.txAmount)
        private val date        : TextView  = itemView.findViewById(R.id.txDate)
        private val receiptBadge: CardView  = itemView.findViewById(R.id.txReceiptBadge)

        fun bind(
            entry: ExpenseEntry,
            categories: Map<Int, Category>,
            accounts: Map<Int, BankAccount>
        ) {
            val cat     = categories[entry.categoryId]
            val account = accounts[entry.bankAccountId]

            // Icon -- derived from category name
            val catName = cat?.name ?: ""
            icon.text = emojiFor(catName)

            // Description -- first line of the stored description
            description.text = entry.description.lines().firstOrNull()?.ifBlank { "Transaction" } ?: "Transaction"

            // Subtitle: "Category  •  AccountNickname (BankName)"
            val catLabel = cat?.name ?: "Uncategorized"
            val accLabel = account?.let { "${it.nickname} (${it.bankName})" } ?: "Unknown account"
            subtitle.text = "$catLabel  •  $accLabel"

            // Amount -- green for income, red for expense
            val isIncome = entry.amount >= 0
            val formatted = "R %,.2f".format(abs(entry.amount))
            amount.text = if (isIncome) "+ $formatted" else "- $formatted"
            amount.setTextColor(
                if (isIncome) Color.parseColor("#4ADE80") else Color.parseColor("#F87171")
            )

            // Date
            date.text = entry.date

            // Receipt badge -- show and wire up tap if a receipt URI is stored
            if (!entry.photoPath.isNullOrBlank()) {
                receiptBadge.visibility = View.VISIBLE
                receiptBadge.setOnClickListener {
                    val uri = Uri.parse(entry.photoPath)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, itemView.context.contentResolver.getType(uri))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    itemView.context.startActivity(Intent.createChooser(intent, "Open receipt"))
                }
            } else {
                receiptBadge.visibility = View.GONE
            }
        }
    }

    // -- Helpers ---------------------------------------------------------------
    private fun emojiFor(name: String): String {
        val l = name.lowercase()
        return when {
            l.contains("grocer") || l.contains("food") -> "🛒"
            l.contains("din") || l.contains("restaurant") || l.contains("eat") -> "🍽️"
            l.contains("transport") || l.contains("fuel") || l.contains("car") -> "🚗"
            l.contains("entertain") || l.contains("game") -> "🎮"
            l.contains("subscri") || l.contains("stream") || l.contains("tv") -> "📺"
            l.contains("util") || l.contains("electric") || l.contains("water") -> "⚡"
            l.contains("health") || l.contains("medical") || l.contains("gym") -> "💊"
            l.contains("cloth") || l.contains("fashion") -> "👗"
            l.contains("travel") || l.contains("holiday") || l.contains("flight") -> "✈️"
            l.contains("edu") || l.contains("school") || l.contains("book") -> "📚"
            l.contains("rent") || l.contains("home") || l.contains("hous") -> "🏠"
            l.contains("insur") -> "🛡️"
            l.contains("saving") || l.contains("invest") -> "💰"
            l.contains("income") || l.contains("salary") || l.contains("wage") -> "💼"
            else -> "📁"
        }
    }

    // -- DiffUtil callback -----------------------------------------------------
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ExpenseEntry>() {
            override fun areItemsTheSame(a: ExpenseEntry, b: ExpenseEntry) = a.id == b.id
            override fun areContentsTheSame(a: ExpenseEntry, b: ExpenseEntry) = a == b
        }
    }
}