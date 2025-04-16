package com.example.bookapp.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.Navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
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
import com.example.bookapp.utils.adapter.BookAdapter
import com.example.bookapp.utils.model.Book


class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private lateinit var binding: FragmentHomeBinding
    private lateinit var database: DatabaseReference

    private lateinit var auth: FirebaseAuth
    private lateinit var authId: String
    private lateinit var navController: NavController // Khai báo navController

    private lateinit var bookAdapter: BookAdapter
    private val bookList: MutableList<Book> = mutableListOf()


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

        fetchBooksFromFirebase()

        // Thêm sự kiện cho nút đăng xuất
        binding.logoutBtn.setOnClickListener {
            auth.signOut()
            navController.navigate(R.id.action_homeFragment_to_signInFragment)
        }
        // Sự kiện mở profile
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_profile -> {
                    findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
                    true
                }
                R.id.nav_home -> {
                    // Không cần chuyển vì đang ở HomeFragment
                    true
                }
                R.id.nav_search -> {
                    // Nếu có Fragment search thì điều hướng ở đây
                    // findNavController().navigate(R.id.action_homeFragment_to_searchFragment)
                    true
                }
                else -> false
            }
        }

    }

    //top book
    private fun fetchBooksFromFirebase() {
        val booksRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
            .reference.child("books")

        booksRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                bookList.clear()
                for (bookSnapshot in snapshot.children) {
                    try {
                        val book = bookSnapshot.getValue(Book::class.java)?.copy(id = bookSnapshot.key ?: "")
                        book?.let {
                            if (bookList.size < 10) {
                                bookList.add(it)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing book ${bookSnapshot.key}: ${e.message}")
                    }
                    if (bookList.size == 10) break
                }
                if (bookList.isNotEmpty()) {
                    bookAdapter.notifyDataSetChanged()
                    Log.d(TAG, "Loaded ${bookList.size} books from Firebase")
                } else {
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

    private fun init(view: View) {
        auth = FirebaseAuth.getInstance()
        authId = auth.currentUser!!.uid
        database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app").reference.child("Tasks").child(authId)
        navController = Navigation.findNavController(view) // Khởi tạo navController

        binding.mainRecyclerView.setHasFixedSize(true)
        binding.mainRecyclerView.layoutManager = LinearLayoutManager(context)



        // Khởi tạo RecyclerView cho books ngay từ đầu với danh sách rỗng
        setupBookRecyclerView()
    }

}

