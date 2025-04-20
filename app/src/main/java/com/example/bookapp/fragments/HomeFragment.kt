package com.example.bookapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bookapp.R
import com.example.bookapp.api.GoogleBooksApi
import com.example.bookapp.api.RetrofitClient
import com.example.bookapp.databinding.FragmentHomeBinding
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

import com.example.bookapp.api.GoogleBooksResponse
import com.example.bookapp.api.BookItem
import com.example.bookapp.utils.GridSpacingItemDecoration
import com.example.bookapp.utils.adapter.BookAdapter
import com.example.bookapp.utils.adapter.CategoryAdapter
import com.example.bookapp.utils.model.Book
import com.example.bookapp.utils.model.Category


class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private lateinit var binding: FragmentHomeBinding
    private lateinit var database: DatabaseReference

    private lateinit var auth: FirebaseAuth
    private lateinit var authId: String
    private lateinit var navController: NavController // Khai báo navController

    private lateinit var bookAdapter: BookAdapter
    private val bookList: MutableList<Book> = mutableListOf()
    private var lastVisibleReadCount: Long? = null // Lưu readCount của sách cuối cùng
    private var lastVisibleBookId: String? = null // Lưu ID của sách cuối cùng
    private val booksPerPageTopBooks = 10 // Số sách ban đầu
    private val additionalBooksPerLoad = 5 // Số sách tải thêm mỗi lần

    private lateinit var categoryAdapter: CategoryAdapter
    private val categories: MutableList<Category> = mutableListOf()
    private lateinit var categoryBooksAdapter: BookAdapter
    private val categoryBooksList: MutableList<Book> = mutableListOf()
    private var currentCategory: String? = null
    private var lastVisibleBook: String? = null
    private val booksPerPage = 4


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        init(view) // Truyền view để khởi tạo navController

        // Lấy dữ liệu sách từ Google Books API và đẩy lên Firebase
        //fetchBooksFromGoogleBooks()

        fetchBooksFromFirebase(true)

        // Thêm sự kiện cho nút đăng xuất
        binding.logoutBtn.setOnClickListener {
            auth.signOut()
            navController.navigate(R.id.action_homeFragment_to_signInFragment)
        }
        // Thêm listener để thay đổi màu nền của topBooksTitle khi cuộn RecyclerView
        binding.booksRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                // Nếu có cuộn ngang (dx != 0), đổi màu nền của topBooksTitle
                if (dx != 0) {
                    binding.topBooksTitle.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.purple_200) // Màu tím nhạt
                    )
                } else {
                    // Nếu không cuộn, đặt lại màu nền ban đầu
                    binding.topBooksTitle.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.white)
                    )
                }
            }
        })

        // Thiết lập danh sách thể loại
        setupCategoriesRecyclerView()

        // Thiết lập RecyclerView cho sách theo thể loại
        setupCategoryBooksRecyclerView()
        // Sự kiện nhấn vào mũi tên "Tải thêm" ở topbook
        binding.loadMoreArrow.setOnClickListener {
            fetchBooksFromFirebase(false)
        }

        // Sự kiện nhấn nút "Xem thêm"
        binding.loadMoreBtn.setOnClickListener {
            fetchMoreBooksByCategory()
        }
    }

    // Top book
    private fun fetchBooksFromFirebase(clearList: Boolean) {
        val booksRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("books")

        val query = if (clearList || lastVisibleReadCount == null || lastVisibleBookId == null) {
            // Lần đầu tiên, lấy 10 sách có readCount cao nhất
            booksRef.orderByChild("readCount").limitToLast(booksPerPageTopBooks)
        } else {
            // Tải thêm 5 sách tiếp theo
            booksRef.orderByChild("readCount")
                .endBefore(lastVisibleReadCount!!.toDouble(), lastVisibleBookId)
                .limitToLast(additionalBooksPerLoad)
        }

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (clearList) {
                    bookList.clear()
                    lastVisibleReadCount = null
                    lastVisibleBookId = null
                }

                val tempList = mutableListOf<Book>()
                for (bookSnapshot in snapshot.children) {
                    try {
                        val book = bookSnapshot.getValue(Book::class.java)?.copy(id = bookSnapshot.key ?: "")
                        book?.let {
                            tempList.add(it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing book ${bookSnapshot.key}: ${e.message}")
                    }
                }

                // Đảo ngược danh sách để hiển thị từ readCount cao nhất đến thấp nhất
                val sortedList = tempList.reversed()
                bookList.addAll(sortedList)

                if (bookList.isNotEmpty()) {
                    bookAdapter.notifyDataSetChanged()
                    Log.d(TAG, "Loaded ${bookList.size} books from Firebase with highest readCount")

                    // Lưu thông tin của sách cuối cùng để phân trang
                    val lastBook = sortedList.lastOrNull()
                    lastVisibleReadCount = lastBook?.readCount?.toLong()
                    lastVisibleBookId = lastBook?.id

                    // Hiển thị mũi tên "Tải thêm" nếu có sách được tải
                    binding.loadMoreArrow.visibility = View.VISIBLE
                } else {
                    binding.loadMoreArrow.visibility = View.GONE
                    Toast.makeText(context, "No books found in Firebase", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to fetch books from Firebase: ${error.message}")
                Toast.makeText(context, "Failed to fetch books: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
    private fun setupBookRecyclerView() {
        bookAdapter = BookAdapter(bookList) { book ->
            val bundle = Bundle().apply {
                putParcelable("book", book)
            }
            navController.navigate(R.id.action_homeFragment_to_bookDetailFragment, bundle)
        }
        binding.booksRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = bookAdapter
        }
    }

    // Thiết lập RecyclerView cho danh sách thể loại
    private fun setupCategoriesRecyclerView() {
        // Danh sách thể loại
        val categoryNames = listOf(
            "Fiction", "Nonfiction", "Computers", "Economics", "Health",
            "Medical", "Science", "Self-Help", "Art", "Novel"
        )
        categories.clear()
        categoryNames.forEachIndexed { index, name ->
            val category = Category(name, index == 0) // Chọn thể loại đầu tiên mặc định
            categories.add(category)
            if (index == 0) currentCategory = name
        }

        categoryAdapter = CategoryAdapter(categories) { categoryName ->
            currentCategory = categoryName
            fetchBooksByCategory(categoryName, true)
        }
        binding.categoriesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = categoryAdapter
        }

        // Lấy sách cho thể loại đầu tiên mặc định
        fetchBooksByCategory(currentCategory!!, true)
    }

    // Thiết lập RecyclerView cho sách theo thể loại
    private fun setupCategoryBooksRecyclerView() {
        categoryBooksAdapter = BookAdapter(categoryBooksList) { book ->
            val bundle = Bundle().apply {
                putParcelable("book", book)
            }
            navController.navigate(R.id.action_homeFragment_to_bookDetailFragment, bundle)
        }
        binding.categoryBooksRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = categoryBooksAdapter
            addItemDecoration(GridSpacingItemDecoration(2, 140, true)) // 16dp spacing
        }
    }

    // Lấy sách theo thể loại từ Firebase
    private fun fetchBooksByCategory(category: String, clearList: Boolean) {
        if (clearList) {
            categoryBooksList.clear()
            lastVisibleBook = null
        }

        val booksRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("categories").child(category)

        val query = if (lastVisibleBook == null) {
            booksRef.orderByKey().limitToFirst(booksPerPage)
        } else {
            booksRef.orderByKey().startAfter(lastVisibleBook).limitToFirst(booksPerPage)
        }

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (clearList) {
                    categoryBooksList.clear()
                }

                val bookIds = mutableListOf<String>()
                for (bookSnapshot in snapshot.children) {
                    val bookId = bookSnapshot.key ?: continue
                    bookIds.add(bookId)
                    lastVisibleBook = bookId
                }

                if (bookIds.isEmpty()) {
                    binding.loadMoreBtn.visibility = View.GONE
                    Toast.makeText(context, "No books found in category $category", Toast.LENGTH_SHORT).show()
                    return@onDataChange
                }

                val booksRefGlobal = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
                    .reference.child("books")

                bookIds.forEach { bookId ->
                    booksRefGlobal.child(bookId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(bookSnapshot: DataSnapshot) {
                            if (bookSnapshot.exists()) {
                                try {
                                    val book = bookSnapshot.getValue(Book::class.java)?.copy(id = bookId)
                                    book?.let {
                                        categoryBooksList.add(it)
                                        updateRecyclerView(category)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error parsing book $bookId from books: ${e.message}")
                                }
                            } else {
                                booksRef.child(bookId).addListenerForSingleValueEvent(object : ValueEventListener {
                                    override fun onDataChange(categorySnapshot: DataSnapshot) {
                                        try {
                                            val book = categorySnapshot.getValue(Book::class.java)?.copy(id = bookId)
                                            book?.let { bookToAdd ->
                                                booksRefGlobal.child(bookId).setValue(bookToAdd)
                                                    .addOnSuccessListener {
                                                        Log.d(TAG, "Added book $bookId to books from category $category")
                                                        categoryBooksList.add(bookToAdd) // Sử dụng bookToAdd thay vì it
                                                        updateRecyclerView(category)
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Log.e(TAG, "Failed to add book $bookId to books: ${e.message}")
                                                    }
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error parsing book $bookId from categories: ${e.message}")
                                        }
                                    }

                                    override fun onCancelled(error: DatabaseError) {
                                        Log.e(TAG, "Failed to fetch book $bookId from categories: ${error.message}")
                                    }
                                })
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e(TAG, "Failed to fetch book $bookId from books: ${error.message}")
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to fetch books for category $category: ${error.message}")
                Toast.makeText(context, "Failed to fetch books: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Hàm tiện ích để cập nhật RecyclerView và nút "Xem thêm"
    private fun updateRecyclerView(category: String) {
        categoryBooksAdapter.notifyDataSetChanged()
        binding.loadMoreBtn.visibility = View.VISIBLE
        Log.d(TAG, "Loaded ${categoryBooksList.size} books for category $category")
    }

    // Tải thêm sách khi nhấn "Xem thêm"
    private fun fetchMoreBooksByCategory() {
        currentCategory?.let { category ->
            val previousSize = categoryBooksList.size
            fetchBooksByCategory(category, false)
            // Cuộn NestedScrollView xuống dưới cùng sau khi thêm dữ liệu mới
            binding.root.post {
                binding.root.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun init(view: View) {
        auth = FirebaseAuth.getInstance()
        authId = auth.currentUser!!.uid
        database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app").reference.child("Tasks").child(authId)
        navController = Navigation.findNavController(view) // Khởi tạo navController




        // Khởi tạo RecyclerView cho books ngay từ đầu với danh sách rỗng
        setupBookRecyclerView()
    }

//    private fun fetchBooksFromGoogleBooks() {
//        val api = RetrofitClient.api
//        api.searchBooks("popular books", RetrofitClient.API_KEY).enqueue(object :
//            Callback<GoogleBooksResponse> {
//            override fun onResponse(
//                call: Call<GoogleBooksResponse>,
//                response: Response<GoogleBooksResponse>
//            ) {
//                if (response.isSuccessful) {
//                    val books = response.body()?.items ?: emptyList()
//                    for (book in books) {
//                        saveBookToFirebase(book)
//                    }
//                    Toast.makeText(context, "Fetched ${books.size} books", Toast.LENGTH_SHORT).show()
//                } else {
//                    Log.e(TAG, "API error: ${response.code()} - ${response.message()}")
//                }
//            }
//
//            override fun onFailure(call: Call<GoogleBooksResponse>, t: Throwable) {
//                Log.e(TAG, "API call failed: ${t.message}")
//                Toast.makeText(context, "Failed to fetch books: ${t.message}", Toast.LENGTH_SHORT).show()
//            }
//        })
//    }

//    private fun fetchBooksFromGoogleBooks() {
//        val api = RetrofitClient.api
//        val categories = listOf(
//            "Fiction", "Nonfiction", "Computers", "Economics", "Health",
//            "Medical", "Science", "Self-Help", "Art", "Novel"
//        )
//        val booksPerCategory = 10
//        var totalBooksFetched = 0
//
//        for (category in categories) {
//            api.searchBooks(
//                category,              // Truy vấn theo thể loại
//                RetrofitClient.API_KEY,
//                booksPerCategory,      // Lấy 10 sách mỗi thể loại
//                0                      // Bắt đầu từ 0
//            ).enqueue(object : Callback<GoogleBooksResponse> {
//                override fun onResponse(
//                    call: Call<GoogleBooksResponse>,
//                    response: Response<GoogleBooksResponse>
//                ) {
//                    if (response.isSuccessful) {
//                        val books = response.body()?.items ?: emptyList()
//                        for (book in books) {
//                            saveBookToFirebase(book, category) // Truyền category để lưu đúng node
//                        }
//                        totalBooksFetched += books.size
//                        if (totalBooksFetched >= categories.size * booksPerCategory) {
//                            Toast.makeText(context, "Fetched $totalBooksFetched books across all categories", Toast.LENGTH_SHORT).show()
//                        }
//                    } else {
//                        Log.e(TAG, "API error for $category: ${response.code()} - ${response.message()}")
//                    }
//                }
//
//                override fun onFailure(call: Call<GoogleBooksResponse>, t: Throwable) {
//                    Log.e(TAG, "API call failed for $category: ${t.message}")
//                    Toast.makeText(context, "Failed to fetch books for $category: ${t.message}", Toast.LENGTH_SHORT).show()
//                }
//            })
//        }
//    }
//
//    private fun saveBookToFirebase(book: BookItem, category: String) {
//        val bookId = book.id
//        val volumeInfo = book.volumeInfo
//
//        val bookData = mapOf(
//            "title" to (volumeInfo.title ?: "Unknown Title"),
//            "titleLowercase" to (volumeInfo.title?.toLowerCase() ?: "unknown title"),
//            "authors" to (volumeInfo.authors ?: emptyList<String>()),
//            "publisher" to (volumeInfo.publisher ?: "Unknown Publisher"),
//            "publishedDate" to (volumeInfo.publishedDate ?: ""),
//            "description" to (volumeInfo.description ?: ""),
//            "categories" to (volumeInfo.categories ?: emptyList<String>()),
//            "thumbnail" to (volumeInfo.imageLinks?.thumbnail ?: ""),
//            "googleBooksLink" to "https://books.google.com/books?id=$bookId",
//            "purchaseLinks" to mapOf("amazon" to "https://amazon.com/$bookId"),
//            "averageRating" to (volumeInfo.averageRating ?: 0.0),
//            "ratingCount" to (volumeInfo.ratingsCount ?: 0),
//            "readCount" to 0 // Khởi tạo
//        )
//
//        // Tạo tham chiếu cho categories/<category>
//        val booksRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
//            .reference.child("categories").child(category)
//
//        booksRef.child(bookId).setValue(bookData)
//            .addOnSuccessListener {
//                Log.d(TAG, "Saved book $bookId to $category")
//            }
//            .addOnFailureListener { e ->
//                Log.e(TAG, "Failed to save book $bookId to $category: ${e.message}")
//            }
//    }



}

