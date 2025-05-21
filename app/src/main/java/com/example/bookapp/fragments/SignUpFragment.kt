package com.example.bookapp.fragments

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.bookapp.R
import com.example.bookapp.databinding.FragmentSignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Date
import java.util.Locale


class SignUpFragment : Fragment() {

    private lateinit var navController: NavController
    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var binding: FragmentSignUpBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        init(view)

        binding.tvSignIn.setOnClickListener {
            navController.navigate(R.id.action_signUpFragment_to_signInFragment)
        }

        binding.btnSignUp.setOnClickListener {
            val email = binding.edtEmail.text.toString()
            val pass = binding.edtPassword.text.toString()
            val verifyPass = binding.edtConfirmPassword.text.toString()

            if (email.isNotEmpty() && pass.isNotEmpty() && verifyPass.isNotEmpty()) {
                if (pass == verifyPass) {

                    registerUser(email, pass)

                } else {
                    Toast.makeText(context, "Mật khẩu không trùng khơp", Toast.LENGTH_SHORT).show()
                }
            } else
                Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show()
        }

    }

    private fun registerUser(email: String, pass: String) {
        mAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Lấy UID của người dùng vừa tạo
                val userId = mAuth.currentUser?.uid
                if (userId != null) {
                    // Tạo dữ liệu người dùng với các trường trống
                    val userData = hashMapOf(
                        "email" to email,                // Giá trị từ đăng ký
                        "displayName" to "",            // Chuỗi rỗng
                        "avatarUrl" to "",              // Chuỗi rỗng
                        "readingPreferences" to emptyList<String>(), // Mảng rỗng
                        "createdAt" to "",              // Chuỗi rỗng
                        "favorites" to hashMapOf<String, Any>() // Node rỗng cho favorites
                    )

                    // Lưu vào Realtime Database
                    val userRef = database.getReference("users").child(userId)
                    userRef.setValue(userData)
                        .addOnSuccessListener {
                            navController.navigate(R.id.action_signUpFragment_to_homeFragment)
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Lỗi khi lưu dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                Toast.makeText(context, task.exception?.message ?: "Đăng ký thất ba", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun init(view: View) {
        navController = Navigation.findNavController(view)
        mAuth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
    }


}