package com.example.tafakkurai.location

/** Geographic coordinates used for prayer-time calculation. */
data class Coordinates(val latitude: Double, val longitude: Double)

/**
 * Requests the device location. Platform layer registers the actual
 * implementation (Android framework LocationManager / iOS CLLocationManager).
 */
expect class PrayerLocationManager {
    /** Returns current coordinates, or null if unavailable / permission denied. */
    suspend fun current(): Coordinates?
}
