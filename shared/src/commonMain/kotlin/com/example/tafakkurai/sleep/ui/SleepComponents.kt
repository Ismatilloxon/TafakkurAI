package com.example.tafakkurai.sleep.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.i18n.Language
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.screens.HPAD
import com.example.tafakkurai.screens.InsetCard
import com.example.tafakkurai.sleep.*
import com.example.tafakkurai.ui.*

/**
 * The Sleep & Rest component kit.
 *
 * Everything here is theme-driven: no component hard-codes a colour, so all seven palettes —
 * including the two light ones — get a coherent sleep module without a second design pass.
 *
 * ### The one visual rule
 *
 * Sleep screens are looked at in two states: half-awake in a dark bedroom, and over coffee the
 * next morning. Both want the same thing — one number large enough to read at a glance, and
 * detail that stays out of the way until asked for. So every screen here leads with a single
 * ring and defers everything else to cards below the fold.
 */

// ─────────────────────────────────────────────────────────── stage palette

/** Stage → palette colour. Kept in one place so the ring, timeline and legend cannot drift. */
@Composable
fun stageColor(stage: SleepStage): Color {
    val c = LocalTf.current
    return when (stage) {
        SleepStage.AWAKE -> c.warn
        SleepStage.REM -> c.cyan
        SleepStage.CORE -> c.teal
        SleepStage.DEEP -> c.purple
        SleepStage.UNKNOWN -> c.fg3
    }
}

@Composable
fun stageLabel(stage: SleepStage): String = when (stage) {
    SleepStage.AWAKE -> tr("sleep.stage.awake")
    SleepStage.REM -> tr("sleep.stage.rem")
    SleepStage.CORE -> tr("sleep.stage.core")
    SleepStage.DEEP -> tr("sleep.stage.deep")
    SleepStage.UNKNOWN -> tr("sleep.stage.asleep")
}

/** Band → palette colour, shared by both score rings. */
@Composable
fun bandColor(band: ScoreBand): Color {
    val c = LocalTf.current
    return when (band) {
        ScoreBand.EXCELLENT -> c.green
        ScoreBand.GOOD -> c.teal
        ScoreBand.FAIR -> c.gold
        ScoreBand.POOR -> c.danger
    }
}

@Composable
fun bandLabel(band: ScoreBand): String = when (band) {
    ScoreBand.EXCELLENT -> tr("sleep.band.excellent")
    ScoreBand.GOOD -> tr("sleep.band.good")
    ScoreBand.FAIR -> tr("sleep.band.fair")
    ScoreBand.POOR -> tr("sleep.band.poor")
}

@Composable
fun sleepDuration(totalMinutes: Int): String {
    val safe = totalMinutes.coerceAtLeast(0)
    val hours = safe / 60
    val minutes = safe % 60
    return when {
        hours == 0 -> tr("sleep.duration.minutes", minutes)
        minutes == 0 -> tr("sleep.duration.hours", hours)
        else -> tr("sleep.duration.hours_minutes", hours, minutes)
    }
}

// ─────────────────────────────────────────────────────────── rings

/**
 * The headline score ring.
 *
 * Sweeps from the top and animates on first composition, which is the one piece of motion the
 * dashboard has. It exists because the number arriving rather than simply being there is what
 * makes the screen feel like a measurement instead of a report.
 */
@Composable
fun SleepRing(
    score: Int,
    band: ScoreBand,
    size: Int = 168,
    strokeWidth: Float = 12f,
    caption: String? = null,
    center: @Composable (() -> Unit)? = null,
) {
    val c = LocalTf.current
    val color = bandColor(band)
    val progress by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "sleepRing",
    )

    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = strokeWidth.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(c.card3, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.55f), color, color)),
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (center != null) {
            center()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TfText("$score", color = c.fg, size = (size / 3.6f), weight = FontWeight.Bold, family = Fonts.mono)
                TfText(caption ?: bandLabel(band), color = c.fg3, size = 11f, weight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Sleep and recovery as concentric rings.
 *
 * They are shown together because the pair is the actual insight: a high sleep score with a
 * low recovery score is the specific combination that means "you slept fine but you are still
 * depleted", and neither ring alone can say it.
 */
@Composable
fun DualScoreRing(
    sleep: SleepScore,
    recovery: RecoveryScore,
    size: Int = 176,
) {
    val c = LocalTf.current
    val sleepColor = bandColor(sleep.band)
    val recoveryColor = bandColor(recovery.band)
    val sleepProgress by animateFloatAsState((sleep.value / 100f).coerceIn(0f, 1f), tween(900), label = "outer")
    val recoveryProgress by animateFloatAsState((recovery.value / 100f).coerceIn(0f, 1f), tween(900), label = "inner")

    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = 11.dp.toPx()
            val gap = 7.dp.toPx()

            fun ring(inset: Float, progress: Float, color: Color) {
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(c.card3, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            ring(stroke / 2f, sleepProgress, sleepColor)
            ring(stroke / 2f + stroke + gap, recoveryProgress, recoveryColor)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            TfText("${sleep.value}", color = c.fg, size = 40f, weight = FontWeight.Bold, family = Fonts.mono)
            TfText(tr("sleep.score.sleep"), color = c.fg3, size = 10.5f, family = Fonts.mono, letterSpacing = 0.6f)
            if (recovery.hasBaseline) {
                Row(
                    Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(recoveryColor))
                    TfText(tr("sleep.score.recovery_value", recovery.value), color = c.fg2, size = 11.5f, family = Fonts.mono)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────── timeline

/**
 * The hypnogram — the night as a shape.
 *
 * Four lanes, awake at the top down to deep at the bottom, which is the convention every
 * sleep product shares and therefore the one users can already read. Rounded blocks rather
 * than a step line because the block widths encode duration legibly at phone scale, where a
 * line's vertical transitions eat the horizontal information.
 *
 * Renders against the **in-bed** window, not the asleep window, so the gap before falling
 * asleep is visible rather than silently cropped.
 */
@Composable
fun SleepTimeline(
    session: SleepSession,
    height: Int = 120,
    use24Hour: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    val awake = stageColor(SleepStage.AWAKE)
    val rem = stageColor(SleepStage.REM)
    val core = stageColor(SleepStage.CORE)
    val deep = stageColor(SleepStage.DEEP)
    val unknown = stageColor(SleepStage.UNKNOWN)

    val windowStart = session.inBedStartMs
    val windowEnd = session.inBedEndMs
    val span = (windowEnd - windowStart).coerceAtLeast(1L).toFloat()

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val laneHeight = this.size.height / 4f
            val radius = CornerRadius(3.dp.toPx(), 3.dp.toPx())

            // Lane guides, so an empty stretch still reads as a lane rather than as nothing.
            repeat(4) { lane ->
                drawRect(
                    color = c.card3.copy(alpha = 0.35f),
                    topLeft = Offset(0f, lane * laneHeight + laneHeight * 0.42f),
                    size = Size(this.size.width, 1.dp.toPx()),
                )
            }

            val segments = session.segments.ifEmpty {
                // No staging: one honest block across the asleep window rather than an
                // invented hypnogram. UNKNOWN renders muted, which is the visual admission.
                listOf(SleepSegment(SleepStage.UNKNOWN, session.startMs, session.endMs))
            }

            for (segment in segments) {
                val x = ((segment.startMs - windowStart) / span) * this.size.width
                val w = (segment.durationMs / span) * this.size.width
                val lane = segment.stage.lane
                val color = when (segment.stage) {
                    SleepStage.AWAKE -> awake
                    SleepStage.REM -> rem
                    SleepStage.CORE -> core
                    SleepStage.DEEP -> deep
                    SleepStage.UNKNOWN -> unknown
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x.coerceAtLeast(0f), lane * laneHeight + laneHeight * 0.18f),
                    size = Size(w.coerceAtLeast(2f), laneHeight * 0.62f),
                    cornerRadius = radius,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TfText(
                SleepTime.formatClockAt(windowStart, use24Hour),
                color = c.fg3, size = 10.5f, family = Fonts.mono,
            )
            TfText(
                SleepTime.formatClockAt(windowEnd, use24Hour),
                color = c.fg3, size = 10.5f, family = Fonts.mono,
            )
        }
    }
}

/** Legend for [SleepTimeline]. Separate so a compact timeline can omit it. */
@Composable
fun SleepTimelineLegend(showUnknown: Boolean = false) {
    val c = LocalTf.current
    val stages = buildList {
        addAll(listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.CORE, SleepStage.DEEP))
        if (showUnknown) add(SleepStage.UNKNOWN)
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        stages.forEach { stage ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(stageColor(stage)))
                TfText(stageLabel(stage), color = c.fg3, size = 11f)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────── stage cards

/** One stage's duration, share and healthy range. */
@Composable
fun StageCard(
    stage: SleepStage,
    minutes: Int,
    share: Double,
    healthyRange: IntRange?,
) {
    val c = LocalTf.current
    val color = stageColor(stage)
    val percent = (share * 100).toInt()
    val inRange = healthyRange == null || percent in healthyRange

    Card(Modifier.fillMaxWidth(), pad = 14) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            TfText(stageLabel(stage), color = c.fg, size = 14f, weight = FontWeight.SemiBold)
            Box(Modifier.weight(1f)) {
                if (healthyRange != null) {
                    TfText(
                        if (inRange) {
                            tr("sleep.stage.typical_range", healthyRange.first, healthyRange.last)
                        } else {
                            tr("sleep.stage.outside_range", healthyRange.first, healthyRange.last)
                        },
                        color = if (inRange) c.fg3 else c.warn,
                        size = 11.5f,
                    )
                }
            }
            TfText(sleepDuration(minutes), color = c.fg2, size = 13f, family = Fonts.mono)
            TfText("$percent%", color = color, size = 13f, family = Fonts.mono, weight = FontWeight.SemiBold)
        }
        ProgressBar(
            value = share.toFloat().coerceIn(0f, 1f),
            color = color,
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────── statistics

/** A labelled metric with an optional comparison against the user's baseline. */
@Composable
fun StatisticCard(
    label: String,
    value: String,
    caption: String? = null,
    accent: Color? = null,
    trend: TrendDirection? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    Card(modifier, pad = 14) {
        Overline(label)
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TfText(value, color = accent ?: c.fg, size = 22f, weight = FontWeight.Bold, family = Fonts.mono)
            if (trend != null) {
                val (icon, tint) = when (trend) {
                    TrendDirection.IMPROVING -> "trendingUp" to c.green
                    TrendDirection.DECLINING -> "trendingDown" to c.danger
                    TrendDirection.STABLE -> "arrowRight" to c.fg3
                }
                Box(Modifier.padding(bottom = 3.dp)) { TfIcon(icon, size = 14, color = tint) }
            }
        }
        if (caption != null) {
            TfText(caption, color = c.fg3, size = 11.5f, lineHeight = 1.35f, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/**
 * Nightly duration bars against the goal line.
 *
 * The goal line is the point: bars alone say "some nights were longer", the line says
 * "these three were short". Nights with no data are drawn as hollow slots rather than
 * skipped, so a gap reads as a gap.
 */
@Composable
fun SleepDurationChart(
    series: List<SleepDayPoint>,
    goalMinutes: Int,
    height: Int = 108,
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    val accent = c.teal
    val short = c.warn
    if (series.isEmpty()) return

    val maxMinutes = maxOf(
        series.maxOfOrNull { it.asleepMinutes } ?: 0,
        goalMinutes,
    ).coerceAtLeast(60) * 1.15f

    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val slot = this.size.width / series.size
        val barWidth = (slot * 0.56f).coerceAtLeast(3f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Goal line first, so bars draw over it.
        val goalY = this.size.height * (1f - goalMinutes / maxMinutes)
        var x = 0f
        while (x < this.size.width) {
            drawRect(
                color = c.fg3.copy(alpha = 0.35f),
                topLeft = Offset(x, goalY),
                size = Size(4.dp.toPx(), 1.dp.toPx()),
            )
            x += 8.dp.toPx()
        }

        series.forEachIndexed { index, point ->
            val left = index * slot + (slot - barWidth) / 2f
            if (!point.hasSession) {
                drawRoundRect(
                    color = c.card3.copy(alpha = 0.6f),
                    topLeft = Offset(left, this.size.height - 4.dp.toPx()),
                    size = Size(barWidth, 4.dp.toPx()),
                    cornerRadius = radius,
                )
                return@forEachIndexed
            }
            val h = (this.size.height * (point.asleepMinutes / maxMinutes)).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = if (point.asleepMinutes >= goalMinutes) accent else short,
                topLeft = Offset(left, this.size.height - h),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * Bed and wake times per night, drawn as a floating bar per day.
 *
 * This is the consistency chart, and it is the one view where irregularity is instantly
 * obvious — a ragged left edge means bedtime moves around, and that is a harder thing to
 * argue with than a consistency score of 61.
 */
@Composable
fun BedtimeConsistencyChart(
    series: List<SleepDayPoint>,
    height: Int = 120,
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    val accent = c.purple
    val logged = series.filter { it.hasSession && it.bedtimeMinutes != null && it.wakeMinutes != null }
    if (logged.isEmpty()) return

    // Night-centred axis: −360 (18:00) through +720 (12:00). Wide enough for any human
    // schedule, narrow enough that a normal night fills most of the height.
    val axisMin = -360f
    val axisMax = 720f
    val axisSpan = axisMax - axisMin

    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val slot = this.size.width / series.size
        val barWidth = (slot * 0.5f).coerceAtLeast(3f)
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        // Midnight reference.
        val midnightY = this.size.height * ((0f - axisMin) / axisSpan)
        drawRect(
            color = c.sep,
            topLeft = Offset(0f, midnightY),
            size = Size(this.size.width, 1.dp.toPx()),
        )

        series.forEachIndexed { index, point ->
            val bed = point.bedtimeMinutes ?: return@forEachIndexed
            val wake = point.wakeMinutes ?: return@forEachIndexed
            if (!point.hasSession) return@forEachIndexed

            val top = this.size.height * ((bed - axisMin) / axisSpan)
            val bottom = this.size.height * ((wake - axisMin) / axisSpan)
            val left = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = accent.copy(alpha = 0.85f),
                topLeft = Offset(left, top.coerceIn(0f, this.size.height)),
                size = Size(barWidth, (bottom - top).coerceAtLeast(3.dp.toPx())),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * A 13-week contribution-style heatmap.
 *
 * Weeks as columns, days as rows — the shape people already read from activity graphs. Cell
 * opacity is the score, not the duration, so a long bad night does not read as a good one.
 */
@Composable
fun SleepCalendarHeatmap(
    points: List<SleepDayPoint>,
    modifier: Modifier = Modifier,
    onSelect: ((SleepDayPoint) -> Unit)? = null,
) {
    val c = LocalTf.current
    val accent = c.teal
    if (points.isEmpty()) return

    // Pad the head so the first column starts on a Monday and the grid stays aligned.
    val leading = points.firstOrNull()
        ?.let { com.example.tafakkurai.spiritual.dayNumberOf(it.dateKey) }
        ?.let { (((it + 3) % 7) + 7) % 7 }
        ?.toInt() ?: 0
    val cells: List<SleepDayPoint?> = List(leading) { null } + points
    val weeks = cells.chunked(7)

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        weeks.forEach { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { day ->
                    val point = week.getOrNull(day)
                    val alpha = when {
                        point == null || !point.hasSession -> 0f
                        else -> (0.20f + (point.score / 100f) * 0.80f).coerceIn(0.20f, 1f)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (alpha == 0f) c.card3.copy(alpha = 0.5f) else accent.copy(alpha = alpha))
                            .then(
                                if (point != null && point.hasSession && onSelect != null) {
                                    pressScale({ onSelect(point) }, 0.85f)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────── coach

/** One coach recommendation, with the evidence that produced it. */
@Composable
fun RecommendationCard(
    recommendation: SleepCoach.Recommendation,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    val accent = c.named(recommendation.colorToken)
    val title = localizedRecommendation(recommendation, "title")
    val body = localizedRecommendation(recommendation, "body")
    val evidence = localizedRecommendation(recommendation, "evidence")

    Card(modifier.fillMaxWidth(), pad = 15) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(recommendation.icon, accent, size = 38, iconSize = 19)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                TfText(title, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold, lineHeight = 1.3f)
                TfText(body, color = c.fg2, size = 13f, lineHeight = 1.5f)
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TfIcon("barChart", size = 11, color = c.fg3)
                    // The number that fired the rule, verbatim. Advice you can audit is
                    // advice you can disagree with, which is what makes it trustworthy.
                    TfText(evidence, color = c.fg3, size = 11f, family = Fonts.mono)
                }
                if (recommendation.actionRoute != null) {
                    Row(
                        Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(accent.soft(0.18f))
                            .then(pressScale(onAction, 0.96f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        TfText(tr("sleep.coach.${recommendation.id}.action"), color = accent, size = 12.5f, weight = FontWeight.SemiBold)
                        TfIcon("arrowRight", size = 12, color = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun localizedRecommendation(recommendation: SleepCoach.Recommendation, part: String): String {
    val raw = when (part) {
        "title" -> recommendation.titleArgs
        "body" -> recommendation.bodyArgs
        else -> recommendation.evidenceArgs
    }
    val args: Array<out Any> = when (recommendation.id to part) {
        ("short-sleep" to "title"), ("sleep-debt" to "title"),
        ("social-jetlag" to "title"), ("latency" to "title"),
        ("bedtime-drift" to "title"), ("fajr-routine" to "title") ->
            arrayOf(sleepDuration(raw.firstOrNull() ?: 0))
        ("short-sleep" to "body") ->
            arrayOf(sleepDuration(raw.getOrElse(0) { 0 }), sleepDuration(raw.getOrElse(1) { 0 }))
        ("short-sleep" to "evidence") ->
            arrayOf<Any>(sleepDuration(raw.getOrElse(0) { 0 }), raw.getOrElse(1) { 0 })
        ("sleep-debt" to "evidence"), ("social-jetlag" to "evidence") ->
            arrayOf(sleepDuration(raw.firstOrNull() ?: 0))
        ("fragmented" to "evidence") ->
            arrayOf<Any>(raw.getOrElse(0) { 0 }, sleepDuration(raw.getOrElse(1) { 0 }))
        ("bedtime-drift" to "body") ->
            arrayOf(SleepTime.formatClock(raw.getOrElse(0) { 0 }), SleepTime.formatClock(raw.getOrElse(1) { 0 }))
        ("bedtime-drift" to "evidence") -> arrayOf(SleepTime.formatClock(raw.firstOrNull() ?: 0))
        ("fajr-routine" to "body") -> arrayOf(SleepTime.formatClock(raw.firstOrNull() ?: 0))
        ("fajr-routine" to "evidence") ->
            arrayOf(SleepTime.formatClock(raw.getOrElse(0) { 0 }), SleepTime.formatClock(raw.getOrElse(1) { 0 }))
        else -> raw.toTypedArray()
    }
    return LocalStrings.current.format("sleep.coach.${recommendation.id}.$part", *args)
}

// ─────────────────────────────────────────────────────────── routine

/**
 * A routine step row.
 *
 * Tapping the row toggles it; the Arabic and the source, when present, sit beneath. The
 * religious steps use exactly this row — no badge, no separate section, no different
 * affordance — which is the visual half of the promise that they are ordinary steps the user
 * chose, not a mode the app has put them in.
 */
@Composable
fun RoutineStepRow(
    state: RoutineStepState,
    onToggle: () -> Unit,
    onOpenQuran: ((String) -> Unit)? = null,
) {
    val c = LocalTf.current
    val step = state.step
    val accent = c.named(step.colorToken)
    val done = state.completed

    Card(Modifier.fillMaxWidth(), pad = 14, onClick = onToggle) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (done) accent else Color.Transparent)
                    .border(if (done) 0.dp else 1.5.dp, if (done) Color.Transparent else c.line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) TfIcon("check", size = 15, color = Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TfText(
                        tr("sleep.routine.step.${step.id}.title"),
                        color = if (done) c.fg3 else c.fg,
                        size = 14.5f,
                        weight = FontWeight.SemiBold,
                    )
                    TfText(
                        if (step.estimatedSeconds >= 60) {
                            tr("sleep.duration.minutes_short", step.estimatedSeconds / 60)
                        } else {
                            tr("sleep.duration.seconds_short", step.estimatedSeconds)
                        },
                        color = c.fg3, size = 11f, family = Fonts.mono,
                    )
                }
                if (step.arabic != null) {
                    TfText(
                        step.arabic,
                        color = if (done) c.fg3 else c.fg,
                        size = 19f,
                        family = Fonts.serif,
                        lineHeight = 1.7f,
                        align = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    )
                }
                if (step.arabic != null) {
                    TfText(
                        tr("sleep.routine.step.${step.id}.transliteration"),
                        color = c.fg2,
                        size = 12.5f,
                        lineHeight = 1.45f,
                    )
                }
                if (step.hasTranslation) {
                    TfText("“${tr("sleep.routine.step.${step.id}.translation")}”", color = c.fg2, size = 12.5f, lineHeight = 1.45f)
                }
                TfText(tr("sleep.routine.step.${step.id}.detail"), color = c.fg3, size = 12f, lineHeight = 1.45f)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (step.source != null) {
                        TfText(step.source, color = c.fg3, size = 10.5f, family = Fonts.mono)
                    }
                    if (step.quranRoute != null && onOpenQuran != null) {
                        Row(
                            Modifier.then(pressScale({ onOpenQuran(step.quranRoute) }, 0.94f)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TfText(tr("sleep.routine.read"), color = accent, size = 11.5f, weight = FontWeight.SemiBold)
                            TfIcon("arrowRight", size = 11, color = accent)
                        }
                    }
                }
            }
        }
    }
}

/** Routine header: progress ring, count and remaining time. */
@Composable
fun RoutineCard(
    progress: RoutineProgress,
    title: String,
    onOpen: () -> Unit,
) {
    val c = LocalTf.current
    val accent = if (progress.phase == RoutinePhase.BEDTIME) c.purple else c.gold
    val fraction by animateFloatAsState(progress.fraction, tween(500), label = "routine")

    InsetCard(pad = 16, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(52.dp)) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                    drawArc(c.card3, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(accent, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                TfIcon(
                    if (progress.phase == RoutinePhase.BEDTIME) "moonStars" else "sunrise",
                    size = 20,
                    color = accent,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TfText(title, color = c.fg, size = 15.5f, weight = FontWeight.SemiBold)
                TfText(
                    when {
                        progress.totalCount == 0 -> tr("sleep.routine.no_steps")
                        progress.isComplete -> tr("sleep.routine.complete_well_done")
                        else -> tr("sleep.routine.progress", progress.completedCount, progress.totalCount, progress.remainingSeconds / 60)
                    },
                    color = c.fg2, size = 12.5f,
                )
            }
            TfIcon("chevronRight", size = 18, color = c.fg3)
        }
    }
}

// ─────────────────────────────────────────────────────────── states

/**
 * The empty state.
 *
 * Offers the two things that actually get a user to a first night — connect a health store,
 * or log one by hand — rather than an illustration and an encouraging sentence.
 */
@Composable
fun SleepEmptyState(
    healthAvailable: Boolean,
    onConnectHealth: () -> Unit,
    onLogManually: () -> Unit,
) {
    val c = LocalTf.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(c.purple.soft(0.22f), c.purple.soft(0.06f)))),
            contentAlignment = Alignment.Center,
        ) { TfIcon("moonStars", size = 40, color = c.purple) }

        TfText(tr("sleep.empty.title"), color = c.fg, size = 21f, weight = FontWeight.Bold, family = Fonts.display, align = TextAlign.Center)
        TfText(
            tr("sleep.empty.body"),
            color = c.fg2, size = 14f, lineHeight = 1.5f, align = TextAlign.Center,
        )

        if (healthAvailable) {
            SleepPrimaryButton(tr("sleep.health.connect"), onConnectHealth)
        }
        Row(
            Modifier.then(pressScale(onLogManually, 0.95f)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TfIcon("plus", size = 15, color = c.accent)
            TfText(tr("sleep.manual.log_night"), color = c.accent, size = 14f, weight = FontWeight.SemiBold)
        }
    }
}

/** Skeleton shown while the first health import runs. */
@Composable
fun SleepLoadingState() {
    val c = LocalTf.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = HPAD.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(168.dp).clip(CircleShape).background(c.card2))
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(c.card2))
        }
        TfText(tr("sleep.loading.history"), color = c.fg3, size = 13f)
    }
}

/** Error state. Always offers a retry, because every failure here is transient or a permission. */
@Composable
fun SleepErrorState(message: String, onRetry: () -> Unit) {
    val c = LocalTf.current
    InsetCard(pad = 16) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge("x", c.danger, size = 36, iconSize = 17)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TfText(tr("sleep.error.sync_title"), color = c.fg, size = 14f, weight = FontWeight.SemiBold)
                TfText(tr("sleep.error.sync_body"), color = c.fg3, size = 12f, lineHeight = 1.4f)
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.card3)
                    .then(pressScale(onRetry, 0.95f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) { TfText(tr("common.retry"), color = c.accent, size = 12.5f, weight = FontWeight.SemiBold) }
        }
    }
}

// ─────────────────────────────────────────────────────────── primitives

/** Full-width primary action, matching the app's button weight. */
@Composable
fun SleepPrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = LocalTf.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (enabled) c.accent else c.card3)
            .semantics { contentDescription = label; role = Role.Button }
            .then(if (enabled) pressScale(onClick, 0.98f) else Modifier)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        TfText(label, color = if (enabled) Color.White else c.fg3, size = 15f, weight = FontWeight.SemiBold)
    }
}

/** A labelled row inside a summary card. */
@Composable
fun SleepMetaRow(label: String, value: String, accent: Color? = null) {
    val c = LocalTf.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TfText(label, color = c.fg3, size = 12.5f)
        TfText(value, color = accent ?: c.fg2, size = 12.5f, family = Fonts.mono, weight = FontWeight.Medium)
    }
}

/** Score breakdown — every component, its weight and the reason it scored what it did. */
@Composable
fun ScoreBreakdown(components: List<ScoreComponent>) {
    val c = LocalTf.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        components.forEach { component ->
            val tint = bandColor(ScoreBand.of(component.value))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TfText(tr("sleep.score.${component.key}.label"), color = c.fg, size = 13.5f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TfText("${(component.weight * 100).toInt()}%", color = c.fg3, size = 11f, family = Fonts.mono)
                    Spacer(Modifier.width(10.dp))
                    TfText("${component.value}", color = tint, size = 14f, weight = FontWeight.Bold, family = Fonts.mono)
                }
                ProgressBar(component.value / 100f, tint, modifier = Modifier.fillMaxWidth())
                TfText(localizedScoreDetail(component), color = c.fg3, size = 11.5f, lineHeight = 1.35f)
            }
        }
    }
}

@Composable
private fun localizedScoreDetail(component: ScoreComponent): String {
    fun number(index: Int): Double = component.detailArgs.getOrElse(index) { 0.0 }
    fun integer(index: Int): Int = number(index).toInt()
    return when (component.key) {
        "duration" -> tr("sleep.score.duration.detail", sleepDuration(integer(0)), sleepDuration(integer(1)))
        "efficiency" -> tr("sleep.score.efficiency.detail", integer(0), sleepDuration(integer(1)))
        "deep", "rem" -> tr("sleep.score.stage.detail", sleepDuration(integer(0)), integer(1))
        "restfulness" -> {
            val interruptions = integer(0)
            val latency = integer(1)
            when {
                interruptions == 0 && latency <= 0 -> tr("sleep.score.restfulness.none")
                interruptions == 0 -> tr("sleep.score.restfulness.none_latency", sleepDuration(latency))
                latency <= 0 -> tr("sleep.score.restfulness.wakings", interruptions)
                else -> tr("sleep.score.restfulness.wakings_latency", interruptions, sleepDuration(latency))
            }
        }
        "timing" -> {
            val drift = integer(0)
            when {
                kotlin.math.abs(drift) < 5 -> tr("sleep.score.timing.on_schedule")
                drift < 0 -> tr("sleep.score.timing.earlier", sleepDuration(kotlin.math.abs(drift)))
                else -> tr("sleep.score.timing.later", sleepDuration(drift))
            }
        }
        "hrv" -> tr("sleep.score.hrv.detail", integer(0), integer(1))
        "rhr" -> {
            val value = integer(0)
            val delta = integer(1)
            when {
                delta > 0 -> tr("sleep.score.rhr.above", value, delta)
                delta < 0 -> tr("sleep.score.rhr.below", value, kotlin.math.abs(delta))
                else -> tr("sleep.score.rhr.baseline", value)
            }
        }
        "sleep" -> tr("sleep.score.sleep.detail", component.value)
        "debt" -> if (integer(0) <= 15) {
            tr("sleep.score.debt.none")
        } else {
            tr("sleep.score.debt.owed", sleepDuration(integer(0)))
        }
        "respiration" -> tr("sleep.score.respiration.detail", localizedDecimal(number(0)))
        else -> tr("sleep.score.detail.unavailable")
    }
}

@Composable
private fun localizedDecimal(value: Double): String {
    val plain = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    return if (LocalStrings.current.language == Language.ENGLISH) plain else plain.replace('.', ',')
}
