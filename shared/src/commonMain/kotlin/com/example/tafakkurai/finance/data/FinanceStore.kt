package com.example.tafakkurai.finance.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.finance.domain.FINANCE_CATEGORIES
import com.example.tafakkurai.finance.domain.FinanceBudget
import com.example.tafakkurai.finance.domain.FinanceEntry
import com.example.tafakkurai.finance.domain.FinanceEntryKind
import com.example.tafakkurai.finance.domain.FinanceGoal
import com.example.tafakkurai.finance.domain.FinanceRepository
import com.example.tafakkurai.finance.domain.FinanceSnapshot
import com.example.tafakkurai.finance.domain.SUPPORTED_FINANCE_CURRENCIES
import com.example.tafakkurai.finance.domain.calculateFinanceSnapshot
import com.example.tafakkurai.finance.domain.normalizeCurrency
import com.example.tafakkurai.finance.domain.parseAmountMinor
import com.example.tafakkurai.flashcards.epochMillis
import com.example.tafakkurai.prayer.deviceDateKey
import com.example.tafakkurai.sync.ActivityLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/** SQLDelight source of truth for the private ledger and monthly category budgets. */
class FinanceStore(
    private val db: TafakkurDb,
    private val activity: ActivityLog,
    private val today: () -> String = ::deviceDateKey,
    private val now: () -> Long = ::epochMillis,
    private val newId: () -> String = { randomFinanceId("fin") },
    remoteChanges: Flow<Set<String>> = emptyFlow(),
    scope: CoroutineScope? = null,
) : FinanceRepository {
    private val gate = Mutex()
    private val q get() = db.financeQueries
    private val _entries = mutableStateListOf<FinanceEntry>()
    private val _budgets = mutableStateListOf<FinanceBudget>()
    private val _goals = mutableStateListOf<FinanceGoal>()

    override val entries: List<FinanceEntry> get() = gated { _entries.toList() }
    override val budgets: List<FinanceBudget> get() = gated { _budgets.toList() }
    override val goals: List<FinanceGoal> get() = gated { _goals.toList() }

    override var preferredCurrency by mutableStateOf(DEFAULT_CURRENCY)
        private set

    override var revision by mutableStateOf(0)
        private set
    override var loadFailed by mutableStateOf(false)
        private set

    init {
        runCatching {
            FINANCE_TABLES.forEach { table ->
                db.syncEventQueries.upsertPolicy(table, FEATURE, 1L)
            }
        }
        // Subscribe before the first read. UNDISPATCHED reaches SharedFlow.collect before
        // launch returns, so a pull cannot land in the refresh-to-subscribe gap.
        scope?.launch(start = CoroutineStart.UNDISPATCHED) {
            remoteChanges.collect { tables ->
                if (tables.any(FINANCE_TABLES::contains)) {
                    gate.withLock { refreshLocked() }
                }
            }
        }
        // If a pull finished before this repository existed, its rows are already in SQL
        // and this authoritative read observes them even though SharedFlow did not replay.
        refresh()
    }

    override fun refresh() = gated { refreshLocked() }

    private fun refreshLocked() {
        val loaded = runCatching {
            val contributions = q.selectGoalContributions().executeAsList()
                .groupBy { it.goalId }
            val goals = q.selectGoals().executeAsList().map { row ->
                val matching = contributions[row.id].orEmpty()
                    .filter { it.currencyCode == row.currencyCode }
                goalFromRow(
                    row = row,
                    savedMinor = matching.fold(0L) { total, item ->
                        safeMinorSum(total, item.amountMinor)
                    },
                    latestContributionAt = matching.maxOfOrNull { it.occurredAt } ?: 0L,
                )
            }.sortedWith(
                compareBy<FinanceGoal> { it.isComplete }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.id },
            )
            LoadedFinance(
                entries = q.selectTransactions().executeAsList().map(::entryFromRow),
                budgets = q.selectBudgets().executeAsList().map(::budgetFromRow),
                goals = goals,
                preferredCurrency = q.selectFinancePreference(PREF_CURRENCY)
                    .executeAsOneOrNull()
                    ?.let(::normalizeCurrency)
                    ?.takeIf(SUPPORTED_FINANCE_CURRENCIES::contains)
                    ?: DEFAULT_CURRENCY,
            )
        }.getOrElse {
            loadFailed = true
            revision++
            return
        }
        _entries.clear()
        _entries.addAll(loaded.entries)
        _budgets.clear()
        _budgets.addAll(loaded.budgets)
        _goals.clear()
        _goals.addAll(loaded.goals)
        preferredCurrency = loaded.preferredCurrency
        loadFailed = false
        revision++
    }

    override fun currentMonthKey(): String = today().take(7)
    override fun currentDateKey(): String = today()

    override fun setPreferredCurrency(currencyCode: String): Boolean = gated {
        val currency = normalizeCurrency(currencyCode)
            ?.takeIf(SUPPORTED_FINANCE_CURRENCIES::contains)
            ?: return false
        if (preferredCurrency == currency) return true
        val persisted = runCatching {
            q.putFinancePreference(PREF_CURRENCY, currency, now())
        }.isSuccess
        if (!persisted) return false
        preferredCurrency = currency
        revision++
        return true
    }

    override fun snapshot(
        monthKey: String,
        currencyCode: String,
    ): FinanceSnapshot = gated {
        calculateFinanceSnapshot(_entries, _budgets, monthKey, currencyCode)
    }

    override fun record(
        kind: FinanceEntryKind,
        amountInput: String,
        categoryId: String,
        note: String,
        currencyCode: String,
        dateKey: String,
        source: String,
    ): FinanceEntry? = gated {
        val currency = normalizeCurrency(currencyCode) ?: return null
        val amountMinor = parseAmountMinor(amountInput, currency) ?: return null
        val category = categoryId.takeIf { id -> FINANCE_CATEGORIES.any { it.id == id } } ?: return null
        if (kind == FinanceEntryKind.EXPENSE && category == "income") return null
        if (kind == FinanceEntryKind.INCOME && category !in INCOME_CATEGORIES) return null
        if (!DATE_PATTERN.matches(dateKey)) return null
        val stamp = now()
        val entry = FinanceEntry(
            id = newId(),
            kind = kind,
            amountMinor = amountMinor,
            currencyCode = currency,
            categoryId = category,
            note = note.trim().take(MAX_NOTE_LENGTH),
            entryDate = dateKey,
            occurredAt = stamp,
            createdAt = stamp,
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                q.upsertTransaction(
                    entry.id,
                    entry.kind.id,
                    entry.amountMinor,
                    entry.currencyCode,
                    entry.categoryId,
                    entry.note,
                    entry.entryDate,
                    entry.occurredAt,
                    entry.createdAt,
                    entry.updatedAt,
                    0L,
                )
                recordActivityOrThrow(
                    type = if (kind == FinanceEntryKind.EXPENSE) "expense_recorded" else "income_recorded",
                    entityId = entry.id,
                    source = source,
                    occurredAt = stamp,
                    localDate = dateKey,
                    properties = {
                        put("transaction_id", entry.id)
                        put("kind", entry.kind.id)
                        put("amount_minor", entry.amountMinor)
                        put("currency_code", entry.currencyCode)
                        put("category", entry.categoryId)
                        put("entry_date", entry.entryDate)
                    },
                )
            }
        }.isSuccess
        if (!persisted) return null
        _entries.add(0, entry)
        revision++
        return entry
    }

    override fun deleteEntry(id: String, source: String): Boolean = gated {
        val entry = _entries.firstOrNull { it.id == id } ?: return false
        val stamp = now()
        val persisted = runCatching {
            db.transaction {
                q.softDeleteTransaction(stamp, id)
                recordActivityOrThrow(
                    type = "transaction_deleted",
                    entityId = id,
                    source = source,
                    occurredAt = stamp,
                    localDate = currentDateKey(),
                    properties = {
                        put("transaction_id", id)
                        put("kind", entry.kind.id)
                        put("amount_minor", entry.amountMinor)
                        put("currency_code", entry.currencyCode)
                        put("category", entry.categoryId)
                    },
                )
            }
        }.isSuccess
        if (!persisted) return false
        _entries.removeAll { it.id == id }
        revision++
        return true
    }

    override fun setBudget(
        categoryId: String,
        limitInput: String,
        monthKey: String,
        currencyCode: String,
        source: String,
    ): FinanceBudget? = gated {
        val currency = normalizeCurrency(currencyCode) ?: return null
        val limitMinor = parseAmountMinor(limitInput, currency) ?: return null
        if (
            categoryId == "income" ||
            FINANCE_CATEGORIES.none { it.id == categoryId } ||
            !MONTH_PATTERN.matches(monthKey)
        ) return null
        val existing = _budgets.firstOrNull {
            it.monthKey == monthKey && it.categoryId == categoryId && it.currencyCode == currency
        }
        // Re-saving an identical limit is a successful no-op, not a second user activity.
        if (existing?.limitMinor == limitMinor) return existing
        val stamp = now()
        val budget = FinanceBudget(
            id = existing?.id ?: randomFinanceId("budget"),
            monthKey = monthKey,
            categoryId = categoryId,
            limitMinor = limitMinor,
            currencyCode = currency,
            createdAt = existing?.createdAt ?: stamp,
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                q.upsertBudget(
                    budget.id,
                    budget.monthKey,
                    budget.categoryId,
                    budget.limitMinor,
                    budget.currencyCode,
                    budget.createdAt,
                    budget.updatedAt,
                )
                recordActivityOrThrow(
                    type = "budget_set",
                    entityId = budget.id,
                    source = source,
                    occurredAt = stamp,
                    localDate = currentDateKey(),
                    properties = {
                        put("budget_id", budget.id)
                        put("month", budget.monthKey)
                        put("category", budget.categoryId)
                        put("limit_minor", budget.limitMinor)
                        put("currency_code", budget.currencyCode)
                    },
                )
            }
        }.isSuccess
        if (!persisted) return null
        existing?.let { _budgets.remove(it) }
        _budgets.add(budget)
        revision++
        return budget
    }

    override fun deleteBudget(id: String, source: String): Boolean = gated {
        val budget = _budgets.firstOrNull { it.id == id } ?: return false
        val stamp = now()
        val persisted = runCatching {
            db.transaction {
                q.softDeleteBudget(stamp, id)
                recordActivityOrThrow(
                    type = "budget_deleted",
                    entityId = id,
                    source = source,
                    occurredAt = stamp,
                    localDate = currentDateKey(),
                    properties = {
                        put("budget_id", id)
                        put("month", budget.monthKey)
                        put("category", budget.categoryId)
                        put("limit_minor", budget.limitMinor)
                        put("currency_code", budget.currencyCode)
                    },
                )
            }
        }.isSuccess
        if (!persisted) return false
        _budgets.removeAll { it.id == id }
        revision++
        return true
    }

    override fun createGoal(
        title: String,
        targetInput: String,
        currencyCode: String,
        source: String,
    ): FinanceGoal? = gated {
        val safeTitle = title.trim().take(MAX_GOAL_TITLE_LENGTH)
        if (safeTitle.isEmpty()) return null
        val currency = normalizeCurrency(currencyCode) ?: return null
        val targetMinor = parseAmountMinor(targetInput, currency) ?: return null
        val stamp = now()
        val goal = FinanceGoal(
            id = randomFinanceId("goal"),
            title = safeTitle,
            targetMinor = targetMinor,
            savedMinor = 0L,
            currencyCode = currency,
            createdAt = stamp,
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                q.upsertGoal(
                    goal.id,
                    goal.title,
                    goal.targetMinor,
                    goal.currencyCode,
                    goal.createdAt,
                    goal.updatedAt,
                )
                recordActivityOrThrow(
                    type = "savings_goal_created",
                    entityId = goal.id,
                    source = source,
                    occurredAt = stamp,
                    localDate = currentDateKey(),
                    properties = {
                        put("goal_id", goal.id)
                        put("title", goal.title)
                        put("target_minor", goal.targetMinor)
                        put("currency_code", goal.currencyCode)
                    },
                )
            }
        }.isSuccess
        if (!persisted) return null
        _goals.add(0, goal)
        sortGoalsInPlace()
        revision++
        return goal
    }

    override fun contributeToGoal(
        goalId: String,
        amountInput: String,
        source: String,
    ): FinanceGoal? = gated {
        val index = _goals.indexOfFirst { it.id == goalId }
        if (index < 0) return null
        val goal = _goals[index]
        if (goal.isComplete) return null
        val amountMinor = parseAmountMinor(amountInput, goal.currencyCode) ?: return null
        val stamp = now()
        val date = currentDateKey()
        val contributionId = randomFinanceId("goal-contribution")
        val updated = goal.copy(
            savedMinor = safeMinorSum(goal.savedMinor, amountMinor),
            updatedAt = stamp,
        )
        val persisted = runCatching {
            db.transaction {
                q.insertGoalContribution(
                    contributionId,
                    goal.id,
                    amountMinor,
                    goal.currencyCode,
                    date,
                    stamp,
                    stamp,
                )
                recordActivityOrThrow(
                    type = "savings_goal_contribution",
                    entityId = goal.id,
                    source = source,
                    occurredAt = stamp,
                    localDate = date,
                    properties = {
                        put("goal_id", goal.id)
                        put("contribution_id", contributionId)
                        put("title", goal.title)
                        put("amount_minor", amountMinor)
                        put("saved_minor", updated.savedMinor)
                        put("target_minor", goal.targetMinor)
                        put("currency_code", goal.currencyCode)
                        put("completed", updated.isComplete)
                    },
                )
            }
        }.isSuccess
        if (!persisted) return null
        _goals[index] = updated
        sortGoalsInPlace()
        revision++
        return updated
    }

    override fun deleteGoal(id: String, source: String): Boolean = gated {
        val goal = _goals.firstOrNull { it.id == id } ?: return false
        val stamp = now()
        val persisted = runCatching {
            db.transaction {
                q.softDeleteGoal(stamp, id)
                recordActivityOrThrow(
                    type = "savings_goal_deleted",
                    entityId = id,
                    source = source,
                    occurredAt = stamp,
                    localDate = currentDateKey(),
                    properties = {
                        put("goal_id", id)
                        put("title", goal.title)
                        put("saved_minor", goal.savedMinor)
                        put("target_minor", goal.targetMinor)
                        put("currency_code", goal.currencyCode)
                    },
                )
            }
        }.isSuccess
        if (!persisted) return false
        _goals.removeAll { it.id == id }
        revision++
        return true
    }

    private fun recordActivityOrThrow(
        type: String,
        entityId: String,
        source: String,
        occurredAt: Long,
        localDate: String,
        properties: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ) {
        check(
            activity.record(
                feature = FEATURE,
                type = type,
                entityId = entityId,
                source = source,
                occurredAtOverride = occurredAt,
                localDateOverride = localDate,
                properties = buildJsonObject(properties),
            ),
        ) { "Could not append finance ActivityEvent" }
    }

    private fun entryFromRow(row: com.example.tafakkurai.db.FinanceTransaction) = FinanceEntry(
        id = row.id,
        kind = FinanceEntryKind.from(row.kind),
        amountMinor = row.amountMinor,
        currencyCode = row.currencyCode,
        categoryId = row.categoryId,
        note = row.note,
        entryDate = row.entryDate,
        occurredAt = row.occurredAt,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun budgetFromRow(row: com.example.tafakkurai.db.FinanceBudget) = FinanceBudget(
        id = row.id,
        monthKey = row.monthKey,
        categoryId = row.categoryId,
        limitMinor = row.limitMinor,
        currencyCode = row.currencyCode,
        createdAt = row.createdAt,
        updatedAt = row.updatedAt,
    )

    private fun goalFromRow(
        row: com.example.tafakkurai.db.FinanceGoal,
        savedMinor: Long,
        latestContributionAt: Long,
    ) = FinanceGoal(
        id = row.id,
        title = row.title,
        targetMinor = row.targetMinor,
        savedMinor = savedMinor,
        currencyCode = row.currencyCode,
        createdAt = row.createdAt,
        updatedAt = maxOf(row.updatedAt, latestContributionAt),
    )

    private fun sortGoalsInPlace() {
        val sorted = _goals.sortedWith(
            compareBy<FinanceGoal> { it.isComplete }
                .thenByDescending { it.updatedAt }
                .thenBy { it.id },
        )
        _goals.clear()
        _goals.addAll(sorted)
    }

    /**
     * Finance's public contract is synchronous because UI and on-device actions commit local
     * SQL immediately. Acquiring the coroutine mutex first lets that contract share one gate
     * with the async sync collector; the tiny SQL/cache section remains deterministic.
     */
    private inline fun <T> gated(block: () -> T): T {
        runBlocking { gate.lock() }
        try {
            return block()
        } finally {
            gate.unlock()
        }
    }

    private companion object {
        const val FEATURE = "finance"
        const val DEFAULT_CURRENCY = "UZS"
        const val MAX_NOTE_LENGTH = 500
        const val MAX_GOAL_TITLE_LENGTH = 80
        const val PREF_CURRENCY = "preferredCurrency"
        val FINANCE_TABLES = setOf(
            "financeTransaction",
            "financeBudget",
            "financeGoal",
            "financeGoalContribution",
            "financePreference",
        )
        val INCOME_CATEGORIES = setOf("income", "other")
        val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
        val MONTH_PATTERN = Regex("\\d{4}-\\d{2}")
    }
}

private data class LoadedFinance(
    val entries: List<FinanceEntry>,
    val budgets: List<FinanceBudget>,
    val goals: List<FinanceGoal>,
    val preferredCurrency: String,
)

/** Corrupt or hostile remote input cannot overflow a goal total into a negative number. */
private fun safeMinorSum(left: Long, right: Long): Long = when {
    right <= 0L -> left.coerceAtLeast(0L)
    left < 0L -> right
    Long.MAX_VALUE - left < right -> Long.MAX_VALUE
    else -> left + right
}

private fun randomFinanceId(prefix: String): String {
    val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
    return "$prefix-" + buildString { repeat(20) { append(alphabet[Random.nextInt(alphabet.length)]) } }
}
