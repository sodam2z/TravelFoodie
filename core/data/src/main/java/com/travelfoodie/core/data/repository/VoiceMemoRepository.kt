package com.travelfoodie.core.data.repository

import com.travelfoodie.core.data.local.dao.VoiceMemoDao
import com.travelfoodie.core.data.local.entity.VoiceMemoEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceMemoRepository @Inject constructor(
    private val voiceMemoDao: VoiceMemoDao
) {

    fun getMemosByUser(userId: String): Flow<List<VoiceMemoEntity>> {
        return voiceMemoDao.getMemosByUser(userId)
    }

    fun getMemosByTrip(tripId: String): Flow<List<VoiceMemoEntity>> {
        return voiceMemoDao.getMemosByTrip(tripId)
    }

    fun getAllMemos(): Flow<List<VoiceMemoEntity>> {
        return voiceMemoDao.getAllMemos()
    }

    suspend fun getMemoById(memoId: String): VoiceMemoEntity? {
        return voiceMemoDao.getMemoById(memoId)
    }

    fun searchMemos(query: String): Flow<List<VoiceMemoEntity>> {
        return voiceMemoDao.searchMemos(query)
    }

    suspend fun createMemo(
        userId: String,
        title: String,
        transcribedText: String,
        tripId: String? = null,
        audioFilePath: String? = null,
        durationMs: Long = 0
    ): VoiceMemoEntity {
        val memo = VoiceMemoEntity(
            memoId = UUID.randomUUID().toString(),
            userId = userId,
            title = title,
            transcribedText = transcribedText,
            tripId = tripId,
            audioFilePath = audioFilePath,
            durationMs = durationMs
        )
        voiceMemoDao.insertMemo(memo)
        return memo
    }

    suspend fun updateMemo(memo: VoiceMemoEntity) {
        voiceMemoDao.updateMemo(memo)
    }

    suspend fun deleteMemo(memoId: String) {
        voiceMemoDao.deleteMemoById(memoId)
    }

    suspend fun deleteMemosByTrip(tripId: String) {
        voiceMemoDao.deleteMemosByTrip(tripId)
    }
}
