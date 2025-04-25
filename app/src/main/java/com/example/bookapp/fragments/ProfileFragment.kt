package com.example.bookapp.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.bookapp.R

import com.example.bookapp.databinding.FragmentProfileBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.squareup.picasso.Picasso

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLogout.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.action_nav_profile_to_signInFragment)
        }

        val editProfileButton = view.findViewById<Button>(R.id.button3)
        editProfileButton.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_editProfileFragment)
        }


        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val name = currentUser.displayName
            val email = currentUser.email
            val avatarUrl = currentUser.photoUrl?.toString()

            if (!name.isNullOrEmpty() && !avatarUrl.isNullOrEmpty()) {
                // 👉 Trường hợp đăng nhập Google
                binding.name.text = name
                binding.email.text = email
                Picasso.get().load(avatarUrl)
                    .placeholder(R.drawable.ic_3)
                    .into(binding.avatar)
            } else {
                // 👉 Trường hợp đăng nhập email/password hoặc thông tin Google chưa đủ
                val uid = currentUser.uid
                database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users").child(uid)

                database.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val displayName = snapshot.child("displayName").getValue(String::class.java)
                        val avatarUrl = snapshot.child("avatarUrl").getValue(String::class.java)

                        binding.name.text = displayName ?: ""
                        binding.email.text = currentUser.email ?: ""

                        if (!avatarUrl.isNullOrEmpty()) {
                            Picasso.get()
                                .load(avatarUrl)
                                .transform(CircleTransform())
                                .into(binding.avatar)

                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(requireContext(), "Lỗi: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            // Change password
            binding.changePassword.setOnClickListener {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email.toString())
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(context, "Đã gửi email khôi phục mật khẩu", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Lỗi: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }
        } else {
            Toast.makeText(requireContext(), "Chưa đăng nhập", Toast.LENGTH_SHORT).show()
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private fun signOut() {
        // Đăng xuất Firebase trước (tài khoản backend)
        FirebaseAuth.getInstance().signOut()

        // Tiếp theo, cố gắng đăng xuất Google (nếu không được thì không sao)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        googleSignInClient.signOut().addOnCompleteListener {
            // Vẫn hiện thông báo và chuyển màn hình dù thành công hay không
            Toast.makeText(requireContext(), "Đã đăng xuất", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_nav_profile_to_signInFragment)
        }
    }


}
