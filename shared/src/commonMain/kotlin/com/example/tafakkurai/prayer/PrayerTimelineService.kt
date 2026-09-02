package com.example.tafakkurai.prayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.PrayerTime
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
 * Honest fallback used while location is unavailable. An empty schedule is rendered as an
 * explicit permission/unavailable state; prayer times for an unrelated city are never invented.
 */
class UnavailablePrayerScheduleRepository : PrayerScheduleRepository {
    override fun schedule(dayOffset: Int): List<PrayerTime> = emptyList()
}

/**
 * Computes the schedule from the device location using [IslomUzPrayerCalculator]. Call
 * [refresh] (suspending) to fetch coordinates before rendering; [schedule] stays
 * synchronous so the timeline/ViewModel are untouched. Until a fix is available, callers
 * receive an empty schedule and show an explicit unavailable state.
 */
class LocationPrayerScheduleRepository(
    private val locationManager: PrayerLocationManager,
    private val fallback: PrayerScheduleRepository = UnavailablePrayerScheduleRepository(),
) : PrayerScheduleRepository {

    /**
     * Held as Compose state so every screen reading [schedule] recomposes the moment
     * a fix lands — the permission dialog is answered long after first composition.
     */
    private var coordinates by mutableStateOf<Coordinates?>(null)

    /**
     * Active calc convention, also Compose state so a settings change recomputes every
     * screen live. Default = the islom.uz convention (15°/15° twilight, Hanafi Asr,
     * ±5 min Fajr/Maghrib), so times match the islom.uz app out of the box.
     * [setConvention] swaps it when the user picks another method/madhab.
     */
    private var calculator by mutableStateOf(IslomUzPrayerCalculator())

    /**
     * Rebuilds the calculator from a user-chosen method + madhab. Unknown values fall
     * back to the islom.uz convention (see [IslomUzParams.forConvention]). Coordinates
     * still drive the actual times, so this works wherever the user is.
     */
    fun setConvention(calcMethod: String?, madhab: String?) {
        calculator = IslomUzPrayerCalculator(IslomUzParams.forConvention(calcMethod, madhab))
    }

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
    private val repository: PrayerScheduleRepository = UnavailablePrayerScheduleRepository(),
) {
    fun timeline(): List<PrayerInstant> {
        // Sunrise is a landmark, not a salah, so it must not split the Fajr→Dhuhr gap
        // into two rotations — the ring turns once per *prayer* interval. The card and
        // the day-list still show sunrise; only the progress segmentation skips it.
        fun day(offset: Int, shift: Float) =
            repository.schedule(offset)
                .filter { it.key != "sunrise" }
                .map { it.toInstant(shift) }
        return (day(-1, -1440f) + day(0, 0f) + day(1, 1440f)).sortedBy { it.absoluteMinute }
    }

    private fun PrayerTime.toInstant(dayShift: Float) =
        PrayerInstant(key = key, name = name, absoluteMinute = min + dayShift)
}
