package com.example.pulserds_prog7313.ui.adapters

import android.content.res.ColorStateList
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
import com.example.pulserds_prog7313.data.SavingsGoal
import kotlin.math.min
import kotlin.math.roundToInt

class DashSavingsAdapter :
    ListAdapter<SavingsGoal, DashSavingsAdapter.GoalViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GoalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dash_savings_row, parent, false)
        return GoalViewHolder(view)
    }

    override fun onBindViewHolder(holder: GoalViewHolder, position: Int) {
        holder.bind(getItem(position), isLast = position == itemCount - 1)
    }

    inner class GoalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: TextView          = itemView.findViewById(R.id.dashGoalIcon)
        private val name: TextView          = itemView.findViewById(R.id.dashGoalName)
        private val amounts: TextView       = itemView.findViewById(R.id.dashGoalAmounts)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.dashGoalProgressBar)
        private val percent: TextView       = itemView.findViewById(R.id.dashGoalPercent)
        private val divider: View           = itemView.findViewById(R.id.dashGoalDivider)

        fun bind(goal: SavingsGoal, isLast: Boolean) {
            icon.text = goal.icon.ifBlank { "🏦" }
            name.text = goal.name

            val saved  = goal.savedAmount
            val target = goal.targetAmount
            val pct    = if (target > 0) min((saved / target * 100).roundToInt(), 100) else 0
            val toGo   = target - saved

            amounts.text = "R %,.0f / R %,.0f".format(saved, target)
            progressBar.progress = pct

            val (barHex, labelHex, labelText) = when {
                pct >= 90 -> Triple("#22C55E", "#22C55E", "$pct% — almost there! 🔥")
                pct >= 50 -> Triple("#4ADE80", "#4ADE80", "$pct% reached")
                else      -> Triple("#60A5FA", "#60A5FA", "$pct% • R %,.0f to go".format(toGo))
            }
            progressBar.progressTintList = ColorStateList.valueOf(Color.parseColor(barHex))
            percent.text = labelText
            percent.setTextColor(Color.parseColor(labelHex))

            divider.visibility = if (isLast) View.GONE else View.VISIBLE
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SavingsGoal>() {
            override fun areItemsTheSame(a: SavingsGoal, b: SavingsGoal) = a.id == b.id
            override fun areContentsTheSame(a: SavingsGoal, b: SavingsGoal) = a == b
        }
    }
}
