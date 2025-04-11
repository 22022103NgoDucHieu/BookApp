package com.example.bookapp.api


import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GoogleBooksApi {
    @GET("volumes")
    fun searchBooks(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("maxResults") maxResults: Int = 20, // Đặt mặc định là 20
        @Query("startIndex") startIndex: Int = 0  // Thêm startIndex
    ): Call<GoogleBooksResponse>

    @GET("volumes/{bookId}")
    fun getBookDetails(
        @Path("bookId") bookId: String,
        @Query("key") apiKey: String
    ): Call<BookItem>
}

data class GoogleBooksResponse(
    val items: List<BookItem>?
)

data class BookItem(
    val id: String,
    val volumeInfo: VolumeInfo
)

data class VolumeInfo(
    val title: String?,
    val authors: List<String>?,
    val publisher: String?,
    val publishedDate: String?,
    val description: String?,
    val categories: List<String>?,
    val imageLinks: ImageLinks?,
    val averageRating: Double?,
    val ratingsCount: Int?
)

data class ImageLinks(
    val thumbnail: String?
)

object RetrofitClient {
    private const val BASE_URL = "https://www.googleapis.com/books/v1/"
    const val API_KEY = "AIzaSyCVwgDkWJRhmnT7_M1cLNvaod46w2x7wPY" // Thay bằng API key thật

    val api: GoogleBooksApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleBooksApi::class.java)
    }
}