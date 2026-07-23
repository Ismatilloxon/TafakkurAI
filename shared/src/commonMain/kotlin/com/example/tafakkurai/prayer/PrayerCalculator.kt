package com.example.tafakkurai.prayer

import com.example.tafakkurai.PrayerTime
import com.example.tafakkurai.location.Coordinates
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A calendar day in the device's local timezone. */
data class CalendarDate(val year: Int, val month: Int, val day: Int)

/**
 * A parameter that is either a twilight angle below the horizon, or a fixed
 * offset in minutes from the anchor prayer (Umm al-Qura's "90 min after Maghrib").
 */
sealed class Twilight {
    data class Angle(val degrees: Double) : Twilight()
    data class Minutes(val minutes: Double) : Twilight()
}

/** Shadow-length convention for Asr: shadow == object (most schools) or twice it (Hanafi). */
enum class AsrJuristic(val shadowFactor: Int) { STANDARD(1), HANAFI(2) }

/**
 * What to do at latitudes where the sun never dips to the Fajr/Isha angle, so the
 * plain formula yields no solution. See praytimes.org "Higher Latitudes".
 */
enum class HighLatitudeRule { NONE, NIGHT_MIDDLE, ANGLE_BASED, ONE_SEVENTH }

/** Islamic midnight: half of sunset→sunrise (Sunni) or sunset→Fajr (Jafari). */
enum class MidnightMode { STANDARD, JAFARI }

/**
 * Twilight angles, Asr school and per-prayer offsets that define a calculation
 * convention. Presets follow the parameter table on praytimes.org/calculation.
 */
data class CalculationMethod(
    val name: String = "Muslim World League",
    val fajr: Twilight = Twilight.Angle(18.0),
    val isha: Twilight = Twilight.Angle(17.0),
    /** Sunset for Sunni conventions; 4° below the horizon for Shia ones. */
    val maghrib: Twilight = Twilight.Minutes(0.0),
    /** Start of the fast — conventionally 10 minutes before Fajr. */
    val imsak: Twilight = Twilight.Minutes(10.0),
    /** Safety margin added after the sun crosses the meridian. */
    val dhuhrMinutes: Double = 0.0,
    val asr: AsrJuristic = AsrJuristic.STANDARD,
    val midnight: MidnightMode = MidnightMode.STANDARD,
) {
    companion object {
        val MWL = CalculationMethod("Muslim World League")

        val ISNA = CalculationMethod(
            name = "Islamic Society of North America",
            fajr = Twilight.Angle(15.0), isha = Twilight.Angle(15.0),
        )

        val EGYPT = CalculationMethod(
            name = "Egyptian General Authority of Survey",
            fajr = Twilight.Angle(19.5), isha = Twilight.Angle(17.5),
        )

        /** Isha is a fixed 90 minutes after Maghrib (120 during Ramadan — see [ramadan]). */
        val MAKKAH = CalculationMethod(
            name = "Umm al-Qura University, Makkah",
            fajr = Twilight.Angle(18.5), isha = Twilight.Minutes(90.0),
        )

        val KARACHI = CalculationMethod(
            name = "University of Islamic Sciences, Karachi",
            fajr = Twilight.Angle(18.0), isha = Twilight.Angle(18.0),
        )

        /**
         * Uzbekistan Muslim Board. Same twilight angles as [MWL] (18° / 17°), kept
         * as its own convention so the choice survives in settings and can diverge
         * later. Uzbekistan follows the Hanafi school, so pair it with
         * `AsrJuristic.HANAFI` to match locally printed calendars.
         */
        val UZBEKISTAN = CalculationMethod(
            name = "O'zbekiston musulmonlari idorasi",
            fajr = Twilight.Angle(18.0), isha = Twilight.Angle(17.0),
        )

        val TEHRAN = CalculationMethod(
            name = "Institute of Geophysics, University of Tehran",
            fajr = Twilight.Angle(17.7), isha = Twilight.Angle(14.0),
            maghrib = Twilight.Angle(4.5), midnight = MidnightMode.JAFARI,
        )

        val JAFARI = CalculationMethod(
            name = "Shia Ithna-Ashari, Leva Institute, Qum",
            fajr = Twilight.Angle(16.0), isha = Twilight.Angle(14.0),
            maghrib = Twilight.Angle(4.0), midnight = MidnightMode.JAFARI,
        )

        val FRANCE = CalculationMethod(
            name = "Union des Organisations Islamiques de France",
            fajr = Twilight.Angle(12.0), isha = Twilight.Angle(12.0),
        )

        val RUSSIA = CalculationMethod(
            name = "Spiritual Administration of Muslims of Russia",
            fajr = Twilight.Angle(16.0), isha = Twilight.Angle(15.0),
        )

        val SINGAPORE = CalculationMethod(
            name = "Majlis Ugama Islam Singapura",
            fajr = Twilight.Angle(20.0), isha = Twilight.Angle(18.0),
        )

        /** Lookup by stable key — matches the backend's `calc_method` string. */
        val ALL: Map<String, CalculationMethod> = mapOf(
            "MWL" to MWL, "Uzbekistan" to UZBEKISTAN, "ISNA" to ISNA,
            "Egypt" to EGYPT, "Makkah" to MAKKAH, "Karachi" to KARACHI,
            "Tehran" to TEHRAN, "Jafari" to JAFARI, "France" to FRANCE,
            "Russia" to RUSSIA, "Singapore" to SINGAPORE,
        )

        /**
         * Resolves the profile's `calc_method` + `madhab` (see PrayerSettingsDto)
         * into a method. Unknown or missing values fall back to [MWL] / Standard Asr.
         */
        fun of(calcMethod: String?, madhab: String? = null): CalculationMethod {
            val base = ALL.entries
                .firstOrNull { it.key.equals(calcMethod, ignoreCase = true) }?.value
                ?: MWL
            val asr = if (madhab.equals("hanafi", ignoreCase = true)) {
                AsrJuristic.HANAFI
            } else {
                base.asr
            }
            return base.copy(asr = asr)
        }
    }
}

/** Umm al-Qura pushes Isha to 120 minutes after Maghrib during Ramadan. */
fun CalculationMethod.ramadan(): CalculationMethod =
    if (this == CalculationMethod.MAKKAH) copy(isha = Twilight.Minutes(120.0)) else this

/**
 * Astronomical prayer-time calculator implementing the algorithm documented at
 * https://praytimes.org/calculation — USNO sun position, the twilight equation
 * T(α), the Asr shadow equation A(t), higher-latitude adjustment and Islamic
 * midnight. Pure math: coordinates + local date + UTC offset in, times out.
 *
 * [timesFor] returns the six times the UI renders, with `min` = minutes since
 * local midnight. [rawTimes] exposes everything (imsak, sunset, midnight) as
 * fractional hours for callers that need more.
 *
 * @param elevationMeters observer height above the horizon, widens the rise/set angle.
 * @param tuning per-key correction in minutes, e.g. mapOf("isha" to 2.0).
 */
class PrayerCalculator(
    private val method: CalculationMethod = CalculationMethod.MWL,
    private val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.NIGHT_MIDDLE,
    private val elevationMeters: Double = 0.0,
    private val tuning: Map<String, Double> = emptyMap(),
) {

    // ── Public API ───────────────────────────────────────────────────────────

    fun timesFor(
        coords: Coordinates,
        date: CalendarDate,
        utcOffsetHours: Double,
    ): List<PrayerTime> {
        val t = rawTimes(coords, date, utcOffsetHours)
        val dhuhr = t.getValue("dhuhr")

        // Polar day/night can leave sunrise/sunset unsolvable even after the
        // high-latitude rules, which only cover Fajr/Isha/Imsak/Maghrib. Fall back
        // to a nominal 6h from noon so the timeline stays ordered and finite.
        fun solved(key: String, fallback: Double) =
            t.getValue(key).let { if (it.isNaN()) fallback else it }

        return listOf(
            entry("fajr", "Fajr", "Fajr", solved("fajr", dhuhr - 7.0), "sunrise"),
            entry("sunrise", "Sunrise", "Sunr", solved("sunrise", dhuhr - 6.0), "sun"),
            entry("dhuhr", "Dhuhr", "Dhuh", dhuhr, "sun"),
            entry("asr", "Asr", "Asr", solved("asr", dhuhr + 3.0), "sun"),
            entry("maghrib", "Maghrib", "Magh", solved("maghrib", dhuhr + 6.0), "sunset"),
            entry("isha", "Isha", "Isha", solved("isha", dhuhr + 7.0), "moon"),
        )
    }

    /**
     * Every computed instant as hours of the local day: imsak, fajr, sunrise,
     * dhuhr, asr, sunset, maghrib, isha, midnight. NaN means "no solution at this
     * latitude" for anything the higher-latitude rule does not cover.
     */
    fun rawTimes(
        coords: Coordinates,
        date: CalendarDate,
        utcOffsetHours: Double,
    ): Map<String, Double> {
        // Longitude shift puts the Julian date at local, not Greenwich, midnight.
        val jDate = julianDay(date.year, date.month, date.day) - coords.longitude / (15.0 * 24.0)

        var times = DEFAULT_HOURS
        repeat(ITERATIONS) { times = computePrayerTimes(times, jDate, coords) }
        return adjustTimes(times, coords, utcOffsetHours)
    }

    // ── Stage 1: solve each time from a first guess of its day portion ────────

    /**
     * The sun's declination changes through the day, so each time is solved at its
     * own moment. [previous] holds the last estimate (hours of day) per key; the
     * loop in [rawTimes] feeds the result back in to refine it.
     */
    private fun computePrayerTimes(
        previous: Map<String, Double>,
        jDate: Double,
        coords: Coordinates,
    ): Map<String, Double> {
        val portion = previous.mapValues { it.value / 24.0 }
        fun at(key: String) = jDate + portion.getValue(key)

        val riseSet = riseSetAngle()
        val sunrise = sunAngleTime(riseSet, at("sunrise"), coords, ccw = true)
        val sunset = sunAngleTime(riseSet, at("sunset"), coords, ccw = false)

        // Minute-based parameters are resolved in adjustTimes; carry the previous
        // estimate so the next iteration still has a finite day portion to use.
        fun angular(key: String, param: Twilight, ccw: Boolean): Double = when (param) {
            is Twilight.Angle -> sunAngleTime(param.degrees, at(key), coords, ccw)
            is Twilight.Minutes -> previous.getValue(key)
        }

        return mapOf(
            "imsak" to angular("imsak", method.imsak, ccw = true),
            "fajr" to angular("fajr", method.fajr, ccw = true),
            "sunrise" to sunrise,
            "dhuhr" to midDay(at("dhuhr")),
            "asr" to asrTime(at("asr"), coords),
            "sunset" to sunset,
            "maghrib" to angular("maghrib", method.maghrib, ccw = false),
            "isha" to angular("isha", method.isha, ccw = false),
        )
    }

    // ── Stage 2: shift to local clock time, then apply the conventions ───────

    private fun adjustTimes(
        times: Map<String, Double>,
        coords: Coordinates,
        utcOffsetHours: Double,
    ): Map<String, Double> {
        // Times so far are solar hours at the observer's meridian.
        val shift = utcOffsetHours - coords.longitude / 15.0
        val out = times.mapValues { it.value + shift }.toMutableMap()

        if (highLatitudeRule != HighLatitudeRule.NONE) adjustHighLatitudes(out)

        (method.imsak as? Twilight.Minutes)?.let {
            out["imsak"] = out.getValue("fajr") - it.minutes / 60.0
        }
        (method.maghrib as? Twilight.Minutes)?.let {
            out["maghrib"] = out.getValue("sunset") + it.minutes / 60.0
        }
        // Order matters: a minute-based Isha counts from the adjusted Maghrib.
        (method.isha as? Twilight.Minutes)?.let {
            out["isha"] = out.getValue("maghrib") + it.minutes / 60.0
        }
        out["dhuhr"] = out.getValue("dhuhr") + method.dhuhrMinutes / 60.0
        out["midnight"] = midnight(out)

        tuning.forEach { (key, minutes) ->
            out[key]?.let { out[key] = it + minutes / 60.0 }
        }
        return out
    }

    /**
     * Where the twilight never reaches the required angle, place Fajr/Isha a
     * fraction of the night away from sunrise/sunset instead of leaving them unsolved.
     */
    private fun adjustHighLatitudes(times: MutableMap<String, Double>) {
        val night = timeDiff(times.getValue("sunset"), times.getValue("sunrise"))

        fun fix(key: String, base: Double, param: Twilight, ccw: Boolean) {
            val portion = nightPortion(param, night)
            val current = times.getValue(key)
            val diff = if (ccw) timeDiff(current, base) else timeDiff(base, current)
            if (current.isNaN() || diff > portion) {
                times[key] = base + if (ccw) -portion else portion
            }
        }

        val sunrise = times.getValue("sunrise")
        val sunset = times.getValue("sunset")
        fix("imsak", sunrise, method.imsak, ccw = true)
        fix("fajr", sunrise, method.fajr, ccw = true)
        fix("isha", sunset, method.isha, ccw = false)
        fix("maghrib", sunset, method.maghrib, ccw = false)
    }

    /** Fraction of the night that separates the twilight from sunrise/sunset. */
    private fun nightPortion(param: Twilight, night: Double): Double {
        val fraction = when (highLatitudeRule) {
            HighLatitudeRule.ONE_SEVENTH -> 1.0 / 7.0
            // Angle-based needs an angle; minute-based parameters fall back to
            // the middle-of-night rule rather than treating minutes as degrees.
            HighLatitudeRule.ANGLE_BASED ->
                (param as? Twilight.Angle)?.let { it.degrees / 60.0 } ?: 0.5
            else -> 0.5
        }
        return fraction * night
    }

    private fun midnight(times: Map<String, Double>): Double {
        val sunset = times.getValue("sunset")
        val end = if (method.midnight == MidnightMode.JAFARI) {
            times.getValue("fajr")
        } else {
            times.getValue("sunrise")
        }
        return sunset + timeDiff(sunset, end) / 2.0
    }

    // ── Astronomy (praytimes.org formulas) ───────────────────────────────────

    /**
     * T(α): hours between midday and the moment the sun sits α° below the horizon.
     *
     * `T(α) = 1/15 · arccos( (−sin(α) − sin(D)·sin(φ)) / (cos(D)·cos(φ)) )`
     *
     * Returns NaN when the sun never reaches α at this latitude — deliberately,
     * so [adjustHighLatitudes] can step in.
     */
    private fun sunAngleTime(
        angle: Double,
        jd: Double,
        coords: Coordinates,
        ccw: Boolean,
    ): Double {
        val decl = sunDeclination(jd)
        val lat = coords.latitude
        val t = 1.0 / 15.0 * acosDeg(
            (-sinDeg(angle) - sinDeg(decl) * sinDeg(lat)) / (cosDeg(decl) * cosDeg(lat)),
        )
        return midDay(jd) + if (ccw) -t else t
    }

    /**
     * A(t): hours from midday to when an object's shadow grows by t times its own
     * length beyond the shadow at noon.
     *
     * `A(t) = 1/15 · arccos( (sin(arccot(t + tan|φ − D|)) − sin(D)·sin(φ)) / (cos(D)·cos(φ)) )`
     *
     * which is T(−arccot(t + tan|φ − D|)) — note **arccot**, not arctan.
     */
    private fun asrTime(jd: Double, coords: Coordinates): Double {
        val decl = sunDeclination(jd)
        val angle = -arccotDeg(
            method.asr.shadowFactor + tanDeg(abs(coords.latitude - decl)),
        )
        return sunAngleTime(angle, jd, coords, ccw = false)
    }

    /** Solar noon: `12 − EqT`, before the timezone/longitude shift. */
    private fun midDay(jd: Double): Double = fixHour(12.0 - equationOfTime(jd))

    /** 0.833° covers refraction and the solar disc; height above the horizon widens it. */
    private fun riseSetAngle(): Double =
        0.833 + 0.0347 * sqrt(elevationMeters.coerceAtLeast(0.0))

    private fun sunDeclination(jd: Double): Double {
        val l = sunTrueLongitude(jd)
        val e = obliquity(jd)
        return asinDeg(sinDeg(e) * sinDeg(l))
    }

    private fun equationOfTime(jd: Double): Double {
        val d = jd - J2000
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = sunTrueLongitude(jd)
        val e = obliquity(jd)
        val ra = atan2Deg(cosDeg(e) * sinDeg(l), cosDeg(l)) / 15.0
        return q / 15.0 - fixHour(ra)
    }

    private fun sunTrueLongitude(jd: Double): Double {
        val d = jd - J2000
        val g = fixAngle(357.529 + 0.98560028 * d)   // mean anomaly
        val q = fixAngle(280.459 + 0.98564736 * d)   // mean longitude
        return fixAngle(q + 1.915 * sinDeg(g) + 0.020 * sinDeg(2 * g))
    }

    private fun obliquity(jd: Double): Double = 23.439 - 0.00000036 * (jd - J2000)

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    // ── Formatting ───────────────────────────────────────────────────────────

    private fun entry(key: String, name: String, short: String, hours: Double, icon: String): PrayerTime {
        val min = (fixHour(hours) * 60.0).roundToInt() % 1440
        val label = (min / 60).pad() + ":" + (min % 60).pad()
        return PrayerTime(key, name, short, label, min, icon)
    }

    private fun Int.pad() = if (this < 10) "0$this" else "$this"

    // ── Degree-based trig helpers ────────────────────────────────────────────

    private fun sinDeg(d: Double) = sin(d * PI / 180.0)
    private fun cosDeg(d: Double) = cos(d * PI / 180.0)
    private fun tanDeg(d: Double) = tan(d * PI / 180.0)
    private fun asinDeg(x: Double) = asin(x) * 180.0 / PI

    /** NaN for |x| > 1 — that is how "the sun never gets there" surfaces. */
    private fun acosDeg(x: Double) = acos(x) * 180.0 / PI
    private fun arccotDeg(x: Double) = atan(1.0 / x) * 180.0 / PI
    private fun atan2Deg(y: Double, x: Double) = atan2(y, x) * 180.0 / PI

    private fun fixAngle(a: Double) = ((a % 360.0) + 360.0) % 360.0
    private fun fixHour(a: Double) = ((a % 24.0) + 24.0) % 24.0

    /** Hours from [from] to [to], wrapped into a single day. */
    private fun timeDiff(from: Double, to: Double) = fixHour(to - from)

    private companion object {
        const val J2000 = 2451545.0

        /** One refinement pass, as in the reference implementation. */
        const val ITERATIONS = 1

        /** First guesses (hours of day) that seed the refinement. */
        val DEFAULT_HOURS = mapOf(
            "imsak" to 5.0, "fajr" to 5.0, "sunrise" to 6.0, "dhuhr" to 12.0,
            "asr" to 13.0, "sunset" to 18.0, "maghrib" to 18.0, "isha" to 18.0,
        )
    }
}
