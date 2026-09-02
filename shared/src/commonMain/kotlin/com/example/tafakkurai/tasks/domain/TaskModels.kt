package com.example.tafakkurai.tasks.domain

/** Persisted task exposed to presentation and cross-feature projections. */
data class ProductivityTask(
    val id: String,
    val title: String,
    val notes: String = "",
    val categoryId: String = DEFAULT_TASK_CATEGORY,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: String = "",
    val dueTime: String = "",
    val completedAt: Long? = null,
    val origin: TaskOrigin = TaskOrigin.USER,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val completed: Boolean get() = completedAt != null
}

enum class TaskPriority(val id: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high");

    companion object {
        fun from(id: String): TaskPriority = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class TaskOrigin(val id: String) {
    USER("user"), AI("ai"), IMPORT("import");

    companion object {
        fun from(id: String): TaskOrigin = entries.firstOrNull { it.id == id } ?: USER
    }
}

data class CreateTaskCommand(
    val title: String,
    val notes: String = "",
    val categoryId: String = DEFAULT_TASK_CATEGORY,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDate: String = "",
    val dueTime: String = "",
    val origin: TaskOrigin = TaskOrigin.USER,
)

/**
 * Validates untrusted manual/AI task input before it reaches SQL or an ActivityEvent.
 * A missing due date intentionally becomes today so a newly requested action is visible
 * in Today's plan, matching the repository-wide connected-activity invariant.
 */
fun CreateTaskCommand.normalized(today: String): CreateTaskCommand? {
    val safeTitle = title.trim().replace(WHITESPACE, " ").take(MAX_TASK_TITLE)
    if (safeTitle.isBlank()) return null
    val safeDate = dueDate.trim().takeIf(::isValidDateKey) ?: today
    val safeTime = dueTime.trim().takeIf(TIME_PATTERN::matches).orEmpty()
    return copy(
        title = safeTitle,
        notes = notes.trim().take(MAX_TASK_NOTES),
        categoryId = categoryId.trim().lowercase().take(MAX_TASK_CATEGORY)
            .ifBlank { DEFAULT_TASK_CATEGORY },
        dueDate = safeDate,
        dueTime = safeTime,
    )
}

const val DEFAULT_TASK_CATEGORY = "personal"
private const val MAX_TASK_TITLE = 200
private const val MAX_TASK_NOTES = 2_000
private const val MAX_TASK_CATEGORY = 64
private val WHITESPACE = Regex("\\s+")
private val TIME_PATTERN = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")

private fun isValidDateKey(value: String): Boolean {
    val parts = value.split('-')
    if (parts.size != 3 || parts[0].length != 4 || parts[1].length != 2 || parts[2].length != 2) {
        return false
    }
    val year = parts[0].toIntOrNull() ?: return false
    val month = parts[1].toIntOrNull() ?: return false
    val day = parts[2].toIntOrNull() ?: return false
    if (year !in 1970..9999 || month !in 1..12) return false
    val leap = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
    val maxDay = when (month) {
        2 -> if (leap) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..maxDay
}
