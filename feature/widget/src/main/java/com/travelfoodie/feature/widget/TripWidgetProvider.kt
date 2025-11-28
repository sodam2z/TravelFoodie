package com.travelfoodie.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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

        /**
         * Call this method to update all widgets when trip data changes
         */
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

        // Handle refresh button click
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
            android.util.Log.d(TAG, "Updating widget ID: $appWidgetId")
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        android.util.Log.d(TAG, "updateAppWidget started for widgetId: $appWidgetId")

        // Get resource IDs dynamically from app's package
        val packageName = context.packageName
        val layoutId = context.resources.getIdentifier("widget_trip", "layout", packageName)
        val titleId = context.resources.getIdentifier("widget_trip_title", "id", packageName)
        val ddayId = context.resources.getIdentifier("widget_trip_dday", "id", packageName)
        val datesId = context.resources.getIdentifier("widget_trip_dates", "id", packageName)
        val regionViewId = context.resources.getIdentifier("widget_trip_region", "id", packageName)
        val infoId = context.resources.getIdentifier("widget_trip_info", "id", packageName)
        val containerId = context.resources.getIdentifier("widget_container", "id", packageName)
        val refreshButtonId = context.resources.getIdentifier("widget_refresh_button", "id", packageName)

        android.util.Log.d(TAG, "Resource IDs - layout: $layoutId, title: $titleId, dday: $ddayId, dates: $datesId, region: $regionViewId, info: $infoId, container: $containerId, refresh: $refreshButtonId")

        if (layoutId == 0) {
            android.util.Log.e(TAG, "Could not find widget_trip layout!")
            return
        }

        // Check for missing IDs
        if (titleId == 0 || ddayId == 0 || datesId == 0 || regionViewId == 0 || infoId == 0) {
            android.util.Log.e(TAG, "Some view IDs not found! title=$titleId, dday=$ddayId, dates=$datesId, region=$regionViewId, info=$infoId")
        }

        val views = RemoteViews(packageName, layoutId)

        // Load trip data from database asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val currentTime = System.currentTimeMillis()

                // First check all trips in database
                val allTrips = database.tripDao().getAllTripsOrderedByDate()
                android.util.Log.d(TAG, "Total trips in database: ${allTrips.size}")
                allTrips.forEachIndexed { index, trip ->
                    android.util.Log.d(TAG, "  Trip $index: ${trip.title}, start=${trip.startDate}, end=${trip.endDate}")
                }

                // Get active or upcoming trip (ongoing trips first, then future trips)
                android.util.Log.d(TAG, "Querying database for active/upcoming trips... currentTime=$currentTime")
                var nextTrip = database.tripDao().getActiveOrUpcomingTrips(currentTime)
                    .firstOrNull()

                // If no active/upcoming trips, get the most recent trip (for newly created trips)
                if (nextTrip == null && allTrips.isNotEmpty()) {
                    android.util.Log.d(TAG, "No active/upcoming trips, using first from all trips...")
                    nextTrip = allTrips.firstOrNull()
                }

                android.util.Log.d(TAG, "Selected trip: ${nextTrip?.title ?: "null"}, startDate=${nextTrip?.startDate}, endDate=${nextTrip?.endDate}")

                if (nextTrip != null) {
                    // Calculate D-day
                    val daysUntil = TimeUnit.MILLISECONDS.toDays(nextTrip.startDate - currentTime)
                    val dDayText = when {
                        daysUntil < 0 -> "완료"
                        daysUntil == 0L -> "D-Day"
                        else -> "D-$daysUntil"
                    }

                    // Get region for this trip
                    val regions = database.regionDao().getRegionsByTripId(nextTrip.tripId)
                    val firstRegion = regions.firstOrNull()

                    // Count POIs and restaurants
                    var poiCount = 0
                    var restaurantCount = 0

                    if (firstRegion != null) {
                        poiCount = database.poiDao().getPoiByRegionId(firstRegion.regionId).size
                        restaurantCount = database.restaurantDao().getRestaurantsByRegionId(firstRegion.regionId).size
                    }

                    // Format date range
                    val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                    val startDate = dateFormat.format(Date(nextTrip.startDate))
                    val endDate = dateFormat.format(Date(nextTrip.endDate))
                    val dateRange = "$startDate - $endDate"

                    android.util.Log.d(TAG, "Widget data: title=${nextTrip.title}, dday=$dDayText, region=${firstRegion?.name}, pois=$poiCount, restaurants=$restaurantCount")

                    // Update widget on main thread
                    CoroutineScope(Dispatchers.Main).launch {
                        views.setTextViewText(titleId, nextTrip.title)
                        views.setTextViewText(ddayId, dDayText)

                        val region = firstRegion?.name ?: ""
                        val regionText = if (region.isNotEmpty()) "📍 $region" else ""
                        views.setTextViewText(datesId, dateRange)
                        views.setTextViewText(regionViewId, regionText)
                        views.setTextViewText(infoId, "명소 ${poiCount}개 / 맛집 ${restaurantCount}개")

                        // Add click intent to open app
                        val openAppIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (openAppIntent != null) {
                            val openAppPendingIntent = PendingIntent.getActivity(
                                context,
                                0,
                                openAppIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(containerId, openAppPendingIntent)
                        }

                        // Add refresh button click intent
                        val refreshIntent = Intent(context, TripWidgetProvider::class.java).apply {
                            action = ACTION_REFRESH
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            context,
                            1,
                            refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(refreshButtonId, refreshPendingIntent)

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                        android.util.Log.d(TAG, "Widget updated successfully!")
                    }
                } else {
                    // No upcoming trips
                    android.util.Log.d(TAG, "No trips found, showing empty state")
                    CoroutineScope(Dispatchers.Main).launch {
                        views.setTextViewText(titleId, "예정된 여행 없음")
                        views.setTextViewText(ddayId, "--")
                        views.setTextViewText(datesId, "")
                        views.setTextViewText(regionViewId, "")
                        views.setTextViewText(infoId, "새로운 여행을 계획하세요")

                        // Add click intent to open app
                        val openAppIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (openAppIntent != null) {
                            val openAppPendingIntent = PendingIntent.getActivity(
                                context,
                                0,
                                openAppIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            views.setOnClickPendingIntent(containerId, openAppPendingIntent)
                        }

                        // Add refresh button click intent
                        val refreshIntent = Intent(context, TripWidgetProvider::class.java).apply {
                            action = ACTION_REFRESH
                        }
                        val refreshPendingIntent = PendingIntent.getBroadcast(
                            context,
                            1,
                            refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(refreshButtonId, refreshPendingIntent)

                        appWidgetManager.updateAppWidget(appWidgetId, views)
                        android.util.Log.d(TAG, "Widget updated with empty state")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "ERROR loading widget data: ${e.message}", e)
                e.printStackTrace()
                // Fallback to default display
                CoroutineScope(Dispatchers.Main).launch {
                    views.setTextViewText(titleId, "여행 정보 로딩 실패")
                    views.setTextViewText(ddayId, "--")
                    views.setTextViewText(infoId, "앱을 열어 확인하세요")

                    // Still set up click to open app
                    val openAppIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (openAppIntent != null) {
                        val openAppPendingIntent = PendingIntent.getActivity(
                            context,
                            0,
                            openAppIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(containerId, openAppPendingIntent)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }
    }
}
