package com.travelfoodie.core.data.local.dao

import androidx.room.*
import com.travelfoodie.core.data.local.entity.VoiceMemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceMemoDao {

    @Query("SELECT * FROM voice_memos WHERE userId = :userId ORDER BY createdAt DESC")
    fun getMemosByUser(userId: String): Flow<List<VoiceMemoEntity>>

    @Query("SELECT * FROM voice_memos WHERE tripId = :tripId ORDER BY createdAt DESC")
    fun getMemosByTrip(tripId: String): Flow<List<VoiceMemoEntity>>

    @Query("SELECT * FROM voice_memos ORDER BY createdAt DESC")
    fun getAllMemos(): Flow<List<VoiceMemoEntity>>

    @Query("SELECT * FROM voice_memos WHERE memoId = :memoId")
    suspend fun getMemoById(memoId: String): VoiceMemoEntity?

    @Query("SELECT * FROM voice_memos WHERE transcribedText LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchMemos(query: String): Flow<List<VoiceMemoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemo(memo: VoiceMemoEntity)

    @Update
    suspend fun updateMemo(memo: VoiceMemoEntity)

    @Delete
    suspend fun deleteMemo(memo: VoiceMemoEntity)

    @Query("DELETE FROM voice_memos WHERE memoId = :memoId")
    suspend fun deleteMemoById(memoId: String)

    @Query("DELETE FROM voice_memos WHERE tripId = :tripId")
    suspend fun deleteMemosByTrip(tripId: String)
}
