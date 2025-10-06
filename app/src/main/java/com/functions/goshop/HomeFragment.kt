package com.functions.goshop

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var productAdapter: ProductAdapter
    private val productList = mutableListOf<Product>()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var db: AppDatabase
    private var favoriteIds: Set<String> = emptySet()
    private var userId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        recyclerView = view.findViewById(R.id.homeRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        val shopAllTextView = view.findViewById<TextView>(R.id.shopAllItemsTextView)

        db = AppDatabase.getDatabase(requireContext())
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        productAdapter = ProductAdapter(
            productList,
            onItemClick = { product -> showProductDetailsDialog(product) },
            onFavoriteClick = { product, isFav -> handleFavorite(product, isFav) },
            onBuyClick = { product -> showBuyDialog(product) }
        )

        shopAllTextView.setOnClickListener {
            startActivity(Intent(requireContext(), AllProductFragment::class.java))
        }

        recyclerView.adapter = productAdapter

        val appleIcon = view.findViewById<ImageView>(R.id.iconApple)
        val samsungIcon = view.findViewById<ImageView>(R.id.iconSamsung)
        val nikeIcon = view.findViewById<ImageView>(R.id.iconNike)

        appleIcon.setOnClickListener { fetchProductsByBrand("Apple") }
        samsungIcon.setOnClickListener { fetchProductsByBrand("Samsung") }
        nikeIcon.setOnClickListener { fetchProductsByBrand("Nike") }

        preloadFavorites()
        return view
    }

    override fun onResume() {
        super.onResume()
        preloadFavorites()
    }

    private fun fetchProductsByBrand(brand: String?) {
        val collectionRef = firestore.collection("products")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        var query: Query = collectionRef

        if (brand != null) {
            query = query.whereEqualTo("brand", brand)
        } else {
            query = query.limit(6)
        }

        query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                showTranslatedToast("Error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && !snapshot.isEmpty) {
                productList.clear()
                for (doc in snapshot.documents) {
                    val product = doc.toObject(Product::class.java)
                    if (product != null) {
                        product.id = doc.id
                        product.isFavorite = favoriteIds.contains(product.id)
                        productList.add(product)
                    }
                }
                productAdapter.updateList(productList)
            } else {
                productList.clear()
                productAdapter.updateList(productList)
                showTranslatedToast("No items found for $brand.")
            }
        }
    }

    private fun preloadFavorites() {
        lifecycleScope.launch {
            val favorites = withContext(Dispatchers.IO) {
                db.favoriteDao().getAllFavorites()
            }
            favoriteIds = favorites.map { it.productId }.toSet()
            fetchProducts()
        }
    }

    private fun fetchProducts() {
        firestore.collection("products")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(6)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    showTranslatedToast("Error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    productList.clear()
                    for (doc in snapshot.documents) {
                        val product = doc.toObject(Product::class.java)
                        if (product != null) {
                            product.id = doc.id
                            product.isFavorite = favoriteIds.contains(product.id)
                            productList.add(product)
                        }
                    }
                    productAdapter.updateList(productList)
                }
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
                firestore.collection("users").document(userId)
                    .collection("favorites").document(product.id).set(favEntity)
                withContext(Dispatchers.Main) {
                    favoriteIds = favoriteIds + product.id
                    product.isFavorite = true
                    productAdapter.notifyItemChanged(productList.indexOf(product))
                    showTranslatedToast("Added to favorites.")
                }
            } else {
                db.favoriteDao().deleteFavorite(favEntity)
                firestore.collection("users").document(userId)
                    .collection("favorites").document(product.id).delete()
                withContext(Dispatchers.Main) {
                    favoriteIds = favoriteIds - product.id
                    product.isFavorite = false
                    productAdapter.notifyItemChanged(productList.indexOf(product))
                    showTranslatedToast("Removed from favorites.")
                }
            }
        }
    }

    // --------------------------------------------------------------------------------------------------
    // --- DIALOGS (translated)
    // --------------------------------------------------------------------------------------------------

    private fun showProductDetailsDialog(product: Product) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_product_details, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)
        val title = dialogView.findViewById<TextView>(R.id.productTitle)
        val priceTv = dialogView.findViewById<TextView>(R.id.productPrice)
        val description = dialogView.findViewById<TextView>(R.id.productDescription)
        val buyNowButton = dialogView.findViewById<Button>(R.id.buyNowButton)
        val closeBtn = dialogView.findViewById<ImageView>(R.id.backButton)
        val ratingBar = dialogView.findViewById<RatingBar>(R.id.ratingBar)

        title.text = product.title
        product.price.toDoubleOrNull()?.let {
            priceTv.text = String.format("R %.2f", it)
        } ?: run {
            priceTv.text = "R ${product.price}"
        }
        description.text = product.description

        try {
            val imageBytes = android.util.Base64.decode(product.imageBase64, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            productImage.setImageBitmap(bitmap)
        } catch (e: Exception) {
            productImage.setImageResource(R.drawable.smartwatch_example)
        }

        // Translate dialog fields
        val prefs = requireContext().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("LANGUAGE_CODE", "en") ?: "en"
        translateDialogText(title, priceTv, description, buyNowButton, targetLang = langCode)

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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_seller_contact, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        val profileImage = dialogView.findViewById<CircleImageView>(R.id.sellerProfileImage)
        val titleTv = dialogView.findViewById<TextView>(R.id.contactTitle)
        val nameTv = dialogView.findViewById<TextView>(R.id.sellerNameTextView)
        val emailTv = dialogView.findViewById<TextView>(R.id.sellerEmailTextView)
        val contactTv = dialogView.findViewById<TextView>(R.id.sellerContactTextView)
        val whatsappBtn = dialogView.findViewById<Button>(R.id.whatsappButton)
        val closeBtn = dialogView.findViewById<Button>(R.id.closeContactButton)

        titleTv.text = "Contact Seller for ${product.title}"
        nameTv.text = "Loading seller details..."
        profileImage.setImageResource(R.drawable.ic_person)

        val prefs = requireContext().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("LANGUAGE_CODE", "en") ?: "en"
        translateDialogText(titleTv, nameTv, emailTv, contactTv, whatsappBtn, closeBtn, targetLang = langCode)

        firestore.collection("products").document(product.id).get()
            .addOnSuccessListener { doc ->
                val sellerId = doc.getString("sellerId")
                val sellerEmail = doc.getString("sellerEmail") ?: "N/A"
                val whatsappNumber = doc.getString("whatsappNumber") ?: "N/A"

                emailTv.text = "Email: $sellerEmail"
                contactTv.text = "Contact: $whatsappNumber"

                if (!sellerId.isNullOrEmpty()) {
                    firestore.collection("users").document(sellerId).get()
                        .addOnSuccessListener { userDoc ->
                            nameTv.text = "Seller: ${userDoc.getString("name") ?: "Unknown"}"
                            FirebaseDatabase.getInstance().reference.child("users").child(sellerId)
                                .child("profileImage").get()
                                .addOnSuccessListener { snapshot ->
                                    val base64String = snapshot.value as? String
                                    if (!base64String.isNullOrEmpty()) {
                                        val imageBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                                        Glide.with(requireContext())
                                            .load(imageBytes)
                                            .placeholder(R.drawable.ic_person)
                                            .into(profileImage)
                                        profileImage.setOnClickListener {
                                            showImageFullScreen(base64String)
                                        }
                                    }
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
                showTranslatedToast("Failed to load product details.")
                dialog.dismiss()
            }
    }

    private fun showImageFullScreen(base64String: String) {
        if (base64String.isEmpty()) {
            showTranslatedToast("Profile image not available.")
            return
        }

        try {
            val imageBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val imageView = ImageView(requireContext())
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.setOnClickListener { dialog.dismiss() }
            dialog.setContentView(imageView)
            dialog.show()
        } catch (e: Exception) {
            showTranslatedToast("Error displaying image.")
        }
    }

    private fun openWhatsApp(number: String, productTitle: String) {
        if (number != "N/A") {
            try {
                val cleanedNumber = number.replace("\\s|-|\\+".toRegex(), "")
                val url = "https://wa.me/$cleanedNumber?text=I'm interested in buying your product: $productTitle"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                showTranslatedToast("Could not open WhatsApp.")
            }
        } else {
            showTranslatedToast("Seller contact details are unavailable.")
        }
    }

    private fun saveProductRating(product: Product, newRating: Float) {
        if (product.id.isEmpty()) {
            showTranslatedToast("Cannot rate product: ID missing.")
            return
        }

        firestore.collection("products").document(product.id)
            .update(
                "ratingSum", FieldValue.increment(newRating.toDouble()),
                "ratingCount", FieldValue.increment(1)
            )
            .addOnSuccessListener {
                showTranslatedToast("Thanks for rating ${product.title}!")
            }
            .addOnFailureListener { e ->
                showTranslatedToast("Failed to save rating: ${e.message}")
            }
    }
    private fun translateText(text: String, targetLang: String, callback: (String) -> Unit) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()

        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translatedText -> callback(translatedText) }
                    .addOnFailureListener { callback(text) }
            }
            .addOnFailureListener { callback(text) }
    }

    private fun translateDialogText(vararg views: View, targetLang: String) {
        for (view in views) {
            when (view) {
                is TextView -> translateText(view.text.toString(), targetLang) { view.text = it }
                is Button -> translateText(view.text.toString(), targetLang) { view.text = it }
                is EditText -> translateText(view.hint?.toString() ?: "", targetLang) { view.hint = it }
            }
        }
    }
    // 🔤 Translate Toasts using existing ML Kit helper
    private fun showTranslatedToast(message: String) {
        val prefs = requireContext().getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("LANGUAGE_CODE", "en") ?: "en"
        translateText(message, langCode) { translated ->
            Toast.makeText(requireContext(), translated, Toast.LENGTH_SHORT).show()
        }
    }
}
