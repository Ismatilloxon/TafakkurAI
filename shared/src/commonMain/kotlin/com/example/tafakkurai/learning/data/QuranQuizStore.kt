package com.example.tafakkurai.learning.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.learning.domain.QuranQuizAttempt
import com.example.tafakkurai.learning.domain.QuranQuizGoal
import com.example.tafakkurai.learning.domain.QuranQuizRepository
import com.example.tafakkurai.learning.domain.quranQuizQuestions
import com.example.tafakkurai.quran.surahOf
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** SQLDelight source of truth for exact-surah quiz goals and completed attempts. */
class QuranQuizStore(
    private val db: TafakkurDb,
    private val activity: ActivityLog,
    private val now: () -> Long = ::epochMillis,
    remoteChanges: Flow<Set<String>> = emptyFlow(),
    scope: CoroutineScope? = null,
) : QuranQuizRepository {
    private val q get() = db.quranQuizQueries
    private val goalState = mutableStateListOf<QuranQuizGoal>()
    private val attemptState = mutableStateMapOf<Int, QuranQuizAttempt>()

    override val goals: List<QuranQuizGoal> get() = goalState
    override val latestAttempts: Map<Int, QuranQuizAttempt> get() = attemptState

    override var revision by mutableStateOf(0)
        private set
    override var loadFailed by mutableStateOf(false)
        private set

    init {
        runCatching {
            db.syncEventQueries.upsertPolicy("quranQuizGoal", FEATURE, 1L)
            db.syncEventQueries.upsertPolicy("quranQuizAttempt", FEATURE, 1L)
        }
        refresh()
        scope?.launch {
            remoteChanges.collect { tables ->
                if (tables.any { it in SYNC_TABLES }) refresh()
            }
        }
    }

    override fun refresh() {
        val loaded = runCatching {
            val goals = mergeGoalSources(
                q.selectGoals(mapper = ::rawGoalFromFields).executeAsList().mapNotNull(::decodeGoalSource),
            )
            val attempts = q.selectLatestAttempts(mapper = ::rawAttemptFromFields)
                .executeAsList()
                .mapNotNull(::decodeAttempt)
                .filter { attempt -> goals.any { it.surahNumber == attempt.surahNumber } }
                .associateBy(QuranQuizAttempt::surahNumber)
            goals to attempts
        }.getOrElse {
            loadFailed = true
            revision++
            return
        }
        goalState.clear()
        goalState.addAll(loaded.first)
        attemptState.clear()
        attemptState.putAll(loaded.second)
        loadFailed = false
        revision++
    }

    /** Idempotently merges reading/Hifz provenance without replacing the original creation time. */
    override fun ensureAvailable(surahNumber: Int, source: String): QuranQuizGoal? {
        if (surahOf(surahNumber) == null) return null
        val safeSource = source.trim().lowercase().takeIf { it in ALLOWED_SOURCES } ?: return null
        val cached = goalState.firstOrNull { it.surahNumber == surahNumber }
            ?.takeIf { !loadFailed }
        if (cached != null && safeSource in cached.source.sourceSet()) return cached

        val existingSource = runCatching {
            q.selectGoalSource(
                surahNumber.toLong(),
                safeSource,
                mapper = ::rawGoalFromFields,
            ).executeAsOneOrNull()?.let(::decodeGoalSource)
        }.getOrElse {
            markLoadFailure()
            return null
        }
        if (existingSource != null) {
            val existingGoal = runCatching { readGoal(surahNumber) }.getOrElse {
                markLoadFailure()
                return null
            } ?: return null
            publishGoal(existingGoal)
            return existingGoal
        }

        val stamp = now().coerceAtLeast(0L)
        val proposed = QuranQuizGoal(
            surahNumber = surahNumber,
            source = safeSource,
            createdAt = stamp,
            updatedAt = stamp,
        )
        var canonical: QuranQuizGoal? = null
        val persisted = runCatching {
            db.transaction {
                q.insertGoal(
                    surahNumber = proposed.surahNumber.toLong(),
                    source = proposed.source,
                    createdAt = proposed.createdAt,
                    updatedAt = proposed.updatedAt,
                )
                canonical = readGoal(proposed.surahNumber)
                check(canonical != null && safeSource in canonical!!.source.sourceSet())
            }
        }.isSuccess
        val goal = canonical
        if (!persisted || goal == null) {
            markLoadFailure()
            return null
        }
        publishGoal(goal)
        return goal
    }

    override fun complete(
        attemptId: String,
        surahNumber: Int,
        correct: Int,
        total: Int,
        completedAt: Long,
        localDate: String,
        timezone: String,
    ): QuranQuizAttempt? {
        val safeId = attemptId.trim().takeIf { it.isNotEmpty() && it.length <= MAX_ID_LENGTH } ?: return null
        val safeDate = localDate.trim().takeIf(DATE_PATTERN::matches) ?: return null
        val safeTimezone = timezone.trim().takeIf { it.isNotEmpty() && it.length <= MAX_TIMEZONE_LENGTH }
            ?: return null
        val goal = runCatching {
            readGoal(surahNumber)
        }.getOrElse {
            markLoadFailure()
            return null
        } ?: return null
        val expectedTotal = quranQuizQuestions(surahNumber).size
        if (expectedTotal == 0 || total != expectedTotal || correct !in 0..total || completedAt <= 0L) return null

        val proposed = QuranQuizAttempt(
            id = safeId,
            surahNumber = surahNumber,
            correct = correct,
            total = total,
            completedAt = completedAt,
        )
        var canonical: QuranQuizAttempt? = null
        val persisted = runCatching {
            db.transaction {
                val existing = readAttempt(safeId)
                if (existing == null) {
                    q.insertAttempt(
                        id = proposed.id,
                        surahNumber = proposed.surahNumber.toLong(),
                        correct = proposed.correct.toLong(),
                        total = proposed.total.toLong(),
                        completedAt = proposed.completedAt,
                    )
                } else {
                    check(existing == proposed) { "Quiz attempt id conflicts with a different payload" }
                }
                canonical = readAttempt(safeId)
                check(canonical == proposed)
                check(
                    activity.record(
                        feature = FEATURE,
                        type = "quiz_completed",
                        entityId = proposed.id,
                        eventId = proposed.id,
                        occurredAtOverride = proposed.completedAt,
                        localDateOverride = safeDate,
                        timezoneOverride = safeTimezone,
                        properties = buildJsonObject {
                            put("quiz_id", proposed.id)
                            put("scope", "surah")
                            put("surah", proposed.surahNumber)
                            put("correct", proposed.correct)
                            put("total", proposed.total)
                            put("goal_source", goal.source)
                        },
                    ),
                )
            }
        }.isSuccess
        val attempt = canonical
        if (!persisted || attempt == null) return null

        val currentLatest = attemptState[surahNumber]
        if (currentLatest == null ||
            attempt.completedAt > currentLatest.completedAt ||
            (attempt.completedAt == currentLatest.completedAt && attempt.id > currentLatest.id)
        ) {
            attemptState[surahNumber] = attempt
        }
        loadFailed = false
        revision++
        return attempt
    }

    private fun rawGoalFromFields(
        surahNumber: Long,
        source: String,
        createdAt: Long,
        updatedAt: Long,
    ): RawQuranQuizGoal = RawQuranQuizGoal(surahNumber, source, createdAt, updatedAt)

    private fun rawAttemptFromFields(
        id: String,
        surahNumber: Long,
        correct: Long,
        total: Long,
        completedAt: Long,
    ): RawQuranQuizAttempt = RawQuranQuizAttempt(id, surahNumber, correct, total, completedAt)

    private fun decodeGoalSource(row: RawQuranQuizGoal): QuranQuizGoal? {
        if (row.surahNumber !in 1L..114L || row.createdAt < 0L || row.updatedAt < row.createdAt) return null
        if (row.source !in ALLOWED_SOURCES) return null
        return QuranQuizGoal(row.surahNumber.toInt(), row.source, row.createdAt, row.updatedAt)
    }

    private fun decodeAttempt(row: RawQuranQuizAttempt): QuranQuizAttempt? {
        if (row.id.isBlank() || row.id.length > MAX_ID_LENGTH || row.surahNumber !in 1L..114L) return null
        if (row.total !in 1L..Int.MAX_VALUE.toLong() || row.correct !in 0L..row.total || row.completedAt <= 0L) {
            return null
        }
        return QuranQuizAttempt(
            id = row.id,
            surahNumber = row.surahNumber.toInt(),
            correct = row.correct.toInt(),
            total = row.total.toInt(),
            completedAt = row.completedAt,
        )
    }

    private fun readGoal(surahNumber: Int): QuranQuizGoal? = mergeGoalSources(
        q.selectGoal(surahNumber.toLong(), mapper = ::rawGoalFromFields)
            .executeAsList()
            .mapNotNull(::decodeGoalSource),
    ).singleOrNull()

    private fun readAttempt(id: String): QuranQuizAttempt? =
        q.selectAttempt(id, mapper = ::rawAttemptFromFields).executeAsOneOrNull()?.let(::decodeAttempt)

    private fun mergeGoalSources(rows: List<QuranQuizGoal>): List<QuranQuizGoal> = rows
        .groupBy(QuranQuizGoal::surahNumber)
        .map { (surahNumber, sources) ->
            QuranQuizGoal(
                surahNumber = surahNumber,
                source = sources.map(QuranQuizGoal::source).distinct().sorted().joinToString(SOURCE_SEPARATOR),
                createdAt = sources.minOf(QuranQuizGoal::createdAt),
                updatedAt = sources.maxOf(QuranQuizGoal::updatedAt),
            )
        }
        .sortedWith(compareByDescending<QuranQuizGoal> { it.updatedAt }.thenBy { it.surahNumber })

    private fun publishGoal(goal: QuranQuizGoal) {
        goalState.removeAll { it.surahNumber == goal.surahNumber }
        goalState.add(goal)
        goalState.sortWith(compareByDescending<QuranQuizGoal> { it.updatedAt }.thenBy { it.surahNumber })
        loadFailed = false
        revision++
    }

    private fun markLoadFailure() {
        loadFailed = true
        revision++
    }

    private fun String?.sourceSet(): Set<String> = this
        ?.split(SOURCE_SEPARATOR)
        ?.map(String::trim)
        ?.filterTo(linkedSetOf()) { it in ALLOWED_SOURCES }
        .orEmpty()

    private companion object {
        const val FEATURE = "learning"
        const val SOURCE_SEPARATOR = "+"
        const val MAX_ID_LENGTH = 128
        const val MAX_TIMEZONE_LENGTH = 64
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        val ALLOWED_SOURCES = setOf("quran", "hifz")
        val SYNC_TABLES = setOf("quranQuizGoal", "quranQuizAttempt")
    }
}

private data class RawQuranQuizGoal(
    val surahNumber: Long,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

private data class RawQuranQuizAttempt(
    val id: String,
    val surahNumber: Long,
    val correct: Long,
    val total: Long,
    val completedAt: Long,
)
