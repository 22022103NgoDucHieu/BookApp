package com.example.bookapp.utils.adapter


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.bookapp.R
import com.example.bookapp.utils.model.Book
import com.squareup.picasso.Picasso

class BookAdapter(
    private val bookList: List<Book>,
    private val onBookClick: (Book) -> Unit
) : RecyclerView.Adapter<BookAdapter.BookViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_book, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = bookList[position]
        holder.bind(book)
    }

    override fun getItemCount(): Int = bookList.size

    inner class BookViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.bookThumbnail)

        fun bind(book: Book) {
            if (!book.thumbnail.isNullOrEmpty()) {
                Picasso.get()
                    .load(book.thumbnail)
                    .placeholder(R.drawable.placeholder_book)
                    .error(R.drawable.error_book)
                    .into(thumbnail)
            } else {
                // Nếu thumbnail rỗng hoặc null, hiển thị hình ảnh mặc định
                thumbnail.setImageResource(R.drawable.placeholder_book)
            }

            itemView.setOnClickListener {
                onBookClick(book)
            }
        }
    }
}