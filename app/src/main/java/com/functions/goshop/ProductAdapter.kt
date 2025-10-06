package com.functions.goshop

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView

class ProductAdapter(
    private var productList: List<Product>,
    private val onItemClick: (Product) -> Unit,
    private val onFavoriteClick: (Product, Boolean) -> Unit,
    private val onBuyClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.productImage)
        val title: TextView = view.findViewById(R.id.productName)
        val price: TextView = view.findViewById(R.id.productPrice)
        val ratingText: TextView = view.findViewById(R.id.productRatingText)
        val favButton: ImageButton = view.findViewById(R.id.favButton)
        val buyButton: Button = view.findViewById(R.id.buyButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]

        holder.title.text = product.title
        val priceValue = product.price.toDoubleOrNull() ?: 0.0
        holder.price.text = "R %.2f".format(priceValue)

        try {
            val imageBytes = Base64.decode(product.imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            holder.image.setImageBitmap(bitmap)
        } catch (e: Exception) {
            holder.image.setImageResource(R.drawable.smartwatch_example)
        }

        holder.ratingText.text = "★ %.1f (%d reviews)".format(product.avgRating, product.ratingCount)

        // Initialize favorite state
        holder.favButton.setImageResource(
            if (product.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
        )
        holder.favButton.tag = product.isFavorite

        holder.favButton.setOnClickListener {
            val newFavState = !(holder.favButton.tag as? Boolean ?: false)
            product.isFavorite = newFavState
            holder.favButton.setImageResource(
                if (newFavState) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )
            holder.favButton.tag = newFavState
            onFavoriteClick(product, newFavState)
        }

        holder.buyButton.setOnClickListener {
            onBuyClick(product)
        }

        holder.itemView.setOnClickListener {
            onItemClick(product)
        }
    }

    override fun getItemCount(): Int = productList.size

    fun updateList(newList: List<Product>) {
        productList = newList
        notifyDataSetChanged()
    }
}
