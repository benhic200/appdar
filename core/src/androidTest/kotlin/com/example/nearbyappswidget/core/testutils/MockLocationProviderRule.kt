package com.benhic.appdar.core.testutils

import android.location.Location
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.runBlocking
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that enables mock‑location mode before each test and disables it after.
 * Also grants the `android.permission.ACCESS_FINE_LOCATION` permission if needed.
 */
class MockLocationProviderRule : TestWatcher() {
    private lateinit var fusedClient: FusedLocationProviderClient

    override fun starting(description: Description) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fusedClient = LocationServices.getFusedLocationProviderClient(context)

        runBlocking {
            // Enable mock mode
            fusedClient.setMockMode(true).await()
            // Push a default location so the provider is active
            val defaultLocation = Location("test").apply {
                latitude = 51.5074 // London
                longitude = -0.1278
                accuracy = 10f
                time = System.currentTimeMillis()
            }
            fusedClient.setMockLocation(defaultLocation).await()
        }
        super.starting(description)
    }

    override fun finished(description: Description) {
        runBlocking {
            fusedClient.setMockMode(false).await()
        }
        super.finished(description)
    }
}