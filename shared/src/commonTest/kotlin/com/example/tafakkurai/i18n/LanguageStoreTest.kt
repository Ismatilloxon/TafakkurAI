package com.example.tafakkurai.i18n

import com.example.tafakkurai.spiritual.InMemorySpiritualPersistence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguageStoreTest {

    @Test
    fun fresh_install_requires_an_explicit_language_choice() {
        val store = LanguageStore(InMemorySpiritualPersistence())

        assertFalse(store.hasChosen)
        assertEquals(Language.DEFAULT, store.current)
    }

    @Test
    fun choice_is_persisted_for_the_next_launch() {
        val prefs = InMemorySpiritualPersistence()
        LanguageStore(prefs).choose(Language.RUSSIAN)

        val restored = LanguageStore(prefs)
        assertTrue(restored.hasChosen)
        assertEquals(Language.RUSSIAN, restored.current)
    }
}
