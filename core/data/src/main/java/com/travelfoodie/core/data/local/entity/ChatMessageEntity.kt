package com.travelfoodie.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Removed ForeignKey constraints because:
// 1. Data comes from Firebase Realtime Database asynchronously
// 2. ChatRoom/User may not exist in local DB when message arrives
// 3. This prevents FOREIGN KEY constraint crashes on receiver's device
@Entity(
    tableName = "chat_messages",
    indices = [Index("chatRoomId"), Index("senderId"), Index("timestamp")]
)
data class ChatMessageEntity(
    @PrimaryKey val messageId: String,
    val chatRoomId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val imageUrl: String? = null,
    val type: String = "text", // "text", "image", "system"
    val timestamp: Long = System.currentTimeMillis(),
    val synced: Boolean = false // Track if synced to Firebase
)
