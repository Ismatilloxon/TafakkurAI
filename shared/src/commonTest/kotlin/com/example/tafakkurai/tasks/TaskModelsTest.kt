package com.example.tafakkurai.tasks

import com.example.tafakkurai.tasks.domain.CreateTaskCommand
import com.example.tafakkurai.tasks.domain.normalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskModelsTest {
    @Test
    fun blankTaskIsRejected() {
        assertNull(CreateTaskCommand("   \n ").normalized(TODAY))
    }

    @Test
    fun missingOrInvalidDueDateBecomesToday() {
        val task = CreateTaskCommand("  Call   family  ", dueDate = "someday", dueTime = "28:91")
            .normalized(TODAY)

        assertEquals("Call family", task?.title)
        assertEquals(TODAY, task?.dueDate)
        assertEquals("", task?.dueTime)
    }

    @Test
    fun impossibleCalendarDateBecomesToday() {
        val task = CreateTaskCommand("Read", dueDate = "2026-02-30").normalized(TODAY)

        assertEquals(TODAY, task?.dueDate)
    }

    @Test
    fun validLocalScheduleIsPreserved() {
        val task = CreateTaskCommand("Read", dueDate = "2026-08-29", dueTime = "07:30")
            .normalized(TODAY)

        assertEquals("2026-08-29", task?.dueDate)
        assertEquals("07:30", task?.dueTime)
    }

    private companion object { const val TODAY = "2026-08-28" }
}
