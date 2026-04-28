package com.example.pulserds_prog7313.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.Category

/**
 * Displays [Category] objects from the database in the 2-column Budget Categories grid.
 *
 * Tile background colours cycle through the four palette colours used in the original
 * hard-coded layout so the visual design is preserved regardless of how many categories
 * the user has created.
 *
 * Once ExpenseEntry totals are wired up, pass a Map<Int, Double> (categoryId -> total)
 * into [submitCategories] and the tile amount will update automatically.
 */
class CategoryTileAdapter(
    initialTotals: Map<Int, Double> = emptyMap()
) : ListAdapter<Category, CategoryTileAdapter.TileViewHolder>(DIFF_CALLBACK) {

    private var totals: Map<Int, Double> = initialTotals

    /**
     * Submit both new categories and updated spending totals at once.
     * Triggers a single DiffUtil pass.
     */
    fun submitCategories(categories: List<Category>, newTotals: Map<Int, Double>) {
        totals = newTotals
        submitList(categories)
    }

    // Palette matches the original XML tile colours (dark-green, dark-maroon, dark-navy, dark-brown)
    private val tileColors = listOf("#1A3A2A", "#2A1A1A", "#1A1A3A", "#2A1E10")

    // Amount text colours that pair with each background
    private val amountColors = listOf("#6ADB8A", "#FF9F9F", "#9A9AFF", "#FFBF69")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_tile, parent, false)
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val category = getItem(position)
        val colorIndex = position % tileColors.size

        holder.bind(
            category = category,
            bgColor = tileColors[colorIndex],
            amountColor = amountColors[colorIndex],
            total = totals[category.id] ?: 0.0
        )
    }

    // --- ViewHolder -----------------------------------------------------------

    inner class TileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView = itemView as androidx.cardview.widget.CardView
        private val icon: TextView = itemView.findViewById(R.id.tileIcon)
        private val name: TextView = itemView.findViewById(R.id.tileName)
        private val amount: TextView = itemView.findViewById(R.id.tileAmount)

        fun bind(category: Category, bgColor: String, amountColor: String, total: Double) {
            // Apply cycling background colour
            cardView.setCardBackgroundColor(android.graphics.Color.parseColor(bgColor))

            // Pick an emoji that loosely matches common category names; fall back to 📁
            icon.text = emojiForCategory(category.name)

            name.text = category.name

            // Format amount -- shows "R 0" until expense totals are available
            amount.text = if (total == 0.0) "R 0" else "R ${"%,.0f".format(total)}"
            amount.setTextColor(android.graphics.Color.parseColor(amountColor))
        }
    }

    // --- Helpers --------------------------------------------------------------

    /**
     * Returns a best-guess emoji for well-known category names.
     * Matching is case-insensitive and partial so "grocery", "groceries", etc. all match.
     */
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
            lower.contains("cloth") || lower.contains("fashion") || lower.contains("apparel") -> "👗"
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