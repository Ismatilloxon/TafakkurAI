package com.example.tafakkurai.ai.domain

import com.example.tafakkurai.quran.SURAH_CATALOG
import com.example.tafakkurai.tasks.domain.TaskPriority

/** Deterministic on-device command parser. It never invokes a network model. */
class LocalActionParser {
    fun parse(text: String, today: String, tomorrow: String): ParsedLocalIntent {
        val raw = text.trim()
        if (raw.isBlank()) return ParsedLocalIntent.NotLocalAction
        val normalized = normalize(raw)
        if (!isExplicitCommand(normalized)) return ParsedLocalIntent.NotLocalAction

        return when {
            FLASHCARD_WORD.containsMatchIn(normalized) -> parseFlashcards(raw, normalized)
            TASK_WORD.containsMatchIn(normalized) -> parseTask(raw, normalized, today, tomorrow)
            HABIT_WORD.containsMatchIn(normalized) -> parseHabit(raw, normalized)
            GRATITUDE_WORD.containsMatchIn(normalized) -> parseGratitude(raw, normalized)
            HIFZ_WORD.containsMatchIn(normalized) -> parseHifz(normalized)
            ACTIVITY_WORD.containsMatchIn(normalized) -> parseActivity(raw, normalized, today)
            else -> ParsedLocalIntent.Invalid(LocalActionIssue.UNSUPPORTED_ACTION)
        }
    }

    private fun parseTask(
        raw: String,
        normalized: String,
        today: String,
        tomorrow: String,
    ): ParsedLocalIntent {
        val title = payloadAfter(raw, TASK_WORD)
            .replace(TIME_METADATA, " ")
            .replace(DATE_METADATA, " ")
            .replace(PRIORITY_METADATA, " ")
            .replace(WHITESPACE, " ").trim(' ', '.', ',', ':', '-')
        if (title.isBlank()) return ParsedLocalIntent.Invalid(LocalActionIssue.MISSING_TITLE)
        val dueDate = if (TOMORROW_WORD.containsMatchIn(normalized)) tomorrow else today
        val dueTime = CLOCK.find(normalized)?.groupValues?.get(1).orEmpty()
        val priority = when {
            HIGH_PRIORITY.containsMatchIn(normalized) -> TaskPriority.HIGH
            LOW_PRIORITY.containsMatchIn(normalized) -> TaskPriority.LOW
            else -> TaskPriority.MEDIUM
        }
        return ParsedLocalIntent.Action(
            ProposedAction.CreateTask(title.take(200), dueDate, dueTime, priority),
        )
    }

    private fun parseActivity(raw: String, normalized: String, today: String): ParsedLocalIntent {
        val title = payloadAfter(raw, ACTIVITY_WORD)
            .replace(DURATION_METADATA, " ")
            .replace(DATE_METADATA, " ")
            .replace(WHITESPACE, " ").trim(' ', '.', ',', ':', '-')
        if (title.isBlank()) return ParsedLocalIntent.Invalid(LocalActionIssue.MISSING_TITLE)
        val minutes = DURATION.find(normalized)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 1_440)
        val category = when {
            PHYSICAL_WORD.containsMatchIn(normalized) -> "health"
            SOCIAL_WORD.containsMatchIn(normalized) -> "social"
            LEARNING_WORD.containsMatchIn(normalized) -> "study"
            else -> "personal"
        }
        return ParsedLocalIntent.Action(
            ProposedAction.LogActivity(title.take(200), category, minutes, today),
        )
    }

    private fun parseHabit(raw: String, normalized: String): ParsedLocalIntent {
        val title = payloadAfter(raw, HABIT_WORD).replace(WHITESPACE, " ")
            .trim(' ', '.', ',', ':', '-')
        if (title.isBlank()) return ParsedLocalIntent.Invalid(LocalActionIssue.MISSING_TITLE)
        val category = when {
            PHYSICAL_WORD.containsMatchIn(normalized) -> "health"
            SOCIAL_WORD.containsMatchIn(normalized) -> "family"
            LEARNING_WORD.containsMatchIn(normalized) -> "study"
            else -> "custom"
        }
        return ParsedLocalIntent.Action(ProposedAction.CreateHabit(title.take(160), category))
    }

    private fun parseGratitude(raw: String, normalized: String): ParsedLocalIntent {
        val text = payloadAfter(raw, GRATITUDE_WORD)
            .replace(Regex("^(?:that|for|uchun|за|учун)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
        if (text.isBlank()) return ParsedLocalIntent.Invalid(LocalActionIssue.MISSING_TITLE)
        return ParsedLocalIntent.Action(ProposedAction.SaveGratitude(text.take(4_000)))
    }

    private fun parseHifz(normalized: String): ParsedLocalIntent {
        val number = SURAH_NUMBER.find(normalized)?.groupValues?.get(1)?.toIntOrNull()
        val surah = number?.let { n -> SURAH_CATALOG.firstOrNull { it.number == n } }
            ?: SURAH_CATALOG.firstOrNull { candidate ->
                val names = listOf(candidate.nameEnglish, candidate.nameTranslation)
                    .map(::normalizeSurahName)
                names.any { it.length >= 3 && normalized.contains(it) }
            }
            ?: return ParsedLocalIntent.Invalid(LocalActionIssue.UNKNOWN_SURAH)
        return ParsedLocalIntent.Action(ProposedAction.StartHifz(surah.number, surah.nameEnglish))
    }

    private fun parseFlashcards(raw: String, normalized: String): ParsedLocalIntent {
        val source = when {
            normalized.contains("destination b2") -> "Destination B2"
            else -> SOURCE_TITLE.find(raw)?.groupValues?.get(1)?.trim(' ', '.', ',', ':', '-')
        }.orEmpty().take(200)
        if (source.isBlank()) return ParsedLocalIntent.Invalid(LocalActionIssue.MISSING_SOURCE)
        val match = THEME_RANGE_AFTER.find(normalized) ?: THEME_RANGE_BEFORE.find(normalized)
            ?: return ParsedLocalIntent.Invalid(LocalActionIssue.MISSING_SCOPE)
        val from = match.groupValues[1].toIntOrNull()
        val to = match.groupValues[2].toIntOrNull()
        if (from == null || to == null || from !in 1..200 || to !in 1..200) {
            return ParsedLocalIntent.Invalid(LocalActionIssue.MISSING_SCOPE)
        }
        return ParsedLocalIntent.Action(ProposedAction.CreateFlashcards(source, minOf(from, to), maxOf(from, to)))
    }

    private fun payloadAfter(raw: String, noun: Regex): String {
        val match = noun.find(normalize(raw)) ?: return ""
        // All supported command words and nouns are ASCII-length preserving after
        // normalization except modifier apostrophes, which are one code point either way.
        return raw.substring(match.range.last + 1)
            .trim()
            .replace(ACTION_AFTER_NOUN, "")
            .trim()
    }

    /**
     * Accepts both verb-first commands ("add a task") and common noun-first commands
     * ("vazifa qoʻsh" / "задачу добавь"). A request such as "explain how to create
     * tasks" stays conversational because neither command shape starts the sentence.
     */
    private fun isExplicitCommand(normalized: String): Boolean {
        if (ACTION_PREFIX.containsMatchIn(normalized)) return true
        val nouns = listOf(TASK_WORD, ACTIVITY_WORD, HABIT_WORD, GRATITUDE_WORD, HIFZ_WORD, FLASHCARD_WORD)
        return nouns.any { noun ->
            val match = noun.find(normalized)
            match?.range?.first == 0 && ACTION_AFTER_NOUN.containsMatchIn(
                normalized.substring(match.range.last + 1).trimStart(),
            )
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val ACTION_VERB_PATTERN =
            "(?:(?:add|create|make|start|record|log|import|save)\\b|" +
                "(?:qoʻsh|qosh|yarat|boshl|qayd|saqla)\\p{L}*|" +
                "(?:қўш|ярат|бошла|қайд|сақла|добав|созда|начн|запиш|сохран)\\p{L}*)"
        val ACTION_PREFIX = Regex(
            "^(?:please\\s+)?$ACTION_VERB_PATTERN",
            RegexOption.IGNORE_CASE,
        )
        val ACTION_AFTER_NOUN = Regex(
            "^(?:(?:ni|ни)\\s+)?(?:please\\s+)?$ACTION_VERB_PATTERN\\s*[:,-]?\\s*",
            RegexOption.IGNORE_CASE,
        )
        val TASK_WORD = Regex("(?:tasks?|todo|vazifa(?:ni)?|вазифа(?:ни)?|задач[ауи]?)", RegexOption.IGNORE_CASE)
        val ACTIVITY_WORD = Regex("(?:activit(?:y|ies)|faoliyat(?:ni)?|фаолият(?:ни)?|активност[ьи]?)", RegexOption.IGNORE_CASE)
        val HABIT_WORD = Regex("(?:habits?|odat(?:ni)?|одат(?:ни)?|привычк[ауи]?)", RegexOption.IGNORE_CASE)
        val GRATITUDE_WORD = Regex("(?:gratitude|thankfulness|shukr(?:onalik)?|шукр(?:оналик)?|благодарност[ьи]?)", RegexOption.IGNORE_CASE)
        val HIFZ_WORD = Regex("(?:hifz|hafiz|ҳифз|хифз|surah?|quran|yodla|ёдла|сур[аы]|қуръон)", RegexOption.IGNORE_CASE)
        val FLASHCARD_WORD = Regex("(?:flashcards?|vocabulary cards?|kartochk\\p{L}*|карточк\\p{L}*)", RegexOption.IGNORE_CASE)
        val CLOCK = Regex("(?:at|soat|соат|в)?\\s*((?:[01]\\d|2[0-3]):[0-5]\\d)")
        val TIME_METADATA = Regex(
            "(?:at|soat|соат|в)?\\s*(?:[01]\\d|2[0-3]):[0-5]\\d(?:\\s*(?:da|да))?",
            RegexOption.IGNORE_CASE,
        )
        val DATE_METADATA = Regex("\\b(?:today|tomorrow|bugun|ertaga|бугун|эртага|сегодня|завтра)\\b", RegexOption.IGNORE_CASE)
        val PRIORITY_METADATA = Regex("\\b(?:high|medium|low|yuqori|oʻrta|past|юқори|ўрта|паст|высокий|средний|низкий)(?:\\s+priority)?\\b", RegexOption.IGNORE_CASE)
        val TOMORROW_WORD = Regex("(?:tomorrow|ertaga|эртага|завтра)", RegexOption.IGNORE_CASE)
        val HIGH_PRIORITY = Regex("(?:high|yuqori|юқори|высок)", RegexOption.IGNORE_CASE)
        val LOW_PRIORITY = Regex("(?:low|past|паст|низк)", RegexOption.IGNORE_CASE)
        val DURATION = Regex("(\\d{1,4})\\s*(?:minutes?|mins?|minut|дақиқа|минут)", RegexOption.IGNORE_CASE)
        val DURATION_METADATA = Regex("\\d{1,4}\\s*(?:minutes?|mins?|minut|дақиқа|минут)", RegexOption.IGNORE_CASE)
        val PHYSICAL_WORD = Regex("(?:walk|run|gym|workout|sport|yur|yugur|mashq|юриш|югур|машқ|ходьб|бег|спорт)", RegexOption.IGNORE_CASE)
        val SOCIAL_WORD = Regex("(?:family|friend|call|meet|oila|doʻst|қўнғироқ|оила|дўст|семь|друг|позвон)", RegexOption.IGNORE_CASE)
        val LEARNING_WORD = Regex("(?:read|study|learn|book|oʻqi|oʻrgan|ўқи|ўрган|читать|учить|книг)", RegexOption.IGNORE_CASE)
        val SURAH_NUMBER = Regex("(?:surah?|sura|сур[аы]|сураси)\\s*#?\\s*(\\d{1,3})", RegexOption.IGNORE_CASE)
        val SOURCE_TITLE = Regex(
            "from\\s+(?:the\\s+)?(?:book\\s+)?(.+?)(?=" +
                "\\s+(?:book(?:['’]s)?\\s+)?(?:themes?|units?|topics?|lessons?)|" +
                "\\s+(?:book(?:['’]s)?\\s+)?\\d+\\s*[-–—]\\s*\\d+\\s*(?:themes?|units?|topics?|lessons?)|$)",
            RegexOption.IGNORE_CASE,
        )
        val THEME_RANGE_AFTER = Regex("(?:themes?|units?|topics?|lessons?|mavzu(?:lar)?|мавзу(?:лар)?|бўлим|тем(?:а|ы)?)\\D{0,12}(\\d{1,3})\\s*(?:-|–|—|to|through|dan|дан|до)\\s*(\\d{1,3})", RegexOption.IGNORE_CASE)
        val THEME_RANGE_BEFORE = Regex("(\\d{1,3})\\s*(?:-|–|—|to|through|dan|дан|до)\\s*(\\d{1,3})\\s*(?:themes?|units?|topics?|lessons?|mavzu(?:lar)?|мавзу(?:лар)?|бўлим|тем(?:а|ы)?)", RegexOption.IGNORE_CASE)
    }
}

private fun normalize(value: String): String = value.trim().lowercase()
    .replace('’', 'ʻ').replace('ʼ', 'ʻ').replace('\'', 'ʻ').replace(Regex("\\s+"), " ")

private fun normalizeSurahName(value: String): String = value.lowercase()
    .replace(Regex("[^a-z0-9а-яёқғҳў ]"), "")
    .removePrefix("al ").removePrefix("an ").removePrefix("as ").removePrefix("at ")
    .replace(Regex("\\s+"), " ").trim()
