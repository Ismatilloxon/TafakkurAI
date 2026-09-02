package com.example.tafakkurai.finance

import com.example.tafakkurai.finance.domain.FinanceBudget
import com.example.tafakkurai.finance.domain.FinanceEntry
import com.example.tafakkurai.finance.domain.FinanceEntryKind
import com.example.tafakkurai.finance.domain.calculateFinanceSnapshot
import com.example.tafakkurai.finance.domain.formatMoney
import com.example.tafakkurai.finance.domain.parseAmountMinor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FinanceModelsTest {
    @Test
    fun amountParsingUsesIntegerMinorUnitsWithoutFloatingPointRounding() {
        assertEquals(123_456L, parseAmountMinor("1 234,56", "USD"))
        assertEquals(1_234L, parseAmountMinor("1 234", "UZS"))
        assertNull(parseAmountMinor("12.50", "UZS"))
        assertNull(parseAmountMinor("-10", "USD"))
        assertNull(parseAmountMinor("0", "UZS"))
    }

    @Test
    fun monthlySnapshotNeverMixesCurrenciesOrMonths() {
        val entries = listOf(
            entry("income", FinanceEntryKind.INCOME, 1_000_000, "UZS", "income", "2026-08-01"),
            entry("food", FinanceEntryKind.EXPENSE, 250_000, "UZS", "food", "2026-08-02"),
            entry("usd", FinanceEntryKind.EXPENSE, 5_00, "USD", "food", "2026-08-02"),
            entry("old", FinanceEntryKind.EXPENSE, 900_000, "UZS", "food", "2026-07-28"),
        )
        val budgets = listOf(
            FinanceBudget("b", "2026-08", "food", 500_000, "UZS", 1, 1),
        )

        val result = calculateFinanceSnapshot(entries, budgets, "2026-08", "UZS")

        assertEquals(1_000_000, result.incomeMinor)
        assertEquals(250_000, result.expenseMinor)
        assertEquals(750_000, result.balanceMinor)
        assertEquals(0.5f, result.categorySpend.single().progress)
    }

    @Test
    fun moneyFormattingIsDeterministicAndIncludesCurrency() {
        assertEquals("1 234 567 UZS", formatMoney(1_234_567, "UZS"))
        assertEquals("−12.34 USD", formatMoney(-1_234, "USD"))
    }

    private fun entry(
        id: String,
        kind: FinanceEntryKind,
        amount: Long,
        currency: String,
        category: String,
        date: String,
    ) = FinanceEntry(
        id = id,
        kind = kind,
        amountMinor = amount,
        currencyCode = currency,
        categoryId = category,
        note = "",
        entryDate = date,
        occurredAt = 1,
        createdAt = 1,
        updatedAt = 1,
    )
}
