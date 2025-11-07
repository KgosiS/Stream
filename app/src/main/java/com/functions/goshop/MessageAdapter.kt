package com.functions.goshop

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

// Message data model


class MessageAdapter(
    private val messages: MutableList<Message>
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubbleCard: CardView = itemView.findViewById(R.id.message_bubble_card)
        val messageText: TextView = itemView.findViewById(R.id.message_text)
        val messageTimestamp: TextView = itemView.findViewById(R.id.message_timestamp)
        val messageImage: ImageView = itemView.findViewById(R.id.message_image)
        val container: ConstraintLayout = itemView as ConstraintLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message_sent, parent, false)
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val context = holder.itemView.context

        // Set message text
        holder.messageText.text = message.text

        // Set timestamp
        holder.messageTimestamp.text = message.timestamp
        holder.messageTimestamp.visibility =
            if (message.timestamp.isNotEmpty()) View.VISIBLE else View.GONE

        // Image handling
        if (message.imageUrl.isNullOrEmpty()) {
            holder.messageImage.visibility = View.GONE
        } else {
            holder.messageImage.visibility = View.VISIBLE
            Glide.with(context)
                .load(message.imageUrl)
                .placeholder(R.drawable.ic_chatbot)
                .into(holder.messageImage)
        }

        // Safe alignment using bubbleCard LayoutParams
        val layoutParams = holder.bubbleCard.layoutParams
        if (layoutParams is ConstraintLayout.LayoutParams) {
            if (message.isUser) {
                // Sent message: align to end (right)
                layoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
                layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.marginStart = 64
                layoutParams.marginEnd = 16
                holder.bubbleCard.setCardBackgroundColor(context.getColor(R.color.primaryColor))
                holder.messageText.setTextColor(context.getColor(android.R.color.white))
            } else {
                // Received message: align to start (left)
                layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                layoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
                layoutParams.marginStart = 16
                layoutParams.marginEnd = 64
                holder.bubbleCard.setCardBackgroundColor(context.getColor(android.R.color.white))
                holder.messageText.setTextColor(context.getColor(R.color.black))
            }
            holder.bubbleCard.layoutParams = layoutParams
        }
    }

    // Add a new message dynamically
    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}
