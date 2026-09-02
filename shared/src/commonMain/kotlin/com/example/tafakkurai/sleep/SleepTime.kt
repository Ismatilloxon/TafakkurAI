package com.example.tafakkurai.sleep

import com.example.tafakkurai.prayer.deviceUtcOffsetHours
import com.example.tafakkurai.spiritual.daysFromCivil
import com.example.tafakkurai.spiritual.parseDateKey
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Clock arithmetic for sleep.
 *
 * The project deliberately does not depend on kotlinx-datetime (see `spiritual/DateKeys.kt`),
 * so this file does the same thing the prayer engine does: pure integer and trigonometric
 * maths over epoch milliseconds and a UTC offset, with no timezone database and no platform
 * calls. That keeps every function here directly unit-testable.
 *
 * ### Why bedtimes are signed minutes
 *
 * Bedtimes straddle midnight, and the naive representation (0..1439 minutes from midnight)
 * makes 23:50 and 00:10 look 23 hours 40 minutes apart when they are 20 minutes apart. Every
 * "average bedtime" and every consistency score built on that representation is wrong, and
 * wrong in the direction that punishes the most consistent users hardest.
 *
 * This file uses a **night-centred signed** representation instead: minutes from midnight,
 * negative before it. 23:50 is −10, 00:10 is +10, and plain subtraction is correct.
 *
 * Averaging still cannot use plain arithmetic — the mean of −10 and +10 happens to work, but
 * the mean of a spread crossing noon does not — so [circularMeanMinutes] does the real thing:
 * treats each time as a unit vector on a 24-hour circle and averages the vectors.
 */
object SleepTime {

    const val MINUTES_PER_DAY = 1440
    const val MS_PER_MINUTE = 60_000L
    const val MS_PER_DAY = 86_400_000L

    /** Boundary between "this is a wake time" and "this is a bedtime": noon. */
    private const val NOON = 720

    // ────────────────────────────────────────────────── epoch → wall clock

    /**
     * Minutes from local midnight, 0..1439, for an instant.
     *
     * @param offsetHours UTC offset to interpret [epochMs] in. Defaults to the device's
     *  current offset. Pass the session's own offset when rendering historical nights from
     *  another timezone, or a trip across zones redraws old nights in the wrong place.
     */
    fun minuteOfDay(epochMs: Long, offsetHours: Double = deviceUtcOffsetHours()): Int {
        val shifted = epochMs + (offsetHours * 3_600_000.0).toLong()
        val within = ((shifted % MS_PER_DAY) + MS_PER_DAY) % MS_PER_DAY
        return (within / MS_PER_MINUTE).toInt()
    }

    /**
     * Night-centred signed minutes for an instant: 23:50 → −10, 07:00 → +420.
     *
     * Everything after noon is expressed as negative minutes before the following midnight,
     * which is what makes bedtime comparison and averaging plain subtraction.
     */
    fun signedMinuteOfDay(epochMs: Long, offsetHours: Double = deviceUtcOffsetHours()): Int =
        toSigned(minuteOfDay(epochMs, offsetHours))

    /** 0..1439 → night-centred signed. */
    fun toSigned(minuteOfDay: Int): Int {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (m >= NOON) m - MINUTES_PER_DAY else m
    }

    /** Night-centred signed → 0..1439. */
    fun toMinuteOfDay(signed: Int): Int = ((signed % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY

    /** Local "yyyy-MM-dd" for an instant. */
    fun dateKeyOf(epochMs: Long, offsetHours: Double = deviceUtcOffsetHours()): String {
        val shifted = epochMs + (offsetHours * 3_600_000.0).toLong()
        val day = floorDiv(shifted, MS_PER_DAY)
        return dateKeyOfDayNumber(day)
    }

    /** Days-since-epoch → "yyyy-MM-dd", inverting `daysFromCivil`. */
    fun dateKeyOfDayNumber(dayNumber: Long): String {
        // Howard Hinnant's civil_from_days, the exact inverse of daysFromCivil.
        val z = dayNumber + 719468L
        val era = (if (z >= 0) z else z - 146096L) / 146097L
        val doe = z - era * 146097L                                   // [0, 146096]
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)             // [0, 365]
        val mp = (5 * doy + 2) / 153                                  // [0, 11], Mar = 0
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()                // [1, 31]
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()             // [1, 12]
        val year = (if (m <= 2) y + 1 else y).toInt()
        return buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(m.toString().padStart(2, '0'))
            append('-')
            append(d.toString().padStart(2, '0'))
        }
    }

    /**
     * Epoch millis for local midnight starting [dateKey], or null when it does not parse.
     *
     * Used to place a session on a timeline that is anchored to a calendar day rather than to
     * the session's own start.
     */
    fun startOfDayMs(dateKey: String, offsetHours: Double = deviceUtcOffsetHours()): Long? {
        val (y, m, d) = parseDateKey(dateKey) ?: return null
        val days = daysFromCivil(y, m, d)
        return days * MS_PER_DAY - (offsetHours * 3_600_000.0).toLong()
    }

    private fun floorDiv(a: Long, b: Long): Long {
        val q = a / b
        return if (a % b != 0L && (a xor b) < 0) q - 1 else q
    }

    // ────────────────────────────────────────────────── circular statistics

    /**
     * The circular mean of a set of clock times, returned night-centred and signed.
     *
     * Each time becomes a unit vector at its angle on the 24-hour circle; the vectors are
     * summed and the resultant's angle is the mean. This is the only averaging that survives
     * midnight: the arithmetic mean of 23:50 and 00:10 is 12:00, which is not merely
     * imprecise but the exact opposite of the right answer.
     *
     * Returns null for an empty input, and for the degenerate case where the vectors cancel
     * out (times spread evenly around the clock), where no mean exists and reporting one
     * would be fiction.
     */
    fun circularMeanMinutes(minutes: List<Int>): Int? {
        if (minutes.isEmpty()) return null
        var sumSin = 0.0
        var sumCos = 0.0
        for (m in minutes) {
            val angle = m.toDouble() / MINUTES_PER_DAY * TWO_PI
            sumSin += sin(angle)
            sumCos += cos(angle)
        }
        val n = minutes.size
        val resultant = sqrt(sumSin * sumSin + sumCos * sumCos) / n
        if (resultant < DEGENERATE_RESULTANT) return null

        var angle = atan2(sumSin / n, sumCos / n)
        if (angle < 0) angle += TWO_PI
        val meanMinuteOfDay = (angle / TWO_PI * MINUTES_PER_DAY).roundToInt() % MINUTES_PER_DAY
        return toSigned(meanMinuteOfDay)
    }

    /**
     * Circular standard deviation in minutes — the spread of a set of clock times.
     *
     * Derived from the resultant length R as `sqrt(-2 ln R)` radians, the standard circular
     * dispersion measure. R near 1 means tightly clustered times and a small deviation; R near
     * 0 means times scattered around the clock, and the deviation is capped rather than
     * allowed to run to infinity.
     *
     * This is the number behind the consistency score, and it is why a user who sleeps 23:55
     * one night and 00:05 the next scores as near-perfectly consistent instead of chaotic.
     */
    fun circularDeviationMinutes(minutes: List<Int>): Double {
        if (minutes.size < 2) return 0.0
        var sumSin = 0.0
        var sumCos = 0.0
        for (m in minutes) {
            val angle = m.toDouble() / MINUTES_PER_DAY * TWO_PI
            sumSin += sin(angle)
            sumCos += cos(angle)
        }
        val n = minutes.size
        val resultant = (sqrt(sumSin * sumSin + sumCos * sumCos) / n).coerceIn(1e-6, 1.0)
        val radians = sqrt(-2.0 * ln(resultant))
        val asMinutes = radians / TWO_PI * MINUTES_PER_DAY
        // Half a day is the maximum meaningful spread; beyond it the times are uniform.
        return asMinutes.coerceAtMost(MINUTES_PER_DAY / 2.0)
    }

    /**
     * Shortest signed distance from [from] to [to] on the 24-hour circle, in minutes.
     *
     * Positive means [to] is later. Always within ±720, so "an hour late" never reads as
     * "23 hours early".
     */
    fun circularDeltaMinutes(from: Int, to: Int): Int {
        var delta = (to - from) % MINUTES_PER_DAY
        if (delta > MINUTES_PER_DAY / 2) delta -= MINUTES_PER_DAY
        if (delta < -MINUTES_PER_DAY / 2) delta += MINUTES_PER_DAY
        return delta
    }

    // ────────────────────────────────────────────────── formatting

    /** `444` → `"7h 24m"`. Drops the hours part below an hour, the minutes part when exact. */
    fun formatDuration(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }

    /** `444` → `"7:24"`. The compact form for rings and widgets. */
    fun formatDurationCompact(totalMinutes: Int): String {
        val safe = totalMinutes.coerceAtLeast(0)
        return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
    }

    /**
     * Night-centred signed minutes → a wall-clock label.
     *
     * @param use24Hour when false, renders `11:30 PM`. Sleep UIs read better in the user's own
     *  convention, and the 12-hour form is what most of the world's phone clocks show.
     */
    fun formatClock(signedMinutes: Int, use24Hour: Boolean = true): String {
        val m = toMinuteOfDay(signedMinutes)
        val hour = m / 60
        val minute = m % 60
        if (use24Hour) {
            return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        }
        val suffix = if (hour < 12) "AM" else "PM"
        val h12 = when {
            hour % 12 == 0 -> 12
            else -> hour % 12
        }
        return "$h12:${minute.toString().padStart(2, '0')} $suffix"
    }

    /** Wall-clock label for an instant. */
    fun formatClockAt(
        epochMs: Long,
        use24Hour: Boolean = true,
        offsetHours: Double = deviceUtcOffsetHours(),
    ): String = formatClock(signedMinuteOfDay(epochMs, offsetHours), use24Hour)

    /** `-42` → `"42m earlier"`, `+42` → `"42m later"`, `0` → `"on time"`. */
    fun formatDelta(deltaMinutes: Int): String = when {
        abs(deltaMinutes) < 5 -> "on time"
        deltaMinutes < 0 -> "${formatDuration(abs(deltaMinutes))} earlier"
        else -> "${formatDuration(deltaMinutes)} later"
    }

    private const val TWO_PI = 2.0 * kotlin.math.PI

    /**
     * Below this resultant length the times are spread so evenly that no mean direction
     * exists. Reporting one would be an artefact of floating-point noise.
     */
    private const val DEGENERATE_RESULTANT = 1e-6
}
