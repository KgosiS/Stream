package com.functions.goshop

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Change Fragment to AppCompatActivity
class AllProductFragment   : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var productAdapter: ProductAdapter
    private lateinit var emptyStateText: TextView
    private val allProducts = mutableListOf<Product>()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var db: AppDatabase
    private var favoriteIds: Set<String> = emptySet()
    private var userId: String = ""

    // Use onCreate() instead of onCreateView()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the content view using the XML layout
        // Assuming your layout file is still named fragment_all_product.xml
        setContentView(R.layout.fragment_all_product)

        // Initialize views
        recyclerView = findViewById(R.id.allProductsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        val backButton = findViewById<ImageView>(R.id.backButton)

        // Setup RecyclerView
        // Use 'this' (the activity context) instead of 'requireContext()'
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        // Initialize DB and userId for favorite management
        db = AppDatabase.getDatabase(this)
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Initialize ProductAdapter with full callbacks
        productAdapter = ProductAdapter(
            productList = allProducts,
            // Use local method references
            onItemClick = { product -> showProductDetailsDialog(product) },
            onFavoriteClick = { product, isFav -> handleFavorite(product, isFav) },
            onBuyClick = { product -> showBuyDialog(product) }
        )
        recyclerView.adapter = productAdapter

        // Set up back button
        backButton.setOnClickListener {
            // In an Activity, the equivalent of popping the back stack is finishing the activity
            finish()
        }

        // Load data
        preloadFavorites()
    }

    // --- Lifecycle for synchronization ---
    // onResume is still valid in an Activity
    override fun onResume() {
        super.onResume()
        preloadFavorites()
    }

    // --- Favorite Management ---
    private fun preloadFavorites() {
        lifecycleScope.launch {
            val favorites = withContext(Dispatchers.IO) {
                db.favoriteDao().getAllFavorites()
            }
            favoriteIds = favorites.map { it.productId }.toSet()
            fetchAllProducts()
        }
    }

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
                if (userId.isNotEmpty()) {
                    firestore.collection("users").document(userId)
                        .collection("favorites").document(product.id).set(favEntity)
                }
                withContext(Dispatchers.Main) {
                    favoriteIds = favoriteIds + product.id
                    product.isFavorite = true
                    productAdapter.notifyItemChanged(allProducts.indexOf(product))
                }
            } else {
                db.favoriteDao().deleteFavorite(favEntity)
                if (userId.isNotEmpty()) {
                    firestore.collection("users").document(userId)
                        .collection("favorites").document(product.id).delete()
                }
                withContext(Dispatchers.Main) {
                    favoriteIds = favoriteIds - product.id
                    product.isFavorite = false
                    productAdapter.notifyItemChanged(allProducts.indexOf(product))
                }
            }
        }
    }

    // --- Fetching All Products (No Limit) ---
    private fun fetchAllProducts() {
        firestore.collection("products")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener(this) { snapshot, error -> // Use 'this' for the activity as the lifecycle owner
                if (error != null) {
                    // Use 'this' context for Toast
                    Toast.makeText(this, "Error fetching products: ${error.message}", Toast.LENGTH_SHORT).show()
                    emptyStateText.visibility = View.VISIBLE
                    return@addSnapshotListener
                }

                allProducts.clear()
                if (snapshot != null && !snapshot.isEmpty) {
                    emptyStateText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    for (doc in snapshot.documents) {
                        val product = doc.toObject(Product::class.java)
                        if (product != null) {
                            product.id = doc.id
                            product.isFavorite = favoriteIds.contains(product.id)
                            allProducts.add(product)
                        }
                    }
                } else {
                    emptyStateText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
                productAdapter.updateList(allProducts)
            }
    }

    // --------------------------------------------------------------------------------------------------
    // --- DIALOG AND HELPER FUNCTIONS ---
    // --------------------------------------------------------------------------------------------------

    private fun showProductDetailsDialog(product: Product) {
        // Use 'this' context for LayoutInflater and AlertDialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_product_details, null)

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)
        val title = dialogView.findViewById<TextView>(R.id.productTitle)
        val priceTv = dialogView.findViewById<TextView>(R.id.productPrice)
        val description = dialogView.findViewById<TextView>(R.id.productDescription)
        val buyNowButton = dialogView.findViewById<Button>(R.id.buyNowButton)
        val closeBtn = dialogView.findViewById<ImageView>(R.id.backButton)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)

        title.text = product.title

        product.price.toDoubleOrNull()?.let { priceValue ->
            priceTv.text = String.format("R %.2f", priceValue)
        } ?: run {
            priceTv.text = "R ${product.price}"
        }

        description.text = product.description

        try {
            val imageBytes = Base64.decode(product.imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            productImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            productImage.setImageResource(R.drawable.smartwatch_example)
        }

        ratingBar.setOnRatingBarChangeListener { _, newRating, _ ->
            saveProductRating(product, newRating)
        }

        buyNowButton.setOnClickListener {
            dialog.dismiss()
            showBuyDialog(product)
        }
        closeBtn.setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showBuyDialog(product: Product) {
        // Use 'this' context
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_seller_contact, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val profileImage = dialogView.findViewById<CircleImageView>(R.id.sellerProfileImage)
        val titleTv = dialogView.findViewById<TextView>(R.id.contactTitle)
        val nameTv = dialogView.findViewById<TextView>(R.id.sellerNameTextView)
        val emailTv = dialogView.findViewById<TextView>(R.id.sellerEmailTextView)
        val contactTv = dialogView.findViewById<TextView>(R.id.sellerContactTextView)
        val whatsappBtn = dialogView.findViewById<Button>(R.id.whatsappButton)
        val closeBtn = dialogView.findViewById<Button>(R.id.closeContactButton)

        titleTv.text = "Contact Seller for ${product.title}"
        nameTv.text = "Loading seller details..."

        firestore.collection("products").document(product.id).get()
            .addOnSuccessListener { doc ->
                val sellerId = doc.getString("sellerId")
                val sellerEmail = doc.getString("sellerEmail") ?: "N/A"
                val whatsappNumber = doc.getString("whatsappNumber") ?: "N/A"

                emailTv.text = "Email: $sellerEmail"
                contactTv.text = "Contact: $whatsappNumber"
                profileImage?.setImageResource(R.drawable.ic_person)

                if (!sellerId.isNullOrEmpty()) {
                    firestore.collection("users").document(sellerId).get()
                        .addOnSuccessListener { userDoc ->
                            nameTv.text = "Seller: ${userDoc.getString("name") ?: "Unknown"}"
                            val profileImageUrl = userDoc.getString("profileImageUrl")
                            if (!profileImageUrl.isNullOrEmpty()) {
                                // Use 'this' context for Glide
                                Glide.with(this)
                                    .load(profileImageUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .error(R.drawable.ic_person)
                                    .into(profileImage)
                            }
                        }
                }

                whatsappBtn.setOnClickListener {
                    openWhatsApp(whatsappNumber, product.title)
                    dialog.dismiss()
                }
                closeBtn.setOnClickListener { dialog.dismiss() }
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load seller details.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }

    private fun openWhatsApp(number: String, productTitle: String) {
        if (number != "N/A") {
            try {
                val cleanedNumber = number.replace("\\s|-|\\+".toRegex(), "")
                val url = "https://wa.me/$cleanedNumber?text=I'm interested in buying your product: $productTitle"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open WhatsApp.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "Seller contact details are unavailable.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveProductRating(product: Product, newRating: Float) {
        if (product.id.isEmpty()) {
            Toast.makeText(this, "Cannot rate product: ID missing.", Toast.LENGTH_SHORT).show()
            return
        }
        firestore.collection("products").document(product.id)
            .update(
                "ratingSum", FieldValue.increment(newRating.toDouble()),
                "ratingCount", FieldValue.increment(1)
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Thanks for rating ${product.title}!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save rating: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}