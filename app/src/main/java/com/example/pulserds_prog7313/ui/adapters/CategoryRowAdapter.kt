package com.example.pulserds_prog7313.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pulserds_prog7313.R
import com.example.pulserds_prog7313.data.Category

// Displays a list of categories, each row showing the name, type badge,
// and an optional delete button controlled by the showDelete flag.
class CategoryRowAdapter(
    private val showDelete: Boolean,
    private val onDelete: (Category) -> Unit
) : ListAdapter<Category, CategoryRowAdapter.ViewHolder>(DIFF) {

    // -- ViewHolder ------------------------------------------------------------
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.categoryName)
        val badge: TextView = view.findViewById(R.id.categoryTypeBadge)
        val deleteBtn: ImageButton = view.findViewById(R.id.deleteCategoryBtn)
    }

    // -- ViewHolder creation ---------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_row, parent, false)
        return ViewHolder(view)
    }

    // -- ViewHolder binding ----------------------------------------------------
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cat = getItem(position)
        holder.name.text = cat.name
        holder.badge.text = cat.type

        // Colour-code the badge by category type
        holder.badge.setBackgroundColor(
            when (cat.type) {
                "INCOME"  -> 0xFFDCFCE7.toInt()
                "EXPENSE" -> 0xFFFFE4E6.toInt()
                else      -> 0xFFE0F2FE.toInt()
            }
        )

        // Show or hide delete button based on caller's preference
        if (showDelete) {
            holder.deleteBtn.visibility = View.VISIBLE
            holder.deleteBtn.setOnClickListener { onDelete(cat) }
        } else {
            holder.deleteBtn.visibility = View.GONE
        }
    }

    // -- DiffUtil callback -----------------------------------------------------
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Category>() {
            override fun areItemsTheSame(a: Category, b: Category) = a.id == b.id
            override fun areContentsTheSame(a: Category, b: Category) = a == b
        }
    }
}