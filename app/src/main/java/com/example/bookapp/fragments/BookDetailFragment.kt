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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import android.graphics.Typeface

class BookDetailFragment : Fragment() {

    private var _binding: FragmentBookDetailBinding? = null
    private val binding get() = _binding!!
    private var isDescriptionExpanded = false
    private var isReviewsExpanded = false
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var userRef: DatabaseReference
    private lateinit var allReviews: MutableList<Review>
    private lateinit var bookId: String
    private var isFavorite = false // Trạng thái yêu thích của sách

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        database = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app").getReference("books")
        val currentUser = auth.currentUser
        if (currentUser != null) {
            userRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users").child(currentUser.uid)
        }

        val book = arguments?.getParcelable<Book>("book")
        book?.let {
            bookId = it.id
            binding.bookTitle.text = it.title
            binding.bookAuthors.text = "Tác giả: ${it.authors.joinToString(", ") { author -> author }}"
            binding.bookPublisher.text = "Xuất bản: ${it.publisher}"
            binding.bookPublishedDate.text = "Ngày xuất bản: ${it.publishedDate}"
            binding.bookCategories.text = "Thể loại: ${it.categories.joinToString(", ") { category -> category }}"
            binding.bookAverageRating.rating = it.averageRating.toFloat()
            binding.bookRatingCount.text = "(${it.ratingCount})"
            binding.bookReadCount.text = "Lượt đọc: ${it.readCount}"

            val description = it.description.ifEmpty { "No description available" }
            val shortDescription = getShortDescription(description, 30)
            binding.bookDescription.text = shortDescription
            binding.expandDescriptionButton.visibility = if (description.split(" ").size > 30) View.VISIBLE else View.GONE

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

            if (!it.thumbnail.isNullOrEmpty()) {
                Glide.with(this)
                    .load(it.thumbnail)
                    .apply(RequestOptions()
                        .placeholder(R.drawable.placeholder_book)
                        .error(R.drawable.error_book))
                    .into(binding.bookThumbnail)
            } else {
                binding.bookThumbnail.setImageResource(R.drawable.placeholder_book)
            }

            val googleLink = it.googleBooksLink
            val bookid = it.id
            var readcount = it.readCount
            binding.bookGoogleBooksButton.setOnClickListener {
                incrementReadCount(bookid, readcount)
                openLink(googleLink)
            }

            val amazonLink = it.purchaseLinks["amazon"]
            if (amazonLink.isNullOrEmpty()) {
                binding.bookPurchaseButton.isEnabled = false
                binding.bookPurchaseButton.text = "Null"
            } else {
                binding.bookPurchaseButton.setOnClickListener {
                    openLink(amazonLink)
                }
            }

            allReviews = it.reviews.values.sortedByDescending { review -> review.timestamp }.toMutableList()
            displayReviews(allReviews.take(3))

            if (allReviews.size > 3) {
                binding.expandReviewsButton.visibility = View.VISIBLE
            }

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
            rBar.progressTintList = ColorStateList.valueOf(Color.parseColor("#FFD700"))
            rBar.secondaryProgressTintList = ColorStateList.valueOf(Color.parseColor("#CCCCCC"))

            val rBar1 = view.findViewById<RatingBar>(R.id.userRatingBar)
            rBar1.progressTintList = ColorStateList.valueOf(Color.parseColor("#FFD700"))
            rBar1.secondaryProgressTintList = ColorStateList.valueOf(Color.parseColor("#CCCCCC"))

            binding.submitReviewButton.setOnClickListener {
                val rating = binding.userRatingBar.rating
                val comment = binding.userCommentInput.text.toString().trim()

                if (rating == 0f) {
                    showToast("Vui lòng chọn số sao!")
                    return@setOnClickListener
                }

                if (comment.isEmpty()) {
                    showToast("Vui lòng nhập bình luận!")
                    return@setOnClickListener
                }

                val currentUser = auth.currentUser
                if (currentUser == null) {
                    showToast("Vui lòng đăng nhập để gửi đánh giá!")
                    return@setOnClickListener
                }

                val newReview = Review(
                    userId = currentUser.uid,
                    rating = rating,
                    comment = comment,
                    timestamp = System.currentTimeMillis()
                )

                submitReviewToFirebase(newReview)
            }

            // Kiểm tra trạng thái yêu thích ban đầu
            checkFavoriteStatus(bookId, it.title)

            // Xử lý sự kiện nhấn vào nút yêu thích
            val fvrTitle = it.title
            binding.favoriteButton.setOnClickListener {
                toggleFavorite(bookId, fvrTitle)
            }
        } ?: run {
            binding.bookTitle.text = "No book data available"
        }
    }

    private fun checkFavoriteStatus(bookId: String, bookTitle: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            binding.favoriteButton.setImageResource(R.drawable.ic_heart_border) // Chưa đăng nhập, hiển thị trái tim rỗng
            return
        }

        userRef.child("favouriteBooks").child(bookId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    isFavorite = true
                    binding.favoriteButton.setImageResource(R.drawable.ic_heart_filled) // Đã yêu thích
                } else {
                    isFavorite = false
                    binding.favoriteButton.setImageResource(R.drawable.ic_heart_border) // Chưa yêu thích
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("BookDetailFragment", "Failed to check favorite status: ${error.message}")
            }
        })
    }

    private fun toggleFavorite(bookId: String, bookTitle: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            showToast("Vui lòng đăng nhập để thêm sách vào danh sách yêu thích!")
            return
        }

        if (isFavorite) {
            // Xóa khỏi danh sách yêu thích
            userRef.child("favouriteBooks").child(bookId).removeValue()
                .addOnSuccessListener {
                    isFavorite = false
                    binding.favoriteButton.setImageResource(R.drawable.ic_heart_border)
                    showToast("Đã xóa khỏi danh sách yêu thích!")
                }
                .addOnFailureListener { e ->
                    Log.e("BookDetailFragment", "Failed to remove favorite: ${e.message}")
                    showToast("Lỗi khi xóa khỏi danh sách yêu thích!")
                }
        } else {
            // Thêm vào danh sách yêu thích
            val favoriteData = mapOf(
                "id" to bookId,
                "title" to bookTitle
            )
            userRef.child("favouriteBooks").child(bookId).setValue(favoriteData)
                .addOnSuccessListener {
                    isFavorite = true
                    binding.favoriteButton.setImageResource(R.drawable.ic_heart_filled)
                    showToast("Đã thêm vào danh sách yêu thích!")
                }
                .addOnFailureListener { e ->
                    Log.e("BookDetailFragment", "Failed to add favorite: ${e.message}")
                    showToast("Lỗi khi thêm vào danh sách yêu thích!")
                }
        }
    }

    private fun getShortDescription(description: String, wordLimit: Int): String {
        val words = description.split(" ")
        return if (words.size <= wordLimit) {
            description
        } else {
            words.take(wordLimit).joinToString(" ") + "..."
        }
    }

    private fun openLink(url: String) {
        if (url.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }

    private fun incrementReadCount(bookId: String, currentReadCount: Long) {
        val newReadCount = currentReadCount + 1
        database.child(bookId).child("readCount").setValue(newReadCount)
            .addOnSuccessListener {
                binding.bookReadCount.text = "Lượt đọc: ${newReadCount}"
                Log.d("BookDetailFragment", "Read count updated successfully: $newReadCount")
            }
            .addOnFailureListener { e ->
                Log.e("BookDetailFragment", "Failed to update read count: ${e.message}")
            }
    }

    private fun displayReviews(reviews: List<Review>) {
        binding.reviewsContainer.removeAllViews()

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
            val reviewLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
                orientation = LinearLayout.VERTICAL
            }
            val userAndRatingLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val ratingBar = RatingBar(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(-20, 0, 0, 0)
                }
                numStars = 5
                stepSize = 0.1f
                rating = review.rating
                scaleX = 0.5f
                scaleY = 0.5f
                progressTintList = ColorStateList.valueOf(Color.parseColor("#FFD700"))
                secondaryProgressTintList = ColorStateList.valueOf(Color.parseColor("#CCCCCC"))
            }

            val userRef = FirebaseDatabase.getInstance("https://bookapp-6d5d8-default-rtdb.asia-southeast1.firebasedatabase.app")
                .getReference("users").child(review.userId)
            userRef.get().addOnSuccessListener { snapshot ->
                val displayName = snapshot.child("displayName").getValue(String::class.java)
                val email = snapshot.child("email").getValue(String::class.java) ?: "Anonymous"

                val userText = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = displayName ?: email
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                }
                userAndRatingLayout.addView(userText)
                userAndRatingLayout.addView(ratingBar)
            }.addOnFailureListener { e ->
                Log.e("BookDetailFragment", "Failed to load user data: ${e.message}")
                val userText = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = "Anonymous"
                    textSize = 14f
                }
                userAndRatingLayout.addView(userText)
                userAndRatingLayout.addView(ratingBar)
            }

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

            reviewLayout.addView(userAndRatingLayout)
            reviewLayout.addView(commentText)
            binding.reviewsContainer.addView(reviewLayout)

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

        if (binding.reviewsContainer.childCount > 0) {
            binding.reviewsContainer.removeViewAt(binding.reviewsContainer.childCount - 1)
        }
    }

    private fun submitReviewToFirebase(review: Review) {
        val reviewId = database.child(bookId).child("reviews").push().key ?: return
        val reviewPath = database.child(bookId).child("reviews").child(reviewId)

        reviewPath.setValue(review)
            .addOnSuccessListener {
                allReviews.add(review)
                allReviews.sortByDescending { it.timestamp }
                displayReviews(if (isReviewsExpanded) allReviews else allReviews.take(3))

                if (allReviews.size > 3) {
                    binding.expandReviewsButton.visibility = View.VISIBLE
                }

                updateAverageRating()

                binding.userRatingBar.rating = 0f
                binding.userCommentInput.text.clear()

                showToast("Đánh giá đã được gửi!")
            }
            .addOnFailureListener { e ->
                showToast("Lỗi khi gửi đánh giá: ${e.message}")
                Log.e("BookDetailFragment", "Failed to submit review: ${e.message}")
            }
    }

    private fun updateAverageRating() {
        val totalRatings = allReviews.size
        val averageRating = if (totalRatings > 0) {
            allReviews.sumOf { it.rating.toDouble() } / totalRatings
        } else {
            0.0
        }

        binding.bookAverageRating.rating = averageRating.toFloat()
        binding.bookRatingCount.text = "($totalRatings)"

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

    private fun showToast(message: String) {
        if (isAdded && context != null) {
            activity?.runOnUiThread {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.w("BookDetailFragment", "Cannot show Toast: Fragment not attached or context is null")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}