package com.example.tafakkurai.journal.domain

import com.example.tafakkurai.spiritual.dayNumberOf
import com.example.tafakkurai.spiritual.streakLength

/** Stable prompt identifiers. The words themselves live in the localization catalogs. */
enum class JournalPrompt(val id: String) {
    GRATITUDE("gratitude"),
    PATIENCE("patience"),
    GOOD_DEED("good_deed"),
    LESSON("lesson"),
    PRESENCE("presence"),
    REPAIR("repair"),
    INTENTION("intention");

    val titleKey: String get() = "journal.prompt.$id"

    companion object {
        fun from(id: String?): JournalPrompt = entries.firstOrNull { it.id == id } ?: GRATITUDE
    }
}

/** A small, non-diagnostic mood vocabulary used only as optional reflection context. */
enum class JournalMood(val id: String) {
    CALM("calm"),
    GRATEFUL("grateful"),
    HOPEFUL("hopeful"),
    TESTED("tested");

    val titleKey: String get() = "journal.mood.$id"

    companion object {
        fun from(id: String?): JournalMood = entries.firstOrNull { it.id == id } ?: CALM
    }
}

enum class JournalOrigin(val id: String) {
    USER("user"), ON_DEVICE_AI("on_device_ai"), IMPORT("import");

    companion object {
        fun from(id: String?): JournalOrigin = entries.firstOrNull { it.id == id } ?: USER
    }
}

/** Private source-of-truth record. [prompt] is stable across interface-language changes. */
data class JournalReflection(
    val id: String,
    val prompt: JournalPrompt,
    val mood: JournalMood,
    val body: String,
    val entryDate: String,
    val origin: JournalOrigin = JournalOrigin.USER,
    val createdAt: Long,
    val updatedAt: Long,
)

data class JournalDraft(
    val prompt: JournalPrompt,
    val mood: JournalMood,
    val body: String,
    val origin: JournalOrigin = JournalOrigin.USER,
)

/** Read-only journal statistics. They are derived locally and contain no entry text. */
data class JournalSummary(
    val entryCount: Int = 0,
    val activeDaysInLastSeven: Int = 0,
    val streakDays: Int = 0,
)

interface JournalRepository {
    /** Compose-observable implementations bump this after every successful mutation. */
    val revision: Int
    val entries: List<JournalReflection>
    /** True when the latest SQL read failed. Existing in-memory rows remain read-only. */
    val hasLoadError: Boolean

    fun create(draft: JournalDraft): JournalReflection?
    fun update(id: String, draft: JournalDraft): JournalReflection?
    fun delete(id: String, actionOrigin: JournalOrigin = JournalOrigin.USER): Boolean
    fun refresh(): Boolean
}

/** Validates private manual/AI input before it reaches SQL or an ActivityEvent. */
fun JournalDraft.normalized(): JournalDraft? {
    val safeBody = body.trim().take(MAX_JOURNAL_BODY_CHARS)
    if (safeBody.isBlank()) return null
    return copy(body = safeBody)
}

/** Daily default is deterministic: reopening the screen does not unexpectedly change it. */
fun promptForDate(dateKey: String): JournalPrompt {
    val day = dayNumberOf(dateKey) ?: 0L
    val index = ((day % JournalPrompt.entries.size) + JournalPrompt.entries.size)
        .toInt() % JournalPrompt.entries.size
    return JournalPrompt.entries[index]
}

fun nextPrompt(current: JournalPrompt): JournalPrompt {
    val prompts = JournalPrompt.entries
    return prompts[(prompts.indexOf(current) + 1) % prompts.size]
}

/** Pure filtering kept outside composition so long journals are folded only per query/change. */
fun filterReflections(
    entries: List<JournalReflection>,
    query: String,
    localizedLabel: (String) -> String = { it },
): List<JournalReflection> {
    val needle = query.trim().lowercase()
    val ordered = entries.sortedWith(
        compareByDescending<JournalReflection> { it.entryDate }
            .thenByDescending { it.createdAt }
            .thenByDescending { it.id },
    )
    if (needle.isEmpty()) return ordered
    return ordered.filter { entry ->
        entry.body.lowercase().contains(needle) ||
            entry.mood.id.contains(needle) ||
            entry.prompt.id.replace('_', ' ').contains(needle) ||
            localizedLabel(entry.mood.titleKey).lowercase().contains(needle) ||
            localizedLabel(entry.prompt.titleKey).lowercase().contains(needle)
    }
}

fun journalSummary(entries: List<JournalReflection>, todayKey: String): JournalSummary {
    val today = dayNumberOf(todayKey)
    val activeDays = entries.mapTo(linkedSetOf()) { it.entryDate }
    val recent = if (today == null) 0 else activeDays.count { key ->
        val day = dayNumberOf(key) ?: return@count false
        day in (today - 6)..today
    }
    return JournalSummary(
        entryCount = entries.size,
        activeDaysInLastSeven = recent,
        streakDays = streakLength(activeDays, todayKey),
    )
}

const val MAX_JOURNAL_BODY_CHARS = 20_000
