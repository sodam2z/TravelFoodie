package com.travelfoodie.core.data.local.dao

import androidx.room.*
import com.travelfoodie.core.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE chatRoomId = :chatRoomId ORDER BY timestamp ASC")
    fun getMessagesByChatRoom(chatRoomId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatRoomId = :chatRoomId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(chatRoomId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE synced = 0")
    suspend fun getUnsyncedMessages(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE messageId = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM chat_messages WHERE chatRoomId = :chatRoomId")
    suspend fun deleteMessagesByChatRoom(chatRoomId: String)

    @Query("UPDATE chat_messages SET synced = 1 WHERE messageId = :messageId")
    suspend fun markMessageAsSynced(messageId: String)
}
