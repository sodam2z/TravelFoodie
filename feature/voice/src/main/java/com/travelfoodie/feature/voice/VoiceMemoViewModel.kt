package com.travelfoodie.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelfoodie.core.data.local.entity.VoiceMemoEntity
import com.travelfoodie.core.data.repository.VoiceMemoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VoiceMemoViewModel @Inject constructor(
    private val voiceMemoRepository: VoiceMemoRepository
) : ViewModel() {

    private val _memos = MutableStateFlow<List<VoiceMemoEntity>>(emptyList())
    val memos: StateFlow<List<VoiceMemoEntity>> = _memos.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentTranscription = MutableStateFlow("")
    val currentTranscription: StateFlow<String> = _currentTranscription.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Default user ID - in production, get from auth
    private var currentUserId: String = "guest"

    init {
        loadMemos()
    }

    fun setUserId(userId: String) {
        currentUserId = userId
        loadMemos()
    }

    private fun loadMemos() {
        viewModelScope.launch {
            voiceMemoRepository.getAllMemos().collect { memoList ->
                _memos.value = memoList
            }
        }
    }

    fun setRecording(recording: Boolean) {
        _isRecording.value = recording
    }

    fun setTranscription(text: String) {
        _currentTranscription.value = text
    }

    fun saveMemo(title: String, transcribedText: String, tripId: String? = null) {
        viewModelScope.launch {
            try {
                val memoTitle = title.ifEmpty {
                    "음성 메모 ${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.KOREA).format(java.util.Date())}"
                }
                voiceMemoRepository.createMemo(
                    userId = currentUserId,
                    title = memoTitle,
                    transcribedText = transcribedText,
                    tripId = tripId
                )
                _currentTranscription.value = ""
            } catch (e: Exception) {
                _error.value = "메모 저장 실패: ${e.message}"
            }
        }
    }

    fun deleteMemo(memoId: String) {
        viewModelScope.launch {
            try {
                voiceMemoRepository.deleteMemo(memoId)
            } catch (e: Exception) {
                _error.value = "메모 삭제 실패: ${e.message}"
            }
        }
    }

    fun updateMemo(memo: VoiceMemoEntity) {
        viewModelScope.launch {
            try {
                voiceMemoRepository.updateMemo(memo)
            } catch (e: Exception) {
                _error.value = "메모 수정 실패: ${e.message}"
            }
        }
    }

    fun searchMemos(query: String) {
        viewModelScope.launch {
            if (query.isEmpty()) {
                loadMemos()
            } else {
                voiceMemoRepository.searchMemos(query).collect { memoList ->
                    _memos.value = memoList
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
