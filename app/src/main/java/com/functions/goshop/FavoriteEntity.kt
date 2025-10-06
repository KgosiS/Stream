package com.functions.goshop

import androidx.room.Entity
import androidx.room.PrimaryKey

// -----------------------------------------------------------------------
// --- LOCAL DATABASE ENTITY ---
// -----------------------------------------------------------------------
@Entity(tableName = "favorites")
data class FavoriteEntity(
    // Firestore/Deserialization requires default values for all fields
    @PrimaryKey var productId: String = "",
    var title: String = "",
    var price: String = "",
    var imageBase64: String = ""
) {
    // This empty constructor is explicitly added just to be safe,
    // although default values should usually generate it.
    constructor() : this("", "", "", "")
}

