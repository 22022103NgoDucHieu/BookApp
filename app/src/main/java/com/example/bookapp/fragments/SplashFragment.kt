package com.example.bookapp.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.example.bookapp.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch


class SplashFragment : Fragment() {
    private lateinit var mAuth: FirebaseAuth
    private lateinit var navController: NavController

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_splash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init(view)

        lifecycleScope.launch {
            // Đợi 2 giây để hiển thị splash screen
            kotlinx.coroutines.delay(2000)

            // Làm mới trạng thái người dùng
            mAuth.currentUser?.reload()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    if (mAuth.currentUser != null) {
                        navController.navigate(R.id.action_splashFragment_to_homeFragment)
                    } else {
                        navController.navigate(R.id.action_splashFragment_to_signInFragment)
                    }
                } else {
                    Log.e("SplashFragment", "Lỗi khi làm mới trạng thái: ${task.exception?.message}")
                    navController.navigate(R.id.action_splashFragment_to_signInFragment)
                }
            } ?: run {
                // Nếu currentUser là null ngay từ đầu
                navController.navigate(R.id.action_splashFragment_to_signInFragment)
            }
        }
    }

    private fun init(view: View) {
        mAuth = FirebaseAuth.getInstance()
        navController = Navigation.findNavController(view)
    }
}