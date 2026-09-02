package com.example.tafakkurai.finance.domain

/**
 * Domain boundary for the private ledger.
 *
 * Presentation depends on this contract and never on SQLDelight or a concrete data-layer
 * implementation. The repository remains local-first and emits the ActivityEvent in the same
 * transaction as every source write.
 */
interface FinanceRepository {
    val entries: List<FinanceEntry>
    val budgets: List<FinanceBudget>
    val goals: List<FinanceGoal>
    val preferredCurrency: String
    val revision: Int
    val loadFailed: Boolean

    fun refresh()
    fun currentMonthKey(): String
    fun currentDateKey(): String

    /** Changes the viewed/entry currency; existing money is never converted. */
    fun setPreferredCurrency(currencyCode: String): Boolean

    fun snapshot(
        monthKey: String = currentMonthKey(),
        currencyCode: String = preferredCurrency,
    ): FinanceSnapshot

    fun record(
        kind: FinanceEntryKind,
        amountInput: String,
        categoryId: String,
        note: String = "",
        currencyCode: String = DEFAULT_FINANCE_CURRENCY,
        dateKey: String = currentDateKey(),
        source: String = "user",
    ): FinanceEntry?

    fun deleteEntry(id: String, source: String = "user"): Boolean

    fun setBudget(
        categoryId: String,
        limitInput: String,
        monthKey: String = currentMonthKey(),
        currencyCode: String = DEFAULT_FINANCE_CURRENCY,
        source: String = "user",
    ): FinanceBudget?

    fun deleteBudget(id: String, source: String = "user"): Boolean

    fun createGoal(
        title: String,
        targetInput: String,
        currencyCode: String = preferredCurrency,
        source: String = "user",
    ): FinanceGoal?

    /** Adds one immutable contribution, preserving concurrent offline contributions. */
    fun contributeToGoal(
        goalId: String,
        amountInput: String,
        source: String = "user",
    ): FinanceGoal?

    fun deleteGoal(id: String, source: String = "user"): Boolean
}

const val DEFAULT_FINANCE_CURRENCY = "UZS"
