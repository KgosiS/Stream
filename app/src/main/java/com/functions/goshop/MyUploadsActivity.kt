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
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MyUploadsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var productAdapter: MyUploadsAdapter // We need a specific adapter for CRUD
    private lateinit var emptyStateText: TextView
    private val uploadedProducts = mutableListOf<Product>()
    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private lateinit var productImagePickerLauncher: ActivityResultLauncher<Intent>

    // Variables needed for the existing showAddProductDialog logic (assuming this activity hosts it)
    private var currentProductImageBase64: String? = null
    private var currentProductImageView: ImageView? = null
    // You must initialize productImagePickerLauncher here if using this Activity for the "Sell" dialog
    // private lateinit var productImagePickerLauncher: ActivityResultLauncher<Intent> // Uncomment and initialize

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_uploads) // Layout provided previously

        if (userId.isEmpty()) {
            Toast.makeText(this, "Please log in to view your uploads.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        recyclerView = findViewById(R.id.myUploadsRecyclerView)
        emptyStateText = findViewById(R.id.emptyStateText)
        val fabAddProduct = findViewById<FloatingActionButton>(R.id.fabAddProduct)
        val backButton = findViewById<ImageView>(R.id.backButton)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize the adapter with CRUD callbacks
        productAdapter = MyUploadsAdapter(
            productList = uploadedProducts,
            onEditClick = { product -> showEditProductDialog(product) },
            onDeleteClick = { product -> confirmDelete(product) }
        )
        recyclerView.adapter = productAdapter

        backButton.setOnClickListener { finish() }

        // FAB click uses the existing dialog for creating new products
        fabAddProduct.setOnClickListener {
            showAddProductDialog()
        }

        fetchMyProducts()
    }
    // ------------------- U: Update (Dialog Implementation) -------------------
    private fun showEditProductDialog(product: Product) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_product, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        // Find views in the custom dialog layout
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val updateButton = dialogView.findViewById<Button>(R.id.updateButton)
        val titleInput = dialogView.findViewById<EditText>(R.id.productTitle)
        val brandInput = dialogView.findViewById<AutoCompleteTextView>(R.id.productBrand)
        val descInput = dialogView.findViewById<EditText>(R.id.productDescription)
        val priceInput = dialogView.findViewById<EditText>(R.id.productPrice)
        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)

        // --- 1. Pre-fill Fields with Existing Data ---
        titleInput.setText(product.title)
        brandInput.setText(product.brand)
        descInput.setText(product.description)
        priceInput.setText(product.price)

        // Set up Brand AutoComplete List
        val brandList = listOf("Apple", "Samsung", "Nike", "Adidas", "Puma", "Sony", "Huawei", "Gucci", "Louis Vuitton", "Microsoft")
        brandInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, brandList))
        brandInput.setOnClickListener { brandInput.showDropDown() }

        // Load existing image
        try {
            val imageBytes = Base64.decode(product.imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            productImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            productImage.setImageResource(R.drawable.ic_image_placeholder)
        }

        // Set initial current image Base64 to the product's existing one
        currentProductImageBase64 = product.imageBase64
        currentProductImageView = productImage // Used by your image picker result handler

        // --- 2. Handle Image Selection ---
        productImage.setOnClickListener {
            // Launches the external image picker (requires productImagePickerLauncher to be set up)
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            // ⚠️ You must use the launcher variable initialized in your Activity's scope
            // productImagePickerLauncher.launch(intent)
        }

        // --- 3. Handle Cancel and Update Clicks ---
        cancelButton.setOnClickListener { dialog.dismiss() }

        updateButton.setOnClickListener {
            val newTitle = titleInput.text.toString().trim()
            val newBrand = brandInput.text.toString().trim()
            val newDescription = descInput.text.toString().trim()
            val newPrice = priceInput.text.toString().trim()
            val newImageBase64 = currentProductImageBase64

            // Basic validation
            if (newTitle.isEmpty() || newBrand.isEmpty() || newDescription.isEmpty() || newPrice.isEmpty() || newImageBase64.isNullOrEmpty()) {
                Toast.makeText(this, "Fill all fields and ensure image is selected.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Execute the Firestore update
            updateProductInFirestore(
                productId = product.id,
                newTitle = newTitle,
                newBrand = newBrand,
                newDescription = newDescription,
                newPrice = newPrice,
                newImageBase64 = newImageBase64
            )
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    // ------------------- U: Update (Firestore Execution) -------------------
    private fun updateProductInFirestore(
        productId: String,
        newTitle: String,
        newBrand: String,
        newDescription: String,
        newPrice: String,
        newImageBase64: String
    ) {
        val updates = hashMapOf<String, Any>(
            "title" to newTitle,
            "brand" to newBrand,
            "description" to newDescription,
            "price" to newPrice,
            "imageBase64" to newImageBase64 // This updates the image if a new one was picked
        )

        firestore.collection("products").document(productId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Product updated successfully!", Toast.LENGTH_SHORT).show()

                // Refresh the list to reflect changes immediately
                fetchMyProducts()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error updating product: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- R: Read (Fetching Products) ---
    private fun fetchMyProducts() {
        // Filter products by the current user's ID
        firestore.collection("products")
            .whereEqualTo("sellerId", userId)
            .get()
            .addOnSuccessListener { snapshot ->
                uploadedProducts.clear()
                if (snapshot.isEmpty) {
                    emptyStateText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyStateText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    for (doc in snapshot.documents) {
                        val product = doc.toObject(Product::class.java)
                        if (product != null) {
                            product.id = doc.id
                            uploadedProducts.add(product)
                        }
                    }
                }
                productAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load uploads: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
                emptyStateText.visibility = View.VISIBLE
            }
    }

    // --- D: Delete (Confirmation and Execution) ---
    private fun confirmDelete(product: Product) {
        AlertDialog.Builder(this)
            .setTitle("Delete Listing")
            .setMessage("Are you sure you want to delete '${product.title}'? This action cannot be undone.")
            .setPositiveButton("DELETE") { _, _ ->
                deleteProduct(product)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteProduct(product: Product) {
        // Delete from Firestore
        firestore.collection("products").document(product.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "${product.title} deleted successfully.", Toast.LENGTH_SHORT)
                    .show()

                // Immediately update the local list and UI
                val index = uploadedProducts.indexOf(product)
                if (index != -1) {
                    uploadedProducts.removeAt(index)
                    productAdapter.notifyItemRemoved(index)
                }

                // Show empty state if the list is now empty
                if (uploadedProducts.isEmpty()) {
                    emptyStateText.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting product: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }
    private fun showAddProductDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sell_product, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelButton)
        val sellNowBtn = dialogView.findViewById<Button>(R.id.sellNowButton)
        val titleInput = dialogView.findViewById<EditText>(R.id.productTitle)
        val brandInput = dialogView.findViewById<AutoCompleteTextView>(R.id.productBrand)
        val descInput = dialogView.findViewById<EditText>(R.id.productDescription)
        val priceInput = dialogView.findViewById<EditText>(R.id.productPrice)
        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)

        currentProductImageBase64 = null
        currentProductImageView = productImage

        val brandList = listOf("Apple", "Samsung", "Nike", "Adidas", "Puma", "Sony", "Huawei", "Gucci", "Louis Vuitton", "Microsoft")
        brandInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, brandList))
        brandInput.setOnClickListener { brandInput.showDropDown() }

        productImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
            productImagePickerLauncher.launch(intent)
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }

        sellNowBtn.setOnClickListener {
            val title = titleInput.text.toString().trim()
            val brand = brandInput.text.toString().trim()
            val description = descInput.text.toString().trim()
            val price = priceInput.text.toString().trim()

            if (title.isEmpty() || brand.isEmpty() || description.isEmpty() || price.isEmpty() || currentProductImageBase64.isNullOrEmpty()) {
                Toast.makeText(this, "Fill all fields + select image", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(this, "You must be logged in to sell", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { userDoc ->
                    val whatsappNumber = userDoc.getString("whatsappNumber") ?: ""

                    val product = hashMapOf(
                        "title" to title,
                        "brand" to brand,
                        "description" to description,
                        "price" to price,
                        "imageBase64" to currentProductImageBase64,
                        "timestamp" to System.currentTimeMillis(),
                        "sellerId" to currentUser.uid,
                        "sellerEmail" to (currentUser.email ?: ""),
                        "whatsappNumber" to whatsappNumber
                    )

                    FirebaseFirestore.getInstance().collection("products")
                        .add(product)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Product added!", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
}
