package com.functions.goshop

// Message.kt
data class Message(
    val text: String,
    val timestamp: String,
    val isUser: Boolean,
    val imageUrl: String? = null
)