package com.travelfoodie.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.travelfoodie.R

class TripWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "TripWidgetProvider"
        const val ACTION_REFRESH = "com.travelfoodie.widget.ACTION_REFRESH"
        const val ACTION_ITEM_CLICK = "com.travelfoodie.widget.ACTION_ITEM_CLICK"

        fun updateAllWidgets(context: Context) {
            android.util.Log.d(TAG, "updateAllWidgets() called")
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TripWidgetProvider::class.java)
            )

            // Notify data changed for list refresh
            appWidgetIds.forEach { widgetId ->
                appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_trip_list)
            }

            // Also trigger onUpdate
            val intent = Intent(context, TripWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }
            context.sendBroadcast(intent)

            android.util.Log.d(TAG, "Updated ${appWidgetIds.size} widgets")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d(TAG, "onReceive: action=${intent.action}")
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_REFRESH -> {
                android.util.Log.d(TAG, "Refresh button clicked")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, TripWidgetProvider::class.java)
                )
                // Refresh data in the list
                appWidgetIds.forEach { widgetId ->
                    appWidgetManager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_trip_list)
                }
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            ACTION_ITEM_CLICK -> {
                val tripId = intent.getStringExtra("trip_id")
                android.util.Log.d(TAG, "Trip clicked: $tripId")
                // Open the app
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.let {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(it)
                }
            }
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

        val views = RemoteViews(context.packageName, R.layout.widget_trip)

        // Set up the RemoteViews adapter for the ListView
        val serviceIntent = Intent(context, TripWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Make each intent unique
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_trip_list, serviceIntent)

        // Set empty view
        views.setEmptyView(R.id.widget_trip_list, R.id.widget_empty_view)

        // Set up item click template
        val itemClickIntent = Intent(context, TripWidgetProvider::class.java).apply {
            action = ACTION_ITEM_CLICK
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val itemClickPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            itemClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_trip_list, itemClickPendingIntent)

        // Set up refresh button
        val refreshIntent = Intent(context, TripWidgetProvider::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

        // Set up click on container to open app
        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (openAppIntent != null) {
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header_title, openAppPendingIntent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        android.util.Log.d(TAG, "Widget update complete")
    }
}
