package com.example.tafakkurai.finance.presentation

import com.example.tafakkurai.finance.domain.FinanceBudget
import com.example.tafakkurai.finance.domain.FinanceEntry
import com.example.tafakkurai.finance.domain.FinanceEntryKind
import com.example.tafakkurai.finance.domain.FinanceGoal
import com.example.tafakkurai.finance.domain.FinanceRepository
import com.example.tafakkurai.finance.domain.FinanceSnapshot
import com.example.tafakkurai.finance.domain.parseAmountMinor

/** Immutable state rendered by the finance screen. */
data class FinanceUiState(
    val revision: Int,
    val snapshot: FinanceSnapshot,
    val monthEntries: List<FinanceEntry>,
    val monthBudgets: List<FinanceBudget>,
    val goals: List<FinanceGoal>,
    val loadFailed: Boolean,
)

enum class FinanceMutationResult {
    SUCCESS,
    INVALID_AMOUNT,
    INVALID_TITLE,
    GOAL_COMPLETE,
    SAVE_FAILED,
}

/**
 * Presentation state holder. It translates UI commands into domain repository calls and keeps
 * input validation out of the composable. SQL and ActivityEvent orchestration stay in data.
 */
class FinanceStateHolder(private val repository: FinanceRepository) {
    val revision: Int get() = repository.revision
    val currentDateKey: String get() = repository.currentDateKey()

    fun state(currencyCode: String = repository.preferredCurrency): FinanceUiState {
        val monthKey = repository.currentMonthKey()
        val snapshot = repository.snapshot(monthKey, currencyCode)
        return FinanceUiState(
            revision = repository.revision,
            snapshot = snapshot,
            monthEntries = repository.entries.filter {
                it.entryDate.startsWith("$monthKey-") &&
                    it.currencyCode == snapshot.currencyCode
            },
            monthBudgets = repository.budgets.filter {
                it.monthKey == monthKey && it.currencyCode == snapshot.currencyCode
            },
            goals = repository.goals.filter { it.currencyCode == snapshot.currencyCode },
            loadFailed = repository.loadFailed,
        )
    }

    fun refresh() = repository.refresh()

    fun setCurrency(currencyCode: String): FinanceMutationResult =
        if (repository.setPreferredCurrency(currencyCode)) {
            FinanceMutationResult.SUCCESS
        } else {
            FinanceMutationResult.SAVE_FAILED
        }

    fun record(
        kind: FinanceEntryKind,
        amountInput: String,
        categoryId: String,
        note: String,
        currencyCode: String,
    ): FinanceMutationResult {
        if (parseAmountMinor(amountInput, currencyCode) == null) {
            return FinanceMutationResult.INVALID_AMOUNT
        }
        return if (
            repository.record(kind, amountInput, categoryId, note, currencyCode) != null
        ) {
            FinanceMutationResult.SUCCESS
        } else {
            FinanceMutationResult.SAVE_FAILED
        }
    }

    fun setBudget(
        categoryId: String,
        limitInput: String,
        currencyCode: String,
    ): FinanceMutationResult {
        if (parseAmountMinor(limitInput, currencyCode) == null) {
            return FinanceMutationResult.INVALID_AMOUNT
        }
        return if (repository.setBudget(categoryId, limitInput, currencyCode = currencyCode) != null) {
            FinanceMutationResult.SUCCESS
        } else {
            FinanceMutationResult.SAVE_FAILED
        }
    }

    fun deleteBudget(id: String): FinanceMutationResult =
        if (repository.deleteBudget(id)) FinanceMutationResult.SUCCESS else FinanceMutationResult.SAVE_FAILED

    fun createGoal(
        title: String,
        targetInput: String,
        currencyCode: String,
    ): FinanceMutationResult {
        if (title.isBlank()) return FinanceMutationResult.INVALID_TITLE
        if (parseAmountMinor(targetInput, currencyCode) == null) {
            return FinanceMutationResult.INVALID_AMOUNT
        }
        return if (repository.createGoal(title, targetInput, currencyCode) != null) {
            FinanceMutationResult.SUCCESS
        } else {
            FinanceMutationResult.SAVE_FAILED
        }
    }

    fun contributeToGoal(goal: FinanceGoal, amountInput: String): FinanceMutationResult {
        if (goal.isComplete) return FinanceMutationResult.GOAL_COMPLETE
        if (parseAmountMinor(amountInput, goal.currencyCode) == null) {
            return FinanceMutationResult.INVALID_AMOUNT
        }
        return if (repository.contributeToGoal(goal.id, amountInput) != null) {
            FinanceMutationResult.SUCCESS
        } else {
            FinanceMutationResult.SAVE_FAILED
        }
    }

    fun deleteGoal(id: String): FinanceMutationResult =
        if (repository.deleteGoal(id)) FinanceMutationResult.SUCCESS else FinanceMutationResult.SAVE_FAILED

    fun deleteEntry(id: String): FinanceMutationResult =
        if (repository.deleteEntry(id)) FinanceMutationResult.SUCCESS else FinanceMutationResult.SAVE_FAILED
}
