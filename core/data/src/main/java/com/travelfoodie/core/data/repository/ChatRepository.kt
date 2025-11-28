package com.travelfoodie.core.data.repository

import com.google.firebase.database.*
import com.travelfoodie.core.data.local.dao.ChatMessageDao
import com.travelfoodie.core.data.local.dao.ChatRoomDao
import com.travelfoodie.core.data.local.dao.UserDao
import com.travelfoodie.core.data.local.entity.ChatMessageEntity
import com.travelfoodie.core.data.local.entity.ChatRoomEntity
import com.travelfoodie.core.data.local.entity.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatRoomDao: ChatRoomDao,
    private val chatMessageDao: ChatMessageDao,
    private val userDao: UserDao,
    private val firebaseDatabase: FirebaseDatabase
) {
    private val chatRoomsRef = firebaseDatabase.getReference("chat_rooms")
    private val messagesRef = firebaseDatabase.getReference("messages")
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Chat Room Management - Firebase Primary
    fun getUserChatRooms(userId: String, userEmail: String? = null): Flow<List<ChatRoomEntity>> {
        return callbackFlow {
            // First emit cached data from local database
            repositoryScope.launch {
                try {
                    val cachedRooms = chatRoomDao.getChatRoomsForUser(userId)
                    if (cachedRooms.isNotEmpty()) {
                        trySend(cachedRooms.sortedByDescending { it.lastMessageTime })
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val chatRooms = mutableListOf<ChatRoomEntity>()
                    snapshot.children.forEach { roomSnapshot ->
                        try {
                            val chatRoomId = roomSnapshot.child("chatRoomId").getValue(String::class.java) ?: return@forEach
                            val memberIds = roomSnapshot.child("memberIds").children.mapNotNull {
                                it.getValue(String::class.java)
                            }
                            val memberEmails = roomSnapshot.child("memberEmails").children.mapNotNull {
                                it.getValue(String::class.java)?.lowercase()
                            }

                            // Check if user is a member by UID or by email
                            val isMemberByUid = memberIds.contains(userId)
                            val isMemberByEmail = userEmail != null && memberEmails.contains(userEmail.lowercase())

                            // Only include rooms where user is a member (by UID or email)
                            if (!isMemberByUid && !isMemberByEmail) return@forEach

                            // If user found by email but not by UID, add their UID to memberIds
                            if (isMemberByEmail && !isMemberByUid) {
                                // Add user's UID to the room's memberIds in Firebase
                                val updatedMemberIds = memberIds.toMutableList().apply { add(userId) }
                                chatRoomsRef.child(chatRoomId).child("memberIds").setValue(updatedMemberIds)
                            }

                            val name = roomSnapshot.child("name").getValue(String::class.java) ?: "Chat Room"
                            val type = roomSnapshot.child("type").getValue(String::class.java) ?: "friend"
                            val tripId = roomSnapshot.child("tripId").getValue(String::class.java)
                            val createdBy = roomSnapshot.child("createdBy").getValue(String::class.java) ?: ""
                            val lastMessageText = roomSnapshot.child("lastMessageText").getValue(String::class.java)
                            val lastMessageTime = roomSnapshot.child("lastMessageTime").getValue(Long::class.java) ?: 0L

                            val chatRoom = ChatRoomEntity(
                                chatRoomId = chatRoomId,
                                name = name,
                                type = type,
                                tripId = tripId?.takeIf { it.isNotEmpty() },
                                createdBy = createdBy,
                                memberIds = memberIds.joinToString(","),
                                lastMessageText = lastMessageText,
                                lastMessageTime = lastMessageTime
                            )
                            chatRooms.add(chatRoom)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Cache to local database
                    repositoryScope.launch {
                        chatRooms.forEach { chatRoomDao.insertChatRoom(it) }
                    }

                    trySend(chatRooms.sortedByDescending { it.lastMessageTime })
                }

                override fun onCancelled(error: DatabaseError) {
                    // Don't close the flow on error, just log it
                    // This prevents crashes when Firebase has connection issues
                    error.toException().printStackTrace()
                    // Emit empty list on error so UI doesn't crash
                    trySend(emptyList())
                }
            }

            chatRoomsRef.addValueEventListener(listener)

            awaitClose {
                chatRoomsRef.removeEventListener(listener)
            }
        }
    }

    suspend fun getChatRoomById(chatRoomId: String): ChatRoomEntity? {
        // Try Firebase first
        try {
            val snapshot = chatRoomsRef.child(chatRoomId).get().await()
            if (snapshot.exists()) {
                val name = snapshot.child("name").getValue(String::class.java) ?: "Chat Room"
                val type = snapshot.child("type").getValue(String::class.java) ?: "friend"
                val tripId = snapshot.child("tripId").getValue(String::class.java)
                val createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: ""
                val memberIds = snapshot.child("memberIds").children.mapNotNull {
                    it.getValue(String::class.java)
                }.joinToString(",")
                val lastMessageText = snapshot.child("lastMessageText").getValue(String::class.java)
                val lastMessageTime = snapshot.child("lastMessageTime").getValue(Long::class.java) ?: 0L

                val chatRoom = ChatRoomEntity(
                    chatRoomId = chatRoomId,
                    name = name,
                    type = type,
                    tripId = tripId?.takeIf { it.isNotEmpty() },
                    createdBy = createdBy,
                    memberIds = memberIds,
                    lastMessageText = lastMessageText,
                    lastMessageTime = lastMessageTime
                )

                // Cache to local database
                chatRoomDao.insertChatRoom(chatRoom)
                return chatRoom
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to local database if Firebase fails
        return chatRoomDao.getChatRoomById(chatRoomId)
    }

    suspend fun getChatRoomByTripId(tripId: String): ChatRoomEntity? {
        // Try Firebase first
        try {
            val query = chatRoomsRef.orderByChild("tripId").equalTo(tripId)
            val snapshot = query.get().await()

            snapshot.children.firstOrNull()?.let { roomSnapshot ->
                val chatRoomId = roomSnapshot.child("chatRoomId").getValue(String::class.java) ?: return@let null
                val name = roomSnapshot.child("name").getValue(String::class.java) ?: "Chat Room"
                val type = roomSnapshot.child("type").getValue(String::class.java) ?: "trip"
                val createdBy = roomSnapshot.child("createdBy").getValue(String::class.java) ?: ""
                val memberIds = roomSnapshot.child("memberIds").children.mapNotNull {
                    it.getValue(String::class.java)
                }.joinToString(",")
                val lastMessageText = roomSnapshot.child("lastMessageText").getValue(String::class.java)
                val lastMessageTime = roomSnapshot.child("lastMessageTime").getValue(Long::class.java) ?: 0L

                val chatRoom = ChatRoomEntity(
                    chatRoomId = chatRoomId,
                    name = name,
                    type = type,
                    tripId = tripId,
                    createdBy = createdBy,
                    memberIds = memberIds,
                    lastMessageText = lastMessageText,
                    lastMessageTime = lastMessageTime
                )

                // Cache to local database
                chatRoomDao.insertChatRoom(chatRoom)
                return chatRoom
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to local database if Firebase fails
        return chatRoomDao.getChatRoomByTripId(tripId)
    }

    suspend fun createChatRoom(
        name: String,
        type: String,
        createdBy: String,
        memberIds: List<String>,
        tripId: String? = null
    ): String {
        val chatRoomId = UUID.randomUUID().toString()
        val chatRoom = ChatRoomEntity(
            chatRoomId = chatRoomId,
            name = name,
            type = type,
            tripId = tripId,
            createdBy = createdBy,
            memberIds = memberIds.joinToString(",")
        )

        // Save to local database
        chatRoomDao.insertChatRoom(chatRoom)

        // Save to Firebase
        val firebaseData = mapOf(
            "chatRoomId" to chatRoomId,
            "name" to name,
            "type" to type,
            "tripId" to (tripId ?: ""),
            "createdBy" to createdBy,
            "memberIds" to memberIds,
            "createdAt" to ServerValue.TIMESTAMP
        )
        chatRoomsRef.child(chatRoomId).setValue(firebaseData).await()

        return chatRoomId
    }

    // Create chat room with email-based member lookup support
    suspend fun createChatRoomWithEmails(
        name: String,
        type: String,
        createdBy: String,
        memberIds: List<String>,
        memberEmails: List<String>,
        tripId: String? = null
    ): String {
        val chatRoomId = UUID.randomUUID().toString()
        val chatRoom = ChatRoomEntity(
            chatRoomId = chatRoomId,
            name = name,
            type = type,
            tripId = tripId,
            createdBy = createdBy,
            memberIds = memberIds.joinToString(",")
        )

        // Save to local database
        chatRoomDao.insertChatRoom(chatRoom)

        // Save to Firebase with both memberIds (UIDs) and memberEmails for lookup
        val firebaseData = mapOf(
            "chatRoomId" to chatRoomId,
            "name" to name,
            "type" to type,
            "tripId" to (tripId ?: ""),
            "createdBy" to createdBy,
            "memberIds" to memberIds,
            "memberEmails" to memberEmails,  // Store emails for lookup
            "createdAt" to ServerValue.TIMESTAMP
        )
        chatRoomsRef.child(chatRoomId).setValue(firebaseData).await()

        return chatRoomId
    }

    suspend fun deleteChatRoom(chatRoomId: String) {
        val chatRoom = chatRoomDao.getChatRoomById(chatRoomId)
        if (chatRoom != null) {
            chatRoomDao.deleteChatRoom(chatRoom)
            chatMessageDao.deleteMessagesByChatRoom(chatRoomId)

            // Delete from Firebase
            chatRoomsRef.child(chatRoomId).removeValue().await()
            messagesRef.child(chatRoomId).removeValue().await()
        }
    }

    // Message Management
    fun getMessagesByChatRoom(chatRoomId: String): Flow<List<ChatMessageEntity>> {
        return callbackFlow {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<ChatMessageEntity>()
                    snapshot.children.forEach { messageSnapshot ->
                        try {
                            val messageId = messageSnapshot.child("messageId").getValue(String::class.java) ?: return@forEach
                            val senderId = messageSnapshot.child("senderId").getValue(String::class.java) ?: return@forEach
                            val senderName = messageSnapshot.child("senderName").getValue(String::class.java) ?: return@forEach
                            val text = messageSnapshot.child("text").getValue(String::class.java) ?: ""
                            val imageUrl = messageSnapshot.child("imageUrl").getValue(String::class.java)
                            val type = messageSnapshot.child("type").getValue(String::class.java) ?: "text"
                            val timestamp = messageSnapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                            val message = ChatMessageEntity(
                                messageId = messageId,
                                chatRoomId = chatRoomId,
                                senderId = senderId,
                                senderName = senderName,
                                text = text,
                                imageUrl = imageUrl,
                                type = type,
                                timestamp = timestamp,
                                synced = true
                            )
                            messages.add(message)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Save to local database (wrapped in try-catch to prevent crashes)
                    repositoryScope.launch {
                        try {
                            chatMessageDao.insertMessages(messages)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Don't crash if local DB insert fails - messages are still shown from Firebase
                        }
                    }

                    trySend(messages.sortedBy { it.timestamp })
                }

                override fun onCancelled(error: DatabaseError) {
                    // Don't close the flow on error, just log it
                    error.toException().printStackTrace()
                    trySend(emptyList())
                }
            }

            messagesRef.child(chatRoomId).addValueEventListener(listener)

            awaitClose {
                messagesRef.child(chatRoomId).removeEventListener(listener)
            }
        }
    }

    suspend fun sendMessage(
        chatRoomId: String,
        senderId: String,
        senderName: String,
        text: String,
        imageUrl: String? = null,
        type: String = "text"
    ): String {
        // Ensure user exists in local database first
        ensureUserExists(senderId, senderName)

        // Ensure chat room exists in local database
        ensureChatRoomExists(chatRoomId)

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val message = ChatMessageEntity(
            messageId = messageId,
            chatRoomId = chatRoomId,
            senderId = senderId,
            senderName = senderName,
            text = text,
            imageUrl = imageUrl,
            type = type,
            timestamp = timestamp,
            synced = false
        )

        // Save to local database first
        chatMessageDao.insertMessage(message)

        // Send to Firebase
        try {
            val firebaseData = mapOf(
                "messageId" to messageId,
                "chatRoomId" to chatRoomId,
                "senderId" to senderId,
                "senderName" to senderName,
                "text" to text,
                "imageUrl" to (imageUrl ?: ""),
                "type" to type,
                "timestamp" to ServerValue.TIMESTAMP
            )
            messagesRef.child(chatRoomId).child(messageId).setValue(firebaseData).await()

            // Mark as synced
            chatMessageDao.markMessageAsSynced(messageId)

            // Update chat room's last message
            updateChatRoomLastMessage(chatRoomId, text, timestamp)
        } catch (e: Exception) {
            e.printStackTrace()
            // Message will remain unsynced and can be retried later
        }

        return messageId
    }

    private suspend fun updateChatRoomLastMessage(chatRoomId: String, text: String, timestamp: Long) {
        val chatRoom = chatRoomDao.getChatRoomById(chatRoomId)
        if (chatRoom != null) {
            val updated = chatRoom.copy(
                lastMessageText = text,
                lastMessageTime = timestamp
            )
            chatRoomDao.updateChatRoom(updated)

            // Update Firebase
            val updates = mapOf(
                "lastMessageText" to text,
                "lastMessageTime" to timestamp
            )
            chatRoomsRef.child(chatRoomId).updateChildren(updates).await()
        }
    }

    suspend fun deleteMessage(messageId: String) {
        val message = chatMessageDao.getMessageById(messageId)
        if (message != null) {
            chatMessageDao.deleteMessage(messageId)

            // Delete from Firebase
            messagesRef.child(message.chatRoomId).child(messageId).removeValue().await()
        }
    }

    // Sync unsynced messages
    suspend fun syncUnsyncedMessages() {
        val unsyncedMessages = chatMessageDao.getUnsyncedMessages()
        unsyncedMessages.forEach { message ->
            try {
                val firebaseData = mapOf(
                    "messageId" to message.messageId,
                    "chatRoomId" to message.chatRoomId,
                    "senderId" to message.senderId,
                    "senderName" to message.senderName,
                    "text" to message.text,
                    "imageUrl" to (message.imageUrl ?: ""),
                    "type" to message.type,
                    "timestamp" to message.timestamp
                )
                messagesRef.child(message.chatRoomId).child(message.messageId).setValue(firebaseData).await()
                chatMessageDao.markMessageAsSynced(message.messageId)
            } catch (e: Exception) {
                e.printStackTrace()
                // Will retry on next sync
            }
        }
    }

    // Create trip chat room automatically
    suspend fun createTripChatRoom(tripId: String, tripName: String, creatorId: String, memberIds: List<String>): String {
        // Check if chat room already exists
        val existing = getChatRoomByTripId(tripId)
        if (existing != null) {
            return existing.chatRoomId
        }

        return createChatRoom(
            name = "$tripName - Chat",
            type = "trip",
            createdBy = creatorId,
            memberIds = memberIds,
            tripId = tripId
        )
    }

    // Invite friends to an existing chat room
    suspend fun inviteFriendsToChatRoom(chatRoomId: String, friendIds: List<String>): Result<Unit> {
        return try {
            // Get current chat room from Firebase
            val snapshot = chatRoomsRef.child(chatRoomId).get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("Chat room not found"))
            }

            // Get current member IDs
            val currentMemberIds = snapshot.child("memberIds").children.mapNotNull {
                it.getValue(String::class.java)
            }.toMutableList()

            // Add new friends (avoid duplicates)
            friendIds.forEach { friendId ->
                if (!currentMemberIds.contains(friendId)) {
                    currentMemberIds.add(friendId)
                }
            }

            // Update Firebase
            val updates = mapOf(
                "memberIds" to currentMemberIds
            )
            chatRoomsRef.child(chatRoomId).updateChildren(updates).await()

            // Update local database
            val chatRoom = chatRoomDao.getChatRoomById(chatRoomId)
            if (chatRoom != null) {
                val updated = chatRoom.copy(
                    memberIds = currentMemberIds.joinToString(",")
                )
                chatRoomDao.updateChatRoom(updated)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Remove member from chat room
    suspend fun removeMemberFromChatRoom(chatRoomId: String, memberId: String): Result<Unit> {
        return try {
            // Get current chat room from Firebase
            val snapshot = chatRoomsRef.child(chatRoomId).get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("Chat room not found"))
            }

            // Get current member IDs
            val currentMemberIds = snapshot.child("memberIds").children.mapNotNull {
                it.getValue(String::class.java)
            }.toMutableList()

            // Remove the member
            currentMemberIds.remove(memberId)

            // Update Firebase
            val updates = mapOf(
                "memberIds" to currentMemberIds
            )
            chatRoomsRef.child(chatRoomId).updateChildren(updates).await()

            // Update local database
            val chatRoom = chatRoomDao.getChatRoomById(chatRoomId)
            if (chatRoom != null) {
                val updated = chatRoom.copy(
                    memberIds = currentMemberIds.joinToString(",")
                )
                chatRoomDao.updateChatRoom(updated)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Get chat room members
    suspend fun getChatRoomMembers(chatRoomId: String): List<String> {
        return try {
            val snapshot = chatRoomsRef.child(chatRoomId).get().await()
            if (snapshot.exists()) {
                snapshot.child("memberIds").children.mapNotNull {
                    it.getValue(String::class.java)
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to local database
            val chatRoom = chatRoomDao.getChatRoomById(chatRoomId)
            chatRoom?.memberIds?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        }
    }

    // Helper method to ensure user exists in local database before inserting messages
    private suspend fun ensureUserExists(userId: String, userName: String) {
        val existingUser = userDao.getUser(userId)
        if (existingUser == null) {
            // Create minimal user entry for foreign key constraint
            val user = UserEntity(
                userId = userId,
                nickname = userName,
                email = ""
            )
            userDao.insertUser(user)
        }
    }

    // Helper method to ensure chat room exists in local database before inserting messages
    private suspend fun ensureChatRoomExists(chatRoomId: String) {
        val existingRoom = chatRoomDao.getChatRoomById(chatRoomId)
        if (existingRoom == null) {
            // Try to fetch from Firebase
            try {
                val snapshot = chatRoomsRef.child(chatRoomId).get().await()
                if (snapshot.exists()) {
                    val name = snapshot.child("name").getValue(String::class.java) ?: "Chat Room"
                    val type = snapshot.child("type").getValue(String::class.java) ?: "friend"
                    val tripId = snapshot.child("tripId").getValue(String::class.java)
                    val createdBy = snapshot.child("createdBy").getValue(String::class.java) ?: ""
                    val memberIds = snapshot.child("memberIds").children.mapNotNull {
                        it.getValue(String::class.java)
                    }.joinToString(",")

                    val chatRoom = ChatRoomEntity(
                        chatRoomId = chatRoomId,
                        name = name,
                        type = type,
                        tripId = tripId?.takeIf { it.isNotEmpty() },
                        createdBy = createdBy,
                        memberIds = memberIds
                    )
                    chatRoomDao.insertChatRoom(chatRoom)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // If Firebase fetch fails, create minimal entry
                val chatRoom = ChatRoomEntity(
                    chatRoomId = chatRoomId,
                    name = "Chat Room",
                    type = "friend",
                    tripId = null,
                    createdBy = "",
                    memberIds = ""
                )
                chatRoomDao.insertChatRoom(chatRoom)
            }
        }
    }

    // Get chat room member emails
    suspend fun getChatRoomMemberEmails(chatRoomId: String): List<String> {
        return try {
            val snapshot = chatRoomsRef.child(chatRoomId).get().await()
            if (snapshot.exists()) {
                snapshot.child("memberEmails").children.mapNotNull {
                    it.getValue(String::class.java)
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Invite member by email
    suspend fun inviteMemberByEmail(chatRoomId: String, email: String): Result<Unit> {
        return try {
            val snapshot = chatRoomsRef.child(chatRoomId).get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("Chat room not found"))
            }

            // Get current member emails
            val currentMemberEmails = snapshot.child("memberEmails").children.mapNotNull {
                it.getValue(String::class.java)
            }.toMutableList()

            // Add new email (avoid duplicates)
            val normalizedEmail = email.lowercase()
            if (!currentMemberEmails.contains(normalizedEmail)) {
                currentMemberEmails.add(normalizedEmail)
            } else {
                return Result.failure(Exception("이미 초대된 멤버입니다"))
            }

            // Update Firebase
            val updates = mapOf(
                "memberEmails" to currentMemberEmails
            )
            chatRoomsRef.child(chatRoomId).updateChildren(updates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
