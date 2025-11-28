package com.travelfoodie.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.travelfoodie.core.data.local.dao.*
import com.travelfoodie.core.data.local.entity.*

@Database(
    entities = [
        UserEntity::class,
        TripEntity::class,
        MemberEntity::class,
        RegionEntity::class,
        PoiEntity::class,
        RestaurantEntity::class,
        FavoriteEntity::class,
        NotifScheduleEntity::class,
        ReceiptEntity::class,
        ChatRoomEntity::class,
        ChatMessageEntity::class,
        FriendEntity::class,
        TripInvitationEntity::class,
        UserInviteCodeEntity::class,
        VoiceMemoEntity::class
    ],
    version = 7, // Removed ForeignKey constraints from ChatMessageEntity
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun tripDao(): TripDao
    abstract fun memberDao(): MemberDao
    abstract fun regionDao(): RegionDao
    abstract fun poiDao(): PoiDao
    abstract fun restaurantDao(): RestaurantDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun notifScheduleDao(): NotifScheduleDao
    abstract fun receiptDao(): ReceiptDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun friendDao(): FriendDao
    abstract fun tripInvitationDao(): TripInvitationDao
    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun userInviteCodeDao(): UserInviteCodeDao
    abstract fun voiceMemoDao(): VoiceMemoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Note: This getInstance is deprecated - use Hilt injection instead
        // Database is provided via DataModule with proper migrations
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "travel_foodie_database"
                )
                    // Removed fallbackToDestructiveMigration - use DataModule for proper migrations
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
