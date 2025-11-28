package com.travelfoodie.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.travelfoodie.core.data.local.AppDatabase
import com.travelfoodie.core.data.remote.GooglePlacesApi
import com.travelfoodie.core.data.remote.OpenAIApi
import com.travelfoodie.core.data.remote.api.KakaoApiService
import com.travelfoodie.core.data.remote.api.NaverApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KakaoRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NaverRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenAIRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GooglePlacesRetrofit

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    // Migration from version 5 to 6: Add voice_memos table
    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS voice_memos (
                    memoId TEXT PRIMARY KEY NOT NULL,
                    tripId TEXT,
                    userId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    transcribedText TEXT NOT NULL,
                    audioFilePath TEXT,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_voice_memos_tripId ON voice_memos(tripId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_voice_memos_userId ON voice_memos(userId)")
        }
    }

    // Migration from version 6 to 7: Remove ForeignKey constraints from chat_messages
    // This fixes crash when receiver opens chat (FK constraint failed because chatRoom/user not in local DB)
    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Recreate chat_messages table without foreign keys
            // Step 1: Create new table without FK constraints
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_messages_new (
                    messageId TEXT PRIMARY KEY NOT NULL,
                    chatRoomId TEXT NOT NULL,
                    senderId TEXT NOT NULL,
                    senderName TEXT NOT NULL,
                    text TEXT NOT NULL,
                    imageUrl TEXT,
                    type TEXT NOT NULL DEFAULT 'text',
                    timestamp INTEGER NOT NULL,
                    synced INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            // Step 2: Copy data from old table
            database.execSQL("""
                INSERT INTO chat_messages_new (messageId, chatRoomId, senderId, senderName, text, imageUrl, type, timestamp, synced)
                SELECT messageId, chatRoomId, senderId, senderName, text, imageUrl, type, timestamp, synced
                FROM chat_messages
            """.trimIndent())

            // Step 3: Drop old table
            database.execSQL("DROP TABLE chat_messages")

            // Step 4: Rename new table
            database.execSQL("ALTER TABLE chat_messages_new RENAME TO chat_messages")

            // Step 5: Recreate indices
            database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_chatRoomId ON chat_messages(chatRoomId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_senderId ON chat_messages(senderId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_timestamp ON chat_messages(timestamp)")
        }
    }

    // Migration from version 4 to 5 (if needed for user_invite_codes)
    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS user_invite_codes (
                    odCode TEXT PRIMARY KEY NOT NULL,
                    odUserId TEXT NOT NULL,
                    odCreatedAt INTEGER NOT NULL,
                    odExpiresAt INTEGER
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_user_invite_codes_odUserId ON user_invite_codes(odUserId)")
        }
    }

    // Migration from version 3 to 4 (for chat and friends tables)
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create chat_rooms table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_rooms (
                    crChatRoomId TEXT PRIMARY KEY NOT NULL,
                    crTripId TEXT,
                    crChatName TEXT NOT NULL,
                    crCreatedAt INTEGER NOT NULL,
                    crLastMessageAt INTEGER
                )
            """.trimIndent())

            // Create chat_messages table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS chat_messages (
                    cmMessageId TEXT PRIMARY KEY NOT NULL,
                    cmChatRoomId TEXT NOT NULL,
                    cmSenderId TEXT NOT NULL,
                    cmSenderName TEXT NOT NULL,
                    cmContent TEXT NOT NULL,
                    cmTimestamp INTEGER NOT NULL,
                    cmIsRead INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_cmChatRoomId ON chat_messages(cmChatRoomId)")

            // Create friends table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS friends (
                    odFriendshipId TEXT PRIMARY KEY NOT NULL,
                    odUserId TEXT NOT NULL,
                    odFriendId TEXT NOT NULL,
                    odFriendName TEXT NOT NULL,
                    odFriendEmail TEXT,
                    odStatus TEXT NOT NULL,
                    odCreatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_friends_odUserId ON friends(odUserId)")

            // Create trip_invitations table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS trip_invitations (
                    odInviteId TEXT PRIMARY KEY NOT NULL,
                    odTripId TEXT NOT NULL,
                    odInviterId TEXT NOT NULL,
                    odInviteeId TEXT NOT NULL,
                    odStatus TEXT NOT NULL,
                    odCreatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trip_invitations_odTripId ON trip_invitations(odTripId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trip_invitations_odInviteeId ON trip_invitations(odInviteeId)")
        }
    }

    // Migration from version 2 to 3 (for receipts table)
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS receipts (
                    receiptId TEXT PRIMARY KEY NOT NULL,
                    tripId TEXT NOT NULL,
                    userId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    amount REAL NOT NULL,
                    currency TEXT NOT NULL DEFAULT 'KRW',
                    category TEXT NOT NULL,
                    imageUri TEXT,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_tripId ON receipts(tripId)")
        }
    }

    // Migration from version 1 to 2 (for initial tables if any changes)
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add any schema changes from version 1 to 2
            // This is a placeholder - adjust based on actual schema changes
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "travelfoodie.db"
        )
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )
        // Removed fallbackToDestructiveMigration() to preserve user data
        // If migration fails, app will crash - this is intentional to catch migration issues early
        .build()
    }

    @Provides
    fun provideUserDao(database: AppDatabase) = database.userDao()

    @Provides
    fun provideTripDao(database: AppDatabase) = database.tripDao()

    @Provides
    fun provideMemberDao(database: AppDatabase) = database.memberDao()

    @Provides
    fun provideRegionDao(database: AppDatabase) = database.regionDao()

    @Provides
    fun providePoiDao(database: AppDatabase) = database.poiDao()

    @Provides
    fun provideRestaurantDao(database: AppDatabase) = database.restaurantDao()

    @Provides
    fun provideFavoriteDao(database: AppDatabase) = database.favoriteDao()

    @Provides
    fun provideNotifScheduleDao(database: AppDatabase) = database.notifScheduleDao()

    @Provides
    fun provideReceiptDao(database: AppDatabase) = database.receiptDao()

    @Provides
    fun provideChatMessageDao(database: AppDatabase) = database.chatMessageDao()

    @Provides
    fun provideVoiceMemoDao(database: AppDatabase) = database.voiceMemoDao()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @KakaoRetrofit
    fun provideKakaoRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @NaverRetrofit
    fun provideNaverRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://openapi.naver.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideKakaoApiService(@KakaoRetrofit retrofit: Retrofit): KakaoApiService {
        return retrofit.create(KakaoApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNaverApiService(@NaverRetrofit retrofit: Retrofit): NaverApiService {
        return retrofit.create(NaverApiService::class.java)
    }

    @Provides
    @Singleton
    @OpenAIRetrofit
    fun provideOpenAIRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @GooglePlacesRetrofit
    fun provideGooglePlacesRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIApi(@OpenAIRetrofit retrofit: Retrofit): OpenAIApi {
        return retrofit.create(OpenAIApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGooglePlacesApi(@GooglePlacesRetrofit retrofit: Retrofit): GooglePlacesApi {
        return retrofit.create(GooglePlacesApi::class.java)
    }

    @Provides
    fun provideFriendDao(database: AppDatabase) = database.friendDao()

    @Provides
    fun provideTripInvitationDao(database: AppDatabase) = database.tripInvitationDao()

    @Provides
    fun provideChatRoomDao(database: AppDatabase) = database.chatRoomDao()

    @Provides
    fun provideUserInviteCodeDao(database: AppDatabase) = database.userInviteCodeDao()
}
