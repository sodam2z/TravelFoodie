package com.travelfoodie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.travelfoodie.core.data.local.entity.FriendEntity
import com.travelfoodie.core.data.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendRepository: FriendRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _friends = MutableStateFlow<List<FriendEntity>>(emptyList())
    val friends: StateFlow<List<FriendEntity>> = _friends.asStateFlow()

    private val _inviteCode = MutableStateFlow("")
    val inviteCode: StateFlow<String> = _inviteCode.asStateFlow()

    private val currentUserId: String
        get() = firebaseAuth.currentUser?.uid ?: "guest"

    init {
        loadFriends()
        generateInviteCode()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            friendRepository.getFriendsByUser(currentUserId).collect { friendList ->
                _friends.value = friendList
            }
        }
    }

    private fun generateInviteCode() {
        viewModelScope.launch {
            val code = friendRepository.generateInviteCode(currentUserId)
            _inviteCode.value = code
        }
    }

    suspend fun addFriendByInviteCode(inviteCode: String): Result<Long> {
        return friendRepository.addFriendByInviteCode(currentUserId, inviteCode)
    }

    suspend fun getPhoneContacts(): List<FriendRepository.ContactInfo> {
        return friendRepository.getPhoneContacts()
    }

    suspend fun addFriendFromContact(contactInfo: FriendRepository.ContactInfo) {
        friendRepository.addFriendFromPhoneContact(currentUserId, contactInfo)
    }

    fun getSMSInviteMessage(): String {
        return friendRepository.generateSMSInviteMessage(_inviteCode.value)
    }

    suspend fun deleteFriend(friend: FriendEntity) {
        friendRepository.deleteFriend(friend)
    }
}
