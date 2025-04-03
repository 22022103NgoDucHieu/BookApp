package com.example.bookapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class MainActivity : AppCompatActivity() {
    private lateinit var database: DatabaseReference
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
//        val navController = navHostFragment.navController
        // Nếu bạn muốn tích hợp với ActionBar hoặc Toolbar, thêm code ở đây
        // Khởi tạo Firebase Database
        database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app").reference

        // Gửi dữ liệu ngay khi ứng dụng chạy
        sendTestDataToFirebase()
    }
    private fun sendTestDataToFirebase() {
        val testRef = database.child("test") // Nhánh "test" trong Firebase
        testRef.setValue("Hello Firebase!")
            .addOnSuccessListener {
                Log.d("FirebaseTest", "Dữ liệu đã được gửi thành công!")
            }
            .addOnFailureListener { e ->
                Log.e("FirebaseTest", "Lỗi khi gửi dữ liệu: ${e.message}")
            }
    }

}

