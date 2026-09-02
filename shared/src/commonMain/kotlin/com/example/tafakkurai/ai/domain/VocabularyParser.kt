package com.example.tafakkurai.ai.domain

data class VocabularyCardDraft(
    val front: String,
    val back: String,
    val theme: Int,
)

data class VocabularyParseResult(
    val cards: List<VocabularyCardDraft>,
    val foundThemes: Set<Int>,
    val missingThemes: Set<Int>,
)

/**
 * Extracts explicit front/back pairs from a user-supplied, theme-labelled source.
 * It never guesses definitions from prose: a line must contain a supported separator,
 * and it must occur under an explicit Theme/Unit/Topic/Lesson heading.
 */
fun parseVocabularySource(
    content: String,
    fromTheme: Int,
    toTheme: Int,
): VocabularyParseResult {
    val lower = minOf(fromTheme, toTheme).coerceAtLeast(1)
    val upper = maxOf(fromTheme, toTheme).coerceAtMost(MAX_THEME)
    val requested = if (lower <= upper) (lower..upper).toSet() else emptySet()
    val found = linkedSetOf<Int>()
    val cards = linkedMapOf<String, VocabularyCardDraft>()
    var currentTheme: Int? = null

    content.lineSequence().take(MAX_SOURCE_LINES).forEach { raw ->
        val line = raw.trim()
        if (line.isBlank()) return@forEach
        val heading = THEME_HEADING.find(line)
        if (heading != null) {
            currentTheme = heading.groupValues[2].toIntOrNull()
            return@forEach
        }
        val theme = currentTheme?.takeIf { it in requested } ?: return@forEach
        val pair = explicitPair(line) ?: return@forEach
        // A heading alone is not evidence that the requested vocabulary is present.
        // Count a theme only after at least one explicit, valid front/back pair.
        found += theme
        val key = pair.first.lowercase().replace(WHITESPACE, " ")
        if (key !in cards) cards[key] = VocabularyCardDraft(pair.first, pair.second, theme)
    }

    return VocabularyParseResult(
        cards = cards.values.toList(),
        foundThemes = found,
        missingThemes = requested - found,
    )
}

private fun explicitPair(line: String): Pair<String, String>? {
    val cleaned = line.replace(BULLET_PREFIX, "").trim()
    val separator = PAIR_SEPARATORS.mapNotNull { token ->
        cleaned.indexOf(token).takeIf { it > 0 }?.let { index -> index to token }
    }.minByOrNull { it.first } ?: return null
    val front = cleaned.substring(0, separator.first).trim()
    val back = cleaned.substring(separator.first + separator.second.length).trim()
    if (front.isBlank() || back.isBlank()) return null
    if (front.length > MAX_FRONT || back.length > MAX_BACK) return null
    return front to back
}

private const val MAX_THEME = 200
private const val MAX_SOURCE_LINES = 100_000
private const val MAX_FRONT = 160
private const val MAX_BACK = 600
private val THEME_HEADING = Regex(
    "^(theme|unit|topic|lesson|mavzu|мавзу|boʻlim|бўлим|тема|урок)\\s*[:#.-]?\\s*(\\d{1,3})(?:\\D.*)?$",
    RegexOption.IGNORE_CASE,
)
private val BULLET_PREFIX = Regex("^(?:[-•*]|\\d+[.)])\\s*")
private val WHITESPACE = Regex("\\s+")
private val PAIR_SEPARATORS = listOf("\t", " — ", " – ", " - ", " = ", ": ")
