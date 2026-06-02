package com.example.pulserds_prog7313.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.Subscription
import com.example.pulserds_prog7313.utils.CurrencyManager

// Displays active subscriptions in the 2-column tile grid on the dashboard
// and analytics screens. Reuses item_category_tile.xml; the icon, name, and
// amount fields map to the subscription's emoji icon, service name, and per-cycle cost.
// Tile background colours cycle through the four palette entries so adjacent
// tiles are always visually distinct regardless of list length.
class SubscriptionTileAdapter : ListAdapter<Subscription, SubscriptionTileAdapter.TileViewHolder>(DIFF) {

    // Background colours for each tile position (cycled via modulo).
    private val tileColors   = listOf("#1A3A2A", "#2A1A1A", "#1A1A3A", "#2A1E10")
    // Accent colours for the amount text that pair with each background.
    private val amountColors = listOf("#6ADB8A", "#FF9F9F", "#9A9AFF", "#FFBF69")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_tile, parent, false)
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val colorIndex = position % tileColors.size
        holder.bind(getItem(position), tileColors[colorIndex], amountColors[colorIndex])
    }

    inner class TileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card:   CardView = itemView as CardView
        private val icon:   TextView = itemView.findViewById(R.id.tileIcon)
        private val name:   TextView = itemView.findViewById(R.id.tileName)
        private val amount: TextView = itemView.findViewById(R.id.tileAmount)

        fun bind(sub: Subscription, bgColor: String, amountColor: String) {
            card.setCardBackgroundColor(Color.parseColor(bgColor))
            icon.text   = sub.icon.ifBlank { "📋" }
            name.text   = sub.serviceName
            // Abbreviate the billing cycle to keep the amount label concise on the tile
            val lbl     = when (sub.billingCycle) { "Weekly" -> "wk"; "Yearly" -> "yr"; else -> "mo" }
            amount.text = "${CurrencyManager.format(itemView.context, sub.amount)}/$lbl"
            amount.setTextColor(Color.parseColor(amountColor))
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Subscription>() {
            override fun areItemsTheSame(a: Subscription, b: Subscription) = a.id == b.id
            override fun areContentsTheSame(a: Subscription, b: Subscription) = a == b
        }
    }
}
