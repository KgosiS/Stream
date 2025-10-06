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

class LatestFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var productAdapter: ProductAdapter
    private val latestProducts = mutableListOf<Product>()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var db: AppDatabase
    private var favoriteIds: Set<String> = emptySet()
    private var userId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_latest, container, false)

        recyclerView = view.findViewById(R.id.latestRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        db = AppDatabase.getDatabase(requireContext())
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        productAdapter = ProductAdapter(
            latestProducts,
            onItemClick = { product -> showProductDetailsDialog(product) },
            onFavoriteClick = { product, isFav ->
                handleFavorite(product, isFav)
            },
            onBuyClick = { product -> showBuyDialog(product) }
        )

        recyclerView.adapter = productAdapter

        // Load favorites first, then fetch products
        preloadFavorites()

        return view
    }

    // --- FIX 2: Force reload data when fragment is made visible ---
    override fun onResume() {
        super.onResume()
        // This is crucial for cross-tab synchronization:
        // It forces the fragment to re-read the latest favorite status
        // and update the heart icons.
        preloadFavorites()
    }
    // ----------------------------------------------------------------

    private fun preloadFavorites() {
        lifecycleScope.launch {
            val favorites = withContext(Dispatchers.IO) {
                db.favoriteDao().getAllFavorites()
            }
            favoriteIds = favorites.map { it.productId }.toSet()
            fetchLatestProducts()
        }
    }

    // --- FIX 1: Add UI Update Logic to handleFavorite ---
    private fun handleFavorite(product: Product, isFav: Boolean) {
        val favEntity = FavoriteEntity(
            productId = product.id,
            title = product.title,
            price = product.price,
            imageBase64 = product.imageBase64
        )

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            if (isFav) {
                db.favoriteDao().insertFavorite(favEntity)
                firestore.collection("users").document(userId)
                    .collection("favorites").document(product.id)
                    .set(favEntity)

                // Update UI immediately for this tab
                withContext(Dispatchers.Main) {
                    favoriteIds = favoriteIds + product.id
                    product.isFavorite = true
                    productAdapter.notifyItemChanged(latestProducts.indexOf(product))
                }
            } else {
                db.favoriteDao().deleteFavorite(favEntity)
                firestore.collection("users").document(userId)
                    .collection("favorites").document(product.id)
                    .delete()

                // Update UI immediately for this tab
                withContext(Dispatchers.Main) {
                    favoriteIds = favoriteIds - product.id
                    product.isFavorite = false
                    productAdapter.notifyItemChanged(latestProducts.indexOf(product))
                }
            }
        }
    }
    // -------------------------------------------------------------

    private fun fetchLatestProducts() {
        firestore.collection("products")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    latestProducts.clear()
                    for (doc in snapshot.documents) {
                        val product = doc.toObject(Product::class.java)
                        if (product != null) {
                            product.id = doc.id
                            product.isFavorite = favoriteIds.contains(product.id)
                            latestProducts.add(product)
                        }
                    }
                    productAdapter.updateList(latestProducts)
                }
            }
    }

    private fun showProductDetailsDialog(product: Product) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_product_details, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)
        val title = dialogView.findViewById<TextView>(R.id.productTitle)
        val priceTv = dialogView.findViewById<TextView>(R.id.productPrice) // Renamed price for clarity
        val description = dialogView.findViewById<TextView>(R.id.productDescription)
        val buyNowButton = dialogView.findViewById<Button>(R.id.buyNowButton)
        val backButton = dialogView.findViewById<ImageView>(R.id.backButton)

        title.text = product.title

        // Safety check for price formatting (from previous issues)
        product.price.toDoubleOrNull()?.let { priceValue ->
            priceTv.text = String.format("R %.2f", priceValue)
        } ?: run {
            priceTv.text = "R 0.00"
        }

        description.text = product.description

        try {
            val imageBytes = Base64.decode(product.imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            productImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            productImage.setImageResource(R.drawable.smartwatch_example)
        }

        buyNowButton.setOnClickListener {
            showBuyDialog(product)
            dialog.dismiss()
        }

        backButton.setOnClickListener { dialog.dismiss() }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showBuyDialog(product: Product) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_seller_contact, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        val profileImage = dialogView.findViewById<ImageView>(R.id.sellerProfileImage)
        val titleTv = dialogView.findViewById<TextView>(R.id.contactTitle)
        val nameTv = dialogView.findViewById<TextView>(R.id.sellerNameTextView)
        val emailTv = dialogView.findViewById<TextView>(R.id.sellerEmailTextView)
        val contactTv = dialogView.findViewById<TextView>(R.id.sellerContactTextView)
        val whatsappBtn = dialogView.findViewById<Button>(R.id.whatsappButton)
        val closeBtn = dialogView.findViewById<Button>(R.id.closeContactButton)

        titleTv.text = "Contact Seller for ${product.title}"
        nameTv.text = "Loading seller details..."
        profileImage.setImageResource(R.drawable.ic_person)

        firestore.collection("products").document(product.id).get()
            .addOnSuccessListener { document ->
                val sellerId = document.getString("sellerId")
                val sellerEmail = document.getString("sellerEmail") ?: "N/A"
                val whatsappNumber = document.getString("whatsappNumber") ?: "N/A"

                emailTv.text = "Email: $sellerEmail"
                contactTv.text = "Contact: $whatsappNumber"

                if (!sellerId.isNullOrEmpty()) {
                    firestore.collection("users").document(sellerId).get()
                        .addOnSuccessListener { userDoc ->
                            val sellerName = userDoc.getString("name") ?: "Seller"
                            nameTv.text = "Seller: $sellerName"
                        }
                        .addOnFailureListener {
                            nameTv.text = "Seller: Unknown"
                        }
                }

                whatsappBtn.setOnClickListener {
                    if (whatsappNumber != "N/A") {
                        try {
                            val cleanedNumber = whatsappNumber.replace(" ", "").replace("-", "").replace("+", "")
                            val url = "https://wa.me/$cleanedNumber?text=I'm interested in your product: ${product.title}"
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Could not open WhatsApp.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Seller contact details are unavailable.", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }

                closeBtn.setOnClickListener { dialog.dismiss() }

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to retrieve product details.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }
}