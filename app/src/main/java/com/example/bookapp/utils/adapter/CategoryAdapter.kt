package com.example.bookapp.utils.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.bookapp.R
import com.example.bookapp.utils.model.Category

class CategoryAdapter(
    private val categories: List<Category>,
    private val onCategoryClick: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryName: TextView = itemView.findViewById(R.id.categoryName)

        fun bind(category: Category) {
            categoryName.text = category.name
            // Thay đổi màu nền và màu chữ khi được chọn
            if (category.isSelected) {
                categoryName.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.purple_200)
                )
                categoryName.setTextColor(
                    ContextCompat.getColor(itemView.context, android.R.color.white)
                )
            } else {
                categoryName.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, android.R.color.white)
                )
                categoryName.setTextColor(
                    ContextCompat.getColor(itemView.context, android.R.color.black)
                )
            }

            itemView.setOnClickListener {
                // Cập nhật trạng thái isSelected
                categories.forEach { it.isSelected = false }
                category.isSelected = true
                notifyDataSetChanged()
                onCategoryClick(category.name)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size
}