package com.example.tafakkurai.journal

import com.example.tafakkurai.journal.data.journalActivityProperties
import com.example.tafakkurai.journal.domain.JournalMood
import com.example.tafakkurai.journal.domain.JournalPrompt
import com.example.tafakkurai.journal.domain.JournalReflection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JournalActivityPrivacyTest {

    @Test
    fun activityProjectionCarriesProvenanceButNeverPrivateWriting() {
        val properties = journalActivityProperties(
            JournalReflection(
                id = "entry-1",
                prompt = JournalPrompt.INTENTION,
                mood = JournalMood.HOPEFUL,
                body = "Private words that must not enter an ActivityEvent",
                entryDate = "2026-08-29",
                createdAt = 10,
                updatedAt = 10,
            ),
        )

        assertEquals("entry-1", properties.getValue("entry_id").toString().trim('"'))
        assertEquals("2026-08-29", properties.getValue("entry_date").toString().trim('"'))
        assertEquals(
            "Private words that must not enter an ActivityEvent".length.toString(),
            properties.getValue("character_count").toString(),
        )
        assertFalse("body" in properties)
        assertFalse("prompt_id" in properties)
        assertFalse("mood" in properties)
        assertFalse(properties.toString().contains("Private words"))
    }
}
