package com.travelfoodie.feature.board

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.travelfoodie.core.data.local.entity.ChatRoomEntity
import java.text.SimpleDateFormat
import java.util.*

class ChatRoomAdapter(
    private val onChatRoomClick: (String) -> Unit
) : ListAdapter<ChatRoomEntity, ChatRoomAdapter.ChatRoomViewHolder>(ChatRoomDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatRoomViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ChatRoomViewHolder(view, onChatRoomClick)
    }

    override fun onBindViewHolder(holder: ChatRoomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ChatRoomViewHolder(
        itemView: View,
        private val onChatRoomClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleTextView: TextView = itemView.findViewById(android.R.id.text1)
        private val subtitleTextView: TextView = itemView.findViewById(android.R.id.text2)
        private val timeFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        fun bind(chatRoom: ChatRoomEntity) {
            titleTextView.text = chatRoom.name

            val typeLabel = if (chatRoom.type == "trip") "Trip Chat" else "Friend Chat"
            val lastTime = chatRoom.lastMessageTime
            val lastMessageInfo = if (lastTime != null) {
                val time = timeFormat.format(Date(lastTime))
                "${chatRoom.lastMessageText?.take(30) ?: "No messages"} • $time"
            } else {
                "No messages yet"
            }

            subtitleTextView.text = "$typeLabel • $lastMessageInfo"

            itemView.setOnClickListener {
                onChatRoomClick(chatRoom.chatRoomId)
            }
        }
    }

    class ChatRoomDiffCallback : DiffUtil.ItemCallback<ChatRoomEntity>() {
        override fun areItemsTheSame(oldItem: ChatRoomEntity, newItem: ChatRoomEntity): Boolean {
            return oldItem.chatRoomId == newItem.chatRoomId
        }

        override fun areContentsTheSame(oldItem: ChatRoomEntity, newItem: ChatRoomEntity): Boolean {
            return oldItem == newItem
        }
    }
}
