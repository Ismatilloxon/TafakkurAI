package com.example.tafakkurai.prayer

import com.example.tafakkurai.PrayerTime
import com.example.tafakkurai.location.Coordinates
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Convention knobs for [IslomUzPrayerCalculator]. Defaults reproduce exactly what
 * the islom.uz iOS app ships (see [IslomUzPrayerCalculator] doc).
 *
 * @param fajrAngle twilight depression for Fajr (islom.uz: 15°, not MWL's 18°).
 * @param ishaAngle twilight depression for Isha (islom.uz: 15°).
 * @param asrShadowRatio Asr shadow factor — 2 = Hanafi, 1 = Shafi'i/other.
 * @param fajrAdjustMin fixed nudge added to Fajr; islom.uz subtracts 5 min.
 * @param maghribAdjustMin fixed nudge added to Maghrib; islom.uz adds 5 min.
 * @param tuningMin optional per-key manual correction in minutes (islom.uz "ExtendN").
 */
data class IslomUzParams(
    val fajrAngle: Double = 15.0,
    val ishaAngle: Double = 15.0,
    val asrShadowRatio: Double = 2.0,
    val fajrAdjustMin: Double = -5.0,
    val maghribAdjustMin: Double = 5.0,
    val tuningMin: Map<String, Double> = emptyMap(),
) {
    companion object {
        /**
         * Maps a stored profile's `calc_method` + `madhab` onto islom.uz-style params.
         * Unknown/missing values fall back to the islom.uz convention itself (15°/15°,
         * Hanafi). Only the Asr factor is madhab-driven; the twilight angles come from
         * the chosen method so a user abroad can still pick, say, ISNA or Egypt.
         */
        fun forConvention(calcMethod: String?, madhab: String?): IslomUzParams {
            val asr = if (madhab.equals("hanafi", ignoreCase = true)) 2.0 else 1.0
            val (fajr, isha) = when (calcMethod?.lowercase()) {
                "mwl" -> 18.0 to 17.0
                "egypt" -> 19.5 to 17.5
                "karachi" -> 18.0 to 18.0
                "isna" -> 15.0 to 15.0
                else -> 15.0 to 15.0 // Uzbekistan / islom.uz
            }
            return IslomUzParams(fajrAngle = fajr, ishaAngle = isha, asrShadowRatio = asr)
        }
    }
}

/**
 * Faithful Kotlin port of islom.uz's iOS `PrayerTime.calculateSalahTimes`. Kept as a
 * separate calculator from [PrayerCalculator] so both conventions can coexist.
 *
 * Reproduces exactly what islom.uz ships:
 *   - Fajr / Isha at **15°** twilight (not MWL's 18°).
 *   - **Hanafi** Asr (shadow factor 2).
 *   - Sunrise / sunset at the **0.833°** horizon dip.
 *   - A fixed **−5 min** nudge on Fajr and **+5 min** on Maghrib (`forceFajrMaghrebOffset`).
 *   - Per-prayer manual "Extend" offsets ([IslomUzParams.tuningMin]), default 0.
 *
 * Longitude/timezone handling matches the source verbatim: the Julian day is the plain
 * civil date and longitude enters through `midday`, with no separate day-fraction shift.
 * Verified against the app's own table for Tashkent (Asr 17:37, Sunrise 05:09, …).
 */
class IslomUzPrayerCalculator(private val params: IslomUzParams = IslomUzParams()) {

    /** The six rendered times, `min` = minutes since local midnight — same shape as
     *  [PrayerCalculator.timesFor], so it is a drop-in for the schedule repository. */
    fun timesFor(coords: Coordinates, date: CalendarDate, utcOffsetHours: Double): List<PrayerTime> {
        val jd = gregorian2Julian(date.year, date.month, date.day)
        val t = coreTimes(jd, utcOffsetHours, coords)

        fun tune(key: String) = (params.tuningMin[key] ?: 0.0) / 60.0

        return listOf(
            entry("fajr", "Fajr", "Fajr", t.fajr + params.fajrAdjustMin / 60.0 + tune("fajr"), "sunrise"),
            entry("sunrise", "Sunrise", "Sunr", t.sunrise + tune("sunrise"), "sun"),
            entry("dhuhr", "Dhuhr", "Dhuh", t.midday + tune("dhuhr"), "sun"),
            entry("asr", "Asr", "Asr", t.asr + tune("asr"), "sun"),
            entry("maghrib", "Maghrib", "Magh", t.sunset + params.maghribAdjustMin / 60.0 + tune("maghrib"), "sunset"),
            entry("isha", "Isha", "Isha", t.isha + tune("isha"), "moon"),
        )
    }

    private data class Core(
        val fajr: Double, val sunrise: Double, val midday: Double,
        val asr: Double, val sunset: Double, val isha: Double,
    )

    /**
     * Port of islom.uz's inner `calculateSalahTimes(jd, timeZone, lat, long, …)`, one
     * pass, no refinement iteration — identical to the source. Angles in degrees;
     * trig wrapped in [rad]/[deg] to mirror the Swift `toRadians`/`toDegrees` wrappers.
     */
    private fun coreTimes(julianDate: Double, timeZone: Double, coords: Coordinates): Core {
        val lat = coords.latitude
        val long = coords.longitude

        val j2000 = julianDate - J2000
        val g = fixAngle(357.529 + 0.98560028 * j2000)
        val q = fixAngle(280.459 + 0.98564736 * j2000)
        val l = fixAngle(q + 1.915 * sin(rad(g)) + 0.020 * sin(rad(2 * g)))
        val e = fixHour(23.439 - 0.00000036 * j2000)
        val ra = fixHour(deg(atan2(cos(rad(e)) * sin(rad(l)), cos(rad(l)))) / 15.0)
        val d = deg(asin(sin(rad(e)) * sin(rad(l)))) // sun declination
        val eqt = q / 15.0 - ra

        val midday = fixHour(12 + timeZone - long / 15.0 - eqt)
        val fajr = fixHour(midday - hourAngle(params.fajrAngle, lat, d))
        val sunrise = fixHour(midday - hourAngle(0.833, lat, d))
        val sunset = fixHour(midday + hourAngle(0.833, lat, d))
        val isha = fixHour(midday + hourAngle(params.ishaAngle, lat, d))
        // Asr: sun altitude when an object's shadow reaches `ratio` × its length.
        val asrAlt = deg(atan(1.0 / (params.asrShadowRatio + tan(rad(lat - d)))))
        val asr = fixHour(
            midday + deg(
                acos((sin(rad(asrAlt)) - sin(rad(lat)) * sin(rad(d))) / (cos(rad(lat)) * cos(rad(d)))),
            ) / 15.0,
        )
        return Core(fajr, sunrise, midday, asr, sunset, isha)
    }

    /** Hours from midday to a sun depression of [angle]° below the horizon at [lat]/[decl]. */
    private fun hourAngle(angle: Double, lat: Double, decl: Double): Double =
        deg(
            acos((-sin(rad(angle)) - sin(rad(lat)) * sin(rad(decl))) / (cos(rad(lat)) * cos(rad(decl)))),
        ) / 15.0

    private fun entry(key: String, name: String, short: String, hours: Double, icon: String): PrayerTime {
        val min = (fixHour(hours) * 60.0).roundToInt() % 1440
        val label = pad(min / 60) + ":" + pad(min % 60)
        return PrayerTime(key, name, short, label, min, icon)
    }

    private fun gregorian2Julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        val c = floor(365.25 * (y + 4716))
        val dd = floor(30.6001 * (m + 1))
        return day + b + c + dd - 1524.5
    }

    private fun rad(x: Double) = x * PI / 180.0
    private fun deg(x: Double) = x * 180.0 / PI
    private fun fixAngle(a: Double) = ((a % 360.0) + 360.0) % 360.0
    private fun fixHour(a: Double) = ((a % 24.0) + 24.0) % 24.0
    private fun pad(n: Int) = if (n < 10) "0$n" else "$n"

    private companion object {
        const val J2000 = 2451545.0
    }
}
