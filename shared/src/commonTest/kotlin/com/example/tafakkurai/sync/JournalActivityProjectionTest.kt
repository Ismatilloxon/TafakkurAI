package com.example.tafakkurai.sync

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalActivityProjectionTest {

    @Test
    fun reflectionCreatesACompletedSpiritualPlanItemOnItsLocalDate() {
        val created = event("reflection_created", at = 10)

        val projection = projectTodayActivities(listOf(created), DATE).single()

        assertEquals("journal:entry-1", projection.planKey)
        assertEquals("today.plan.journal", projection.titleKey)
        assertEquals("spiritual", projection.aspect)
        assertEquals("tafakkur", projection.route)
        assertTrue(projection.completed)
    }

    @Test
    fun editingKeepsOnePlanRowAndDeletingRemovesOnlyThatReflection() {
        val first = event("reflection_created", at = 10)
        val edited = event("reflection_updated", at = 20)
        val another = event("reflection_created", at = 15).copy(entityId = "entry-2")
        val deleted = event("reflection_deleted", at = 30)

        assertEquals(
            listOf("journal:entry-2", "journal:entry-1"),
            projectTodayActivities(listOf(first, another, edited), DATE).map { it.planKey },
        )
        assertEquals(
            listOf("journal:entry-2"),
            projectTodayActivities(listOf(first, another, edited, deleted), DATE).map { it.planKey },
        )
    }

    private fun event(type: String, at: Long) = RecordedActivity(
        id = "00000000-0000-4000-8000-${at.toString().padStart(12, '0')}",
        userId = "user-1",
        feature = "journal",
        type = type,
        entityId = "entry-1",
        occurredAt = at,
        localDate = DATE,
        timezone = "Asia/Tashkent",
        source = "user",
        schemaVersion = 1,
        properties = buildJsonObject {
            put("entry_id", "entry-1")
            put("entry_date", DATE)
            put("character_count", 42)
        },
    )

    private companion object {
        const val DATE = "2026-08-29"
    }
}
