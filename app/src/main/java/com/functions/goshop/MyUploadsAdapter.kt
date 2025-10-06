package com.functions.goshop



import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
// You may need to import Glide if you use it for image loading

class MyUploadsAdapter(
    private val productList: MutableList<Product>,
    private val onEditClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit
) : RecyclerView.Adapter<MyUploadsAdapter.ProductViewHolder>() {

    class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.productTitle)
        val price: TextView = itemView.findViewById(R.id.productPrice)
        val description: TextView = itemView.findViewById(R.id.productDescription)
        val image: ImageView = itemView.findViewById(R.id.productImage)
        val editButton: ImageButton = itemView.findViewById(R.id.editButton)
        val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_my_upload_products, parent, false) // Using the new layout
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]

        holder.title.text = product.title

        // Safely format price (assuming price is a String in your Product model)
        product.price.toDoubleOrNull()?.let { priceValue ->
            holder.price.text = String.format("R %.2f", priceValue)
        } ?: run {
            holder.price.text = "R ${product.price}"
        }

        holder.description.text = product.description

        // Load image from Base64
        try {
            val imageBytes = Base64.decode(product.imageBase64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            holder.image.setImageBitmap(bitmap)
        } catch (e: Exception) {
            holder.image.setImageResource(R.drawable.smartwatch_example) // Placeholder
        }

        // Set up click listeners for CRUD actions
        holder.editButton.setOnClickListener { onEditClick(product) }
        holder.deleteButton.setOnClickListener { onDeleteClick(product) }
    }

    override fun getItemCount() = productList.size

    fun updateList(newList: List<Product>) {
        productList.clear()
        productList.addAll(newList)
        notifyDataSetChanged()
    }
}