package com.example.bookapp.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.bookapp.R
import com.example.bookapp.databinding.FragmentEditProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Picasso

class EditProfileFragment : Fragment() {

    private var _binding: FragmentEditProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private val storageRef by lazy { FirebaseStorage.getInstance().getReference("avatarUrl") }

    private var imageUri: Uri? = null
    private val PICK_IMAGE_REQUEST = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val uid = currentUser.uid
            database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users").child(uid)

            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val name = snapshot.getSafeString("displayName")
                    val bookPref = snapshot.getSafeString("bookPreference")
                    val avatarUrl = snapshot.getSafeString("avatarUrl")

                    binding.edtName.setText(name)
                    binding.edtBookPreference.setText(bookPref)

                    if (!avatarUrl.isNullOrEmpty()) {
                        Picasso.get()
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_3)
                            .transform(CircleTransform())
                            .into(binding.imageAvatar)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Lỗi: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnChangePicture.setOnClickListener {
            openGallery()
        }

        binding.btnUpdate.setOnClickListener {
            updateUserData()
        }
    }

    private fun updateUserData() {
        val name = binding.edtName.text.toString()
        val bookPref = binding.edtBookPreference.text.toString()
        val uid = auth.currentUser?.uid ?: return

        val updates = mapOf(
            "displayName" to name,
            "bookPreference" to bookPref
        )

        database.updateChildren(updates).addOnSuccessListener {
            if (imageUri != null) {
                uploadAvatar(uid)
            } else {
                Toast.makeText(requireContext(), "Đã cập nhật!", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(requireContext(), "Lỗi khi cập nhật!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadAvatar(uid: String) {
        val fileRef = storageRef.child("$uid.jpg")

        fileRef.putFile(imageUri!!)
            .continueWithTask { task ->
                if (!task.isSuccessful) throw task.exception ?: Exception("Upload failed")
                fileRef.downloadUrl
            }.addOnSuccessListener { uri ->
                database.child("avatarUrl").setValue(uri.toString())
                Toast.makeText(requireContext(), "Ảnh đã được cập nhật!", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(requireContext(), "Lỗi upload ảnh", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data
            binding.imageAvatar.setImageURI(imageUri)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Extension để lấy chuỗi an toàn không crash
    private fun DataSnapshot.getSafeString(key: String): String? {
        val value = this.child(key).value
        return when (value) {
            is String -> value
            is Map<*, *> -> value["value"] as? String
            else -> value?.toString()
        }
    }
}
