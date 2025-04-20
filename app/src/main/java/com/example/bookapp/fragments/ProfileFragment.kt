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
            findNavController().navigate(R.id.action_nav_profile_to_signInFragment)
        }

        val editProfileButton = view.findViewById<Button>(R.id.button3)
        editProfileButton.setOnClickListener {
            findNavController().navigate(R.id.action_nav_profile_to_editProfileFragment)
        }


        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val uid = currentUser.uid
            database =
                FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app/")
                    .getReference("users").child(uid)

            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.child("displayName").getValue(String::class.java)
                    val email = snapshot.child("email").getValue(String::class.java)
                    val avatarUrl = snapshot.child("avatarUrl").getValue(String::class.java)

                    binding.name.text = name ?: "No name"
                    binding.email.text = email ?: "No email"

                    if (!avatarUrl.isNullOrEmpty()) {
                        Picasso.get()
                            .load(avatarUrl)
                            .placeholder(R.drawable.avatar)
                            .transform(CircleTransform())
                            .into(binding.avatar)
                    }

                }


                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            })
        } else {
            Toast.makeText(requireContext(), "Chưa đăng nhập", Toast.LENGTH_SHORT).show()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
