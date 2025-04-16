package com.example.bookapp.fragments

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast

import androidx.fragment.app.Fragment
import com.example.bookapp.R
import com.example.bookapp.databinding.FragmentBookDetailBinding
import com.example.bookapp.utils.model.Book
import com.example.bookapp.utils.model.Review
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.squareup.picasso.Picasso
import java.util.UUID
import android.graphics.Typeface

class BookDetailFragment : Fragment() {

    private var _binding: FragmentBookDetailBinding? = null
    private val binding get() = _binding!!
    private var isDescriptionExpanded = false
    private var isReviewsExpanded = false
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth // Thêm FirebaseAuth

    private lateinit var allReviews: MutableList<Review>
    private lateinit var bookId: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Khởi tạo Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Khởi tạo Firebase Database
        database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("books")
        // Lấy dữ liệu sách từ Bundle
        val book = arguments?.getParcelable<Book>("book")
        book?.let {
            bookId = it.id // Lưu bookId để sử dụng khi gửi đánh giá
            // Cập nhật giao diện với thông tin sách
            binding.bookTitle.text = it.title
            binding.bookAuthors.text = "Tác giả: ${ it.authors.joinToString(", ") { author -> author } }"
            binding.bookPublisher.text = "Xuất bản: ${ it.publisher }"
            binding.bookPublishedDate.text = "Ngày xuất bản: ${ it.publishedDate }"
            binding.bookCategories.text =
               "Thể loại: ${ it.categories.joinToString(", ") { category -> category } }"
            binding.bookAverageRating.rating = it.averageRating.toFloat() // Hiển thị số sao
            binding.bookRatingCount.text = "(${it.ratingCount})"
            binding.bookReadCount.text = "Lượt đọc: ${it.readCount}"

            // Giới hạn mô tả 30 từ
            val description = it.description.ifEmpty { "No description available" }
            val shortDescription = getShortDescription(description, 30)
            binding.bookDescription.text = shortDescription
            binding.expandDescriptionButton.visibility = if (description.split(" ").size > 30) View.VISIBLE else View.GONE

            // Xử lý nút "Xem thêm"
            binding.expandDescriptionButton.setOnClickListener {
                if (isDescriptionExpanded) {
                    binding.bookDescription.text = shortDescription
                    binding.expandDescriptionButton.text = "Xem thêm"
                } else {
                    binding.bookDescription.text = description
                    binding.expandDescriptionButton.text = "Thu gọn"
                }
                isDescriptionExpanded = !isDescriptionExpanded
            }

            // Tải ảnh bìa bằng Picasso
            Picasso.get()
                .load(it.thumbnail)
                .placeholder(R.drawable.placeholder_book)
                .error(R.drawable.error_book)
                .into(binding.bookThumbnail)

            // Xử lý nút Google Books
            val googleLink = it.googleBooksLink
            val bookid = it.id
            var readcount = it.readCount
            binding.bookGoogleBooksButton.setOnClickListener {
                // Tăng readCount trên Firebase
                incrementReadCount(bookid, readcount)
                openLink(googleLink)
            }

            // Xử lý nút Purchase
            val amazonLink = it.purchaseLinks["amazon"]
            if (amazonLink.isNullOrEmpty()) {
                binding.bookPurchaseButton.isEnabled = false
                binding.bookPurchaseButton.text = "Không có link mua"
            } else {
                binding.bookPurchaseButton.setOnClickListener {
                    openLink(amazonLink)
                }
            }

// Hiển thị đánh giá
            // Chuyển Map<String, Review> thành List<Review> để hiển thị
            allReviews = it.reviews.values.sortedByDescending { review -> review.timestamp }.toMutableList()
            displayReviews(allReviews.take(3))

            // Hiển thị nút "Xem thêm" nếu có nhiều hơn 3 đánh giá
            if (allReviews.size > 3) {
                binding.expandReviewsButton.visibility = View.VISIBLE
            }

            // Xử lý nút "Xem thêm" cho đánh giá
            binding.expandReviewsButton.setOnClickListener {
                if (isReviewsExpanded) {
                    displayReviews(allReviews.take(3))
                    binding.expandReviewsButton.text = "Xem thêm"
                } else {
                    displayReviews(allReviews)
                    binding.expandReviewsButton.text = "Thu gọn"
                }
                isReviewsExpanded = !isReviewsExpanded
            }

            val rBar = view.findViewById<RatingBar>(R.id.bookAverageRating)
            rBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#FFD700")) // màu vàng
            rBar.secondaryProgressTintList = ColorStateList.valueOf(Color.parseColor("#CCCCCC")) // phần chưa chọn

            val rBar1 = view.findViewById<RatingBar>(R.id.userRatingBar)
            rBar1.progressTintList = ColorStateList.valueOf(Color.parseColor("#FFD700")) // màu vàng
            rBar1.secondaryProgressTintList = ColorStateList.valueOf(Color.parseColor("#CCCCCC")) // phần chưa chọn


            // Xử lý nút "Gửi" đánh giá
            binding.submitReviewButton.setOnClickListener {
                val rating = binding.userRatingBar.rating
                val comment = binding.userCommentInput.text.toString().trim()

                if (rating == 0f) {
                    Toast.makeText(context, "Vui lòng chọn số sao!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (comment.isEmpty()) {
                    Toast.makeText(context, "Vui lòng nhập bình luận!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Kiểm tra người dùng đã đăng nhập chưa
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Toast.makeText(context, "Vui lòng đăng nhập để gửi đánh giá!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Tạo đánh giá mới
                val newReview = Review(
                    userId = currentUser.uid, // Sử dụng uid của người dùng
//                    userId = "user_${UUID.randomUUID()}", // Giả lập userId (có thể thay bằng ID người dùng thực tế)
                    rating = rating,
                    comment = comment,
                    timestamp = System.currentTimeMillis()
                )

                // Lưu đánh giá vào Firebase
                submitReviewToFirebase(newReview)
            }
        } ?: run {
            binding.bookTitle.text = "No book data available"
        }
    }

    // Hàm lấy 30 từ đầu tiên của mô tả
    private fun getShortDescription(description: String, wordLimit: Int): String {
        val words = description.split(" ")
        return if (words.size <= wordLimit) {
            description
        } else {
            words.take(wordLimit).joinToString(" ") + "..."
        }
    }

    // Hàm mở link trong trình duyệt
    private fun openLink(url: String) {
        if (url.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    // Hàm tăng readCount trên Firebase
    private fun incrementReadCount(bookId: String, currentReadCount: Long) {
        val newReadCount = currentReadCount + 1
        database.child(bookId).child("readCount").setValue(newReadCount)
            .addOnSuccessListener {
                // Cập nhật giao diện với giá trị readCount mới
                binding.bookReadCount.text = "Lượt đọc: ${newReadCount}"
                Log.d("BookDetailFragment", "Read count updated successfully: $newReadCount")
            }
            .addOnFailureListener { e ->
                Log.e("BookDetailFragment", "Failed to update read count: ${e.message}")
            }
    }


    // Hàm hiển thị danh sách đánh giá
    private fun displayReviews(reviews: List<Review>) {
        binding.reviewsContainer.removeAllViews() // Xóa các view cũ

        if (reviews.isEmpty()) {
            val noReviewsText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "Chưa có đánh giá nào."
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }
            binding.reviewsContainer.addView(noReviewsText)
            return
        }

        for (review in reviews) {
            // Tạo LinearLayout cho mỗi đánh giá
            val reviewLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                orientation = LinearLayout.VERTICAL
            }
            // Tạo LinearLayout ngang để chứa tên người dùng và RatingBar
            val userAndRatingLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL  // Căn giữa theo chiều dọc
            }

            // RatingBar cho số sao
            val ratingBar = RatingBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(-20, 0, 0, 0) // ← Dịch sang trái 20dp
                }
                numStars = 5
                stepSize = 0.1f
//                isIndicator = true
                rating = review.rating
                scaleX = 0.5f  // Giảm kích thước ngang xuống 50%
                scaleY = 0.5f  // Giảm kích thước dọc xuống 50%
                // Đổi màu ngôi sao thành màu vàng
                progressTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#FFD700") // Màu vàng
                )
                secondaryProgressTintList = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#CCCCCC") // Màu xám cho phần chưa chọn
                )
            }

            // Lấy thông tin người dùng từ Firebase
            val userRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users").child(review.userId)
            userRef.get().addOnSuccessListener { snapshot ->
                val displayName = snapshot.child("displayName").getValue(String::class.java)
                val email = snapshot.child("email").getValue(String::class.java) ?: "Anonymous"

                // Hiển thị displayName nếu có, nếu không thì hiển thị email
                val userText = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = displayName ?: email
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD) // In đậm tên người dùng
                }
                userAndRatingLayout.addView(userText)
                // Thêm RatingBar vào userAndRatingLayout
                userAndRatingLayout.addView(ratingBar)
            }.addOnFailureListener { e ->
                Log.e("BookDetailFragment", "Failed to load user data: ${e.message}")
                // Hiển thị "Anonymous" nếu không lấy được dữ liệu
                val userText = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = "Anonymous"
                    textSize = 14f
                }
                userAndRatingLayout.addView(userText)
                // Thêm RatingBar vào userAndRatingLayout
                userAndRatingLayout.addView(ratingBar)
            }


            // TextView cho bình luận
            val commentText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 2, 0, 0)
                }
                text = review.comment
                textSize = 14f
            }


            // Thêm userAndRatingLayout và commentText vào reviewLayout
            reviewLayout.addView(userAndRatingLayout)
            reviewLayout.addView(commentText)

            // Thêm reviewLayout vào reviewsContainer
            binding.reviewsContainer.addView(reviewLayout)

            // Thêm đường phân cách giữa các đánh giá
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                setBackgroundColor(context.resources.getColor(android.R.color.darker_gray))
            }
            binding.reviewsContainer.addView(divider)
        }

        // Xóa đường phân cách cuối cùng
        if (binding.reviewsContainer.childCount > 0) {
            binding.reviewsContainer.removeViewAt(binding.reviewsContainer.childCount - 1)
        }
    }


    // Hàm gửi đánh giá lên Firebase
    private fun submitReviewToFirebase(review: Review) {
        val reviewId = database.child(bookId).child("reviews").push().key ?: return
        val reviewPath = database.child(bookId).child("reviews").child(reviewId)

        reviewPath.setValue(review)
            .addOnSuccessListener {
                // Thêm đánh giá vào danh sách và cập nhật giao diện
                allReviews.add(review)
                allReviews.sortByDescending { it.timestamp } // Sắp xếp lại theo thời gian
                displayReviews(if (isReviewsExpanded) allReviews else allReviews.take(3))

                // Cập nhật nút "Xem thêm" nếu cần
                if (allReviews.size > 3) {
                    binding.expandReviewsButton.visibility = View.VISIBLE
                }

                // Cập nhật averageRating và ratingCount
                updateAverageRating()

                // Xóa input sau khi gửi
                binding.userRatingBar.rating = 0f
                binding.userCommentInput.text.clear()

                Toast.makeText(context, "Đánh giá đã được gửi!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Lỗi khi gửi đánh giá: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("BookDetailFragment", "Failed to submit review: ${e.message}")
            }
    }

    // Hàm cập nhật averageRating và ratingCount
    private fun updateAverageRating() {
        val totalRatings = allReviews.size
        val averageRating = if (totalRatings > 0) {
            allReviews.sumOf { it.rating.toDouble() } / totalRatings
        } else {
            0.0
        }

        // Cập nhật giao diện
        binding.bookAverageRating.rating = averageRating.toFloat()
        binding.bookRatingCount.text = "($totalRatings)"

        // Cập nhật Firebase
        val updates = mapOf(
            "averageRating" to averageRating,
            "ratingCount" to totalRatings.toLong()
        )
        database.child(bookId).updateChildren(updates)
            .addOnSuccessListener {
                Log.d("BookDetailFragment", "Updated averageRating: $averageRating, ratingCount: $totalRatings")
            }
            .addOnFailureListener { e ->
                Log.e("BookDetailFragment", "Failed to update averageRating: ${e.message}")
            }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}