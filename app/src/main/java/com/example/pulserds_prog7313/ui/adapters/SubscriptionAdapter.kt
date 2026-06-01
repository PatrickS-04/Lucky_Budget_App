package com.example.pulserds_prog7313.ui.adapters

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

/**
 * RecyclerView adapter for the ManageSubscriptionsActivity list.
 *
 * Uses ListAdapter + DiffUtil for efficient, animated updates when the
 * Firestore LiveData emits a new list.
 *
 * Each item shows:
 *  - Icon (emoji in a coloured card)
 *  - Service name + category + linked bank
 *  - Renewal date badge
 *  - Amount / cycle
 *  - Auto-pay status indicator
 *
 * Callbacks:
 *  - onToggleStatus  → called when the row is long-pressed (Active ↔ Paused)
 *  - onDelete        → called when delete is requested from a context action
 */
class SubscriptionAdapter(
    private val onToggleStatus: (Subscription) -> Unit,
    private val onDelete: (Subscription) -> Unit
) : ListAdapter<Subscription, SubscriptionAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val iconCard:       CardView = itemView.findViewById(R.id.subIconCard)
        val iconText:       TextView = itemView.findViewById(R.id.subIconText)
        val nameText:       TextView = itemView.findViewById(R.id.subNameText)
        val detailText:     TextView = itemView.findViewById(R.id.subDetailText)
        val renewalBadge:   CardView = itemView.findViewById(R.id.subRenewalBadge)
        val renewalText:    TextView = itemView.findViewById(R.id.subRenewalText)
        val amountText:     TextView = itemView.findViewById(R.id.subAmountText)
        val autoPayText:    TextView = itemView.findViewById(R.id.subAutoPayText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subscription_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sub = getItem(position)
        val isPaused = sub.status == "Paused"

        // -- Icon --
        holder.iconText.text = sub.icon

        // -- Name --
        holder.nameText.text = sub.serviceName
        holder.nameText.alpha = if (isPaused) 0.5f else 1.0f

        // -- Detail subtitle: "Category  •  Bank nickname" --
        holder.detailText.text = buildString {
            append(sub.category)
            if (sub.bankAccountId.isNotBlank()) append("  •  Linked")
        }

        // -- Renewal badge --
        if (isPaused) {
            holder.renewalText.text = "  ⏸ Paused  "
            holder.renewalText.setTextColor(0xFFFBBF24.toInt())
            holder.renewalBadge.setCardBackgroundColor(0xFF2A2000.toInt())
        } else {
            holder.renewalText.text = "  Renews ${sub.nextRenewal}  "
            holder.renewalText.setTextColor(0xFFFFA040.toInt())
            holder.renewalBadge.setCardBackgroundColor(0xFF2A1000.toInt())
        }

        // -- Amount --
        val cycleLabel = when (sub.billingCycle) {
            "Weekly"  -> "wk"
            "Yearly"  -> "yr"
            else      -> "mo"
        }
        holder.amountText.text = "${com.example.pulserds_prog7313.utils.CurrencyManager.format(holder.itemView.context, sub.amount)}/$cycleLabel"
        holder.amountText.alpha = if (isPaused) 0.5f else 1.0f

        // -- Auto-pay label --
        if (isPaused) {
            holder.autoPayText.text = "Auto-pay OFF"
            holder.autoPayText.setTextColor(0xFFFBBF24.toInt())
        } else if (sub.autoPay) {
            holder.autoPayText.text = "Auto-pay ON"
            holder.autoPayText.setTextColor(0xFF4ADE80.toInt())
        } else {
            holder.autoPayText.text = "Auto-pay OFF"
            holder.autoPayText.setTextColor(0xFFFBBF24.toInt())
        }

        // -- Long-press → toggle Active/Paused --
        holder.itemView.setOnLongClickListener {
            onToggleStatus(sub)
            true
        }

        // -- Click → show delete confirmation --
        holder.itemView.setOnClickListener {
            // Delegate to the activity via a simple delete callback;
            // the activity will show a confirmation dialog
            onDelete(sub)
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Subscription>() {
        override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription) =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription) =
            oldItem == newItem
    }
}