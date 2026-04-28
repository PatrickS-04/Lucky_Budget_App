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
import com.example.pulserds_prog7313.data.Category

/**
 * Displays [Category] rows with spend amount and percentage bars in the
 * Spending Breakdown card.
 *
 * Usage:
 *   1. Set [totals] (categoryId -> amount spent) via [submitData].
 *   2. The adapter computes the percentage of each category against the grand total
 *      and drives the [ProgressBar] accordingly.
 */
class SpendingRowAdapter : ListAdapter<Category, SpendingRowAdapter.RowViewHolder>(DIFF_CALLBACK) {

    // categoryId -> total amount spent; updated together with the category list
    private var totals: Map<Int, Double> = emptyMap()

    // Cached so the adapter can compute percentage in onBindViewHolder without re-summing each time
    private var grandTotal: Double = 0.0

    // Progress bar tint colours cycling through the original palette
    private val progressTints = listOf("#1A3A2A", "#2A1A1A", "#2EC4B6", "#1A1A3A", "#FFBF69", "#2EC4B6")

    /**
     * Call this instead of [submitList] when you have both new categories and new totals.
     * Triggers a single DiffUtil pass and a single grand-total recomputation.
     */
    fun submitData(categories: List<Category>, totals: Map<Int, Double>) {
        this.totals = totals
        this.grandTotal = totals.values.sumOf { it }.takeIf { it > 0.0 } ?: 1.0
        submitList(categories)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spending_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val category = getItem(position)
        val spent = totals[category.id] ?: 0.0
        val pct = ((spent / grandTotal) * 100).toInt().coerceIn(0, 100)
        val tint = progressTints[position % progressTints.size]
        holder.bind(category, spent, pct, tint)
    }

    // --- ViewHolder -----------------------------------------------------------

    inner class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: TextView = itemView.findViewById(R.id.rowIcon)
        private val name: TextView = itemView.findViewById(R.id.rowName)
        private val amount: TextView = itemView.findViewById(R.id.rowAmount)
        private val percent: TextView = itemView.findViewById(R.id.rowPercent)
        private val progress: ProgressBar = itemView.findViewById(R.id.rowProgress)

        fun bind(category: Category, spent: Double, pct: Int, tint: String) {
            icon.text = emojiForCategory(category.name)
            name.text = category.name
            amount.text = "R ${ "%,.0f".format(spent) }"
            percent.text = "$pct%"
            // Colour the percentage label red if the category is over-budget (>25 % of total)
            percent.setTextColor(if (pct > 25) Color.parseColor("#CC4400") else Color.parseColor("#7A6A50"))
            progress.progress = pct
            progress.progressTintList =
                android.content.res.ColorStateList.valueOf(Color.parseColor(tint))
        }
    }

    // --- Helpers --------------------------------------------------------------

    private fun emojiForCategory(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("grocer") || lower.contains("food") -> "🛒"
            lower.contains("din") || lower.contains("restaurant") || lower.contains("eat") -> "🍽️"
            lower.contains("transport") || lower.contains("fuel") || lower.contains("car") -> "🚗"
            lower.contains("entertain") || lower.contains("game") -> "🎮"
            lower.contains("subscri") || lower.contains("stream") || lower.contains("tv") -> "📺"
            lower.contains("util") || lower.contains("electric") || lower.contains("water") -> "⚡"
            lower.contains("health") || lower.contains("medical") || lower.contains("gym") -> "💊"
            lower.contains("cloth") || lower.contains("fashion") -> "👗"
            lower.contains("travel") || lower.contains("holiday") || lower.contains("flight") -> "✈️"
            lower.contains("edu") || lower.contains("school") || lower.contains("book") -> "📚"
            lower.contains("rent") || lower.contains("home") || lower.contains("hous") -> "🏠"
            lower.contains("insur") -> "🛡️"
            lower.contains("saving") || lower.contains("invest") -> "💰"
            else -> "📁"
        }
    }

    // --- DiffUtil -------------------------------------------------------------

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(oldItem: Category, newItem: Category) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Category, newItem: Category) =
                oldItem == newItem
        }
    }
}