package com.example.tafakkurai.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Interface text, one table per language.
 *
 * Keyed maps rather than a data class of a thousand properties: the app has well over a
 * thousand strings, and a map lets [StringsTest] prove mechanically that every key exists
 * in every language — which is the only way "nothing is left untranslated" stays true as
 * screens are added.
 *
 * A missing key falls back to English rather than crashing or drawing an empty label, so a
 * half-finished translation degrades to readable text instead of a blank screen.
 */
class Strings(
    val language: Language,
    private val table: Map<String, String>,
) {
    operator fun get(key: String): String = table[key] ?: EN[key] ?: key

    /**
     * Substitutes positional placeholders — `{0}`, `{1}` — because word order differs
     * between these four languages and concatenation would force English order on all of
     * them ("3 of 5 prayers" vs "5 namozdan 3 tasi").
     */
    fun format(key: String, vararg args: Any?): String {
        var text = get(key)
        args.forEachIndexed { index, arg -> text = text.replace("{$index}", arg.toString()) }
        return text
    }

    companion object {
        fun of(language: Language): Strings = Strings(language, tableFor(language))

        private fun tableFor(language: Language): Map<String, String> = when (language) {
            Language.ENGLISH -> EN
            Language.RUSSIAN -> RU
            Language.UZBEK_LATIN -> UZ_LATN
            Language.UZBEK_CYRILLIC -> UZ_CYRL
        }
    }
}

/**
 * Current language's strings. Static because changing language re-composes the whole tree
 * anyway — every visible label changes at once.
 */
val LocalStrings: ProvidableCompositionLocal<Strings> =
    staticCompositionLocalOf { Strings.of(Language.DEFAULT) }

/** `tr("today.greeting")` — the one call site screens use. */
@Composable
@ReadOnlyComposable
fun tr(key: String): String = LocalStrings.current[key]

@Composable
@ReadOnlyComposable
fun tr(key: String, vararg args: Any?): String = LocalStrings.current.format(key, *args)
