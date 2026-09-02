package com.example.tafakkurai

import androidx.compose.ui.graphics.Color

/**
 * Small presentation models shared by more than one legacy screen/ViewModel.
 *
 * This file intentionally contains no sample user state. The old prototype mixed fixed plans,
 * streaks, balances, social posts, sleep scores, finance values and career suggestions into this
 * source file; reachable product screens now read their real repositories instead.
 */

data class Subject(
    val id: String,
    val name: String,
    val native: String,
    /** Catalog keys exist only for app-owned seeded content; remote authored content falls back to [name]. */
    val titleKey: String? = null,
    val descriptionKey: String? = null,
    val icon: String,
    val seed: Int,
    val level: String,
    val progress: Float,
    val next: String,
    val streak: Int,
    val accent: String,
)

data class ChatMsg(val from: String, val text: String, val time: String)

data class SocialPost(
    val id: String,
    val author: String,
    val handle: String,
    val avatarSeed: Int,
    val time: String,
    val text: String,
    val likes: Int,
    val comments: Int,
    val tag: String,
    val likedByMe: Boolean = false,
)

/** One calculated prayer time. [min] is the local minute of day. */
data class PrayerTime(
    val key: String,
    val name: String,
    val short: String,
    val time: String,
    val min: Int,
    val icon: String,
)

/** UI-only shape used after real plan/prayer/activity projections are merged. */
data class DayPlanItem(
    val type: String,
    val name: String? = null,
    val title: String? = null,
    val time: String,
    val dur: String? = null,
    val cat: String? = null,
    val done: Boolean = false,
    val current: Boolean = false,
    val state: String? = null,
)

data class PrayerTrackerRow(val key: String, val arabic: String)

val PRAYER_TRACKER_ROWS = listOf(
    PrayerTrackerRow("fajr", "الفجر"),
    PrayerTrackerRow("dhuhr", "الظهر"),
    PrayerTrackerRow("asr", "العصر"),
    PrayerTrackerRow("maghrib", "المغرب"),
    PrayerTrackerRow("isha", "العشاء"),
)

data class PrayerStateMeta(val color: Color, val icon: String)

val PRAYER_STATES = mapOf(
    "jamaat" to PrayerStateMeta(Color(0xFF2BB673), "users"),
    "ontime" to PrayerStateMeta(Color(0xFFC9A24E), "check"),
    "late" to PrayerStateMeta(Color(0xFFE0A93B), "clock"),
    "qada" to PrayerStateMeta(Color(0xFFE5746B), "repeat"),
    "upcoming" to PrayerStateMeta(Color(0xFF7E8799), "circle"),
)

/** Stable sport metadata; names live in the localization catalogs. */
data class SportType(val key: String, val icon: String, val met: Float, val color: Color)

val TSPORT_TYPES = listOf(
    SportType("run", "activity", 9.8f, Color(0xFFE5486B)),
    SportType("gym", "dumbbell", 5.5f, Color(0xFF7C5CFF)),
    SportType("football", "circle", 8f, Color(0xFF2BB673)),
    SportType("cycling", "activity", 7.5f, Color(0xFF4CC2FF)),
    SportType("swim", "activity", 8.3f, Color(0xFF3D7BFF)),
    SportType("walk", "activity", 3.5f, Color(0xFFC9A24E)),
)

val TSPORT_GOALS = listOf("lose_weight", "stay_active", "build_strength")
val TSPORT_DURATIONS = listOf(15, 30, 45, 60, 90)
