package com.travelfoodie

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.kakao.sdk.common.KakaoSdk
import com.navercorp.nid.NaverIdLoginSDK
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TravelFoodieApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        initializeSocialSDKs()
    }

    private fun initializeSocialSDKs() {
        // Initialize Kakao SDK
        KakaoSdk.init(this, BuildConfig.KAKAO_API_KEY)

        // Initialize Naver SDK
        NaverIdLoginSDK.initialize(
            context = this,
            clientId = BuildConfig.NAVER_CLIENT_ID,
            clientSecret = BuildConfig.NAVER_CLIENT_SECRET,
            clientName = getString(R.string.app_name)
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Main channel for trip creation and reminders - HIGH importance for heads-up notifications
            val tripChannel = NotificationChannel(
                CHANNEL_TRAVEL_REMINDERS,
                "여행 알림",
                NotificationManager.IMPORTANCE_HIGH  // Changed to HIGH for heads-up notifications
            ).apply {
                description = "새 여행 생성 및 일정 알림"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300)
                setShowBadge(true)
            }
            notificationManager?.createNotificationChannel(tripChannel)
        }
    }

    companion object {
        const val CHANNEL_TRAVEL_REMINDERS = "travel_reminders"
    }
}
