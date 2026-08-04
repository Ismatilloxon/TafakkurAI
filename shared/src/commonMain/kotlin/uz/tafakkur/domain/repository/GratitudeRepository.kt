package uz.tafakkur.domain.repository

import com.example.tafakkurai.gratitude.GratChallengeProgress
import com.example.tafakkurai.gratitude.GratEntry
import com.example.tafakkurai.gratitude.GratSettings
import com.example.tafakkurai.gratitude.GratStats

/**
 * Backend half of the Gratitude feature. The UI never calls this directly — it reads
 * and writes [com.example.tafakkurai.gratitude.GratitudeStore], which is local-first;
 * this repository is what the outbox drains into and what a pull refresh uses.
 */
interface GratitudeRepository {
    suspend fun create(entry: GratEntry): GratEntry
    suspend fun update(entry: GratEntry): GratEntry
    suspend fun setFavourite(id: String, favourite: Boolean): GratEntry
    suspend fun delete(id: String)

    /** Delta pull. Returns the changed entries (deletions flagged) and the next cursor. */
    suspend fun changes(since: String?, limit: Int? = null): GratitudeDelta

    suspend fun prompts(): List<String>
    suspend fun stats(): GratStats
    suspend fun insights(month: String? = null): GratitudeInsights

    suspend fun challenges(): List<GratChallengeProgress>
    suspend fun joinChallenge(id: String, joined: Boolean): List<GratChallengeProgress>
    suspend fun completeChallengeDay(id: String, day: Int): List<GratChallengeProgress>

    suspend fun settings(): GratSettings
    suspend fun saveSettings(settings: GratSettings): GratSettings
}

data class GratitudeDelta(
    val entries: List<GratEntry>,
    val deletedIds: Set<String>,
    val cursor: String,
    val more: Boolean,
)

data class GratitudeInsights(
    val stats: GratStats,
    val moodWeek: List<Int>,
    val categories: List<GratitudeCategoryShare>,
    val reflection: String,
    val month: GratitudeMonth,
)

data class GratitudeCategoryShare(val key: String, val label: String, val percent: Int, val count: Int)

data class GratitudeMonth(
    val name: String,
    val year: Int,
    val entries: Int,
    val days: Int,
    val longest: Int,
    val topCategory: String,
    val line: String,
)
