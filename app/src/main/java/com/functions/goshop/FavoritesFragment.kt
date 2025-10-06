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
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var productAdapter: ProductAdapter
    private val productList = mutableListOf<Product>()
    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // We need the database instance outside of onCreateView for onResume (if needed) and fetchProducts
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_favorites, container, false)

        if (userId.isEmpty()) {
            Toast.makeText(requireContext(), "You must be logged in to view favorites.", Toast.LENGTH_LONG).show()
            return view
        }

        db = AppDatabase.getDatabase(requireContext())

        recyclerView = view.findViewById(R.id.productsRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)

        productAdapter = ProductAdapter(
            productList = productList,
            onItemClick = { product -> showProductDetailsDialog(product) },
            onFavoriteClick = { product, isFav ->
                // When clicked in FavoritesFragment, it means the user wants to REMOVE the favorite.
                // The adapter should pass isFav=false here, but we check explicitly.
                if (!isFav) {
                    handleUnfavorite(product)
                }
            },
            onBuyClick = { product -> showBuyDialog(product) }
        )

        recyclerView.adapter = productAdapter

        // Fetch products only once in onCreateView, but we'll use onResume for re-entry sync
        fetchProducts()
        return view
    }

    // Crucial for synchronization: Fetches the latest list every time the fragment becomes visible.
    override fun onResume() {
        super.onResume()
        // Reload the list to ensure all items are present and in sync if they were added/removed elsewhere
        fetchProducts()
    }

    /**
     * Handles the removal of a product from favorites, updating local list and adapter immediately.
     */
    private fun handleUnfavorite(product: Product) {
        val favEntity = FavoriteEntity(
            productId = product.id,
            title = product.title,
            price = product.price,
            imageBase64 = product.imageBase64
        )

        // 1. Delete from Room DB (async)
        viewLifecycleOwner.lifecycleScope.launch {
            db.favoriteDao().deleteFavorite(favEntity)
        }

        // 2. Delete from Firestore
        firestore.collection("users")
            .document(userId)
            .collection("favorites")
            .document(product.id)
            .delete()
            .addOnSuccessListener {
                // 3. FIX: Update the UI immediately
                val index = productList.indexOf(product)
                if (index != -1) {
                    productList.removeAt(index)
                    productAdapter.notifyItemRemoved(index)
                } else {
                    // Fallback to full fetch if local tracking failed
                    fetchProducts()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to remove ${product.title} from favorites.", Toast.LENGTH_SHORT).show()
            }

        Toast.makeText(requireContext(), "${product.title} removed from favorites.", Toast.LENGTH_SHORT).show()
    }


    // -----------------------------------------------------------------------
    // --- FETCH FAVORITES ---
    // -----------------------------------------------------------------------
    private fun fetchProducts() {
        if (userId.isEmpty()) return

        firestore.collection("users")
            .document(userId)
            .collection("favorites")
            .get()
            .addOnSuccessListener { favoritesSnapshot ->
                if (favoritesSnapshot.isEmpty) {
                    productList.clear()
                    productAdapter.updateList(productList)
                    Toast.makeText(requireContext(), "Your favorites list is empty.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                productList.clear()
                for (doc in favoritesSnapshot.documents) {
                    val fav = doc.toObject(FavoriteEntity::class.java)
                    if (fav != null) {
                        val product = Product(
                            id = fav.productId,
                            title = fav.title,
                            price = fav.price,
                            imageBase64 = fav.imageBase64,
                            description = "",
                            isFavorite = true // All products here must be marked as favorite
                        )
                        productList.add(product)
                    }
                }
                productAdapter.updateList(productList)
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load favorites.", Toast.LENGTH_SHORT).show()
            }
    }

    // -----------------------------------------------------------------------
    // --- PRODUCT DETAILS DIALOG ---
    // -----------------------------------------------------------------------
    private fun showProductDetailsDialog(product: Product) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_product_details, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)
        val title = dialogView.findViewById<TextView>(R.id.productTitle)
        val priceTv = dialogView.findViewById<TextView>(R.id.productPrice) // Use priceTv for clarity
        val description = dialogView.findViewById<TextView>(R.id.productDescription)
        val buyNowButton = dialogView.findViewById<Button>(R.id.buyNowButton)
        val backButton = dialogView.findViewById<ImageView>(R.id.backButton)

        title.text = product.title
        priceTv.text = "R ${product.price}" // Using priceTv
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

    // -----------------------------------------------------------------------
    // --- BUY DIALOG (WhatsApp Contact) ---
    // -----------------------------------------------------------------------
    private fun showBuyDialog(product: Product) {
        if (product.id.isEmpty()) {
            Toast.makeText(requireContext(), "Product ID is missing, cannot retrieve seller details.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_seller_contact, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        // Placeholder image view for seller profile (assuming layout ID is correct)
        val profileImage = dialogView.findViewById<ImageView>(R.id.sellerProfileImage)

        val titleTv = dialogView.findViewById<TextView>(R.id.contactTitle)
        val nameTv = dialogView.findViewById<TextView>(R.id.sellerNameTextView)
        val emailTv = dialogView.findViewById<TextView>(R.id.sellerEmailTextView)
        val contactTv = dialogView.findViewById<TextView>(R.id.sellerContactTextView)
        val whatsappBtn = dialogView.findViewById<Button>(R.id.whatsappButton)
        val closeBtn = dialogView.findViewById<Button>(R.id.closeContactButton)

        titleTv.text = "Contact Seller for ${product.title}"
        nameTv.text = "Loading seller details..."
        profileImage?.setImageResource(R.drawable.ic_person) // Set a placeholder

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
                            val url = "https://wa.me/$cleanedNumber?text=I'm interested in your favorited product: ${product.title}"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Error opening WhatsApp.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Seller contact unavailable.", Toast.LENGTH_SHORT).show()
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