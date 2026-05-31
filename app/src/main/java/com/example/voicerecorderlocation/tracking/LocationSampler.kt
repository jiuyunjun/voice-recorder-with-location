package com.example.voicerecorderlocation.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.HandlerThread
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationSampler(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun locations(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            // NOTE: do NOT setWaitForAccurateLocation(true) — indoors it never gets an
            // "accurate" GPS fix, so the first result would never arrive. We want every
            // sample (incl. coarse WiFi/cell fixes) and let the Kalman filter smooth it.
            .build()

        val handlerThread = HandlerThread("LocationSamplerThread").also { it.start() }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it) }
            }
        }

        // Seed with the last known fix so the map can appear immediately — crucial
        // indoors where a fresh GPS fix may take a long time or never arrive.
        client.lastLocation.addOnSuccessListener { loc -> if (loc != null) trySend(loc) }

        client.requestLocationUpdates(request, callback, handlerThread.looper)
        awaitClose {
            client.removeLocationUpdates(callback)
            handlerThread.quitSafely()
        }
    }
}
