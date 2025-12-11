package com.travelfoodie.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.travelfoodie.R
import com.travelfoodie.core.data.local.AppDatabase
import com.travelfoodie.core.data.local.entity.TripEntity
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class TripWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TripRemoteViewsFactory(applicationContext)
    }
}

class TripRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var trips: List<TripEntity> = emptyList()
    private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

    override fun onCreate() {
        android.util.Log.d("TripWidgetService", "onCreate")
    }

    override fun onDataSetChanged() {
        android.util.Log.d("TripWidgetService", "onDataSetChanged - loading trips")
        // Load trips from database - this is called when notifyAppWidgetViewDataChanged is invoked
        runBlocking {
            try {
                val database = AppDatabase.getInstance(context)
                val currentTime = System.currentTimeMillis()

                // Get ALL trips and sort them: ongoing first, then upcoming, then completed
                val allTrips = database.tripDao().getAllTripsOrderedByDate()

                trips = allTrips.sortedWith(compareBy(
                    // Priority 1: Ongoing trips (currently in progress)
                    { trip ->
                        if (trip.startDate <= currentTime && trip.endDate >= currentTime) 0 else 1
                    },
                    // Priority 2: Upcoming trips (sorted by start date ascending)
                    { trip ->
                        if (trip.startDate > currentTime) trip.startDate else Long.MAX_VALUE
                    },
                    // Priority 3: Completed trips (sorted by end date descending - most recent first)
                    { trip ->
                        if (trip.endDate < currentTime) -trip.endDate else 0
                    }
                ))

                android.util.Log.d("TripWidgetService", "Loaded ${trips.size} trips at time $currentTime")
                trips.forEach { trip ->
                    android.util.Log.d("TripWidgetService", "Trip: ${trip.title}, start: ${trip.startDate}, end: ${trip.endDate}")
                }
            } catch (e: Exception) {
                android.util.Log.e("TripWidgetService", "Error loading trips: ${e.message}")
                trips = emptyList()
            }
        }
    }

    override fun onDestroy() {
        trips = emptyList()
    }

    override fun getCount(): Int = trips.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= trips.size) {
            return RemoteViews(context.packageName, R.layout.widget_trip_item)
        }

        val trip = trips[position]
        val currentTime = System.currentTimeMillis()

        // Calculate D-day based on trip status
        val daysUntilStart = TimeUnit.MILLISECONDS.toDays(trip.startDate - currentTime)
        val daysUntilEnd = TimeUnit.MILLISECONDS.toDays(trip.endDate - currentTime)
        
        val dDayText = when {
            // Trip has ended (end date is in the past)
            trip.endDate < currentTime -> "완료"
            // Trip is ongoing (started but not ended)
            trip.startDate <= currentTime && trip.endDate >= currentTime -> "여행 중"
            // Trip starts today
            daysUntilStart == 0L -> "D-Day"
            // Trip is in the future
            daysUntilStart > 0 -> "D-$daysUntilStart"
            // Fallback
            else -> "완료"
        }

        // Format dates
        val startDate = dateFormat.format(Date(trip.startDate))
        val endDate = dateFormat.format(Date(trip.endDate))

        val views = RemoteViews(context.packageName, R.layout.widget_trip_item).apply {
            setTextViewText(R.id.widget_item_dday, dDayText)
            setTextViewText(R.id.widget_item_title, trip.title)
            setTextViewText(R.id.widget_item_dates, "$startDate - $endDate")
            setTextViewText(R.id.widget_item_region, trip.regionName)
        }

        // Set fill-in intent for item click
        val fillInIntent = Intent().apply {
            putExtra("trip_id", trip.tripId)
        }
        views.setOnClickFillInIntent(R.id.widget_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}
