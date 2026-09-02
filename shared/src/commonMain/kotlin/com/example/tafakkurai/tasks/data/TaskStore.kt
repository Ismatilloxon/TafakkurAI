package com.example.tafakkurai.tasks.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.tasks.domain.CreateTaskCommand
import com.example.tafakkurai.tasks.domain.ProductivityTask
import com.example.tafakkurai.tasks.domain.TaskOrigin
import com.example.tafakkurai.tasks.domain.TaskPriority
import com.example.tafakkurai.tasks.domain.normalized
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** SQLDelight-backed single source of truth for manual and AI-created tasks. */
class TaskStore(
    private val db: TafakkurDb,
    private val activity: ActivityLog? = null,
    private val today: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = ::randomTaskId,
) {
    private val q get() = db.taskQueries
    private val _tasks = mutableStateListOf<ProductivityTask>()
    val tasks: List<ProductivityTask> get() = _tasks

    var revision by mutableStateOf(0)
        private set

    init {
        runCatching { db.syncEventQueries.upsertPolicy(TABLE, FEATURE, 1L) }
        refresh()
    }

    fun refresh() {
        _tasks.clear()
        runCatching { q.selectAll().executeAsList().map(::toDomain) }
            .getOrDefault(emptyList())
            .let(_tasks::addAll)
        revision++
    }

    fun onDate(date: String = today()): List<ProductivityTask> =
        _tasks.filter { it.dueDate == date }

    fun byId(id: String): ProductivityTask? = _tasks.firstOrNull { it.id == id }

    fun create(command: CreateTaskCommand, completed: Boolean = false): ProductivityTask? {
        val input = command.normalized(today()) ?: return null
        val stamp = now()
        val task = ProductivityTask(
            id = newId(),
            title = input.title,
            notes = input.notes,
            categoryId = input.categoryId,
            priority = input.priority,
            dueDate = input.dueDate,
            dueTime = input.dueTime,
            completedAt = stamp.takeIf { completed },
            origin = input.origin,
            createdAt = stamp,
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                persist(task)
                recordOrThrow(
                    type = if (completed) "task_completed" else "task_created",
                    task = task,
                    propertiesCompleted = completed.takeIf { it },
                )
            }
        }.isSuccess
        if (!persisted) return null
        _tasks.add(task)
        sortInPlace()
        revision++
        return task
    }

    fun setCompleted(
        id: String,
        completed: Boolean,
        actionOrigin: TaskOrigin = TaskOrigin.USER,
    ): Boolean {
        val index = _tasks.indexOfFirst { it.id == id }
        if (index < 0 || _tasks[index].completed == completed) return false
        val stamp = now()
        val updated = _tasks[index].copy(
            completedAt = stamp.takeIf { completed },
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                q.setCompleted(updated.completedAt, stamp, id)
                recordOrThrow("task_completion_changed", updated, completed, actionOrigin)
            }
        }.isSuccess
        if (!persisted) return false
        _tasks[index] = updated
        sortInPlace()
        revision++
        return true
    }

    fun toggle(id: String, actionOrigin: TaskOrigin = TaskOrigin.USER): Boolean =
        byId(id)?.let { setCompleted(id, !it.completed, actionOrigin) } ?: false

    fun delete(id: String, actionOrigin: TaskOrigin = TaskOrigin.USER): Boolean {
        val task = byId(id) ?: return false
        val stamp = now()
        val persisted = runCatching {
            db.transaction {
                q.softDelete(stamp, id)
                recordOrThrow("task_deleted", task.copy(updatedAt = stamp), null, actionOrigin)
            }
        }.isSuccess
        if (!persisted) return false
        _tasks.removeAll { it.id == id }
        revision++
        return true
    }

    private fun persist(task: ProductivityTask) {
        q.upsert(
            id = task.id,
            title = task.title,
            notes = task.notes,
            categoryId = task.categoryId,
            priority = task.priority.id,
            dueDate = task.dueDate,
            dueTime = task.dueTime,
            completedAt = task.completedAt,
            origin = task.origin.id,
            createdAt = task.createdAt,
            updatedAt = task.updatedAt,
            deleted = 0L,
        )
    }

    private fun recordOrThrow(
        type: String,
        task: ProductivityTask,
        propertiesCompleted: Boolean?,
        actionOrigin: TaskOrigin = task.origin,
    ) {
        val log = activity ?: return
        val recorded = log.record(
            feature = FEATURE,
            type = type,
            entityId = task.id,
            source = actionOrigin.id,
            properties = buildJsonObject {
                put("task_id", task.id)
                put("title", task.title)
                put("category", task.categoryId)
                put("priority", task.priority.id)
                put("due_date", task.dueDate)
                put("due_time", task.dueTime)
                propertiesCompleted?.let { put("completed", it) }
            },
        )
        check(recorded) { "Could not append task ActivityEvent" }
    }

    private fun sortInPlace() {
        val sorted = _tasks.sortedWith(
            compareBy<ProductivityTask> { it.completed }
                .thenBy { it.dueDate.isBlank() }
                .thenBy { it.dueDate }
                .thenBy { it.dueTime.isBlank() }
                .thenBy { it.dueTime }
                .thenByDescending { it.createdAt },
        )
        _tasks.clear()
        _tasks.addAll(sorted)
    }

    private fun toDomain(row: com.example.tafakkurai.db.Task) = ProductivityTask(
        id = row.id,
        title = row.title,
        notes = row.notes,
        categoryId = row.categoryId,
        priority = TaskPriority.from(row.priority),
        dueDate = row.dueDate,
        dueTime = row.dueTime,
        completedAt = row.completedAt,
        origin = TaskOrigin.from(row.origin),
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private companion object {
        const val TABLE = "task"
        const val FEATURE = "tasks"
    }
}

private fun randomTaskId(): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "task-" + buildString { repeat(20) { append(alphabet[Random.nextInt(alphabet.length)]) } }
}
