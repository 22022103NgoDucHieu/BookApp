package com.example.bookapp.utils.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Book(
    val id: String = "",
    val title: String = "Unknown Title",
    val titleLowercase: String = "unknown title",
    val authors: List<String> = emptyList(),
    val publisher: String = "Unknown Publisher",
    val publishedDate: String = "",
    val description: String = "",
    val categories: List<String> = emptyList(),
    val thumbnail: String = "",
    val googleBooksLink: String = "",
    val purchaseLinks: Map<String, String> = emptyMap(),
    val averageRating: Double = 0.0,
    val ratingCount: Long = 0, // Đổi sang Long để khớp với Realtime Database
    val readCount: Long = 0,   // Đổi sang Long để khớp với Realtime Database
    val reviews: Map<String, Review> = emptyMap() // Thay đổi từ List thành Map
) : Parcelable

@Parcelize
data class Review(
    val userId: String = "",
    val rating: Float = 0f, //double nếu lỗi kiểu dữ liệu
    val comment: String = "",
    val timestamp: Long = 0L
) : Parcelable