package com.example.tafakkurai.prayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.PrayerTime
import com.example.tafakkurai.TPRAYERS
import com.example.tafakkurai.location.Coordinates
import com.example.tafakkurai.location.PrayerLocationManager
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app's prayer-schedule source. This is the seam where a real network/API
 * client plugs in: return that day's [PrayerTime]s (with `min` = minutes of day).
 *
 * @param dayOffset 0 = today, -1 = yesterday, +1 = tomorrow.
 */
interface PrayerScheduleRepository {
    fun schedule(dayOffset: Int): List<PrayerTime>
}

/**
 * Default repository backed by the bundled static schedule ([TPRAYERS]). Swap this
 * for an API-backed implementation without touching the calculator or ViewModel.
 */
class StaticPrayerScheduleRepository : PrayerScheduleRepository {
    override fun schedule(dayOffset: Int): List<PrayerTime> = TPRAYERS
}

/**
 * Computes the schedule from the device location using [PrayerCalculator]. Call
 * [refresh] (suspending) to fetch coordinates before rendering; [schedule] stays
 * synchronous so the timeline/ViewModel are untouched. Falls back to [TPRAYERS]
 * until a fix is available or if permission is denied.
 */
class LocationPrayerScheduleRepository(
    private val locationManager: PrayerLocationManager,
    private val calculator: PrayerCalculator = PrayerCalculator(),
    private val fallback: PrayerScheduleRepository = StaticPrayerScheduleRepository(),
) : PrayerScheduleRepository {

    /**
     * Held as Compose state so every screen reading [schedule] recomposes the moment
     * a fix lands — the permission dialog is answered long after first composition.
     */
    private var coordinates by mutableStateOf<Coordinates?>(null)

    /** True once a fix has been obtained, so callers can stop retrying. */
    val hasFix: Boolean get() = coordinates != null

    /**
     * Fetches current coordinates. Returns true if a fix was obtained. A failed
     * attempt keeps the last known fix rather than falling back to the static list.
     */
    suspend fun refresh(): Boolean {
        val fix = withTimeoutOrNull(FIX_TIMEOUT_MS) { locationManager.current() }
        if (fix != null) coordinates = fix
        return fix != null
    }

    override fun schedule(dayOffset: Int): List<PrayerTime> {
        val coords = coordinates ?: return fallback.schedule(dayOffset)
        return calculator.timesFor(
            coords = coords,
            date = deviceDate(dayOffset),
            utcOffsetHours = deviceUtcOffsetHours(),
        )
    }

    private companion object {
        /** Give the OS dialog + GPS warm-up room, but never hang the caller forever. */
        const val FIX_TIMEOUT_MS = 20_000L
    }
}

/**
 * Builds the absolute-minute timeline the calculator consumes. Stitches
 * yesterday → today → tomorrow so any "now" in [00:00, 24:00) lands inside a
 * segment, and the prayer *after Isha* is automatically tomorrow's Fajr — giving
 * the requested "switch to tomorrow after Isha" behaviour for free.
 */
class PrayerTimelineService(
    private val repository: PrayerScheduleRepository = StaticPrayerScheduleRepository(),
) {
    fun timeline(): List<PrayerInstant> {
        val yesterday = repository.schedule(-1).map { it.toInstant(-1440f) }
        val today = repository.schedule(0).map { it.toInstant(0f) }
        val tomorrow = repository.schedule(1).map { it.toInstant(1440f) }
        return (yesterday + today + tomorrow).sortedBy { it.absoluteMinute }
    }

    private fun PrayerTime.toInstant(dayShift: Float) =
        PrayerInstant(key = key, name = name, absoluteMinute = min + dayShift)
}
