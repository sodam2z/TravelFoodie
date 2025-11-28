package com.travelfoodie.core.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.travelfoodie.core.data.local.dao.FriendDao
import com.travelfoodie.core.data.local.dao.UserInviteCodeDao
import com.travelfoodie.core.data.local.entity.FriendEntity
import com.travelfoodie.core.data.local.entity.UserInviteCodeEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class FriendRepository @Inject constructor(
    private val friendDao: FriendDao,
    private val userInviteCodeDao: UserInviteCodeDao,
    @ApplicationContext private val context: Context
) {
    // Friend management
    fun getFriendsByUser(userId: String): Flow<List<FriendEntity>> {
        return friendDao.getFriendsByUser(userId)
    }

    suspend fun getFriendById(friendId: Long): FriendEntity? {
        return friendDao.getFriendById(friendId)
    }

    suspend fun addFriend(userId: String, name: String, contactType: String, contactValue: String): Long {
        val friend = FriendEntity(
            userId = userId,
            friendUserId = null, // Will be populated if they join
            name = name,
            contactType = contactType,
            contactValue = contactValue
        )
        return friendDao.insertFriend(friend)
    }

    suspend fun deleteFriend(friend: FriendEntity) {
        friendDao.deleteFriend(friend)
    }

    // Invite code management
    suspend fun generateInviteCode(userId: String): String {
        val existingCode = userInviteCodeDao.getInviteCodeByUserId(userId)
        if (existingCode != null) {
            return existingCode.inviteCode
        }

        // Generate unique 6-digit code
        var code: String
        var attempts = 0
        do {
            code = String.format("%06d", Random.nextInt(0, 1000000))
            val existing = userInviteCodeDao.getUserByInviteCode(code)
            attempts++
        } while (existing != null && attempts < 10)

        if (attempts >= 10) {
            // Fallback to UUID-based code
            code = UUID.randomUUID().toString().take(6).uppercase()
        }

        val inviteCodeEntity = UserInviteCodeEntity(
            userId = userId,
            inviteCode = code
        )
        userInviteCodeDao.insertInviteCode(inviteCodeEntity)
        return code
    }

    suspend fun getUserIdByInviteCode(inviteCode: String): String? {
        return userInviteCodeDao.getUserByInviteCode(inviteCode)?.userId
    }

    suspend fun addFriendByInviteCode(currentUserId: String, inviteCode: String): Result<Long> {
        val inviteCodeEntity = userInviteCodeDao.getUserByInviteCode(inviteCode)
            ?: return Result.failure(Exception("Invalid invite code"))

        if (inviteCodeEntity.userId == currentUserId) {
            return Result.failure(Exception("Cannot add yourself as a friend"))
        }

        // Check if already friends
        val existing = friendDao.getFriendByContact(currentUserId, inviteCode)
        if (existing != null) {
            return Result.failure(Exception("Already friends"))
        }

        val friendId = addFriend(
            userId = currentUserId,
            name = "Friend", // Will be updated when they accept
            contactType = "invite_code",
            contactValue = inviteCode
        )

        return Result.success(friendId)
    }

    // Phone contacts integration
    suspend fun getPhoneContacts(): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)?.replace(Regex("[^0-9+]"), "")

                    if (name != null && number != null) {
                        contacts.add(ContactInfo(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            // Permission denied or other error
            e.printStackTrace()
        }

        return contacts
    }

    suspend fun addFriendFromPhoneContact(userId: String, contactInfo: ContactInfo): Long {
        return addFriend(
            userId = userId,
            name = contactInfo.name,
            contactType = "phone",
            contactValue = contactInfo.phoneNumber
        )
    }

    // Generate SMS invite message
    fun generateSMSInviteMessage(inviteCode: String): String {
        return "Join me on TravelFoodie! Use my invite code: $inviteCode to add me as a friend."
    }

    data class ContactInfo(
        val name: String,
        val phoneNumber: String
    )
}
