package com.functions.goshop

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ChatbotManager {

    private val TAG = "ChatbotManager"
    private val firestore = FirebaseFirestore.getInstance()

    // Data class to hold essential product info
    data class ProductInfo(
        val title: String = "",
        val brand: String = "",
        val description: String = "",
        val price: Double = 0.0,
        val whatsappNumber: String = ""
    )

    // 🔥 Replace this with a secure retrieval method later
    private val apiKey = "AIzaSyCKMWI2Nic1X0-RWvLHY9KmNk4Ky2CY-fQ"

    // Simple Gemini model initialization (no generationConfig)

    
    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = apiKey
    )

    // --- Fetch products from Firestore ---
    private suspend fun fetchAllProductsForContext(): List<ProductInfo> {
        val productsList = mutableListOf<ProductInfo>()
        try {
            val querySnapshot = firestore.collection("products").get().await()
            Log.d(TAG, "Fetched ${querySnapshot.size()} products from Firestore")

            for (document in querySnapshot.documents) {
                val data = document.data
                Log.d(TAG, "Document ${document.id}: $data")

                // Safely extract each field
                val title = document.getString("title") ?: "N/A"
                val brand = document.getString("brand") ?: "N/A"
                val description = document.getString("description") ?: "N/A"

                // Price can be Double or String, handle both
                val rawPrice = document.get("price")
                val price = when (rawPrice) {
                    is Double -> rawPrice
                    is Long -> rawPrice.toDouble()
                    is String -> rawPrice.toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }

                val whatsappNumber = document.getString("whatsappNumber") ?: "Not provided"

                // Add to list
                val product = ProductInfo(title, brand, description, price, whatsappNumber)
                productsList.add(product)
            }

            if (productsList.isEmpty()) {
                Log.w(TAG, "No products found in Firestore collection 'products'")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching products: ${e.message}", e)
        }

        Log.d(TAG, "Returning ${productsList.size} products for chatbot context")
        return productsList
    }

    // --- Generate chatbot response ---
    suspend fun getChatbotResponse(userQuery: String): String {
        val products = fetchAllProductsForContext()

        val dataContext = products.joinToString(separator = "\n---\n") { product ->
            "Title: ${product.title}\n" +
                    "Brand: ${product.brand}\n" +
                    "Description: ${product.description.take(150)}...\n" +
                    "Price: $${product.price}\n" +
                    "Contact: ${product.whatsappNumber}"
        }

        val prompt = """
            You are the GO SHOP Assistant. 
            Use the data below to answer user questions about products.
            If asked about a product, mention its brand, price, and short description.
            If asked how to buy or contact a seller, provide the seller's WhatsApp number.
            If info is missing, say you cannot help with that specific query.
            Prices are in Rands!

            INVENTORY DATA:
            ============================
            $dataContext
            ============================
 
            USER QUESTION: "$userQuery"
        """.trimIndent()

        return try {
            val response = generativeModel.generateContent(prompt)
            response.text ?: "I couldn’t generate a response."
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API Error: ${e.message}")
            "An error occurred while contacting the assistant. Please try again."
        }
    }
}
