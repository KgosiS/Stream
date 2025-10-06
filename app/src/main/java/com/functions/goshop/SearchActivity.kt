package com.functions.goshop

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var productAdapter: ProductAdapter
    private val productList = mutableListOf<Product>()
    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val db = AppDatabase.getDatabase(this)

        searchView = findViewById(R.id.searchView)
        recyclerView = findViewById(R.id.searchRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        productAdapter = ProductAdapter(
            productList = productList,
            onItemClick = { product -> showProductDetailsDialog(product) },
            onFavoriteClick = { product, isFav ->
                val favEntity = FavoriteEntity(
                    productId = product.id,
                    title = product.title,
                    price = product.price,
                    imageBase64 = product.imageBase64
                )

                if (isFav) {
                    lifecycleScope.launch { db.favoriteDao().insertFavorite(favEntity) }
                    firestore.collection("users").document(userId)
                        .collection("favorites").document(product.id).set(favEntity)
                } else {
                    lifecycleScope.launch { db.favoriteDao().deleteFavorite(favEntity) }
                    firestore.collection("users").document(userId)
                        .collection("favorites").document(product.id).delete()
                }
            },
            onBuyClick = { product -> showBuyDialog(product) }
        )
        recyclerView.adapter = productAdapter

        fetchAllProducts()

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    searchProducts(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    searchProducts(newText)
                } else {
                    fetchAllProducts()
                }
                return true
            }
        })
    }

    private fun fetchAllProducts() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@SearchActivity)
            val favoriteIds = db.favoriteDao().getAllFavorites().map { it.productId }.toSet()

            firestore.collection("products")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { snapshot ->
                    productList.clear()
                    for (doc in snapshot.documents) {
                        val product = doc.toObject(Product::class.java)
                        if (product != null) {
                            product.id = doc.id
                            product.isFavorite = favoriteIds.contains(product.id)

                            product.avgRating = (doc.getDouble("ratingSum") ?: 0.0).toFloat()
                            val ratingCount = (doc.getLong("ratingCount") ?: 0L).toInt()
                            product.ratingCount = ratingCount
                            if (ratingCount > 0) {
                                product.avgRating /= ratingCount
                            }

                            productList.add(product)
                        }
                    }
                    productAdapter.updateList(productList)
                }
                .addOnFailureListener {
                    Toast.makeText(this@SearchActivity, "Failed to load products", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun searchProducts(query: String) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@SearchActivity)
            val favoriteIds = db.favoriteDao().getAllFavorites().map { it.productId }.toSet()

            firestore.collection("products")
                .orderBy("title")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .get()
                .addOnSuccessListener { snapshot ->
                    productList.clear()
                    for (doc in snapshot.documents) {
                        val product = doc.toObject(Product::class.java)
                        if (product != null) {
                            product.id = doc.id
                            product.isFavorite = favoriteIds.contains(product.id)

                            product.avgRating = (doc.getDouble("ratingSum") ?: 0.0).toFloat()
                            val ratingCount = (doc.getLong("ratingCount") ?: 0L).toInt()
                            product.ratingCount = ratingCount
                            if (ratingCount > 0) {
                                product.avgRating /= ratingCount
                            }

                            productList.add(product)
                        }
                    }
                    productAdapter.updateList(productList)
                }
                .addOnFailureListener {
                    Toast.makeText(this@SearchActivity, "Search failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showProductDetailsDialog(product: Product) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_product_details, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)
        val title = dialogView.findViewById<TextView>(R.id.productTitle)
        val price = dialogView.findViewById<TextView>(R.id.productPrice)
        val description = dialogView.findViewById<TextView>(R.id.productDescription)
        val buyNowButton = dialogView.findViewById<Button>(R.id.buyNowButton)
        val backButton = dialogView.findViewById<ImageView>(R.id.backButton)

        title.text = product.title
        price.text = "R ${product.price}"
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
        if (product.id.isEmpty()) {
            Toast.makeText(this, "Product ID is missing, cannot retrieve seller details.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_seller_contact, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

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
                            nameTv.text = "Seller: Unknown (Profile Load Failed)"
                        }
                }

                whatsappBtn.setOnClickListener {
                    if (whatsappNumber != "N/A") {
                        try {
                            val cleanedNumber = whatsappNumber.replace(" ", "").replace("-", "").replace("+", "")
                            val url = "https://wa.me/$cleanedNumber?text=I'm interested in your product: ${product.title} (Found via search)"
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = Uri.parse(url)
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, "Error opening WhatsApp.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Seller contact details unavailable.", Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                }

                closeBtn.setOnClickListener { dialog.dismiss() }

                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                dialog.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to retrieve product details.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
    }
}
