package com.example.bookapp.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bookapp.R
import com.example.bookapp.databinding.FragmentSearchBinding
import com.example.bookapp.utils.GridSpacingItemDecoration
import com.example.bookapp.utils.adapter.BookAdapter
import com.example.bookapp.utils.adapter.CategoryAdapter
import com.example.bookapp.utils.model.Book
import com.example.bookapp.utils.model.Category
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SearchFragment : Fragment() {

    private val TAG = "SearchFragment"
    private lateinit var binding: FragmentSearchBinding
    private lateinit var navController: NavController

    private lateinit var categoryAdapter: CategoryAdapter
    private val categories: MutableList<Category> = mutableListOf()
    private val selectedCategories: MutableList<String> = mutableListOf()

    private lateinit var searchResultsAdapter: BookAdapter
    private val searchResultsList: MutableList<Book> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        init(view)

        setupCategoriesRecyclerView()
        setupSearchResultsRecyclerView()

        // Xử lý tìm kiếm khi người dùng nhập từ khóa
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim().lowercase()
                if (query.isNotEmpty()) {
                    searchBooks(query, selectedCategories)
                } else {
                    searchResultsList.clear()
                    searchResultsAdapter.notifyDataSetChanged()
                }
            }
        })
    }

    private fun init(view: View) {
        navController = Navigation.findNavController(view)
        binding.searchResultsTitle.visibility = View.GONE // Ẩn tiêu đề kết quả ban đầu
    }

    private fun setupCategoriesRecyclerView() {
        val categoryNames = listOf(
            "Fiction", "Nonfiction", "Computers", "Economics", "Health",
            "Medical", "Science", "Self-Help", "Art", "Novel"
        )
        categories.clear()
        categoryNames.forEach { name ->
            categories.add(Category(name, false)) // Không chọn mặc định
        }

        categoryAdapter = CategoryAdapter(categories) { categoryName ->
            // Xử lý chọn/không chọn thể loại
            if (selectedCategories.contains(categoryName)) {
                selectedCategories.remove(categoryName)
            } else {
                selectedCategories.add(categoryName)
            }
            // Cập nhật danh sách thể loại
            categories.forEach { it.isSelected = selectedCategories.contains(it.name) }
            categoryAdapter.notifyDataSetChanged()

            // Tìm kiếm lại nếu có từ khóa
            val query = binding.searchEditText.text.toString().trim().lowercase()
            if (query.isNotEmpty()) {
                searchBooks(query, selectedCategories)
            }
        }
        binding.categoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }
    }

    private fun setupSearchResultsRecyclerView() {
        searchResultsAdapter = BookAdapter(searchResultsList) { book ->
            val bundle = Bundle().apply {
                putParcelable("book", book)
            }
            navController.navigate(R.id.action_searchFragment_to_bookDetailFragment, bundle)
        }
        binding.searchResultsRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = searchResultsAdapter
            addItemDecoration(GridSpacingItemDecoration(2, 16, true)) // Khoảng cách 16dp
        }
    }

    private fun searchBooks(query: String, selectedCategories: List<String>) {
        searchResultsList.clear()
        binding.searchResultsTitle.visibility = View.VISIBLE

        val booksRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("books")

        booksRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (bookSnapshot in snapshot.children) {
                    try {
                        val book = bookSnapshot.getValue(Book::class.java)?.copy(id = bookSnapshot.key ?: "")
                        book?.let {
                            // Kiểm tra từ khóa trong tiêu đề
                            val titleMatches = it.title?.lowercase()?.contains(query) == true

                            // Kiểm tra thể loại
                            val categoriesMatch = if (selectedCategories.isEmpty()) {
                                true // Nếu không chọn thể loại, lấy tất cả
                            } else {
                                it.categories?.any { category -> selectedCategories.contains(category) } == true
                            }

                            if (titleMatches && categoriesMatch) {
                                searchResultsList.add(it)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing book ${bookSnapshot.key}: ${e.message}")
                    }
                }

                if (searchResultsList.isNotEmpty()) {
                    searchResultsAdapter.notifyDataSetChanged()
                    Log.d(TAG, "Found ${searchResultsList.size} books matching query: $query")
                } else {
                    Toast.makeText(context, "No books found for query: $query", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to search books: ${error.message}")
                Toast.makeText(context, "Failed to search books: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}