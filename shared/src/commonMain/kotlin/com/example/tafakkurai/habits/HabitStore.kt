package com.example.tafakkurai.habits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.sync.syncJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Local-first, observable source of truth for Habits.
 *
 * Every user mutation commits its feature-owned SQL row and immutable [ActivityLog] fact in
 * one SQLDelight transaction. The database triggers then queue both records for sync. Compose
 * state changes only after that transaction succeeds, so a failed append cannot leave a habit
 * that appears completed locally while Today, Balance, progression, and the backend missed it.
 * Cross-feature projections deliberately use the source table only: they never append a second
 * activity for the same user action.
 */
class HabitStore(
    private val db: TafakkurDb,
    private val todayKey: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = ::randomHabitId,
    private val activity: ActivityLog? = null,
) {
    private val hq get() = db.habitQueries
    private val eq get() = db.habitEntryQueries
    private val nq get() = db.habitNoteQueries
    private val aq get() = db.habitAchievementQueries

    /** Display order: sortIndex, then creation. Observed — any write recomposes readers. */
    private val _habits = mutableStateListOf<Habit>()
    val habits: List<Habit> get() = _habits

    /** Keyed by entry id ("<habitId>:<dateKey>"). */
    private val _entries = mutableStateMapOf<String, HabitEntry>()

    private val _notes = mutableStateListOf<HabitNote>()
    private val _achievements = mutableStateMapOf<String, HabitAchievementState>()

    /**
     * Bumped by every mutation. Screens `remember(revision)` their derived state, so a
     * 371-day heatmap is folded once per change instead of once per recomposition.
     */
    var revision by mutableStateOf(0)
        private set

    init {
        seedSyncFeatureLabel()
        refresh()
    }

    /**
     * Groups the four habit tables under one sync feature label.
     *
     * Purely cosmetic: the backend keys off `entity`, which is the table name and never
     * varies. Without this each table would report as its own feature and the four would
     * not group together in cross-feature queries. Idempotent, and safe to run before or
     * after [com.example.tafakkurai.sync.SyncEngine.attach] — attach re-reads this table
     * on every launch.
     */
    private fun seedSyncFeatureLabel() {
        runCatching {
            val q = db.syncEventQueries
            listOf("habit", "habitEntry", "habitNote", "habitAchievement").forEach {
                q.upsertPolicy(it, FEATURE, 1L)
            }
        }
    }

    // ── reads ────────────────────────────────────────────────────────────────

    fun refresh() {
        _habits.clear()
        runCatching { hq.selectAll().executeAsList().map(::toHabit) }
            .getOrDefault(emptyList())
            .let(_habits::addAll)

        _entries.clear()
        runCatching { eq.selectAll().executeAsList() }.getOrDefault(emptyList())
            .forEach { row -> toEntry(row)?.let { _entries[it.id] = it } }

        _notes.clear()
        runCatching { nq.selectAll().executeAsList().map(::toNote) }
            .getOrDefault(emptyList())
            .let(_notes::addAll)

        _achievements.clear()
        runCatching { aq.selectAll().executeAsList() }.getOrDefault(emptyList())
            .forEach { _achievements[it.id] = HabitAchievementState(it.id, it.unlockedAt, it.progress.toFloat(), it.updatedAt) }

        revision++
    }

    fun today(): String = todayKey()

    fun byId(id: String): Habit? = _habits.firstOrNull { it.id == id }

    val active: List<Habit> get() = _habits.filter { it.state == HabitLifecycle.ACTIVE }
    val paused: List<Habit> get() = _habits.filter { it.state == HabitLifecycle.PAUSED }

    /** Habits due on [dateKey], in display order. */
    fun scheduledOn(dateKey: String): List<Habit> = active.filter { it.scheduledOn(dateKey) }

    fun entry(habitId: String, dateKey: String): HabitEntry? = _entries[habitEntryId(habitId, dateKey)]

    /** One habit's whole history, keyed by day — the shape [HabitMath] wants. */
    fun entriesFor(habitId: String): Map<String, HabitEntry> {
        val out = HashMap<String, HabitEntry>()
        for (e in _entries.values) if (e.habitId == habitId) out[e.dateKey] = e
        return out
    }

    /** Every habit's history, keyed by habit then day. Built once per derivation. */
    fun entryIndex(): Map<String, Map<String, HabitEntry>> {
        val out = HashMap<String, MutableMap<String, HabitEntry>>()
        for (e in _entries.values) {
            out.getOrPut(e.habitId) { HashMap() }[e.dateKey] = e
        }
        return out
    }

    fun stats(habitId: String): HabitStats {
        val h = byId(habitId) ?: return HabitStats(habitId)
        return HabitMath.habitStats(h, entriesFor(habitId), todayKey())
    }

    fun statsAll(): Map<String, HabitStats> {
        val index = entryIndex()
        val today = todayKey()
        return _habits.associate { h ->
            h.id to HabitMath.habitStats(h, index[h.id] ?: emptyMap(), today)
        }
    }

    /** Day rolls for the trailing [days] days, ending today. */
    fun rolls(days: Int): List<DayRoll> {
        val today = todayKey()
        val todayNum = dayNumberOfKey(today) ?: return emptyList()
        return HabitMath.rollDays(_habits, entryIndex(), dateKeyOf(todayNum - days + 1), today)
    }

    fun notesFor(habitId: String): List<HabitNote> =
        _notes.filter { it.habitId == habitId }.sortedByDescending { it.createdAt }

    fun achievementState(): Map<String, HabitAchievementState> = _achievements.toMap()

    // ── habit writes ─────────────────────────────────────────────────────────

    /** Creates a habit and returns it. The id is generated here so Create can close instantly. */
    fun create(
        name: String,
        categoryId: String,
        icon: String,
        colorKey: String,
        slot: HabitSlot,
        timeLocal: String = "",
        reminderEnabled: Boolean = false,
        reminderTime: String = "",
        smartTiming: Boolean = false,
        frequency: HabitFrequency = HabitFrequency.DAILY,
        weekdays: List<Int> = emptyList(),
        difficulty: HabitDifficulty = HabitDifficulty.MEDIUM,
        goalType: HabitGoalType = HabitGoalType.CHECK,
        goalTarget: Int = 1,
        goalUnit: String = "",
        motivation: String = "",
        description: String = "",
        source: String = "user",
    ): Habit {
        val stamp = now()
        val habit = Habit(
            id = newId(),
            name = name.trim(),
            description = description,
            categoryId = categoryId,
            colorKey = colorKey,
            icon = icon,
            slot = slot,
            timeLocal = timeLocal,
            reminderEnabled = reminderEnabled,
            reminderTime = if (reminderEnabled) reminderTime else "",
            smartTiming = smartTiming,
            frequency = frequency,
            weekdays = weekdays,
            difficulty = difficulty,
            goalType = goalType,
            goalTarget = if (goalType == HabitGoalType.CHECK) 1 else goalTarget.coerceAtLeast(1),
            goalUnit = if (goalType == HabitGoalType.CHECK) "" else goalUnit,
            motivation = motivation,
            state = HabitLifecycle.ACTIVE,
            // New habits land at the top, where the user just created them. Negative is
            // fine — sortIndex is only ever compared.
            sortIndex = -(stamp / 1000).toInt(),
            createdAt = stamp,
            updatedAt = stamp,
        )
        val persisted = commitActivity(
            type = "habit_created",
            entityId = habit.id,
            properties = buildJsonObject {
                put("habit_id", habit.id)
                put("category", habit.categoryId)
                put("goal_type", habit.goalType.id)
                put("difficulty", habit.difficulty.id)
            },
            source = source,
        ) { persist(habit) }
        check(persisted) { "Could not persist habit and ActivityEvent" }
        _habits.add(0, habit)
        sortInPlace()
        revision++
        return habit
    }

    fun createFromTemplate(t: HabitTemplate, localizedName: String): Habit {
        val cat = habitCategory(t.categoryId)
        return create(
            name = localizedName, categoryId = t.categoryId, icon = cat.icon, colorKey = cat.color,
            slot = t.slot, timeLocal = t.timeLocal,
            reminderEnabled = t.timeLocal.isNotBlank(), reminderTime = t.timeLocal,
            goalType = t.goalType, goalTarget = t.goalTarget, goalUnit = t.goalUnit,
            difficulty = t.difficulty,
        )
    }

    /**
     * Ensures one system-linked habit exists for a cross-feature intention.
     *
     * The deterministic id is the idempotency boundary: replaying a month of activity or
     * receiving it twice from sync still creates one habit. A user-deleted linked habit is
     * respected and never resurrected. User-edited names are preserved; only a name that is
     * still one of the app's known translations follows an interface-language change.
     */
    fun ensureConnectedHabit(
        connectionKey: String,
        displayName: String,
        knownTranslatedNames: Set<String>,
        categoryId: String,
        icon: String,
        colorKey: String,
        slot: HabitSlot = HabitSlot.ANYTIME,
    ): Habit? {
        val id = connectedHabitId(connectionKey)
        val existing = byId(id)
        if (existing != null) {
            if (existing.name != displayName && existing.name in knownTranslatedNames) {
                val renamed = existing.copy(name = displayName, updatedAt = now())
                val persisted = commitSourceWrite { persist(renamed) }
                if (!persisted) return existing
                val index = _habits.indexOfFirst { it.id == id }
                if (index >= 0) _habits[index] = renamed
                revision++
                return renamed
            }
            return existing
        }

        // selectAll deliberately hides tombstones. Check the row directly so a habit the
        // user deleted stays deleted when another source event arrives tomorrow.
        val stored = runCatching { hq.selectById(id).executeAsOneOrNull() }.getOrNull()
        if (stored?.deleted == 1L) return null

        val stamp = now()
        val habit = Habit(
            id = id,
            name = displayName,
            description = displayName,
            categoryId = categoryId,
            colorKey = colorKey,
            icon = icon,
            slot = slot,
            difficulty = HabitDifficulty.EASY,
            goalType = HabitGoalType.CHECK,
            goalTarget = 1,
            state = HabitLifecycle.ACTIVE,
            sortIndex = -(stamp / 1000).toInt(),
            createdAt = stamp,
            updatedAt = stamp,
        )
        if (!commitSourceWrite { persist(habit) }) return null
        _habits.add(0, habit)
        sortInPlace()
        revision++
        return habit
    }

    /** Completes a linked habit once for the event's local date. */
    fun completeConnectedHabit(connectionKey: String, dateKey: String): Boolean {
        val habitId = connectedHabitId(connectionKey)
        val habit = byId(habitId) ?: return false
        val id = habitEntryId(habitId, dateKey)
        if (_entries[id]?.status == HabitStatus.DONE) return false
        val stamp = now()
        val previous = _entries[id]
        return writeEntry(
            HabitEntry(
                id = id,
                habitId = habitId,
                dateKey = dateKey,
                status = HabitStatus.DONE,
                value = 1,
                target = 1,
                completedAt = stamp,
                createdAt = previous?.createdAt ?: stamp,
                updatedAt = stamp,
            ),
        )
    }

    /** Replaces an edited habit. Same id, so the backend upsert updates in place. */
    fun update(habit: Habit) {
        if (byId(habit.id) == habit) return
        val updated = habit.copy(updatedAt = now())
        commitHabitUpdate(
            updated = updated,
            type = "habit_edited",
            properties = buildJsonObject { put("habit_id", updated.id) },
        )
    }

    fun setReminder(habitId: String, enabled: Boolean, time: String, smart: Boolean) {
        val h = byId(habitId) ?: return
        val storedTime = if (enabled) time else ""
        if (
            h.reminderEnabled == enabled &&
            h.reminderTime == storedTime &&
            h.smartTiming == smart
        ) return
        commitHabitUpdate(
            updated = h.copy(
                reminderEnabled = enabled,
                reminderTime = storedTime,
                smartTiming = smart,
                updatedAt = now(),
            ),
            type = "reminder_changed",
            properties = buildJsonObject {
                put("habit_id", habitId)
                put("enabled", enabled)
                put("time", storedTime)
                put("smart", smart)
            },
        )
    }

    fun pause(habitId: String, days: Int) {
        val h = byId(habitId) ?: return
        val until = if (days > 0) {
            dayNumberOfKey(todayKey())?.let { dateKeyOf(it + days) } ?: ""
        } else {
            ""
        }
        if (h.state == HabitLifecycle.PAUSED && h.pausedUntil == until) return
        applyState(
            h = h,
            state = HabitLifecycle.PAUSED,
            pausedUntil = until,
            eventType = "habit_paused",
            properties = buildJsonObject {
                put("habit_id", habitId)
                put("days", days)
            },
        )
    }

    fun resume(habitId: String) {
        val h = byId(habitId) ?: return
        if (h.state == HabitLifecycle.ACTIVE && h.pausedUntil.isEmpty()) return
        applyState(
            h,
            HabitLifecycle.ACTIVE,
            "",
            "habit_resumed",
            buildJsonObject { put("habit_id", habitId) },
        )
    }

    fun archive(habitId: String) {
        val h = byId(habitId) ?: return
        if (h.state == HabitLifecycle.ARCHIVED) return
        applyState(
            h,
            HabitLifecycle.ARCHIVED,
            "",
            "habit_archived",
            buildJsonObject { put("habit_id", habitId) },
        )
    }

    private fun applyState(
        h: Habit,
        state: HabitLifecycle,
        pausedUntil: String,
        eventType: String,
        properties: JsonObject,
    ) {
        val stamp = now()
        val updated = h.copy(state = state, pausedUntil = pausedUntil, updatedAt = stamp)
        val persisted = commitActivity(eventType, h.id, properties, occurredAt = stamp) {
            hq.setState(state.id, pausedUntil, stamp, h.id)
        }
        if (!persisted) return
        val index = _habits.indexOfFirst { it.id == h.id }
        if (index >= 0) _habits[index] = updated
        revision++
    }

    /**
     * Removes a habit. The row is tombstoned rather than dropped so the delete still
     * reaches the backend after a restart, and so [restore] — the Undo on the delete
     * snackbar — is one UPDATE rather than a re-insert of everything.
     *
     * Entries and notes are left alone: nothing lists an orphan, and keeping them is what
     * makes the undo lossless.
     */
    fun delete(habitId: String) {
        if (byId(habitId) == null) return
        val stamp = now()
        val persisted = commitActivity(
            "habit_deleted",
            habitId,
            buildJsonObject { put("habit_id", habitId) },
            occurredAt = stamp,
        ) { hq.softDelete(stamp, habitId) }
        if (!persisted) return
        _habits.removeAll { it.id == habitId }
        revision++
    }

    fun restore(habitId: String) {
        if (byId(habitId) != null) return
        val row = runCatching { hq.selectById(habitId).executeAsOneOrNull() }.getOrNull()
            ?: return
        if (row.deleted != 1L) return
        val stamp = now()
        val persisted = commitActivity(
            "habit_restored",
            habitId,
            buildJsonObject { put("habit_id", habitId) },
            occurredAt = stamp,
        ) { hq.restore(stamp, habitId) }
        if (!persisted) return
        _habits.add(toHabit(row).copy(updatedAt = stamp))
        sortInPlace()
        revision++
    }

    /**
     * Commits a drag-to-reorder. Writes every habit's index in one pass: last-writer-wins
     * on the list as a unit is the only sane rule, because two devices that each dragged a
     * different row have no meaningful merge and inventing a third order is worse.
     */
    fun reorder(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        if (orderedIds.withIndex().all { (index, id) -> byId(id)?.sortIndex == index }) return
        val stamp = now()
        val updated = _habits.associateBy { it.id }.toMutableMap()
        orderedIds.forEachIndexed { index, id ->
            updated[id]?.let { updated[id] = it.copy(sortIndex = index, updatedAt = stamp) }
        }
        val persisted = commitActivity(
            "habits_reordered",
            "habit-order",
            buildJsonObject { put("count", orderedIds.size) },
            occurredAt = stamp,
        ) {
            orderedIds.forEachIndexed { index, id ->
                hq.setSortIndex(index.toLong(), stamp, id)
            }
        }
        if (!persisted) return
        _habits.indices.forEach { index ->
            updated[_habits[index].id]?.let { _habits[index] = it }
        }
        sortInPlace()
        revision++
    }

    // ── entry writes ─────────────────────────────────────────────────────────

    /** What a tap on the completion control did, so the caller can pick copy and haptics. */
    enum class ToggleOutcome { COMPLETED, PROGRESSED, UNDONE }

    /**
     * The completion tap.
     *
     * A count or duration goal increments first and completes on the last step, which is
     * what makes "5 of 8 glasses" a single control rather than a separate editor. Tapping a
     * completed habit undoes it entirely — including the accumulated progress, because
     * leaving 8/8 behind on an undone habit is a state the user cannot then clear.
     */
    fun toggle(habitId: String, dateKey: String = todayKey()): ToggleOutcome {
        val habit = byId(habitId) ?: return ToggleOutcome.UNDONE
        val id = habitEntryId(habitId, dateKey)
        val existing = _entries[id]
        val stamp = now()

        if (existing?.status == HabitStatus.DONE) {
            val persisted = commitActivity(
                "habit_uncompleted",
                id,
                buildJsonObject {
                    put("habit_id", habitId)
                    put("date", dateKey)
                    put("category", habit.categoryId)
                    put("title", habit.name)
                },
                occurredAt = stamp,
                localDate = dateKey,
            ) { eq.remove(id) }
            check(persisted) { "Could not persist habit completion undo and ActivityEvent" }
            removeEntryFromMemory(id)
            return ToggleOutcome.UNDONE
        }

        val stepped = (existing?.value ?: 0) + 1
        val completing = habit.goalType == HabitGoalType.CHECK || stepped >= habit.goalTarget
        val entry = HabitEntry(
            id = id,
            habitId = habitId,
            dateKey = dateKey,
            status = if (completing) HabitStatus.DONE else HabitStatus.PARTIAL,
            value = if (completing) habit.goalTarget else stepped,
            target = habit.goalTarget,
            completedAt = if (completing) stamp else 0,
            createdAt = existing?.createdAt ?: stamp,
            updatedAt = stamp,
        )
        val persisted = commitActivity(
            type = if (completing) "habit_completed" else "habit_progressed",
            entityId = id,
            properties = buildJsonObject {
                put("habit_id", habitId)
                put("date", dateKey)
                put("category", habit.categoryId)
                put("title", habit.name)
                put("value", entry.value)
                put("target", entry.target)
            },
            occurredAt = stamp,
            localDate = dateKey,
        ) { persistEntry(entry) }
        check(persisted) { "Could not persist habit progress and ActivityEvent" }
        writeEntryToMemory(entry)
        return if (completing) ToggleOutcome.COMPLETED else ToggleOutcome.PROGRESSED
    }

    fun skip(habitId: String, reason: String = "", dateKey: String = todayKey()) {
        val habit = byId(habitId) ?: return
        val current = _entries[habitEntryId(habitId, dateKey)]
        if (current?.status == HabitStatus.SKIPPED && current.skipReason == reason) return
        val stamp = now()
        val entry = HabitEntry(
            id = habitEntryId(habitId, dateKey),
            habitId = habitId,
            dateKey = dateKey,
            status = HabitStatus.SKIPPED,
            target = habit.goalTarget,
            skipReason = reason,
            createdAt = current?.createdAt ?: stamp,
            updatedAt = stamp,
        )
        val persisted = commitActivity(
            "habit_skipped",
            entry.id,
            buildJsonObject {
                put("habit_id", habitId)
                put("date", dateKey)
                put("category", habit.categoryId)
                put("title", habit.name)
                put("reason", reason)
            },
            occurredAt = stamp,
            localDate = dateKey,
        ) { persistEntry(entry) }
        if (persisted) writeEntryToMemory(entry)
    }

    /** Sets an explicit value on a count/duration goal (the detail screen's stepper). */
    fun setProgress(habitId: String, value: Int, dateKey: String = todayKey()) {
        val habit = byId(habitId) ?: return
        val id = habitEntryId(habitId, dateKey)
        if (value <= 0) {
            if (_entries[id] == null) return
            val stamp = now()
            val persisted = commitActivity(
                "habit_uncompleted",
                id,
                buildJsonObject {
                    put("habit_id", habitId)
                    put("date", dateKey)
                    put("category", habit.categoryId)
                    put("title", habit.name)
                },
                occurredAt = stamp,
                localDate = dateKey,
            ) { eq.remove(id) }
            if (persisted) removeEntryFromMemory(id)
            return
        }
        val stamp = now()
        val completing = value >= habit.goalTarget
        val entry = HabitEntry(
            id = id, habitId = habitId, dateKey = dateKey,
            status = if (completing) HabitStatus.DONE else HabitStatus.PARTIAL,
            value = if (completing) habit.goalTarget else value,
            target = habit.goalTarget,
            completedAt = if (completing) stamp else 0,
            createdAt = _entries[id]?.createdAt ?: stamp,
            updatedAt = stamp,
        )
        val current = _entries[id]
        if (
            current?.status == entry.status &&
            current.value == entry.value &&
            current.target == entry.target
        ) return
        val persisted = commitActivity(
            if (completing) "habit_completed" else "habit_progressed",
            id,
            buildJsonObject {
                put("habit_id", habitId)
                put("date", dateKey)
                put("category", habit.categoryId)
                put("title", habit.name)
                put("value", entry.value)
                put("target", entry.target)
            },
            occurredAt = stamp,
            localDate = dateKey,
        ) { persistEntry(entry) }
        if (persisted) writeEntryToMemory(entry)
    }

    /**
     * Snapshot / restore for the Undo snackbar.
     *
     * The undo has to reverse the local write BEFORE the outbox flushes it, so it operates
     * on the row rather than on a queued op: reverting to [snapshotEntry]'s value produces
     * the same end state whether or not the original already synced.
     */
    fun snapshotEntry(habitId: String, dateKey: String = todayKey()): HabitEntry? =
        _entries[habitEntryId(habitId, dateKey)]

    fun restoreEntry(habitId: String, dateKey: String, snapshot: HabitEntry?) {
        val habit = byId(habitId) ?: return
        val id = habitEntryId(habitId, dateKey)
        val stamp = now()
        val restored = snapshot?.copy(updatedAt = stamp)
        val current = _entries[id]
        if (
            (current == null && restored == null) ||
            (current != null && restored != null &&
                current.copy(updatedAt = restored.updatedAt) == restored)
        ) return
        val eventType = when (restored?.status) {
            HabitStatus.DONE -> "habit_completed"
            HabitStatus.PARTIAL -> "habit_progressed"
            HabitStatus.SKIPPED -> "habit_skipped"
            null -> "habit_uncompleted"
        }
        val persisted = commitActivity(
            eventType,
            id,
            buildJsonObject {
                put("habit_id", habitId)
                put("date", dateKey)
                put("category", habit.categoryId)
                put("title", habit.name)
                restored?.let {
                    put("value", it.value)
                    put("target", it.target)
                    if (it.skipReason.isNotBlank()) put("reason", it.skipReason)
                }
            },
            occurredAt = stamp,
            localDate = dateKey,
        ) {
            if (restored == null) eq.remove(id) else persistEntry(restored)
        }
        if (!persisted) return
        if (restored == null) removeEntryFromMemory(id) else writeEntryToMemory(restored)
    }

    private fun writeEntry(entry: HabitEntry): Boolean {
        val persisted = commitSourceWrite { persistEntry(entry) }
        if (persisted) writeEntryToMemory(entry)
        return persisted
    }

    private fun persistEntry(entry: HabitEntry) {
        eq.upsert(
            id = entry.id,
            habitId = entry.habitId,
            dateKey = entry.dateKey,
            status = entry.status.id,
            value_ = entry.value.toLong(),
            target = entry.target.toLong(),
            skipReason = entry.skipReason,
            completedAt = entry.completedAt,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
        )
    }

    private fun writeEntryToMemory(entry: HabitEntry) {
        _entries[entry.id] = entry
        revision++
    }

    private fun removeEntryFromMemory(id: String) {
        _entries.remove(id)
        revision++
    }

    // ── notes ────────────────────────────────────────────────────────────────

    fun addNote(habitId: String, text: String, dateKey: String = todayKey()): HabitNote? {
        val body = text.trim()
        if (body.isEmpty() || byId(habitId) == null) return null
        val stamp = now()
        val note = HabitNote(newId(), habitId, dateKey, body, stamp, stamp)
        val persisted = commitActivity(
            "note_added",
            note.id,
            buildJsonObject {
                put("habit_id", habitId)
                put("date", dateKey)
            },
            occurredAt = stamp,
            localDate = dateKey,
        ) { nq.upsert(note.id, habitId, dateKey, body, stamp, stamp, 0L) }
        if (!persisted) return null
        _notes.add(0, note)
        revision++
        return note
    }

    fun deleteNote(noteId: String) {
        val note = _notes.firstOrNull { it.id == noteId } ?: return
        val stamp = now()
        val persisted = commitActivity(
            "note_deleted",
            noteId,
            buildJsonObject {
                put("habit_id", note.habitId)
                put("date", note.dateKey)
            },
            occurredAt = stamp,
            localDate = note.dateKey,
        ) { nq.softDelete(stamp, noteId) }
        if (!persisted) return
        _notes.removeAll { it.id == noteId }
        revision++
    }

    // ── achievements ─────────────────────────────────────────────────────────

    /**
     * Scores the catalogue and persists anything that just crossed the line.
     *
     * Only transitions are written: rewriting twelve rows on every visit would queue twelve
     * sync events and hand every other device a pointless delta pull. Returns the newly
     * unlocked badges so the screen can play the unlock celebration exactly once.
     */
    fun evaluateAchievements(): Pair<List<HabitAchievementView>, List<HabitAchievementView>> {
        val stats = statsAll()
        val yearRolls = rolls(HABIT_WINDOW_YEAR)
        val monthRolls = rolls(HABIT_WINDOW_MONTH)

        var bestStreak = 0
        var morningDone = 0
        var eveningDone = 0
        for (h in _habits) {
            val s = stats[h.id] ?: continue
            if (s.longest > bestStreak) bestStreak = s.longest
            when {
                h.timeLocal.isNotBlank() && h.timeLocal < "08:00" -> morningDone += s.done
                h.timeBucket == "evening" || h.timeBucket == "night" -> eveningDone += s.done
            }
        }

        val facts = HabitAchievementFacts(
            habitCount = _habits.count { it.state != HabitLifecycle.ARCHIVED },
            bestStreak = bestStreak,
            perfectRun = HabitMath.longestPerfectRun(yearRolls),
            monthRate = HabitMath.rate(monthRolls),
            morningDone = morningDone,
            eveningDone = eveningDone,
            daysSinceMiss = HabitMath.daysSinceMiss(yearRolls),
        )

        val stored = achievementState()
        val views = HabitAchievements.evaluate(facts, stored, now())
        val fresh = HabitAchievements.newlyUnlocked(views, stored)
        val committed = mutableListOf<HabitAchievementView>()
        fresh.forEach { v ->
            val state = HabitAchievementState(v.id, v.unlockedAt, 1f, now())
            val persisted = commitActivity(
                "achievement_unlocked",
                state.id,
                buildJsonObject { put("achievement_id", v.id) },
                occurredAt = state.updatedAt,
            ) {
                aq.upsert(state.id, state.unlockedAt, state.progress.toDouble(), state.updatedAt)
            }
            if (!persisted) return@forEach
            _achievements[v.id] = state
            committed += v
        }
        if (committed.isNotEmpty()) revision++
        return views to committed
    }

    // ── analytics passthroughs ───────────────────────────────────────────────

    fun recordScreen(name: String, habitId: String? = null) {
        activity?.record(FEATURE, name, buildJsonObject { habitId?.let { put("habit_id", it) } })
    }

    // ── plumbing ─────────────────────────────────────────────────────────────

    private fun commitHabitUpdate(
        updated: Habit,
        type: String,
        properties: JsonObject,
    ): Boolean {
        val persisted = commitActivity(
            type = type,
            entityId = updated.id,
            properties = properties,
            occurredAt = updated.updatedAt,
        ) { persist(updated) }
        if (!persisted) return false
        val index = _habits.indexOfFirst { it.id == updated.id }
        if (index >= 0) _habits[index] = updated else _habits.add(updated)
        sortInPlace()
        revision++
        return true
    }

    /** Commits a derived projection write without manufacturing a second user activity. */
    private fun commitSourceWrite(write: () -> Unit): Boolean = runCatching {
        db.transaction { write() }
    }.isSuccess

    /** Source write first, immutable activity last, one outer transaction for both. */
    private fun commitActivity(
        type: String,
        entityId: String,
        properties: JsonObject = buildJsonObject {},
        source: String = "user",
        occurredAt: Long? = null,
        localDate: String? = null,
        write: () -> Unit,
    ): Boolean = runCatching {
        db.transaction {
            write()
            val log = activity ?: return@transaction
            check(
                log.record(
                    feature = FEATURE,
                    type = type,
                    properties = properties,
                    entityId = entityId,
                    source = source,
                    occurredAtOverride = occurredAt,
                    localDateOverride = localDate,
                ),
            ) { "Could not append habit ActivityEvent" }
        }
    }.isSuccess

    private fun sortInPlace() {
        val sorted = _habits.sortedWith(
            compareBy<Habit> { it.sortIndex }.thenBy { it.createdAt }.thenBy { it.id }
        )
        _habits.clear()
        _habits.addAll(sorted)
    }

    private fun persist(h: Habit) {
        hq.upsert(
            id = h.id,
            name = h.name,
            description = h.description,
            categoryId = h.categoryId,
            colorKey = h.colorKey,
            icon = h.icon,
            slot = h.slot.id,
            timeLocal = h.timeLocal,
            reminderEnabled = if (h.reminderEnabled) 1L else 0L,
            reminderTime = h.reminderTime,
            smartTiming = if (h.smartTiming) 1L else 0L,
            frequency = h.frequency.id,
            weekdays = encodeInts(h.weekdays),
            difficulty = h.difficulty.id,
            goalType = h.goalType.id,
            goalTarget = h.goalTarget.toLong(),
            goalUnit = h.goalUnit,
            motivation = h.motivation,
            state = h.state.id,
            pausedUntil = h.pausedUntil,
            sortIndex = h.sortIndex.toLong(),
            createdAt = h.createdAt,
            updatedAt = h.updatedAt,
            deleted = 0L,
        )
    }

    private fun toHabit(row: com.example.tafakkurai.db.Habit) = Habit(
        id = row.id,
        name = row.name,
        description = row.description,
        categoryId = row.categoryId,
        colorKey = row.colorKey,
        icon = row.icon,
        slot = HabitSlot.from(row.slot),
        timeLocal = row.timeLocal,
        reminderEnabled = row.reminderEnabled == 1L,
        reminderTime = row.reminderTime,
        smartTiming = row.smartTiming == 1L,
        frequency = HabitFrequency.from(row.frequency),
        weekdays = decodeInts(row.weekdays),
        difficulty = HabitDifficulty.from(row.difficulty),
        goalType = HabitGoalType.from(row.goalType),
        goalTarget = row.goalTarget.toInt(),
        goalUnit = row.goalUnit,
        motivation = row.motivation,
        state = HabitLifecycle.from(row.state),
        pausedUntil = row.pausedUntil,
        sortIndex = row.sortIndex.toInt(),
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    /** Null for a row whose status this build does not recognise — a newer client wrote it. */
    private fun toEntry(row: com.example.tafakkurai.db.HabitEntry): HabitEntry? {
        val status = HabitStatus.from(row.status) ?: return null
        return HabitEntry(
            id = row.id,
            habitId = row.habitId,
            dateKey = row.dateKey,
            status = status,
            value = row.value_.toInt(),
            target = row.target.toInt(),
            skipReason = row.skipReason,
            completedAt = row.completedAt,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
        )
    }

    private fun toNote(row: com.example.tafakkurai.db.HabitNote) =
        HabitNote(row.id, row.habitId, row.dateKey, row.body, row.createdAt, row.updatedAt)

    private companion object {
        /** Sync feature label. Must match `habits.Feature` on the backend. */
        const val FEATURE = "habits"
    }
}

private fun encodeInts(v: List<Int>): String =
    syncJson.encodeToString(ListSerializer(Int.serializer()), v)

private fun decodeInts(raw: String): List<Int> =
    runCatching { syncJson.decodeFromString(ListSerializer(Int.serializer()), raw) }.getOrDefault(emptyList())

/** Short, sortable-ish, collision-safe enough for a per-device id. */
private fun randomHabitId(): String {
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "hb-" + buildString { repeat(20) { append(chars[Random.nextInt(chars.length)]) } }
}

/** Stable, backend-safe identity for habits derived from another feature. */
private fun connectedHabitId(connectionKey: String): String =
    "connected-" + connectionKey.lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .trim('-')
        .take(48)
