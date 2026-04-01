package com.benhic.appdar.feature.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Real location provider using FusedLocationProviderClient.
 * Requires either ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION permission.
 * If permission is not granted, returns null.
 */
class RealLocationProvider @Inject constructor(
    private val context: Context
) : LocationProvider {

    companion object {
        private const val TAG = "RealLocationProvider"
        private const val MAX_LOCATION_AGE_MS = 5 * 60 * 1000L // 5 minutes
        private const val MIN_ACCURACY_METERS = 500.0f // Accept locations within 500m — good enough for a 50km business search
    }

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    // Single background thread executor for location callbacks.
    // Avoids deadlock when getCurrentLocation() is called from runBlocking on the main thread:
    // addOnCompleteListener defaults to posting on the main Looper, which is blocked by
    // runBlocking and can never fire. Using an explicit executor dispatches the callback to
    // a background thread instead, allowing continuation.resume() to unblock runBlocking.
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    override suspend fun getCurrentLocation(): Location? {
        Log.d(TAG, "getCurrentLocation() called, checking permissions...")
        // Check location permissions
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Permission check - fine: $hasFineLocation, coarse: $hasCoarseLocation")
        if (!hasFineLocation && !hasCoarseLocation) {
            Log.w(TAG, "Location permission not granted")
            return null
        }
        Log.d(TAG, "Location permission granted, proceeding with location request")

        // Try last known location first — returns instantly from cache and works in background.
        // Uses callbackExecutor so the callback fires on a background thread, not the main Looper
        // (which may be blocked by runBlocking in the widget provider).
        Log.d(TAG, "Trying getLastLocation from fused client...")
        val lastLocation = suspendCancellableCoroutine<Location?> { continuation ->
            fusedClient.lastLocation.addOnCompleteListener(callbackExecutor) { task ->
                Log.d(TAG, "lastLocation task completed, isSuccessful=${task.isSuccessful}, result=${task.result}")
                continuation.resume(if (task.isSuccessful) task.result else null)
            }
        }
        if (lastLocation != null) {
            Log.d(TAG, "Last location obtained: (${lastLocation.latitude}, ${lastLocation.longitude}) accuracy=${lastLocation.accuracy} age=${System.currentTimeMillis() - lastLocation.time}ms")
            val ageMs = System.currentTimeMillis() - lastLocation.time
            val isFresh = ageMs < MAX_LOCATION_AGE_MS
            val isAccurate = lastLocation.hasAccuracy() && lastLocation.accuracy < MIN_ACCURACY_METERS
            if (isFresh && isAccurate) {
                Log.d(TAG, "Last location is fresh and accurate enough, using cached location")
                return lastLocation
            } else {
                Log.d(TAG, "Last location is stale (age=${ageMs}ms) or inaccurate (accuracy=${lastLocation.accuracy}), requesting fresh fix")
                // Continue to request fresh location
            }
        } else {
            Log.d(TAG, "No last location available")
        }

        // No cached location — fall back to requesting a fresh fix.
        Log.d(TAG, "No last location, requesting current location from fused client...")
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Starting suspendCancellableCoroutine for location request")
            val cancellationTokenSource = CancellationTokenSource()

            fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).addOnCompleteListener(callbackExecutor) { task ->
                Log.d(TAG, "Location task completed, isSuccessful: ${task.isSuccessful}")
                if (task.isSuccessful) {
                    val location = task.result
                    if (location != null) {
                        Log.d(TAG, "Location obtained: (${location.latitude}, ${location.longitude}) accuracy=${location.accuracy}")
                        continuation.resume(location)
                    } else {
                        Log.w(TAG, "Location task succeeded but location is null")
                        continuation.resume(null)
                    }
                } else {
                    val exception = task.exception
                    Log.e(TAG, "Failed to get location", exception)
                    continuation.resume(null)
                }
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "Location request cancelled")
                cancellationTokenSource.cancel()
            }
        }
    }
}
