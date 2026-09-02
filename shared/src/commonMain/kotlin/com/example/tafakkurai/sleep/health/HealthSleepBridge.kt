package com.example.tafakkurai.sleep.health

import com.example.tafakkurai.sleep.SleepSession

/**
 * The platform health store, as the shared code sees it.
 *
 * One interface covers HealthKit, Health Connect, and the no-op used before a platform is
 * wired. Everything above this line — the ViewModel, the scoring engine, every screen — is
 * identical on both platforms, which is the whole point of putting the seam here.
 *
 * ### Why an interface rather than `expect class`
 *
 * An `expect class` would force every target to supply exactly one implementation, and the
 * Android target genuinely has three cases to choose between at runtime: Health Connect
 * installed and granted, installed and denied, and not installed at all (it is a separate APK
 * below Android 14). An interface lets the Koin module pick, and lets tests inject a fake
 * without a source set.
 */
interface HealthSleepBridge {

    /** Short label for logs and activity events: "healthkit", "health_connect", "none". */
    val platformName: String

    /** False when this device has no health store at all — hides the whole settings section. */
    val isAvailable: Boolean

    /** Current permission state, without prompting. */
    suspend fun permissionState(): HealthPermissionState

    /**
     * Prompts for the permissions the module needs.
     *
     * Both platforms may return [HealthPermissionState.PARTIAL]: the user granted sleep but
     * not heart rate, which is a perfectly usable state — sleep scores work, recovery scores
     * degrade to sleep-quality-plus-debt. Treating partial as denied would throw away a
     * working feature over an optional one.
     */
    suspend fun requestPermissions(): HealthPermissionState

    /**
     * Reads sessions overlapping `[fromMs, toMs)`, normalised into the app's vocabulary.
     *
     * Implementations must:
     *  - return sessions with `externalId` set, so re-reads deduplicate rather than duplicate;
     *  - map every platform stage onto [com.example.tafakkurai.sleep.SleepStage];
     *  - attach vitals only where the user granted them;
     *  - never throw for a missing optional permission — omit the field instead.
     */
    suspend fun readSessions(fromMs: Long, toMs: Long): List<SleepSession>

    /**
     * Writes a session back to the health store. Off by default.
     *
     * Only ever called for sessions the app itself created ([com.example.tafakkurai.sleep.SleepSource.MANUAL]).
     * Writing back a session that came *from* the health store would create a feedback loop:
     * the next read imports our copy as a second record for the same night.
     *
     * @return true when written; false when unsupported or not permitted.
     */
    suspend fun writeSession(session: SleepSession): Boolean = false

    /**
     * Registers for background delivery so a night that ends at 07:00 is on the dashboard
     * before the user opens the app.
     *
     * @return true when observation is active. False is normal, not an error — it means the
     *  platform declined or the capability is unavailable, and the foreground pass covers it.
     */
    suspend fun observeInBackground(onChange: () -> Unit): Boolean = false
}

/** Health permission state, shared by both platforms. */
enum class HealthPermissionState {
    /** Not yet checked. */
    UNKNOWN,

    /** Everything the module asked for. */
    GRANTED,

    /** Sleep granted, some optional vitals not. Fully usable. */
    PARTIAL,

    /** Refused. The UI offers a route into system settings. */
    DENIED,

    /** No health store on this device, or the app is not entitled to one. */
    UNAVAILABLE,
}

/**
 * The bridge used when no platform store is wired.
 *
 * Deliberately not an error path: the module is fully functional without a health store —
 * manual logging, routines, smart bedtime and the coach all work — so the honest default is
 * "there is nothing here to read", not a crash or a permanently failing sync.
 */
class NoOpHealthSleepBridge : HealthSleepBridge {
    override val platformName: String = "none"
    override val isAvailable: Boolean = false
    override suspend fun permissionState() = HealthPermissionState.UNAVAILABLE
    override suspend fun requestPermissions() = HealthPermissionState.UNAVAILABLE
    override suspend fun readSessions(fromMs: Long, toMs: Long): List<SleepSession> = emptyList()
}
