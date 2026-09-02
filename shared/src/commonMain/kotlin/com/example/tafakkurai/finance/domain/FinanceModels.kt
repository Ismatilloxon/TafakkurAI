package com.example.tafakkurai.finance.domain

/** A ledger entry. Amount is stored in the currency's smallest unit. */
data class FinanceEntry(
    val id: String,
    val kind: FinanceEntryKind,
    val amountMinor: Long,
    val currencyCode: String,
    val categoryId: String,
    val note: String,
    val entryDate: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class FinanceEntryKind(val id: String) {
    EXPENSE("expense"),
    INCOME("income");

    companion object {
        fun from(value: String): FinanceEntryKind = entries.firstOrNull { it.id == value } ?: EXPENSE
    }
}

data class FinanceBudget(
    val id: String,
    val monthKey: String,
    val categoryId: String,
    val limitMinor: Long,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * A private savings goal. Contributions are stored as immutable rows and summed into
 * [savedMinor], so two offline devices can contribute without one overwriting the other.
 */
data class FinanceGoal(
    val id: String,
    val title: String,
    val targetMinor: Long,
    val savedMinor: Long,
    val currencyCode: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (targetMinor <= 0L) 0f else {
            (savedMinor.toDouble() / targetMinor.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    val isComplete: Boolean get() = savedMinor >= targetMinor
}

data class FinanceCategory(
    val id: String,
    val titleKey: String,
    val icon: String,
    val colorKey: String,
)

val FINANCE_CATEGORIES: List<FinanceCategory> = listOf(
    FinanceCategory("food", "finance.category.food", "utensils", "orange"),
    FinanceCategory("transport", "finance.category.transport", "car", "cyan"),
    FinanceCategory("home", "finance.category.home", "home", "purple"),
    FinanceCategory("health", "finance.category.health", "heart", "rose"),
    FinanceCategory("learning", "finance.category.learning", "bookOpen", "blue"),
    FinanceCategory("giving", "finance.category.giving", "handHeart", "green"),
    FinanceCategory("income", "finance.category.income", "wallet", "green"),
    FinanceCategory("other", "finance.category.other", "circleEllipsis", "gray"),
)

data class CategorySpend(
    val categoryId: String,
    val spentMinor: Long,
    val budgetMinor: Long?,
) {
    val progress: Float?
        get() = budgetMinor?.takeIf { it > 0L }
            ?.let { (spentMinor.toDouble() / it.toDouble()).toFloat().coerceAtLeast(0f) }
}

data class FinanceSnapshot(
    val currencyCode: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val balanceMinor: Long,
    val categorySpend: List<CategorySpend>,
)

/** Pure monthly projection. Currencies are never silently converted or added together. */
fun calculateFinanceSnapshot(
    entries: List<FinanceEntry>,
    budgets: List<FinanceBudget>,
    monthKey: String,
    currencyCode: String,
): FinanceSnapshot {
    val currency = normalizeCurrency(currencyCode) ?: DEFAULT_CURRENCY
    val monthEntries = entries.filter {
        it.entryDate.startsWith("$monthKey-") && it.currencyCode == currency
    }
    val income = monthEntries.filter { it.kind == FinanceEntryKind.INCOME }.sumOf { it.amountMinor }
    val expenseEntries = monthEntries.filter { it.kind == FinanceEntryKind.EXPENSE }
    val expense = expenseEntries.sumOf { it.amountMinor }
    val monthBudgets = budgets.filter { it.monthKey == monthKey && it.currencyCode == currency }
        .associateBy { it.categoryId }
    val categoryIds = (expenseEntries.map { it.categoryId } + monthBudgets.keys).distinct()
    val categorySpend = categoryIds.map { categoryId ->
        CategorySpend(
            categoryId = categoryId,
            spentMinor = expenseEntries.filter { it.categoryId == categoryId }.sumOf { it.amountMinor },
            budgetMinor = monthBudgets[categoryId]?.limitMinor,
        )
    }.sortedWith(compareByDescending<CategorySpend> { it.spentMinor }.thenBy { it.categoryId })

    return FinanceSnapshot(
        currencyCode = currency,
        incomeMinor = income,
        expenseMinor = expense,
        balanceMinor = income - expense,
        categorySpend = categorySpend,
    )
}

/** Parses decimal input without Double rounding. Comma and dot are accepted separators. */
fun parseAmountMinor(input: String, currencyCode: String): Long? {
    val digits = currencyFractionDigits(currencyCode)
    val compact = input.trim().replace(" ", "")
    if (compact.isBlank() || compact.startsWith('-') || compact.count { it == '.' || it == ',' } > 1) {
        return null
    }
    val parts = compact.split('.', ',', limit = 2)
    val whole = parts.getOrNull(0).orEmpty()
    val fraction = parts.getOrNull(1).orEmpty()
    if (whole.isEmpty() || whole.any { !it.isDigit() } || fraction.any { !it.isDigit() }) return null
    if (fraction.length > digits || whole.length > MAX_WHOLE_DIGITS) return null
    val scale = pow10(digits)
    val major = whole.toLongOrNull() ?: return null
    val minorFraction = if (digits == 0) 0L else fraction.padEnd(digits, '0').toLongOrNull() ?: 0L
    // [MAX_WHOLE_DIGITS] bounds this multiplication far below Long.MAX_VALUE.
    val value = major * scale + minorFraction
    return value.takeIf { it in 1..MAX_AMOUNT_MINOR }
}

fun normalizeCurrency(value: String): String? = value.trim().uppercase()
    .takeIf { it.length == 3 && it.all(Char::isLetter) }

fun currencyFractionDigits(currencyCode: String): Int = when (currencyCode.uppercase()) {
    "UZS", "JPY", "KRW" -> 0
    else -> 2
}

fun formatMoney(amountMinor: Long, currencyCode: String): String {
    val currency = normalizeCurrency(currencyCode) ?: DEFAULT_CURRENCY
    val fractionDigits = currencyFractionDigits(currency)
    val sign = if (amountMinor < 0) "−" else ""
    val absolute = if (amountMinor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amountMinor)
    val scale = pow10(fractionDigits)
    val whole = absolute / scale
    val grouped = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
    val fraction = if (fractionDigits == 0) "" else {
        "." + (absolute % scale).toString().padStart(fractionDigits, '0')
    }
    return "$sign$grouped$fraction $currency"
}

private fun pow10(power: Int): Long = when (power) {
    0 -> 1L
    1 -> 10L
    else -> 100L
}

private const val DEFAULT_CURRENCY = "UZS"
private const val MAX_WHOLE_DIGITS = 12
private const val MAX_AMOUNT_MINOR = 999_999_999_999_99L

/** Explicit currencies the ledger can display without silently converting between them. */
val SUPPORTED_FINANCE_CURRENCIES: List<String> = listOf("UZS", "USD", "EUR", "RUB")
