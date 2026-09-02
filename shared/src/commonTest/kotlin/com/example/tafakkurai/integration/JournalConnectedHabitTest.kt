package com.example.tafakkurai.integration

import com.example.tafakkurai.habits.HabitSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JournalConnectedHabitTest {

    @Test
    fun tafakkurPlanRouteOwnsOneStableEveningHabitConnection() {
        val spec = assertNotNull(connectedHabitSpec("tafakkur"))

        assertEquals("tafakkur", spec.key)
        assertEquals("habit.connected.tafakkur", spec.titleKey)
        assertEquals("mindfulness", spec.categoryId)
        assertEquals(HabitSlot.EVENING, spec.slot)
    }
}
