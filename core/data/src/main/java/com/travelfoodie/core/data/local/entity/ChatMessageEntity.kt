package com.travelfoodie.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatRoomEntity::class,
            parentColumns = ["chatRoomId"],
            childColumns = ["chatRoomId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["userId"],
            childColumns = ["senderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
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
