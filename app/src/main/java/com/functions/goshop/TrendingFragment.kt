package com.functions.goshop

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrendingFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var productAdapter: ProductAdapter
    private val trendingProducts = mutableListOf<Product>()
    private val firestore = FirebaseFirestore.getInstance()

    // --- ⚡️ FIX 1A: Add state variables for favorites and database ⚡️ ---
    private lateinit var db: AppDatabase
    private var favoriteIds: Set<String> = emptySet()
    private var userId: String = ""
    // --------------------------------------------------------------------

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_trending, container, false)

        // --- ⚡️ FIX 1B: Initialize state variables ⚡️ ---
        db = AppDatabase.getDatabase(requireContext())
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        // ----------------------------------------------

        recyclerView = view.findViewById(R.id.trendingRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        productAdapter = ProductAdapter(
            productList = trendingProducts,
            onItemClick = { product -> showProductDetailsDialog(product) },
            // --- ⚡️ FIX 2: Replace onFavoriteClick with handleFavorite call ⚡️ ---
            onFavoriteClick = { product, isFav ->
                handleFavorite(product, isFav)
            },
            // ------------------------------------------------------------------
            onBuyClick = { product -> showBuyDialog(product) }
        )

        recyclerView.adapter = productAdapter

        // --- ⚡️ FIX 1C: Preload favorites before fetching products ⚡️ ---
        preloadFavorites()
        // ------------------------------------------------------------------
        return view
    }

    // --- ⚡️ FIX 3: Add onResume for cross-tab sync ⚡️ ---
    override fun onResume() {
        super.onResume()
        preloadFavorites()
    }
    // ---------------------------------------------------

    // --- ⚡️ FIX 1D: Add preloadFavorites function ⚡️ ---
    private fun preloadFavorites() {
        lifecycleScope.launch {
            val favorites = withContext(Dispatchers.IO) {
                db.favoriteDao().getAllFavorites()
            }
            favoriteIds = favorites.map { it.productId }.toSet()
            fetchTrendingProducts()
        }
    }
    // ----------------------------------------------------

    // --- ⚡️ FIX 2: Implement handleFavorite for local UI update and DB sync ⚡️ ---
    private fun handleFavorite(product: Product, isFav: Boolean) {
        val favEntity = FavoriteEntity(
            productId = product.id,
            title = product.title,
            price = product.price,
            imageBase64 = product.imageBase64
        )

        lifecycleScope.launch(Dispatchers.IO) {
            if (isFav) {
                db.favoriteDao().insertFavorite(favEntity)
                firestore.collection("users").document(userId)
                    .collection("favorites").document(product.id).set(favEntity)

                // Update UI immediately for this tab
                withContext(Dispatchers.Main) {
                    favoriteIds = favoriteIds + product.id
                    product.isFavorite = true
                    productAdapter.notifyItemChanged(trendingProducts.indexOf(product))
                }
            } else {
                db.favoriteDao().deleteFavorite(favEntity)
                firestore.collection("users").document(userId)
                    .collection("favorites").document(product.id).delete()

                // Update UI immediately for this tab
                withContext(Dispatchers.Main) {
                    favoriteIds = favoriteIds - product.id
                    product.isFavorite = false
                    productAdapter.notifyItemChanged(trendingProducts.indexOf(product))
                }
            }
        }
    }
    // -------------------------------------------------------------------------


    // ---------------- SELLER CONTACT DIALOG ----------------
    private fun showBuyDialog(product: Product) {
        if (product.id.isEmpty()) {
            Toast.makeText(requireContext(), "Missing product ID.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_seller_contact, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        val profileImage = dialogView.findViewById<ImageView>(R.id.sellerProfileImage)
        val titleTv = dialogView.findViewById<TextView>(R.id.contactTitle)
        val nameTv = dialogView.findViewById<TextView>(R.id.sellerNameTextView)
        val emailTv = dialogView.findViewById<TextView>(R.id.sellerEmailTextView)
        val contactTv = dialogView.findViewById<TextView>(R.id.sellerContactTextView)
        val whatsappBtn = dialogView.findViewById<Button>(R.id.whatsappButton)
        val closeBtn = dialogView.findViewById<Button>(R.id.closeContactButton)

        titleTv?.text = "Contact Seller for ${product.title}"
        nameTv?.text = "Loading seller details..."
        profileImage?.setImageResource(R.drawable.ic_person)

        firestore.collection("products").document(product.id).get()
            .addOnSuccessListener { document ->
                val sellerId = document.getString("sellerId")
                val sellerEmail = document.getString("sellerEmail") ?: "N/A"
                val whatsappNumber = document.getString("whatsappNumber") ?: "N/A"

                emailTv?.text = "Email: $sellerEmail"
                contactTv?.text = "Contact: $whatsappNumber"

                if (!sellerId.isNullOrEmpty()) {
                    firestore.collection("users").document(sellerId).get()
                        .addOnSuccessListener { userDoc ->
                            val sellerName = userDoc.getString("name") ?: "Seller"
                            nameTv?.text = "Seller: $sellerName"
                        }
                        .addOnFailureListener {
                            nameTv?.text = "Seller: Unknown"
                        }
                }

                whatsappBtn?.setOnClickListener {
                    if (whatsappNumber != "N/A") {
                        try {
                            val cleaned = whatsappNumber.replace(" ", "")
                                .replace("-", "")
                                .replace("+", "")
                            val url =
                                "https://wa.me/$cleaned?text=I'm interested in your product: ${product.title}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                requireContext(),
                                "WhatsApp not available.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    dialog.dismiss()
                }

                closeBtn?.setOnClickListener { dialog.dismiss() }
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load seller details.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }

    // ---------------- PRODUCT DETAILS DIALOG ----------------
    private fun showProductDetailsDialog(product: Product) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_product_details, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)
        val title = dialogView.findViewById<TextView>(R.id.productTitle)
        val priceTv = dialogView.findViewById<TextView>(R.id.productPrice)
        val description = dialogView.findViewById<TextView>(R.id.productDescription)
        val buyNowButton = dialogView.findViewById<Button>(R.id.buyNowButton)
        val backButton = dialogView.findViewById<ImageView>(R.id.backButton)

        // NEW RATING UI ELEMENTS
        val productRatingAvgTv = dialogView.findViewById<TextView>(R.id.productRating)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val productId = product.id

        // --- UI SETUP ---
        title.text = product.title

        product.price.toDoubleOrNull()?.let { priceValue ->
            priceTv.text = String.format("R %.2f", priceValue)
        } ?: run {
            priceTv.text = "R 0.00"
        }

        description.text = product.description

        // Set a default value until actual rating is loaded
        productRatingAvgTv.text = "Loading rating..."

        try {
            val imageBytes = Base64.decode(product.imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            productImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            productImage.setImageResource(R.drawable.smartwatch_example)
        }

        // --- RATING LOGIC ---

        // 1. Load the User's Existing Rating (and average product rating)
        if (userId.isNotEmpty()) {
            loadRatings(productId, userId, ratingBar, productRatingAvgTv)
        } else {
            // Disable rating input if user is not logged in
            ratingBar.setIsIndicator(true)
            productRatingAvgTv.text = "Login to rate"
        }


        // 2. Setup Listener to Save New Rating
        ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser && userId.isNotEmpty()) {
                saveUserRating(productId, userId, rating, productRatingAvgTv)
            } else if (fromUser && userId.isEmpty()) {
                Toast.makeText(requireContext(), "Please log in to submit a rating.", Toast.LENGTH_SHORT).show()
                // Reset the rating bar visual if they tried to click while logged out
                ratingBar.rating = 0f
            }
        }


        // --- BUTTON LISTENERS ---
        buyNowButton.setOnClickListener {
            showBuyDialog(product)
            dialog.dismiss()
        }

        backButton.setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }


// --- NEW HELPER FUNCTIONS ---

    /**
     * Loads the user's specific rating and the product's average rating.
     * @param productId The ID of the product.
     * @param userId The ID of the current user.
     * @param ratingBar The RatingBar UI element for user input.
     * @param productRatingAvgTv The TextView to display the overall average rating.
     */
    private fun loadRatings(
        productId: String,
        userId: String,
        ratingBar: RatingBar,
        productRatingAvgTv: TextView
    ) {
        // 1. Load User's Existing Rating (for UI update)
        firestore.collection("ratings").document(productId)
            .collection("userRatings").document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val existingRating = doc.getDouble("rating")?.toFloat() ?: 0f
                    ratingBar.rating = existingRating // Set the user's previous rating
                } else {
                    ratingBar.rating = 0f
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Error loading your rating.", Toast.LENGTH_SHORT).show()
            }

        // 2. Load and Calculate Average Rating (for display)
        firestore.collection("ratings").document(productId)
            .collection("userRatings")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    productRatingAvgTv.text = "No ratings yet (0)"
                    return@addOnSuccessListener
                }

                var totalRating = 0.0
                for (doc in snapshot.documents) {
                    totalRating += doc.getDouble("rating") ?: 0.0
                }

                val averageRating = totalRating / snapshot.size()
                productRatingAvgTv.text = String.format("★ %.1f Rating (%d)", averageRating, snapshot.size())
            }
    }


    /**
     * Saves the user's new rating to Firestore and updates the UI.
     * @param productId The ID of the product.
     * @param userId The ID of the current user.
     * @param rating The new rating value (1.0 to 5.0).
     * @param productRatingAvgTv The TextView to update the overall product average.
     */
    private fun saveUserRating(
        productId: String,
        userId: String,
        rating: Float,
        productRatingAvgTv: TextView
    ) {
        val ratingData = hashMapOf(
            "userId" to userId,
            "rating" to rating.toDouble(),
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("ratings").document(productId)
            .collection("userRatings").document(userId)
            .set(ratingData) // Use set() to create or overwrite
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Rating updated to $rating stars!", Toast.LENGTH_SHORT).show()
                // After successful save, immediately reload the average to update the UI
                loadRatings(productId, userId, RatingBar(requireContext()), productRatingAvgTv)
                // Note: We use a dummy RatingBar instance since we only need to update the Avg TextView here.
                // The original RatingBar's value is already updated by the OnRatingBarChangeListener.
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to save rating: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
    // ---------------- FETCH TRENDING PRODUCTS ----------------
    private fun fetchTrendingProducts() {
        firestore.collection("products")
            .orderBy("price", Query.Direction.DESCENDING) // Assuming 'price' trending for now
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                trendingProducts.clear()
                snapshot?.documents?.forEach { doc ->
                    val product = doc.toObject(Product::class.java)
                    if (product != null) {
                        product.id = doc.id
                        // --- ⚡️ FIX 1E: Set isFavorite status based on preloaded IDs ⚡️ ---
                        product.isFavorite = favoriteIds.contains(product.id)
                        // -------------------------------------------------------------------
                        trendingProducts.add(product)
                    }
                }
                productAdapter.updateList(trendingProducts)
            }
    }
}