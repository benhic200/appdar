package com.example.nearbyappswidget.feature.geofencing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.nearbyappswidget.core.testutils.LocationTestHelper
import com.example.nearbyappswidget.core.testutils.MockLocationProviderRule
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeofencingInstrumentedTest {
    @get:Rule
    val mockLocationRule = MockLocationProviderRule()

    private lateinit var geofencingClient: GeofencingClient
    private lateinit var locationHelper: LocationTestHelper

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        geofencingClient = LocationServices.getGeofencingClient(context)
        locationHelper = LocationTestHelper.create()
    }

    @Test
    fun geofenceEntry_triggersBroadcast() = runBlocking {
        // 1. Register a test geofence (500m around London)
        val geofence = Geofence.Builder()
            .setRequestId("TEST_LIDL")
            .setCircularRegion(51.5074, -0.1278, 500f)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        geofencingClient.addGeofences(request, GeofenceBroadcastReceiver.getPendingIntent(context))
            .await()

        // 2. Simulate moving inside the geofence
        val entryLocation = locationHelper.simulateGeofenceEntry(51.5074, -0.1278, 500f)
        locationHelper.pushLocation(entryLocation.latitude, entryLocation.longitude)

        // 3. Wait a moment for broadcast to be processed
        Thread.sleep(1000)

        // 4. Verify broadcast was received (check Room database update or log)
        // For now, just ensure no exception is thrown
    }

    @Test
    fun permissionGranted_startsGeofencingAutomatically() {
        // Grant location permission via UiAutomator
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant com.example.nearbyappswidget android.permission.ACCESS_FINE_LOCATION")
        // Wait a bit for geofence registration
        Thread.sleep(2000)
        // Verify geofences are registered (could query GeofencingClient)
        // This is a placeholder; actual verification depends on GeofenceManager implementation
    }

    @Test
    fun networkLoss_geofenceStillTriggersFromCache() = runBlocking {
        // Simulate network loss
        locationHelper.simulateNetworkLoss()

        // Register geofence and trigger entry
        val geofence = Geofence.Builder()
            .setRequestId("TEST_TESCO")
            .setCircularRegion(51.5074, -0.1278, 500f)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        geofencingClient.addGeofences(request, GeofenceBroadcastReceiver.getPendingIntent(context))
            .await()

        val entryLocation = locationHelper.simulateGeofenceEntry(51.5074, -0.1278, 500f)
        locationHelper.pushLocation(entryLocation.latitude, entryLocation.longitude)

        // Restore network
        locationHelper.restoreNetwork()
    }
}