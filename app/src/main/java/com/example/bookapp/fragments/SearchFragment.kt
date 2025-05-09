package com.example.bookapp.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bookapp.R
import com.example.bookapp.api.BookItem
import com.example.bookapp.api.GoogleBooksResponse
import com.example.bookapp.api.RetrofitClient
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
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SearchFragment : Fragment() {

    private val TAG = "SearchFragment"
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var navController: NavController

    private lateinit var categoryAdapter: CategoryAdapter
    private val categories: MutableList<Category> = mutableListOf()
    private val selectedCategories: MutableList<String> = mutableListOf()

    private lateinit var searchResultsAdapter: BookAdapter
    private val searchResultsList: MutableList<Book> = mutableListOf()
    private val firebaseBooks: MutableList<Book> = mutableListOf()
    private val allMatchingBooks: MutableList<Book> = mutableListOf()
    private val booksPerPage = 6
    private var currentQuery: String = ""
    private var startIndex = 0
    private var hasMoreBooks = true
    private val allowedBookIds: MutableSet<String> = mutableSetOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        init(view)

        setupCategoriesRecyclerView()
        setupSearchResultsRecyclerView()

        loadAllBooksFromFirebase()

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim().lowercase()
                if (query.isNotEmpty()) {
                    binding.searchInputLayout.endIconDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_clear)
                    currentQuery = query
                    resetAndSearchFromFirebase(query, selectedCategories)
                } else {
                    binding.searchInputLayout.endIconDrawable = null
                    searchResultsList.clear()
                    allMatchingBooks.clear()
                    searchResultsAdapter.notifyDataSetChanged()
                    binding.searchResultsTitle.visibility = View.GONE
                    binding.loadMoreButton.visibility = View.GONE
                    currentQuery = ""
                    startIndex = 0
                    hasMoreBooks = true
                }
            }
        })

        binding.searchInputLayout.setStartIconOnClickListener {
            val query = binding.searchEditText.text.toString().trim().lowercase()
            if (query.isNotEmpty()) {
                currentQuery = query
                resetAndSearchFromApi(query, selectedCategories)
            } else {
                showToast("Vui lòng nhập từ khóa tìm kiếm")
            }
        }

        binding.searchInputLayout.setEndIconOnClickListener {
            binding.searchEditText.setText("")
        }

        binding.loadMoreButton.setOnClickListener {
            if (hasMoreBooks) {
                if (binding.searchInputLayout.isStartIconVisible) {
                    loadMoreBooksFromApi(currentQuery, selectedCategories)
                } else {
                    loadMoreBooksFromFirebase(currentQuery, selectedCategories)
                }
            }
        }
    }

    private fun init(view: View) {
        navController = Navigation.findNavController(view)
        binding.searchResultsTitle.visibility = View.GONE
    }

    private fun setupCategoriesRecyclerView() {
        val categoryNames = listOf(
            "Fiction", "Nonfiction", "Computers", "Economics", "Health",
            "Medical", "Science", "Art", "Novel"
        )
        categories.clear()
        categoryNames.forEach { name ->
            categories.add(Category(name, false))
        }

        categoryAdapter = CategoryAdapter(categories) { categoryName ->
            if (selectedCategories.contains(categoryName)) {
                selectedCategories.remove(categoryName)
            } else {
                selectedCategories.add(categoryName)
            }
            categories.forEach { it.isSelected = selectedCategories.contains(it.name) }
            categoryAdapter.notifyDataSetChanged()

            val query = binding.searchEditText.text.toString().trim().lowercase()
            if (query.isNotEmpty()) {
                currentQuery = query
                resetAndSearchFromFirebase(query, selectedCategories)
            }
        }
        binding.categoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
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
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = searchResultsAdapter
            addItemDecoration(GridSpacingItemDecoration(2, 210, true))
        }
    }

    private fun loadAllBooksFromFirebase() {
        val booksRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("books")

        booksRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                firebaseBooks.clear()
                for (bookSnapshot in snapshot.children) {
                    try {
                        val book = bookSnapshot.getValue(Book::class.java)?.copy(id = bookSnapshot.key ?: "")
                        book?.let { firebaseBooks.add(it) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi khi phân tích sách ${bookSnapshot.key}: ${e.message}")
                    }
                }
                Log.d(TAG, "Đã tải ${firebaseBooks.size} sách từ Firebase")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Không thể tải sách từ Firebase: ${error.message}")
            }
        })
    }

    private fun resetAndSearchFromFirebase(query: String, selectedCategories: List<String>) {
        searchResultsList.clear()
        allMatchingBooks.clear()
        hasMoreBooks = true
        searchResultsAdapter.notifyDataSetChanged()
        binding.searchResultsTitle.visibility = View.VISIBLE
        binding.loadMoreButton.visibility = View.GONE
        binding.loadMoreProgress.visibility = View.VISIBLE

        fetchAllowedBookIds(selectedCategories) {
            searchBooksFromFirebase(query)
        }
    }

    private fun loadMoreBooksFromFirebase(query: String, selectedCategories: List<String>) {
        binding.loadMoreButton.visibility = View.GONE
        binding.loadMoreProgress.visibility = View.VISIBLE

        displayBooksFromAllMatchingBooks(searchResultsList.size)
    }

    private fun fetchAllowedBookIds(selectedCategories: List<String>, onComplete: () -> Unit) {
        allowedBookIds.clear()
        if (selectedCategories.isEmpty()) {
            onComplete()
            return
        }

        val categoriesRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("categories")

        categoriesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (categorySnapshot in snapshot.children) {
                    val categoryName = categorySnapshot.key
                    if (categoryName != null && selectedCategories.contains(categoryName)) {
                        for (bookSnapshot in categorySnapshot.children) {
                            val bookId = bookSnapshot.key
                            if (bookId != null) {
                                allowedBookIds.add(bookId)
                            }
                        }
                    }
                }
                Log.d(TAG, "Danh sách ID sách được phép: $allowedBookIds")
                onComplete()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Không thể tải danh mục: ${error.message}")
                onComplete()
            }
        })
    }

    private fun searchBooksFromFirebase(query: String) {
        allMatchingBooks.clear()
        for (book in firebaseBooks) {
            val isInAllowedCategory = allowedBookIds.isEmpty() || allowedBookIds.contains(book.id)
            val containsQuery = book.titleLowercase?.contains(query) == true
            if (isInAllowedCategory && containsQuery) {
                allMatchingBooks.add(book)
            }
        }

        allMatchingBooks.sortBy { it.titleLowercase }
        displayBooksFromAllMatchingBooks(0)
    }

    private fun displayBooksFromAllMatchingBooks(startIndex: Int) {
        binding.loadMoreProgress.visibility = View.GONE

        val endIndex = minOf(startIndex + booksPerPage, allMatchingBooks.size)
        val booksToAdd = if (startIndex < allMatchingBooks.size) {
            allMatchingBooks.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        if (booksToAdd.isNotEmpty()) {
            searchResultsList.addAll(booksToAdd)
            searchResultsAdapter.notifyDataSetChanged()
            binding.searchResultsTitle.visibility = View.VISIBLE
            binding.searchResultsTitle.text = "Kết quả tìm kiếm (${searchResultsList.size})"

            hasMoreBooks = endIndex < allMatchingBooks.size
            if (hasMoreBooks) {
                binding.loadMoreButton.visibility = View.VISIBLE
            } else {
                binding.loadMoreButton.visibility = View.GONE
            }

            Log.d(TAG, "Đã hiển thị ${booksToAdd.size} sách, tổng số hiển thị: ${searchResultsList.size}")
        } else {
            if (searchResultsList.isEmpty()) {
                showToast("Không tìm thấy sách nào cho từ khóa: $currentQuery")
                binding.searchResultsTitle.visibility = View.GONE
            } else {
                showToast("Không còn sách để tải thêm")
            }
            binding.loadMoreButton.visibility = View.GONE
        }
    }

    private fun resetAndSearchFromApi(query: String, selectedCategories: List<String>) {
        searchResultsList.clear()
        startIndex = 0
        hasMoreBooks = true
        searchResultsAdapter.notifyDataSetChanged()
        binding.searchResultsTitle.visibility = View.VISIBLE
        binding.loadMoreButton.visibility = View.GONE
        binding.loadMoreProgress.visibility = View.VISIBLE

        searchBooksFromGoogleBooks(query, selectedCategories, startIndex)
    }

    private fun loadMoreBooksFromApi(query: String, selectedCategories: List<String>) {
        binding.loadMoreButton.visibility = View.GONE
        binding.loadMoreProgress.visibility = View.VISIBLE

        startIndex += booksPerPage
        searchBooksFromGoogleBooks(query, selectedCategories, startIndex)
    }

    private fun searchBooksFromGoogleBooks(query: String, selectedCategories: List<String>, startIndex: Int) {
        val baseQuery = if (query.isNotEmpty()) query else "book"
        val categoryQuery = if (selectedCategories.isNotEmpty()) {
            " subject:${selectedCategories.joinToString(" OR ")}"
        } else {
            ""
        }
        val fullQuery = "$baseQuery$categoryQuery"

        val call = RetrofitClient.api.searchBooks(
            query = fullQuery,
            apiKey = RetrofitClient.API_KEY,
            maxResults = booksPerPage,
            startIndex = startIndex
        )

        call.enqueue(object : Callback<GoogleBooksResponse> {
            override fun onResponse(call: Call<GoogleBooksResponse>, response: Response<GoogleBooksResponse>) {
                if (response.isSuccessful) {
                    val googleBooksResponse = response.body()
                    val items = googleBooksResponse?.items ?: emptyList()
                    handleGoogleBooksResponse(items, selectedCategories) // Truyền selectedCategories
                } else {
                    binding.loadMoreProgress.visibility = View.GONE
                    showToast("Lỗi khi tìm kiếm sách: ${response.message()}")
                    Log.e(TAG, "Lỗi Google Books API: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<GoogleBooksResponse>, t: Throwable) {
                binding.loadMoreProgress.visibility = View.GONE
                showToast("Lỗi khi tìm kiếm sách: ${t.message}")
                Log.e(TAG, "Lỗi Google Books API: ${t.message}")
            }
        })
    }

    private fun handleGoogleBooksResponse(items: List<BookItem>, selectedCategories: List<String>) {
        if (items.isEmpty()) {
            binding.loadMoreProgress.visibility = View.GONE
            showToast("Không tìm thấy sách nào")
            binding.searchResultsTitle.visibility = View.GONE
            return
        }

        val tempBooks = mutableListOf<Book>()
        for (item in items) {
            val purchaseLinks = if (item.saleInfo?.buyLink != null) {
                mapOf("Google Play" to item.saleInfo.buyLink!!)
            } else {
                emptyMap()
            }

            val book = Book(
                id = item.id,
                title = item.volumeInfo.title ?: "Unknown Title",
                titleLowercase = item.volumeInfo.title?.lowercase() ?: "unknown title",
                authors = item.volumeInfo.authors ?: emptyList(),
                publisher = item.volumeInfo.publisher ?: "Unknown Publisher",
                publishedDate = item.volumeInfo.publishedDate ?: "",
                description = item.volumeInfo.description ?: "",
                categories = item.volumeInfo.categories ?: emptyList(),
                thumbnail = item.volumeInfo.imageLinks?.thumbnail ?: "",
                googleBooksLink = "https://books.google.com/books?id=${item.id}",
                purchaseLinks = purchaseLinks,
                averageRating = item.volumeInfo.averageRating ?: 0.0,
                ratingCount = item.volumeInfo.ratingsCount?.toLong() ?: 0L,
                readCount = 0L,
                reviews = emptyMap()
            )
            tempBooks.add(book)
        }

        val bookItemsWithCategories = items.map { bookItem ->
            bookItem to (bookItem.volumeInfo.categories ?: emptyList())
        }

        checkAndAddBooksToFirebase(tempBooks, bookItemsWithCategories, selectedCategories) {
            binding.loadMoreProgress.visibility = View.GONE

            hasMoreBooks = tempBooks.size == booksPerPage
            searchResultsList.addAll(tempBooks)
            searchResultsAdapter.notifyDataSetChanged()

            binding.searchResultsTitle.visibility = View.VISIBLE
            binding.searchResultsTitle.text = "Kết quả tìm kiếm (${searchResultsList.size})"

            if (hasMoreBooks) {
                binding.loadMoreButton.visibility = View.VISIBLE
            } else {
                binding.loadMoreButton.visibility = View.GONE
                showToast("Không còn sách để tải thêm")
            }

            Log.d(TAG, "Đã tải ${tempBooks.size} sách từ API, tổng số hiển thị: ${searchResultsList.size}")
        }
    }

    private fun mapCategory(apiCategory: String): String? {
        val categoryLower = apiCategory.lowercase()
        return when {
            categoryLower.contains("science fiction") || categoryLower.contains("fantasy") || categoryLower.contains("historical fiction") -> "Fiction"
            categoryLower.contains("non-fiction") || categoryLower.contains("biography") || categoryLower.contains("history") -> "Nonfiction"
            categoryLower.contains("computer science") || categoryLower.contains("programming") || categoryLower.contains("technology") -> "Computers"
            categoryLower.contains("economics") || categoryLower.contains("business") -> "Economics"
            categoryLower.contains("health") || categoryLower.contains("self-help") || categoryLower.contains("fitness") -> "Health"
            categoryLower.contains("medical") || categoryLower.contains("medicine") -> "Medical"
            categoryLower.contains("science") || categoryLower.contains("mathematics") || categoryLower.contains("physics") -> "Science"
            categoryLower.contains("art") || categoryLower.contains("design") || categoryLower.contains("photography") -> "Art"
            categoryLower.contains("novel") || categoryLower.contains("literary fiction") -> "Novel"
            else -> null
        }
    }

    private fun checkAndAddBooksToFirebase(
        books: List<Book>,
        bookItemsWithCategories: List<Pair<BookItem, List<String>>>,
        selectedCategories: List<String>, // Thêm tham số selectedCategories
        onComplete: () -> Unit
    ) {
        val booksRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("books")
        val categoriesRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("categories")

        var booksProcessed = 0
        if (books.isEmpty()) {
            onComplete()
            return
        }

        books.forEachIndexed { index, book ->
            booksRef.child(book.id).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        booksRef.child(book.id).setValue(book)
                        // Thêm book.id vào tất cả các thể loại trong selectedCategories
                        selectedCategories.forEach { category ->
                            categoriesRef.child(category).child(book.id).setValue(true)
                        }
                        firebaseBooks.add(book)
                    }

                    booksProcessed++
                    if (booksProcessed == books.size) {
                        onComplete()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Lỗi khi kiểm tra sách trên Firebase: ${error.message}")
                    booksProcessed++
                    if (booksProcessed == books.size) {
                        onComplete()
                    }
                }
            })
        }
    }

    private fun showToast(message: String) {
        if (isAdded && context != null) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w(TAG, "Cannot show Toast: Fragment not attached or context is null")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}