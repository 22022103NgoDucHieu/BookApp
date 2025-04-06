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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bookapp.R
import com.example.bookapp.api.GoogleBooksApi
import com.example.bookapp.api.RetrofitClient
import com.example.bookapp.databinding.FragmentHomeBinding
import com.example.bookapp.utils.adapter.TaskAdapter
import com.example.bookapp.utils.model.ToDoData
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


class HomeFragment : Fragment(), ToDoDialogFragment.OnDialogNextBtnClickListener,
    TaskAdapter.TaskAdapterInterface {

    private val TAG = "HomeFragment"
    private lateinit var binding: FragmentHomeBinding
    private lateinit var database: DatabaseReference
    private var frag: ToDoDialogFragment? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var authId: String
    private lateinit var navController: NavController // Khai báo navController

    private lateinit var taskAdapter: TaskAdapter
    private lateinit var toDoItemList: MutableList<ToDoData>

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
        fetchBooksFromGoogleBooks()

        getTaskFromFirebase()

        binding.addTaskBtn.setOnClickListener {
            if (frag != null)
                childFragmentManager.beginTransaction().remove(frag!!).commit()
            frag = ToDoDialogFragment()
            frag!!.setListener(this)
            frag!!.show(childFragmentManager, ToDoDialogFragment.TAG)
        }

        // Thêm sự kiện cho nút đăng xuất
        binding.logoutBtn.setOnClickListener {
            auth.signOut()
            navController.navigate(R.id.action_homeFragment_to_signInFragment)
        }
    }

    private fun fetchBooksFromGoogleBooks() {
        val api = RetrofitClient.api
        api.searchBooks("popular books", RetrofitClient.API_KEY).enqueue(object :
            Callback<GoogleBooksResponse> {
            override fun onResponse(
                call: Call<GoogleBooksResponse>,
                response: Response<GoogleBooksResponse>
            ) {
                if (response.isSuccessful) {
                    val books = response.body()?.items ?: emptyList()
                    for (book in books) {
                        saveBookToFirebase(book)
                    }
                    Toast.makeText(context, "Fetched ${books.size} books", Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "API error: ${response.code()} - ${response.message()}")
                }
            }

            override fun onFailure(call: Call<GoogleBooksResponse>, t: Throwable) {
                Log.e(TAG, "API call failed: ${t.message}")
                Toast.makeText(context, "Failed to fetch books: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun saveBookToFirebase(book: BookItem) {
        val bookId = book.id
        val volumeInfo = book.volumeInfo

        val bookData = mapOf(
            "title" to (volumeInfo.title ?: "Unknown Title"),
            "titleLowercase" to (volumeInfo.title?.toLowerCase() ?: "unknown title"),
            "authors" to (volumeInfo.authors ?: emptyList<String>()),
            "publisher" to (volumeInfo.publisher ?: "Unknown Publisher"),
            "publishedDate" to (volumeInfo.publishedDate ?: ""),
            "description" to (volumeInfo.description ?: ""),
            "categories" to (volumeInfo.categories ?: emptyList<String>()),
            "thumbnail" to (volumeInfo.imageLinks?.thumbnail ?: ""),
            "googleBooksLink" to "https://books.google.com/books?id=$bookId",
            "purchaseLinks" to mapOf("amazon" to "https://amazon.com/$bookId"), // Mock link
            "averageRating" to (volumeInfo.averageRating ?: 0.0),
            "ratingCount" to (volumeInfo.ratingsCount ?: 0),
            "readCount" to 0 // Khởi tạo
        )

        // Đẩy dữ liệu lên Firebase Realtime Database
        database.child("books").child(bookId).setValue(bookData)
            .addOnSuccessListener {
                Log.d(TAG, "Saved book: $bookId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save book $bookId: ${e.message}")
            }
    }

    private fun getTaskFromFirebase() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                toDoItemList.clear()
                for (taskSnapshot in snapshot.children) {
                    val todoTask = taskSnapshot.key?.let { ToDoData(it, taskSnapshot.value.toString()) }
                    todoTask?.let { toDoItemList.add(it) }
                }
                Log.d(TAG, "onDataChange: $toDoItemList")
                taskAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun init(view: View) {
        auth = FirebaseAuth.getInstance()
        authId = auth.currentUser!!.uid
        database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app").reference.child("Tasks").child(authId)
        navController = Navigation.findNavController(view) // Khởi tạo navController

        binding.mainRecyclerView.setHasFixedSize(true)
        binding.mainRecyclerView.layoutManager = LinearLayoutManager(context)

        toDoItemList = mutableListOf()
        taskAdapter = TaskAdapter(toDoItemList)
        taskAdapter.setListener(this)
        binding.mainRecyclerView.adapter = taskAdapter
    }

    override fun saveTask(todoTask: String, todoEdit: TextInputEditText) {
        Log.d(TAG, "saveTask: Task being saved -> $todoTask") // Log để debug
        database.push().setValue(todoTask).addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(context, "Task Added Successfully", Toast.LENGTH_SHORT).show()
                todoEdit.text = null
            } else {
                Toast.makeText(context, it.exception?.message, Toast.LENGTH_SHORT).show()
            }
            frag?.dismiss()
        }
    }

    override fun updateTask(toDoData: ToDoData, todoEdit: TextInputEditText) {
        val map = HashMap<String, Any>()
        map[toDoData.taskId] = toDoData.task
        database.updateChildren(map).addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(context, "Updated Successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, it.exception?.message, Toast.LENGTH_SHORT).show()
            }
            frag?.dismiss()
        }
    }

    override fun onDeleteItemClicked(toDoData: ToDoData, position: Int) {
        database.child(toDoData.taskId).removeValue().addOnCompleteListener {
            if (it.isSuccessful) {
                Toast.makeText(context, "Deleted Successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, it.exception?.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onEditItemClicked(toDoData: ToDoData, position: Int) {
        if (frag != null)
            childFragmentManager.beginTransaction().remove(frag!!).commit()

        frag = ToDoDialogFragment.newInstance(toDoData.taskId, toDoData.task)
        frag!!.setListener(this)
        frag!!.show(childFragmentManager, ToDoDialogFragment.TAG)
    }
}

