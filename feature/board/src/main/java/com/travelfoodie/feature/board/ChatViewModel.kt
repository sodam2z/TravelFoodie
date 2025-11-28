package com.travelfoodie.feature.board

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.travelfoodie.core.data.repository.ChatRepository
import com.travelfoodie.core.data.local.entity.ChatMessageEntity
import com.travelfoodie.core.data.local.entity.ChatRoomEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    private val _chatRooms = MutableStateFlow<List<ChatRoomEntity>>(emptyList())
    val chatRooms: StateFlow<List<ChatRoomEntity>> = _chatRooms.asStateFlow()

    private var currentChatRoomId: String? = null
    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "guest"

    private val currentUserEmail: String?
        get() = firebaseAuth.currentUser?.email

    init {
        loadUserChatRooms()
    }

    private fun loadUserChatRooms() {
        viewModelScope.launch {
            try {
                // Pass both UID and email so rooms can be found by either
                chatRepository.getUserChatRooms(currentUserId, currentUserEmail).collect { rooms ->
                    _chatRooms.value = rooms
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep empty list on error
                _chatRooms.value = emptyList()
            }
        }
    }

    fun loadMessagesByChatRoom(chatRoomId: String) {
        currentChatRoomId = chatRoomId
        viewModelScope.launch {
            try {
                chatRepository.getMessagesByChatRoom(chatRoomId).collect { messageList ->
                    _messages.value = messageList
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _messages.value = emptyList()
            }
        }
    }

    fun loadMessagesForTrip(tripId: String) {
        viewModelScope.launch {
            val chatRoom = chatRepository.getChatRoomByTripId(tripId)
            if (chatRoom != null) {
                loadMessagesByChatRoom(chatRoom.chatRoomId)
            }
        }
    }

    fun sendMessage(messageText: String) {
        val chatRoomId = currentChatRoomId ?: return
        val userId = currentUserId
        val userName = firebaseAuth.currentUser?.displayName ?: "User"

        viewModelScope.launch {
            chatRepository.sendMessage(
                chatRoomId = chatRoomId,
                senderId = userId,
                senderName = userName,
                text = messageText
            )
        }
    }

    fun createFriendChatRoom(friendIds: List<String>, chatRoomName: String) {
        viewModelScope.launch {
            val memberIds = listOf(currentUserId) + friendIds
            val chatRoomId = chatRepository.createChatRoom(
                name = chatRoomName,
                type = "friend",
                createdBy = currentUserId,
                memberIds = memberIds
            )
            loadMessagesByChatRoom(chatRoomId)
        }
    }

    suspend fun createFriendChatRoomAndGetId(friendIds: List<String>, chatRoomName: String): String {
        val memberIds = listOf(currentUserId) + friendIds
        return chatRepository.createChatRoom(
            name = chatRoomName,
            type = "friend",
            createdBy = currentUserId,
            memberIds = memberIds
        )
    }

    // Create chat room with email-based member identification
    // This stores both UID and email so users can find rooms by either
    suspend fun createEmailBasedChatRoom(
        myEmail: String,
        inviteEmail: String?,
        chatRoomName: String
    ): String {
        val myUid = currentUserId

        // Member IDs include UID for current user
        // For invited users, we store their email until they join (then their UID gets added)
        val memberIds = mutableListOf(myUid)

        // Member emails for lookup (both users can find the room by email)
        val memberEmails = mutableListOf(myEmail.lowercase())
        if (!inviteEmail.isNullOrEmpty()) {
            memberEmails.add(inviteEmail.lowercase())
        }

        return chatRepository.createChatRoomWithEmails(
            name = chatRoomName,
            type = "friend",
            createdBy = myUid,
            memberIds = memberIds,
            memberEmails = memberEmails
        )
    }

    fun createTripChatRoom(tripId: String, tripName: String, memberIds: List<String>) {
        viewModelScope.launch {
            val chatRoomId = chatRepository.createTripChatRoom(
                tripId = tripId,
                tripName = tripName,
                creatorId = currentUserId,
                memberIds = memberIds
            )
            loadMessagesByChatRoom(chatRoomId)
        }
    }

    fun syncUnsyncedMessages() {
        viewModelScope.launch {
            chatRepository.syncUnsyncedMessages()
        }
    }

    // Invite friends to current chat room
    suspend fun inviteFriendsToChatRoom(friendIds: List<String>): Result<Unit> {
        val chatRoomId = currentChatRoomId ?: return Result.failure(Exception("No chat room selected"))
        return chatRepository.inviteFriendsToChatRoom(chatRoomId, friendIds)
    }

    // Invite friends to specific chat room
    suspend fun inviteFriendsToChatRoom(chatRoomId: String, friendIds: List<String>): Result<Unit> {
        return chatRepository.inviteFriendsToChatRoom(chatRoomId, friendIds)
    }

    // Remove member from chat room
    suspend fun removeMemberFromChatRoom(chatRoomId: String, memberId: String): Result<Unit> {
        return chatRepository.removeMemberFromChatRoom(chatRoomId, memberId)
    }

    // Get chat room members
    suspend fun getChatRoomMembers(chatRoomId: String): List<String> {
        return chatRepository.getChatRoomMembers(chatRoomId)
    }

    // Get chat room member emails
    suspend fun getChatRoomMemberEmails(chatRoomId: String): List<String> {
        return chatRepository.getChatRoomMemberEmails(chatRoomId)
    }

    // Invite member by email
    suspend fun inviteMemberByEmail(chatRoomId: String, email: String): Result<Unit> {
        return chatRepository.inviteMemberByEmail(chatRoomId, email)
    }

    // Upload image to Firebase Storage and send as message
    suspend fun uploadAndSendImage(
        chatRoomId: String,
        imageUri: Uri,
        contentResolver: ContentResolver
    ): Result<Unit> {
        return try {
            val userId = currentUserId
            val userName = firebaseAuth.currentUser?.displayName ?: "User"

            // Generate unique filename
            val filename = "chat_images/${chatRoomId}/${UUID.randomUUID()}.jpg"
            val storageRef = FirebaseStorage.getInstance().reference.child(filename)

            // Upload image
            val inputStream = contentResolver.openInputStream(imageUri)
                ?: return Result.failure(Exception("Cannot read image"))

            val uploadTask = storageRef.putStream(inputStream).await()

            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await().toString()

            // Send message with image URL
            chatRepository.sendMessage(
                chatRoomId = chatRoomId,
                senderId = userId,
                senderName = userName,
                text = "",
                imageUrl = downloadUrl,
                type = "image"
            )

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
