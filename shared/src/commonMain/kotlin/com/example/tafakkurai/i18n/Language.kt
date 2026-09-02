package com.example.tafakkurai.i18n

import com.example.tafakkurai.AppPrefs
import com.example.tafakkurai.spiritual.PrefPersistence
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The languages the app speaks.
 *
 * The same four the Qurʼan translation picker offers, and for the same reason: Uzbek is
 * written in both scripts by people who read only one of them, so "Uzbek" alone would
 * leave half the audience guessing.
 *
 * [nativeName] is deliberately written in the language itself — someone who cannot read
 * the current interface language must still recognise their own.
 */
enum class Language(
    val code: String,
    val nativeName: String,
    /** Catalog-backed explanatory label shown under [nativeName]. */
    val detailKey: String,
) {
    UZBEK_LATIN("uz-Latn", "Oʻzbekcha", "lang.option.uz_latn.detail"),
    UZBEK_CYRILLIC("uz-Cyrl", "Ўзбекча", "lang.option.uz_cyrl.detail"),
    RUSSIAN("ru", "Русский", "lang.option.ru.detail"),
    ENGLISH("en", "English", "lang.option.en.detail");

    companion object {
        val DEFAULT: Language = UZBEK_LATIN

        fun from(code: String?): Language = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

/**
 * The selected interface language, persisted through the same preference store everything
 * else uses.
 *
 * [hasChosen] is what the first-run gate reads: it is false until the user picks, which is
 * how the language screen knows to appear before onboarding rather than after it. Choosing
 * is a deliberate act, so the default is never silently recorded as a choice.
 */
class LanguageStore(private val prefs: PrefPersistence) {

    var current by mutableStateOf(Language.DEFAULT)
        private set

    var hasChosen by mutableStateOf(false)
        private set

    init { reload() }

    fun reload() {
        val stored = prefs.get(AppPrefs.LANGUAGE)
        hasChosen = stored != null
        current = Language.from(stored)
    }

    fun choose(language: Language) {
        current = language
        hasChosen = true
        prefs.put(AppPrefs.LANGUAGE, language.code)
    }
}
