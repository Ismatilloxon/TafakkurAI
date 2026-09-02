package com.example.tafakkurai.habits

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Every number the Habits module shows, derived from rows.
 *
 * This file is a deliberate mirror of `backend/internal/habits/stats.go`. The two must
 * agree: the phone renders these locally so the screen never waits on a request, and the
 * backend computes the same values for the coach, the Telegram bot and any future web
 * client. A streak that reads 41 on the phone and 39 on the server is worse than either
 * number being wrong on its own — the user stops trusting both.
 *
 * Nothing here touches the database or Compose. It is pure functions over data classes,
 * which is what makes it testable without a driver and identical to the Go side.
 */

/** Windows. Named because they appear in copy the user reads ("90% for a month"). */
const val HABIT_WINDOW_MONTH = 30
const val HABIT_WINDOW_YEAR = 371 // 53 whole weeks, so the heatmap has round columns
const val HABIT_DETAIL_WEEKS = 17

/** Bounds every backward walk. Longer than any streak, short enough to stay cheap. */
private const val MAX_HISTORY_DAYS = 1095

/** Everything derived about a single habit. Never stored — recomputed on every read. */
data class HabitStats(
    val habitId: String = "",
    val current: Int = 0,
    val longest: Int = 0,
    /** 0..1 over scheduled, un-skipped days. */
    val rate: Float = 0f,
    val scheduled: Int = 0,
    val done: Int = 0,
    val skipped: Int = 0,
    val missed: Int = 0,
    val lastDone: String = "",
    val todayStatus: HabitStatus? = null,
    val todayValue: Int = 0,
) {
    val isDoneToday: Boolean get() = todayStatus == HabitStatus.DONE
    val isSkippedToday: Boolean get() = todayStatus == HabitStatus.SKIPPED
}

/** One calendar day summarised across every habit due that day. */
data class DayRoll(
    val date: String,
    val scheduled: Int = 0,
    val done: Int = 0,
    val skipped: Int = 0,
    /** 0..4 heatmap intensity: 0 nothing, 1 only skips, 2..4 by how much was completed. */
    val level: Int = 0,
    val perfect: Boolean = false,
)

data class LabelledRate(
    val key: String,
    val label: String,
    val value: Float,
    val icon: String = "",
    val color: String = "",
)

/** The four Statistics time-of-day rows, with the icon each draws. */
val HABIT_TIME_BUCKETS = listOf(
    "morning" to "sunrise",
    "midday" to "sun",
    "evening" to "sunset",
    "night" to "moon",
)

object HabitMath {

    // ── per habit ────────────────────────────────────────────────────────────

    /**
     * Streaks and completion rate for one habit.
     *
     * Three rules decide everything, and each exists because the naive version is wrong:
     *
     *  - A day the habit is not scheduled on is invisible. A weekday-only habit must not
     *    lose its streak every Saturday.
     *  - A skipped day is neutral: it neither extends nor breaks the run, and it is
     *    excluded from the rate denominator. That is the promise the Skip sheet makes.
     *  - Today is not a miss until it is over. Without this, every habit reads as broken
     *    each morning and the user's streak visibly drops at midnight.
     */
    fun habitStats(habit: Habit, entries: Map<String, HabitEntry>, todayKey: String): HabitStats {
        val todayNum = dayNumberOfKey(todayKey) ?: return HabitStats(habit.id)

        var start = todayNum
        if (habit.createdAt > 0) start = dayNumberOfMillis(habit.createdAt)
        var lastActivity = Long.MIN_VALUE
        for (key in entries.keys) {
            val n = dayNumberOfKey(key) ?: continue
            if (n < start) start = n
            if (n > lastActivity) lastActivity = n
        }
        if (start > todayNum) start = todayNum
        if (todayNum - start > MAX_HISTORY_DAYS) start = todayNum - MAX_HISTORY_DAYS

        // A paused habit freezes: days after its last activity are neither misses nor
        // opportunities. Without this a two-week pause wipes a 40-day streak, which is the
        // exact opposite of what the Pause sheet promises.
        val horizon = if (habit.state == HabitLifecycle.PAUSED) {
            if (lastActivity == Long.MIN_VALUE) start - 1 else lastActivity
        } else {
            todayNum
        }

        var scheduled = 0
        var done = 0
        var skipped = 0
        var missed = 0
        var longest = 0
        var lastDone = ""
        var run = 0

        var n = start
        while (n <= horizon) {
            val key = dateKeyOf(n)
            if (!habit.scheduledOn(key)) { n++; continue }
            when (entries[key]?.status) {
                HabitStatus.DONE -> {
                    scheduled++; done++
                    run++
                    if (run > longest) longest = run
                    if (key > lastDone) lastDone = key
                }
                HabitStatus.SKIPPED -> skipped++ // neutral: run survives, denominator untouched
                else -> {
                    // Today is pending, not missed — and stays out of the denominator.
                    if (n != todayNum) {
                        scheduled++; missed++
                        run = 0
                    }
                }
            }
            n++
        }

        // Backward walk for the CURRENT run. Separate from the forward pass because it
        // starts somewhere else: a still-pending today is stepped over, which is what keeps
        // yesterday's streak on screen all morning.
        var cursor = horizon
        if (cursor == todayNum && habit.scheduledOn(todayKey)) {
            val s = entries[todayKey]?.status
            if (s != HabitStatus.DONE && s != HabitStatus.SKIPPED) cursor = todayNum - 1
        }
        var current = 0
        while (cursor >= start) {
            val key = dateKeyOf(cursor)
            if (!habit.scheduledOn(key)) { cursor--; continue }
            when (entries[key]?.status) {
                HabitStatus.DONE -> current++
                HabitStatus.SKIPPED -> Unit
                else -> break
            }
            cursor--
        }

        val today = entries[todayKey]
        return HabitStats(
            habitId = habit.id,
            current = current,
            longest = longest,
            rate = if (scheduled > 0) done.toFloat() / scheduled else 0f,
            scheduled = scheduled,
            done = done,
            skipped = skipped,
            missed = missed,
            lastDone = lastDone,
            todayStatus = today?.status,
            todayValue = today?.value ?: 0,
        )
    }

    // ── per day, across habits ───────────────────────────────────────────────

    /** Summarises [fromKey]..[toKey] inclusive. Days after [toKey] are never produced. */
    fun rollDays(
        habits: List<Habit>,
        entries: Map<String, Map<String, HabitEntry>>,
        fromKey: String,
        toKey: String,
    ): List<DayRoll> {
        val from = dayNumberOfKey(fromKey) ?: return emptyList()
        val to = dayNumberOfKey(toKey) ?: return emptyList()
        if (to < from) return emptyList()

        val out = ArrayList<DayRoll>((to - from + 1).toInt())
        var n = from
        while (n <= to) {
            val key = dateKeyOf(n)
            var scheduled = 0
            var done = 0
            var skipped = 0
            for (h in habits) {
                if (h.state == HabitLifecycle.ARCHIVED || !h.scheduledOn(key)) continue
                when (entries[h.id]?.get(key)?.status) {
                    HabitStatus.DONE -> { scheduled++; done++ }
                    HabitStatus.SKIPPED -> skipped++
                    else -> scheduled++
                }
            }
            out.add(
                DayRoll(
                    date = key,
                    scheduled = scheduled,
                    done = done,
                    skipped = skipped,
                    level = heatLevel(done, scheduled, skipped),
                    perfect = scheduled > 0 && done == scheduled,
                )
            )
            n++
        }
        return out
    }

    private fun heatLevel(done: Int, scheduled: Int, skipped: Int): Int {
        if (done == 0) return if (skipped > 0) 1 else 0
        if (scheduled == 0) return 4
        val frac = done.toFloat() / scheduled
        return when {
            frac >= 1f -> 4
            frac >= 0.5f -> 3
            else -> 2
        }
    }

    /**
     * Consecutive days on which the user completed at least one habit, ending today with
     * the same "today is not over" grace as a habit streak.
     *
     * "At least one" rather than "all of them" on purpose: an all-or-nothing account streak
     * resets on the first busy evening and then means nothing for the rest of the year,
     * which is exactly the failure mode habit apps get criticised for.
     */
    fun accountStreak(rolls: List<DayRoll>): Pair<Int, Int> {
        var run = 0
        var longest = 0
        for (r in rolls) {
            if (r.done > 0) {
                run++
                if (run > longest) longest = run
                continue
            }
            if (r.scheduled == 0 && r.skipped > 0) continue // a wholly skipped day is neutral
            run = 0
        }
        var i = rolls.lastIndex
        if (i >= 0 && rolls[i].done == 0) i--
        var current = 0
        while (i >= 0) {
            val r = rolls[i]
            if (r.done > 0) { current++; i--; continue }
            if (r.scheduled == 0 && r.skipped == 0) { i--; continue } // nothing was due
            break
        }
        return current to max(longest, current)
    }

    /** Longest run of days where every scheduled habit was done. */
    fun longestPerfectRun(rolls: List<DayRoll>): Int {
        var run = 0
        var best = 0
        for (r in rolls) {
            if (r.scheduled == 0 && r.skipped == 0) continue
            if (r.perfect) {
                run++
                if (run > best) best = run
                continue
            }
            run = 0
        }
        return best
    }

    /** Days since the last day with a miss, counting back from the end. */
    fun daysSinceMiss(rolls: List<DayRoll>): Int {
        var n = 0
        for (i in rolls.indices.reversed()) {
            val r = rolls[i]
            if (r.scheduled == 0) continue
            if (r.done < r.scheduled) {
                // Today only becomes a miss once it is over, and `rolls` ends at today.
                if (i == rolls.lastIndex) continue
                return n
            }
            n++
        }
        return n
    }

    /** done/scheduled across rolls, ignoring days on which nothing was due. */
    fun rate(rolls: List<DayRoll>): Float {
        var done = 0
        var scheduled = 0
        for (r in rolls) { done += r.done; scheduled += r.scheduled }
        return if (scheduled == 0) 0f else done.toFloat() / scheduled
    }

    /**
     * The 0..100 shown next to the hero ring.
     *
     * Deliberately NOT the completion rate — that number is already on screen, and a second
     * identical one is noise. It blends the last month's completion (70%) with streak health
     * (30%, saturating at three weeks), so it answers "am I keeping this up" rather than
     * "what fraction did I finish", and rewards steady 80% weeks over four perfect days
     * followed by nothing.
     */
    fun consistencyScore(monthRate: Float, currentStreak: Int): Int {
        val streakHealth = min(1f, currentStreak / 21f)
        return (100f * (0.7f * monthRate + 0.3f * streakHealth)).roundToInt()
    }

    // ── breakdowns ───────────────────────────────────────────────────────────

    /** Completion per weekday (Mon-first), plus the best and worst weekday index. */
    fun weekdayRates(rolls: List<DayRoll>): Triple<List<Float>, Int, Int> {
        val done = IntArray(7)
        val scheduled = IntArray(7)
        for (r in rolls) {
            val i = weekdayIndexOfKey(r.date)
            done[i] += r.done
            scheduled[i] += r.scheduled
        }
        val values = MutableList(7) { 0f }
        var best = -1
        var worst = -1
        for (i in 0 until 7) {
            if (scheduled[i] == 0) continue
            values[i] = done[i].toFloat() / scheduled[i]
            // Ties break toward the earlier weekday so the answer is stable between
            // requests — an unstable "your best day is Tuesday… no, Wednesday" reads as a bug.
            if (best < 0 || values[i] > values[best]) best = i
            if (worst < 0 || values[i] < values[worst]) worst = i
        }
        if (best < 0) { best = 0; worst = 0 }
        return Triple(values, best, worst)
    }

    /** Completion for each of the last [months] calendar months, oldest first. */
    fun monthlyRates(rolls: List<DayRoll>, todayKey: String, months: Int = 6): Pair<List<Float>, List<String>> {
        val parsed = com.example.tafakkurai.spiritual.parseDateKey(todayKey)
            ?: return List(months) { 0f } to List(months) { "" }
        val (year, month, _) = parsed

        val keys = ArrayList<String>(months)
        val labels = ArrayList<String>(months)
        for (i in months - 1 downTo 0) {
            var y = year
            var m = month - i
            while (m <= 0) { m += 12; y -= 1 }
            keys.add("$y-${if (m < 10) "0$m" else "$m"}")
            labels.add(MONTH_SHORT_KEYS.getOrElse(m - 1) { "" })
        }

        val done = HashMap<String, Int>(months)
        val scheduled = HashMap<String, Int>(months)
        for (r in rolls) {
            val prefix = r.date.take(7)
            if (!keys.contains(prefix)) continue
            done[prefix] = (done[prefix] ?: 0) + r.done
            scheduled[prefix] = (scheduled[prefix] ?: 0) + r.scheduled
        }
        val values = keys.map { k ->
            val s = scheduled[k] ?: 0
            if (s == 0) 0f else (done[k] ?: 0).toFloat() / s
        }
        return values to labels
    }

    /**
     * Completion per category, high → low.
     *
     * Only categories the user actually has habits in appear: an empty "Business 0%" row
     * is discouraging and says nothing.
     */
    fun categoryRates(habits: List<Habit>, stats: Map<String, HabitStats>): List<LabelledRate> {
        val done = HashMap<String, Int>()
        val scheduled = HashMap<String, Int>()
        for (h in habits) {
            if (h.state == HabitLifecycle.ARCHIVED) continue
            val s = stats[h.id] ?: continue
            done[h.categoryId] = (done[h.categoryId] ?: 0) + s.done
            scheduled[h.categoryId] = (scheduled[h.categoryId] ?: 0) + s.scheduled
        }
        return scheduled.entries
            .filter { it.value > 0 }
            .map { (id, sched) ->
                val cat = habitCategory(id)
                LabelledRate(id, cat.labelKey, (done[id] ?: 0).toFloat() / sched, cat.icon, cat.color)
            }
            .sortedWith(compareByDescending<LabelledRate> { it.value }.thenBy { it.key })
    }

    /**
     * Categories folded onto the five balance axes. Axes with no habits report 0 rather
     * than being dropped: a radar with a missing spoke is a different shape and reads as
     * a data error.
     */
    fun radarValues(habits: List<Habit>, stats: Map<String, HabitStats>): List<LabelledRate> {
        val done = HashMap<String, Int>()
        val scheduled = HashMap<String, Int>()
        for (h in habits) {
            if (h.state == HabitLifecycle.ARCHIVED) continue
            val s = stats[h.id] ?: continue
            val axis = h.category.axis
            done[axis] = (done[axis] ?: 0) + s.done
            scheduled[axis] = (scheduled[axis] ?: 0) + s.scheduled
        }
        return HABIT_RADAR_AXES.map { axis ->
            val sched = scheduled[axis] ?: 0
            LabelledRate(
                axis,
                "habits.axis.${axis.lowercase()}",
                if (sched == 0) 0f else (done[axis] ?: 0).toFloat() / sched,
            )
        }
    }

    /** Completion by Morning / Midday / Evening / Night. */
    fun timeOfDayRates(habits: List<Habit>, stats: Map<String, HabitStats>): List<LabelledRate> {
        val done = HashMap<String, Int>()
        val scheduled = HashMap<String, Int>()
        for (h in habits) {
            if (h.state == HabitLifecycle.ARCHIVED) continue
            val s = stats[h.id] ?: continue
            val b = h.timeBucket
            done[b] = (done[b] ?: 0) + s.done
            scheduled[b] = (scheduled[b] ?: 0) + s.scheduled
        }
        return HABIT_TIME_BUCKETS.map { (bucket, icon) ->
            val sched = scheduled[bucket] ?: 0
            LabelledRate(
                bucket,
                "habits.time_bucket.$bucket",
                if (sched == 0) 0f else (done[bucket] ?: 0).toFloat() / sched,
                icon,
            )
        }
    }
}
