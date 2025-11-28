package com.travelfoodie.feature.board

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.travelfoodie.core.data.local.dao.ChatMessageDao
import com.travelfoodie.core.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseChatSync @Inject constructor(
    private val chatMessageDao: ChatMessageDao
) {

    private val database = FirebaseDatabase.getInstance()
    private val messagesRef = database.getReference("trip_messages")

    fun syncMessages(chatRoomId: String) {
        val chatRoomMessagesRef = messagesRef.child(chatRoomId)

        chatRoomMessagesRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(FirebaseMessage::class.java)
                message?.let {
                    val chatMessage = ChatMessageEntity(
                        messageId = it.messageId,
                        chatRoomId = chatRoomId,
                        senderId = it.senderId,
                        senderName = it.senderName,
                        text = it.message,
                        imageUrl = it.imageUrl,
                        type = "text",
                        timestamp = it.timestamp,
                        synced = true
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        chatMessageDao.insertMessage(chatMessage)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(FirebaseMessage::class.java)
                message?.let {
                    val chatMessage = ChatMessageEntity(
                        messageId = it.messageId,
                        chatRoomId = chatRoomId,
                        senderId = it.senderId,
                        senderName = it.senderName,
                        text = it.message,
                        imageUrl = it.imageUrl,
                        type = "text",
                        timestamp = it.timestamp,
                        synced = true
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        chatMessageDao.updateMessage(chatMessage)
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val messageId = snapshot.key
                messageId?.let {
                    CoroutineScope(Dispatchers.IO).launch {
                        chatMessageDao.deleteMessage(it)
                    }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun sendMessage(chatRoomId: String, message: ChatMessageEntity) {
        val firebaseMessage = FirebaseMessage(
            messageId = message.messageId,
            senderId = message.senderId,
            senderName = message.senderName,
            message = message.text,
            imageUrl = message.imageUrl,
            timestamp = message.timestamp,
            isRead = false
        )

        messagesRef.child(chatRoomId).child(message.messageId).setValue(firebaseMessage)
    }

    data class FirebaseMessage(
        val messageId: String = "",
        val senderId: String = "",
        val senderName: String = "",
        val message: String = "",
        val imageUrl: String? = null,
        val timestamp: Long = 0,
        val isRead: Boolean = false
    )
}
