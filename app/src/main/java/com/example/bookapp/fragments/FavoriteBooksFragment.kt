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
import androidx.recyclerview.widget.GridLayoutManager
import com.example.bookapp.R
import com.example.bookapp.utils.adapter.BookAdapter
import com.example.bookapp.databinding.FragmentFavoriteBooksBinding
import com.example.bookapp.utils.GridSpacingItemDecoration
import com.example.bookapp.utils.model.Book
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FavoriteBooksFragment : Fragment() {

    private var _binding: FragmentFavoriteBooksBinding? = null
    private val binding get() = _binding!!
    private lateinit var navController: NavController
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var userRef: DatabaseReference
    private val favoriteBooks = mutableListOf<Book>()
    private lateinit var fvrAdapter: BookAdapter
    private var lastVisibleBook: String? = null
    private val booksPerPage = 8 // Số sách mỗi lần tải

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoriteBooksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = Navigation.findNavController(view)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("books")
        val currentUser = auth.currentUser
        if (currentUser != null) {
            userRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users").child(currentUser.uid)
        }

        setupRecyclerView()
        loadFavoriteBooks(true) // Tải lần đầu tiên
        binding.loadMoreBtn.setOnClickListener {
            loadFavoriteBooks(false) // Tải thêm sách
        }
    }

    private fun setupRecyclerView() {
        fvrAdapter = BookAdapter(favoriteBooks) { book ->
            val bundle = Bundle().apply {
                putParcelable("book", book)
            }
            navController.navigate(R.id.action_favorite_to_bookDetailFragment, bundle)
        }

        binding.favoriteBooksRecyclerView.apply {
            layoutManager = GridLayoutManager(context, 2) // 2 cột giống HomeFragment
            addItemDecoration(GridSpacingItemDecoration(2, 210, true))
            adapter = this@FavoriteBooksFragment.fvrAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun loadFavoriteBooks(clearList: Boolean) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            binding.favoriteBooksRecyclerView.visibility = View.GONE
            return
        }

        if (clearList) {
            favoriteBooks.clear()
            lastVisibleBook = null
        }

        val query = if (lastVisibleBook == null) {
            userRef.child("favouriteBooks").orderByChild("id").limitToFirst(booksPerPage)
        } else {
            userRef.child("favouriteBooks").orderByChild("id").startAfter(lastVisibleBook).limitToFirst(booksPerPage)
        }

        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (clearList) {
                    favoriteBooks.clear()
                }

                val bookIds = mutableListOf<String>()
                for (bookSnapshot in snapshot.children) {
                    val bookId = bookSnapshot.child("id").getValue(String::class.java) ?: continue
                    bookIds.add(bookId)
                    lastVisibleBook = bookId
                }

                if (bookIds.isEmpty()) {
                    binding.loadMoreBtn.visibility = View.GONE
                    Toast.makeText(context, "No favorite books found", Toast.LENGTH_SHORT).show()
                    return
                }

                bookIds.forEach { bookId ->
                    database.child(bookId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(bookDataSnapshot: DataSnapshot) {
                            val book = bookDataSnapshot.getValue(Book::class.java)?.copy(id = bookId)
                            book?.let {
                                if (!favoriteBooks.contains(it)) { // Tránh trùng lặp
                                    favoriteBooks.add(it)
                                    fvrAdapter.notifyDataSetChanged()
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("FavoriteBooksFragment", "Failed to load book with id $bookId: ${error.message}")
                        }
                    })
                }

                binding.loadMoreBtn.visibility = View.VISIBLE
                Log.d("FavoriteBooksFragment", "Loaded ${favoriteBooks.size} favorite books")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FavoriteBooksFragment", "Failed to load favorite books: ${error.message}")
                binding.favoriteBooksRecyclerView.visibility = View.GONE
                Toast.makeText(context, "Failed to load favorite books: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}