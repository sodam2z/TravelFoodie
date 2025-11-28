package com.travelfoodie.feature.board

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.travelfoodie.core.data.local.entity.ChatMessageEntity
import com.travelfoodie.feature.board.databinding.ItemChatMessageBinding
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val onImageClick: ((String) -> Unit)? = null
) : ListAdapter<ChatMessageEntity, ChatAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding, onImageClick)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatViewHolder(
        private val binding: ItemChatMessageBinding,
        private val onImageClick: ((String) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(message: ChatMessageEntity) {
            binding.apply {
                textViewSenderName.text = message.senderName
                textViewTimestamp.text = timeFormat.format(Date(message.timestamp))

                // Handle image messages
                if (message.type == "image" && !message.imageUrl.isNullOrEmpty()) {
                    imageViewMessage.visibility = View.VISIBLE
                    imageViewMessage.load(message.imageUrl) {
                        crossfade(true)
                        transformations(RoundedCornersTransformation(16f))
                        placeholder(android.R.drawable.ic_menu_gallery)
                        error(android.R.drawable.ic_menu_close_clear_cancel)
                    }
                    imageViewMessage.setOnClickListener {
                        onImageClick?.invoke(message.imageUrl!!)
                    }

                    // Hide or show text based on whether there's additional text
                    if (message.text.isNotEmpty()) {
                        textViewMessage.visibility = View.VISIBLE
                        textViewMessage.text = message.text
                    } else {
                        textViewMessage.visibility = View.GONE
                    }
                } else {
                    // Text message
                    imageViewMessage.visibility = View.GONE
                    textViewMessage.visibility = View.VISIBLE
                    textViewMessage.text = message.text
                }
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<ChatMessageEntity>() {
        override fun areItemsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity): Boolean {
            return oldItem.messageId == newItem.messageId
        }

        override fun areContentsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity): Boolean {
            return oldItem == newItem
        }
    }
}
