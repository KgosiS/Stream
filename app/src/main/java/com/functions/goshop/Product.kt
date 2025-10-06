package com.functions.goshop

data class Product(
    // Added default values for Firebase compatibility
    var id: String = "",
    var title: String = "",
    var price: String = "",
    var brand: String = "",
    var imageBase64: String = "",
    var description: String = "",
    var sellerId: String = "",
    var sellerEmail: String = "",
    var whatsappNumber: String = "",
    var avgRating: Float = 0f,
    var timestamp: Long = 0,
    var isFavorite: Boolean = false,
    var ratingSum: Double = 0.0,
    var ratingCount: Int = 0
)
