package com.example.tafakkurai.sleep.health

import com.example.tafakkurai.prayer.deviceUtcOffsetHours
import com.example.tafakkurai.sleep.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSDate
import platform.Foundation.NSPredicate
import platform.Foundation.NSSortDescriptor
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import platform.HealthKit.*
import kotlin.coroutines.resume

/**
 * HealthKit, normalised into the app's sleep vocabulary.
 *
 * ### Setup this class cannot do for itself
 *
 * HealthKit needs two Info.plist keys and one capability, none of which live in Kotlin:
 *
 *  - `NSHealthShareUsageDescription` — why the app reads sleep. Required, or the permission
 *    sheet does not appear and authorization fails silently.
 *  - `NSHealthUpdateUsageDescription` — required only if `writeSession` is ever enabled.
 *  - The **HealthKit** capability on the app target.
 *
 * The framework itself needs no Gradle dependency: `platform.HealthKit` ships with the
 * Kotlin/Native Apple platform libraries and links automatically.
 *
 * ### Why sessions are stitched rather than read
 *
 * HealthKit has no concept of "a night". It stores a stream of `HKCategorySample`s, each one
 * a stage over an interval, and a single night is dozens of them — sometimes written by two
 * devices at once. [stitch] groups them back into sessions on the gaps, which is the only
 * place in this file with real logic and the only place worth testing.
 */
@OptIn(ExperimentalForeignApi::class)
class HealthKitSleepBridge(
    private val store: HKHealthStore = HKHealthStore(),
    private val offsetHours: () -> Double = ::deviceUtcOffsetHours,
) : HealthSleepBridge {

    override val platformName: String = "healthkit"

    override val isAvailable: Boolean
        get() = HKHealthStore.isHealthDataAvailable()

    private val sleepType: HKCategoryType?
        get() = HKObjectType.categoryTypeForIdentifier(HKCategoryTypeIdentifierSleepAnalysis)

    // Ask only for data the feature currently uses. Requesting optional heart, respiratory,
    // or oxygen permissions before their values can be attributed to the correct night would
    // collect more private data than needed and risks presenting a window-wide average as if
    // it belonged to one session.
    private fun readTypes(): Set<HKObjectType> = listOfNotNull(sleepType).toSet()

    /**
     * HealthKit deliberately never reveals read authorization.
     *
     * `authorizationStatusForType` reports only *write* status: Apple treats "has this app
     * been allowed to read my health data" as itself private, because leaking it would let an
     * app infer that a user has data of a given type at all. So there is no honest answer to
     * give here before a read has been attempted, and reporting DENIED would be a guess that
     * sends the user to a settings screen they may not need.
     *
     * [UNKNOWN] is the truthful answer; a successful [readSessions] is what actually proves
     * access, and the ViewModel treats an empty first read plus no error as "granted, empty".
     */
    override suspend fun permissionState(): HealthPermissionState {
        if (!isAvailable) return HealthPermissionState.UNAVAILABLE
        if (sleepType == null) return HealthPermissionState.UNAVAILABLE
        return HealthPermissionState.UNKNOWN
    }

    override suspend fun requestPermissions(): HealthPermissionState {
        if (!isAvailable) return HealthPermissionState.UNAVAILABLE
        val toRead = readTypes()
        if (toRead.isEmpty()) return HealthPermissionState.UNAVAILABLE

        return suspendCancellableCoroutine { continuation ->
            store.requestAuthorizationToShareTypes(
                typesToShare = null,
                readTypes = toRead,
            ) { success, _ ->
                // `success` means the sheet completed, not that anything was granted — see
                // the note on permissionState. GRANTED here means "we may now attempt reads".
                continuation.resume(
                    if (success) HealthPermissionState.GRANTED else HealthPermissionState.DENIED,
                )
            }
        }
    }

    override suspend fun readSessions(fromMs: Long, toMs: Long): List<SleepSession> {
        if (!isAvailable) return emptyList()
        val type = sleepType ?: return emptyList()

        val samples = querySamples(type, fromMs, toMs)
        if (samples.isEmpty()) return emptyList()

        return stitch(samples, offsetHours())
    }

    // ── querying ─────────────────────────────────────────────────────────────

    private suspend fun querySamples(
        type: HKSampleType,
        fromMs: Long,
        toMs: Long,
    ): List<HKCategorySample> = suspendCancellableCoroutine { continuation ->
        val predicate = HKQuery.predicateForSamplesWithStartDate(
            startDate = fromMs.toNSDate(),
            endDate = toMs.toNSDate(),
            options = HKQueryOptionStrictStartDate,
        )
        val sort = NSSortDescriptor.sortDescriptorWithKey(HKSampleSortIdentifierStartDate, ascending = true)

        val query = HKSampleQuery(
            sampleType = type,
            predicate = predicate,
            limit = HKObjectQueryNoLimit.toULong(),
            sortDescriptors = listOf(sort),
        ) { _, results, _ ->
            // A failure here is almost always "no permission", which is a normal state, not
            // an exception the caller should have to handle at four call sites.
            continuation.resume(results.orEmpty().filterIsInstance<HKCategorySample>())
        }
        store.executeQuery(query)
        continuation.invokeOnCancellation { store.stopQuery(query) }
    }

    // ── normalisation ────────────────────────────────────────────────────────

    internal companion object {

        /**
         * The longest gap between two samples that is still the same night.
         *
         * Chosen because a genuine mid-night waking is almost always shorter than this, while
         * the gap between a night and the next afternoon's nap is far longer. Too small and
         * one night becomes three; too large and a nap is swallowed into the previous night.
         */
        const val SESSION_GAP_MINUTES = 60

        /** HealthKit category value → the app's stage vocabulary. */
        fun stageOf(value: Long): SleepStage? = when (value) {
            HKCategoryValueSleepAnalysisInBed -> null // bracket only, not a stage
            HKCategoryValueSleepAnalysisAwake -> SleepStage.AWAKE
            HKCategoryValueSleepAnalysisAsleepCore -> SleepStage.CORE
            HKCategoryValueSleepAnalysisAsleepDeep -> SleepStage.DEEP
            HKCategoryValueSleepAnalysisAsleepREM -> SleepStage.REM
            HKCategoryValueSleepAnalysisAsleepUnspecified -> SleepStage.UNKNOWN
            else -> SleepStage.UNKNOWN
        }
    }

    /**
     * Groups a flat sample stream back into sessions.
     *
     * `inBed` samples bracket the session rather than becoming stages: they are what makes
     * sleep efficiency computable, and treating them as a stage would double-count every
     * minute of the night.
     */
    private fun stitch(samples: List<HKCategorySample>, offset: Double): List<SleepSession> {
        data class Raw(val startMs: Long, val endMs: Long, val stage: SleepStage?, val uuid: String)

        val raw = samples.map { sample ->
            Raw(
                startMs = sample.startDate.timeIntervalSince1970.toLong() * 1000,
                endMs = sample.endDate.timeIntervalSince1970.toLong() * 1000,
                stage = stageOf(sample.value),
                uuid = sample.UUID.UUIDString,
            )
        }.sortedBy { it.startMs }

        val groups = mutableListOf<MutableList<Raw>>()
        for (item in raw) {
            val current = groups.lastOrNull()
            val previousEnd = current?.maxOfOrNull { it.endMs }
            if (current == null || previousEnd == null ||
                item.startMs - previousEnd > SESSION_GAP_MINUTES * SleepTime.MS_PER_MINUTE
            ) {
                groups += mutableListOf(item)
            } else {
                current += item
            }
        }

        return groups.mapNotNull { group ->
            val staged = group.filter { it.stage != null }
            val inBedStart = group.minOf { it.startMs }
            val inBedEnd = group.maxOf { it.endMs }

            val asleep = staged.filter { it.stage != SleepStage.AWAKE }
            if (asleep.isEmpty()) return@mapNotNull null

            val start = asleep.minOf { it.startMs }
            val end = asleep.maxOf { it.endMs }
            val minutes = ((end - start) / SleepTime.MS_PER_MINUTE).toInt()
            if (minutes < SleepSession.MIN_NAP_MINUTES) return@mapNotNull null

            val segments = staged
                .map { SleepSegment(it.stage!!, it.startMs, it.endMs) }
                .sortedBy { it.startMs }

            SleepSession(
                // The earliest sample's UUID is stable for a given night as long as the same
                // device wrote it, which is what makes re-imports update rather than duplicate.
                id = "hk-${group.first().uuid}",
                externalId = group.first().uuid,
                kind = if (minutes >= SleepSession.MIN_NIGHT_MINUTES) SleepKind.NIGHT else SleepKind.NAP,
                // Staging means a watch was worn; a bare "asleep" block came from the phone
                // or another app writing into HealthKit.
                source = if (segments.any { it.stage == SleepStage.DEEP || it.stage == SleepStage.REM }) {
                    SleepSource.WATCH
                } else {
                    SleepSource.HEALTH_STORE
                },
                startMs = start,
                endMs = end,
                inBedStartMs = inBedStart,
                inBedEndMs = inBedEnd,
                segments = segments,
                dateKey = SleepTime.dateKeyOf(end, offset),
                createdAt = start,
                updatedAt = end,
            )
        }
    }
}

private fun Long.toNSDate(): NSDate =
    NSDate.dateWithTimeIntervalSince1970(this / 1000.0)
