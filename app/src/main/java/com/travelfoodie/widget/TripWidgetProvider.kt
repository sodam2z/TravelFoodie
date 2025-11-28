package com.travelfoodie.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.travelfoodie.R
import com.travelfoodie.core.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class TripWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "TripWidgetProvider"
        const val ACTION_REFRESH = "com.travelfoodie.widget.ACTION_REFRESH"

        fun updateAllWidgets(context: Context) {
            android.util.Log.d(TAG, "updateAllWidgets() called")
            val intent = Intent(context, TripWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TripWidgetProvider::class.java)
            )
            android.util.Log.d(TAG, "Found ${appWidgetIds.size} widgets to update")
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d(TAG, "onReceive: action=${intent.action}")
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH) {
            android.util.Log.d(TAG, "Refresh button clicked - updating widgets")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TripWidgetProvider::class.java)
            )
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        android.util.Log.d(TAG, "onUpdate: updating ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        android.util.Log.d(TAG, "updateAppWidget for widgetId: $appWidgetId")

        // Use direct R references since we're in the app module
        val views = RemoteViews(context.packageName, R.layout.widget_trip)

        // Load trip data from database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val currentTime = System.currentTimeMillis()

                // Get all trips
                val allTrips = database.tripDao().getAllTripsOrderedByDate()
                android.util.Log.d(TAG, "Total trips: ${allTrips.size}")

                // Get active/upcoming trip or most recent
                var nextTrip = database.tripDao().getActiveOrUpcomingTrips(currentTime).firstOrNull()
                if (nextTrip == null && allTrips.isNotEmpty()) {
                    nextTrip = allTrips.first()
                }

                android.util.Log.d(TAG, "Selected trip: ${nextTrip?.title ?: "null"}")

                CoroutineScope(Dispatchers.Main).launch {
                    if (nextTrip != null) {
                        // Calculate D-day
                        val daysUntil = TimeUnit.MILLISECONDS.toDays(nextTrip.startDate - currentTime)
                        val dDayText = when {
                            daysUntil < 0 -> "완료"
                            daysUntil == 0L -> "D-Day"
                            else -> "D-$daysUntil"
                        }

                        // Get region
                        val regions = database.regionDao().getRegionsByTripId(nextTrip.tripId)
                        val firstRegion = regions.firstOrNull()

                        // Count POIs and restaurants
                        var poiCount = 0
                        var restaurantCount = 0
                        if (firstRegion != null) {
                            poiCount = database.poiDao().getPoiByRegionId(firstRegion.regionId).size
                            restaurantCount = database.restaurantDao().getRestaurantsByRegionId(firstRegion.regionId).size
                        }

                        // Format dates
                        val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                        val startDate = dateFormat.format(Date(nextTrip.startDate))
                        val endDate = dateFormat.format(Date(nextTrip.endDate))

                        // Set widget content
                        views.setTextViewText(R.id.widget_trip_title, nextTrip.title)
                        views.setTextViewText(R.id.widget_trip_dday, dDayText)
                        views.setTextViewText(R.id.widget_trip_dates, "$startDate - $endDate")
                        views.setTextViewText(R.id.widget_trip_region, if (firstRegion != null) "📍 ${firstRegion.name}" else "")
                        views.setTextViewText(R.id.widget_trip_info, "명소 ${poiCount}개 / 맛집 ${restaurantCount}개")

                        android.util.Log.d(TAG, "Widget showing trip: ${nextTrip.title}")
                    } else {
                        // No trips
                        views.setTextViewText(R.id.widget_trip_title, "예정된 여행 없음")
                        views.setTextViewText(R.id.widget_trip_dday, "--")
                        views.setTextViewText(R.id.widget_trip_dates, "")
                        views.setTextViewText(R.id.widget_trip_region, "")
                        views.setTextViewText(R.id.widget_trip_info, "새로운 여행을 계획하세요")

                        android.util.Log.d(TAG, "Widget showing empty state")
                    }

                    // Set click to open app
                    val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (openAppIntent != null) {
                        val pendingIntent = PendingIntent.getActivity(
                            context, 0, openAppIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                    }

                    // Set refresh button
                    val refreshIntent = Intent(context, TripWidgetProvider::class.java).apply {
                        action = ACTION_REFRESH
                    }
                    val refreshPendingIntent = PendingIntent.getBroadcast(
                        context, 1, refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    android.util.Log.d(TAG, "Widget update complete")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error: ${e.message}", e)
                CoroutineScope(Dispatchers.Main).launch {
                    views.setTextViewText(R.id.widget_trip_title, "로딩 실패")
                    views.setTextViewText(R.id.widget_trip_dday, "--")
                    views.setTextViewText(R.id.widget_trip_info, "앱을 열어주세요")
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
