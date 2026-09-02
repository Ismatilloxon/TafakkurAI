package com.example.tafakkurai.sync

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityProjectionTest {

    @Test
    fun latestEventWinsSoUndoRemovesCompletedPlanItem() {
        val marked = event("quran", "ayah_marked_read", 10)
        val unmarked = event("quran", "ayah_unmarked_read", 20)

        assertTrue(projectTodayActivities(listOf(marked), DATE).single().completed)
        assertTrue(projectTodayActivities(listOf(marked, unmarked), DATE).isEmpty())
    }

    @Test
    fun startingSurahCreatesPendingConnectedPlanItem() {
        val event = event(
            feature = "hifz",
            type = "ayahs_enrolled",
            at = 10,
            properties = buildJsonObject { put("surah", 36) },
        )

        val item = projectTodayActivities(listOf(event), DATE).single()

        assertEquals("hifz:36", item.planKey)
        assertEquals("today.plan.hifz_surah", item.titleKey)
        assertEquals("36", item.titleArgument)
        assertEquals("hifz", item.route)
        assertFalse(item.completed)
    }

    @Test
    fun gratitudeAndSleepProjectIntoDifferentBalanceAspects() {
        val result = projectTodayActivities(
            listOf(
                event("gratitude", "entry_created", 10),
                event("sleep", "sleep_ended", 20),
            ),
            DATE,
        )

        assertEquals(listOf("spiritual", "physical"), result.map { it.aspect })
        assertTrue(result.all { it.completed })
    }

    @Test
    fun healthDetectedNapCompletesPhysicalSleepPlan() {
        val item = projectTodayActivities(
            listOf(event("sleep", "nap_detected", 30)),
            DATE,
        ).single()

        assertEquals("physical", item.aspect)
        assertEquals("sleep", item.route)
        assertTrue(item.completed)
    }

    @Test
    fun taskUsesItsRealTitleAndLatestCompletionState() {
        val created = event(
            "tasks", "task_created", 10,
            buildJsonObject { put("title", "Call family") },
        )
        val completed = event(
            "tasks", "task_completion_changed", 20,
            buildJsonObject { put("title", "Call family"); put("completed", true) },
        ).copy(entityId = created.entityId)

        val item = projectTodayActivities(listOf(created, completed), DATE).single()

        assertEquals("Call family", item.titleArgument)
        assertEquals("do", item.route)
        assertTrue(item.completed)
    }

    @Test
    fun completedPhysicalTaskFeedsPhysicalPlanAndNativeSportRoute() {
        val completed = event(
            "tasks", "task_completed", 30,
            buildJsonObject {
                put("title", "Morning run")
                put("category", "health")
            },
        )

        val item = projectTodayActivities(listOf(completed), DATE).single()

        assertEquals("physical", item.aspect)
        assertEquals("sport", item.route)
        assertTrue(item.completed)
    }

    @Test
    fun importedFlashcardsBecomePendingLearningPlan() {
        val imported = event(
            "flashcards", "cards_imported", 40,
            buildJsonObject { put("count", 128) },
        )

        val item = projectTodayActivities(listOf(imported), DATE).single()

        assertEquals("today.plan.flashcards_created", item.titleKey)
        assertEquals("128", item.titleArgument)
        assertEquals("educational", item.aspect)
        assertEquals("learn", item.route)
        assertFalse(item.completed)
    }

    @Test
    fun financeDeleteRemovesTheRecordedTransactionFromToday() {
        val recorded = event("finance", "expense_recorded", 10).copy(entityId = "transaction-1")
        val deleted = event("finance", "transaction_deleted", 20).copy(entityId = "transaction-1")

        assertEquals("financial", projectTodayActivities(listOf(recorded), DATE).single().aspect)
        assertTrue(projectTodayActivities(listOf(recorded, deleted), DATE).isEmpty())
    }

    @Test
    fun onlyMeaningfulSocialActionsReachTodayAndBalance() {
        val bookmark = event("social", "post_bookmark_changed", 10)
        val joined = event("social", "circle_joined", 20)
        val checkIn = event("social", "relationship_check_in", 30)

        val result = projectTodayActivities(listOf(bookmark, joined, checkIn), DATE)

        assertEquals(2, result.size)
        assertTrue(result.all { it.aspect == "social" && it.completed })
    }

    @Test
    fun socialUndoAndConnectionIntentReplayToOneStablePlanRow() {
        val added = event("social", "connection_added", 10).copy(entityId = "connection-1")
        val checkedIn = event(
            "social",
            "relationship_check_in",
            20,
            buildJsonObject { put("connection_id", "connection-1") },
        )
        val deleted = event("social", "connection_deleted", 30).copy(entityId = "connection-1")

        val pending = projectTodayActivities(listOf(added), DATE).single()
        assertFalse(pending.completed)
        assertEquals("today.plan.social_pending", pending.titleKey)

        val completed = projectTodayActivities(listOf(added, checkedIn), DATE).single()
        assertTrue(completed.completed)
        assertEquals("social:connection:connection-1", completed.planKey)

        assertTrue(projectTodayActivities(listOf(added, checkedIn, deleted), DATE).isEmpty())
    }

    @Test
    fun leavingCircleRemovesItsSameDayCompletion() {
        val joined = event("social", "circle_joined", 10).copy(entityId = "learning")
        val left = event("social", "circle_left", 20).copy(entityId = "learning")

        assertTrue(projectTodayActivities(listOf(joined), DATE).single().completed)
        assertTrue(projectTodayActivities(listOf(joined, left), DATE).isEmpty())
    }

    private fun event(
        feature: String,
        type: String,
        at: Long,
        properties: kotlinx.serialization.json.JsonObject = buildJsonObject {},
    ) = RecordedActivity(
        id = "00000000-0000-4000-8000-${at.toString().padStart(12, '0')}",
        userId = "user",
        feature = feature,
        type = type,
        entityId = at.toString(),
        occurredAt = at,
        localDate = DATE,
        timezone = "Asia/Tashkent",
        source = "user",
        schemaVersion = 1,
        properties = properties,
    )

    private companion object {
        const val DATE = "2026-08-28"
    }
}
