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
    val readCount: Long = 0    // Đổi sang Long để khớp với Realtime Database
) : Parcelable