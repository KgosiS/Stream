package com.functions.goshop

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    // Separate launchers
    private lateinit var profileImagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var productImagePickerLauncher: ActivityResultLauncher<Intent>

    // Product image temp storage
    private var currentProductImageBase64: String? = null
    private var currentProductImageView: ImageView? = null
    private lateinit var chatBot: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        loadSavedLanguage()

        auth = FirebaseAuth.getInstance()

        /** -------- Profile Image Picker -------- */
        profileImagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val imageUri = result.data?.data
                    if (imageUri != null) {
                        saveProfileImageToBase64(imageUri)
                    }
                }
            }

        /** -------- Product Image Picker -------- */
        productImagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val imageUri = result.data?.data
                    if (imageUri != null) {
                        try {
                            val bitmap: Bitmap =
                                MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                            val baos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
                            val imageData = baos.toByteArray()
                            val base64String =
                                Base64.encodeToString(imageData, Base64.DEFAULT)

                            // Save temporarily
                            currentProductImageBase64 = base64String

                            // Preview in dialog
                            currentProductImageView?.setImageBitmap(bitmap)

                        } catch (e: IOException) {
                            e.printStackTrace()
                            Toast.makeText(
                                this,
                                "Error processing product image.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

    override fun onStart() {
        super.onStart()
        checkLoginStatus()
    }

    /** ---------------- PROFILE IMAGE ---------------- **/
    private fun openProfileImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        profileImagePickerLauncher.launch(intent)
    }

    private fun saveProfileImageToBase64(uri: Uri) {
        val userUid = auth.currentUser?.uid ?: return
        try {
            val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, uri)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
            val imageData = baos.toByteArray()
            val base64String = Base64.encodeToString(imageData, Base64.DEFAULT)

            FirebaseDatabase.getInstance().reference
                .child("users").child(userUid).child("profileImage").setValue(base64String)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Profile image updated!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed to update image.", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error processing image.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeProfileImage() {
        val userUid = auth.currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference
            .child("users").child(userUid).child("profileImage").removeValue()
            .addOnCompleteListener {
                Toast.makeText(this, "Profile image removed.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadProfileImage(imageView: ImageView, userUid: String?) {
        userUid ?: return
        FirebaseDatabase.getInstance().reference
            .child("users").child(userUid).child("profileImage").get()
            .addOnSuccessListener { snapshot ->
                val base64String = snapshot.value as? String
                if (!base64String.isNullOrEmpty()) {
                    val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                    Glide.with(this).load(imageBytes).into(imageView)
                } else {
                    imageView.setImageResource(R.drawable.ic_profile)
                }
            }
    }

    /** ---------------- LOGIN + VIEWPAGER ---------------- **/
    private fun checkLoginStatus() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            setupViewPagerAndTabs()
            setupClickListeners()
            displayUserName()
        }
    }

    private fun setupViewPagerAndTabs() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.categoryTabs)
        val adapter = ProductFragmentPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Home"
                1 -> "Trending"
                2 -> "Latest"
                3 -> "Favorites"
                else -> null
            }
        }.attach()
    }

    private fun setupClickListeners() {
        val profileIcon = findViewById<ImageView>(R.id.profile_icon)
        val translatorIcon = findViewById<ImageView>(R.id.cart_icon)
        val searchLayout = findViewById<LinearLayout>(R.id.searchLayout)
        val chatBot = findViewById<FloatingActionButton>(R.id.chatbotFab)

        chatBot.setOnClickListener { showChatBot() }
        profileIcon.setOnClickListener { showProfilePopup() }
        translatorIcon.setOnClickListener { showLanguageSelectionDialog() }
        searchLayout.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }
    }

    /** ---------------- PROFILE POPUP ---------------- **/
    private fun showProfilePopup() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_profile)

        // Set the dialog window's properties for full width
        // This MUST be done after setContentView() but before dialog.show() or immediately after.
        // Doing it here ensures the dialog takes the full width of the screen.
        if (dialog.window != null) {
            dialog.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // Optional: Remove the default dark background behind the dialog's custom view

        }

        val profileName = dialog.findViewById<TextView>(R.id.profileUserName)
        val profileEmail = dialog.findViewById<TextView>(R.id.profileUserEmail)
        val logoutView = dialog.findViewById<LinearLayout>(R.id.logoutLayout)
        val logoutTextView = dialog.findViewById<TextView>(R.id.logoutTextView) // Get the TextView for translation

        val editProfileButton = dialog.findViewById<View>(R.id.editProfileButton)
        val profileImage = dialog.findViewById<ImageView>(R.id.profileImageView)
        val sellItems = dialog.findViewById<LinearLayout>(R.id.sellMerchandiseLayout)
        val myUploads = dialog.findViewById<LinearLayout>(R.id.myUploadsLayout)

        val sellItemsText = dialog.findViewById<TextView>(R.id.sellItemsText) // Find directly via dialog if using my suggested XML
        val myUploadsText = dialog.findViewById<TextView>(R.id.myUploadsText) // Find directly via dialog if using my suggested XML

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("LANGUAGE_CODE", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        translateDialogText(
            profileName,
            profileEmail,
            logoutTextView, // Use the TextView for translation
            editProfileButton as TextView,
            sellItemsText,
            myUploadsText,
            targetLang = langCode
        )
        val currentUser = auth.currentUser
        profileName.text = currentUser?.displayName ?: "User"
        profileEmail.text = currentUser?.email ?: "No Email"

        loadProfileImage(profileImage, currentUser?.uid)

        // Use the new clickable LinearLayout for logout
        logoutView?.setOnClickListener {
            auth.signOut()
            dialog.dismiss()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        editProfileButton?.setOnClickListener {
            dialog.dismiss()
            showEditProfileDialog()
        }

        sellItems.setOnClickListener { showAddProductDialog() }

        profileImage.setOnClickListener { showImageOptionsDialog() }

        myUploads.setOnClickListener { startActivity(Intent(this, MyUploadsActivity::class.java)) }

        dialog.show()
    }

    private fun showImageOptionsDialog() {
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("LANGUAGE_CODE", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        val options = arrayOf("View Profile Image", "Change Profile Image", "Remove Profile Image")

        translateText("Profile Image Options", langCode) { translatedTitle ->
            val translatedOptions = mutableListOf<String>()
            var completed = 0

            for (opt in options) {
                translateText(opt, langCode) { translatedOpt ->
                    translatedOptions.add(translatedOpt)
                    completed++
                    if (completed == options.size) {
                        AlertDialog.Builder(this)
                            .setTitle(translatedTitle)
                            .setItems(translatedOptions.toTypedArray()) { _, which ->
                                when (which) {
                                    0 -> viewProfileImage()
                                    1 -> openProfileImagePicker()
                                    2 -> removeProfileImage()
                                }
                            }
                            .show()
                    }
                }
            }
        }

    }
    private fun viewProfileImage() {
        val userUid = auth.currentUser?.uid ?: return
        FirebaseDatabase.getInstance().reference.child("users").child(userUid)
        .child("profileImage").get()
        .addOnSuccessListener { snapshot ->
        val base64String = snapshot.value as? String
        if (!base64String.isNullOrEmpty()) {
            val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
             val imageView = ImageView(this)
            imageView.setImageBitmap(bitmap)
            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
            imageView.setOnClickListener { dialog.dismiss() }
            dialog.setContentView(imageView)
            dialog.show() }
        else {
        Toast.makeText(this, "No profile image to view.", Toast.LENGTH_SHORT).show() }
        }
    }

    /** ---------------- EDIT PROFILE ---------------- **/
    private fun showEditProfileDialog() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_edit_profile)


        val editName = dialog.findViewById<EditText>(R.id.editName)
        val editEmail = dialog.findViewById<EditText>(R.id.editEmail)
        val editWhatsapp = dialog.findViewById<EditText>(R.id.editWhatsapp) // 🔑 add to XML
        val saveButton = dialog.findViewById<Button>(R.id.saveButton)

        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("LANGUAGE_CODE", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        translateDialogText(
            editName, editEmail, editWhatsapp, saveButton, targetLang = langCode)

        val currentUser = auth.currentUser
        editName.setText(currentUser?.displayName)
        editEmail.setText(currentUser?.email)

        // Load WhatsApp if stored
        FirebaseFirestore.getInstance().collection("users")
            .document(currentUser!!.uid)
            .get()
            .addOnSuccessListener { doc ->
                val whatsapp = doc.getString("whatsappNumber") ?: ""
                editWhatsapp.setText(whatsapp)
            }

        saveButton.setOnClickListener {
            val newName = editName.text.toString()
            val newEmail = editEmail.text.toString()
            val newWhatsapp = editWhatsapp.text.toString()
            updateUserProfile(newName, newEmail, newWhatsapp, dialog)
        }

        dialog.show()
    }

    private fun updateUserProfile(newName: String, newEmail: String, newWhatsapp: String, dialog: android.app.Dialog) {
        val user = auth.currentUser ?: return

        val profileUpdates = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .build()

        user.updateProfile(profileUpdates).addOnCompleteListener { nameTask ->
            if (nameTask.isSuccessful) {
                user.updateEmail(newEmail).addOnCompleteListener { emailTask ->
                    if (emailTask.isSuccessful) {
                        // Save extra fields in Firestore
                        val userMap = hashMapOf(
                            "name" to newName,
                            "email" to newEmail,
                            "whatsappNumber" to newWhatsapp
                        )
                        FirebaseFirestore.getInstance().collection("users")
                            .document(user.uid)
                            .set(userMap, SetOptions.merge())

                        Toast.makeText(this, "Profile updated!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        showProfilePopup()
                    } else {
                        Toast.makeText(this, "Email update failed: ${emailTask.exception?.message}", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            } else {
                Toast.makeText(this, "Name update failed: ${nameTask.exception?.message}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    /** ---------------- ADD PRODUCT DIALOG ---------------- **/
    private fun showAddProductDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_sell_product, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()

        val cancelBtn = dialogView.findViewById<Button>(R.id.cancelButton)
        val sellNowBtn = dialogView.findViewById<Button>(R.id.sellNowButton)
        val titleInput = dialogView.findViewById<EditText>(R.id.productTitle)
        val brandInput = dialogView.findViewById<AutoCompleteTextView>(R.id.productBrand)
        val descInput = dialogView.findViewById<EditText>(R.id.productDescription)
        val priceInput = dialogView.findViewById<EditText>(R.id.productPrice)
        val productImage = dialogView.findViewById<ImageView>(R.id.productImage)
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("LANGUAGE_CODE", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH

        translateDialogText(
            cancelBtn, sellNowBtn, titleInput, brandInput, descInput, priceInput, targetLang = langCode)

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
    private fun displayUserName() {
        val userNameTextView = findViewById<TextView>(R.id.userName)
        val profileIcon = findViewById<ImageView>(R.id.profile_icon)
        val currentUser = auth.currentUser ?: return
        val uid = currentUser.uid
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { snapshot ->
                val username = snapshot.getString("username")
                    ?: currentUser.displayName ?: "User"
                userNameTextView.text = username
                loadProfileImage(profileIcon, uid) }
                .addOnFailureListener { userNameTextView.text = currentUser.displayName ?: "User"
                loadProfileImage(profileIcon, uid) } }

    /** ---------------- LANGUAGE CHANGE (ML KIT) ---------------- **/
    private fun saveLanguagePreference(languageCode: String) {
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        prefs.edit().putString("LANGUAGE_CODE", languageCode).apply()
    }

    private fun showLanguageSelectionDialog() {
        val languages = arrayOf("English", "Afrikaans", "French")
        val langCodes = arrayOf(
            TranslateLanguage.ENGLISH,
            TranslateLanguage.AFRIKAANS,
            TranslateLanguage.FRENCH
        )

        AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setItems(languages) { _, which ->
                val selectedCode = langCodes[which]
                saveLanguagePreference(selectedCode)
                applyTranslations(selectedCode)
            }
            .show()
    }

    private fun applyTranslations(targetLang: String) {
        val tabLayout = findViewById<TabLayout>(R.id.categoryTabs)
        val tabTexts = listOf("Home", "Trending", "Latest", "Favorites")

        for (i in tabTexts.indices) {
            translateText(tabTexts[i], targetLang) { translated ->
                tabLayout.getTabAt(i)?.text = translated
            }
        }

        val appName = findViewById<TextView>(R.id.appName)
        translateText("GO SHOP", targetLang) { translated ->
            appName.text = translated
        }

        val searchLayout = findViewById<LinearLayout>(R.id.searchLayout)
        val searchHint = searchLayout.getChildAt(1) as TextView
        translateText("Search here...", targetLang) { translated ->
            searchHint.text = translated
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

    private fun loadSavedLanguage() {
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val langCode = prefs.getString("LANGUAGE_CODE", TranslateLanguage.ENGLISH) ?: TranslateLanguage.ENGLISH
        applyTranslations(langCode)
    }

    private fun showChatBot() {
        // Inflate the custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_chatbot, null)

        // Create the dialog
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        // Set width and height AFTER dialog is created
        dialog.setOnShowListener {
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, // Full width
                450.dpToPx(this)                     // Fixed height 250dp
            )
        }

        // --- View references ---
        val messageRecyclerView = dialogView.findViewById<RecyclerView>(R.id.messageRecyclerView)
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)
        val sendButton = dialogView.findViewById<ImageButton>(R.id.sendButton)
        val closeButton = dialogView.findViewById<ImageView>(R.id.closeButton)

        // --- Chat setup ---
        val messages = mutableListOf<Message>()
        val adapter = MessageAdapter(messages)
        val chatbotManager = ChatbotManager()

        messageRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        messageRecyclerView.adapter = adapter



        // --- Close Button ---
        closeButton.setOnClickListener { dialog.dismiss() }

        // --- Send Button Logic ---
        sendButton.setOnClickListener {
            val userMessage = messageEditText.text.toString().trim()
            if (userMessage.isEmpty()) return@setOnClickListener

            // Show user's message
            adapter.addMessage(Message(userMessage, timestamp = "Now", isUser = true))
            messageEditText.setText("")
            messageRecyclerView.scrollToPosition(adapter.itemCount - 1)

            // Show "typing..." while waiting
            val typingMessage = Message("Assistant is typing...", "", false)
            adapter.addMessage(typingMessage)
            messageRecyclerView.scrollToPosition(adapter.itemCount - 1)

            lifecycleScope.launch {
                // Remove typing message
                val typingIndex = messages.indexOf(typingMessage)
                if (typingIndex != -1) {
                    messages.removeAt(typingIndex)
                    adapter.notifyItemRemoved(typingIndex)
                }

                // Get response from chatbot
                val botResponseText = chatbotManager.getChatbotResponse(userMessage)

                // Display bot response
                val botMessage = Message(botResponseText, timestamp = "Now", isUser = false)
                adapter.addMessage(botMessage)
                messageRecyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }

        dialog.show()
    }

    // Extension function to convert dp to px
    fun Int.dpToPx(context: android.content.Context): Int =
        (this * context.resources.displayMetrics.density).toInt()



}
