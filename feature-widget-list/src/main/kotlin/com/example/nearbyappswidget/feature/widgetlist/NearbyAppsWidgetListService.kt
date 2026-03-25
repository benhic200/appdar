package com.example.nearbyappswidget.feature.widgetlist

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * Provides the RemoteViewsFactory for the scrollable widget list.
 *
 * This service is declared in the manifest with an intent-filter for
 * `android.widget.RemoteViewsService`. The widget's ListView references
 * this service via its `android:remoteViewsService` attribute.
 */
class NearbyAppsWidgetListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        // Extract widget ID from intent (needed for per‑widget data isolation)
        val appWidgetId = intent.getIntExtra(
            android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
            android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
        )

        // Return a factory that creates RemoteViews for each list item
        return NearbyAppsWidgetListFactory(applicationContext, appWidgetId)
    }
}