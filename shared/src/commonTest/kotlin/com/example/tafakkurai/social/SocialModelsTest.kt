package com.example.tafakkurai.social

import com.example.tafakkurai.social.domain.SocialCheckIn
import com.example.tafakkurai.social.domain.SocialConnection
import com.example.tafakkurai.social.domain.normalizeConnectionName
import com.example.tafakkurai.social.domain.summarizeConnections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SocialModelsTest {
    @Test
    fun connectionNamesAreTrimmedAndWhitespaceIsCollapsed() {
        assertEquals("Ona Karimova", normalizeConnectionName("  Ona   Karimova  "))
        assertNull(normalizeConnectionName(" \n "))
    }

    @Test
    fun summariesCountCheckInsAndPrioritizeRecentContact() {
        val older = connection("a", "Ali", "2026-08-20")
        val newer = connection("b", "Bibi", "2026-08-28")
        val never = connection("c", "Camil", "")
        val checkIns = listOf(
            checkIn("1", "a"),
            checkIn("2", "b"),
            checkIn("3", "b"),
        )

        val result = summarizeConnections(listOf(older, never, newer), checkIns)

        assertEquals(listOf("b", "a", "c"), result.map { it.connection.id })
        assertEquals(listOf(2, 1, 0), result.map { it.checkInCount })
    }

    private fun connection(id: String, name: String, lastContact: String) = SocialConnection(
        id = id,
        name = name,
        relationshipId = "friend",
        lastContactDate = lastContact,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun checkIn(id: String, connectionId: String) = SocialCheckIn(
        id = id,
        connectionId = connectionId,
        note = "",
        dateKey = "2026-08-28",
        createdAt = 1,
    )
}
