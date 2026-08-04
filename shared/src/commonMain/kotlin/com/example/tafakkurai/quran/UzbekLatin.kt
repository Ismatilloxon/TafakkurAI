package com.example.tafakkurai.quran

/**
 * Uzbek Cyrillic → Latin transliteration.
 *
 * There is no separate Latin-script Uzbek tafsir on the mushaf API — only Muhammad Sodik
 * Muhammad Yusuf's translation, published in Cyrillic. Rather than ship a second,
 * unverified translation, the Latin option is produced mechanically from the same text:
 * the mapping is one-to-one per the 1995 Uzbek Latin alphabet, so no word is ever
 * reinterpreted, only respelled.
 *
 * `ʻ` (U+02BB) and `ʼ` (U+02BC) are the official modifier letters for oʻ/gʻ and the
 * tutuq belgisi — not apostrophes.
 */
internal object UzbekLatin {

    /** Two-or-more-character sources must be tried before single characters. */
    private val digraphs: List<Pair<String, String>> = listOf(
        "ъе" to "ye",
    )

    private val letters: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "yo", 'ж' to "j", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sh", 'ъ' to "ʼ", 'ы' to "i", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya",
        // Letters specific to the Uzbek Cyrillic alphabet.
        'ў' to "oʻ", 'қ' to "q", 'ғ' to "gʻ", 'ҳ' to "h",
    )

    fun transliterate(text: String): String {
        val out = StringBuilder(text.length + text.length / 4)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val lower = ch.lowercaseChar()

            val digraph = digraphs.firstOrNull { (from, _) ->
                text.regionMatches(i, from, 0, from.length, ignoreCase = true)
            }
            if (digraph != null) {
                out.append(match(digraph.second, ch.isUpperCase()))
                i += digraph.first.length
                continue
            }

            val mapped = letters[lower]
            if (mapped == null) {
                out.append(ch) // Latin letters, digits, punctuation and Arabic pass through.
            } else {
                // Word-initial "е" is /ye/ in Uzbek — "Ер" is "Yer", but "келди" is "keldi".
                val value = if (lower == 'е' && startsWord(text, i)) "ye" else mapped
                out.append(match(value, ch.isUpperCase()))
            }
            i++
        }
        return out.toString()
    }

    private fun startsWord(text: String, index: Int): Boolean {
        val previous = text.getOrNull(index - 1) ?: return true
        return !previous.isLetter()
    }

    /** Mirrors the source's case: "Ў" becomes "Oʻ", not "oʻ" or "Oʻ". */
    private fun match(value: String, upper: Boolean): String =
        if (!upper || value.isEmpty()) value else value.replaceFirstChar { it.uppercaseChar() }
}
