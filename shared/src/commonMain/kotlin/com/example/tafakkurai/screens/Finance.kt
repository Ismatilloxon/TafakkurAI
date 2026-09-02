package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.finance.domain.*
import com.example.tafakkurai.finance.presentation.FinanceMutationResult
import com.example.tafakkurai.finance.presentation.FinanceStateHolder
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.formatJournalDate
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfActionButton
import com.example.tafakkurai.ui.components.TfConfirmationDialog
import com.example.tafakkurai.ui.components.TfTextField

/** Real local ledger: no sample portfolio, synthetic growth, or generated advice. */
@Composable
fun FinanceScreen(store: FinanceRepository, onBack: () -> Unit) {
    val c = LocalTf.current
    val stateHolder = remember(store) { FinanceStateHolder(store) }
    val repositoryRevision = stateHolder.revision
    val uiState = remember(repositoryRevision) { stateHolder.state() }
    val snapshot = uiState.snapshot
    val monthEntries = uiState.monthEntries
    val monthBudgets = uiState.monthBudgets
    val goals = uiState.goals
    var kindId by remember { mutableStateOf(FinanceEntryKind.EXPENSE.id) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("food") }
    var entryErrorKey by remember { mutableStateOf<String?>(null) }
    var budgetAmount by remember { mutableStateOf("") }
    var budgetCategoryId by remember { mutableStateOf("food") }
    var budgetErrorKey by remember { mutableStateOf<String?>(null) }
    var goalTitle by remember { mutableStateOf("") }
    var goalTarget by remember { mutableStateOf("") }
    var goalErrorKey by remember { mutableStateOf<String?>(null) }
    val goalContributionAmounts = remember { mutableStateMapOf<String, String>() }
    val goalContributionErrors = remember { mutableStateMapOf<String, String>() }
    var historyErrorKey by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<FinanceEntry?>(null) }
    var pendingBudgetDelete by remember { mutableStateOf<FinanceBudget?>(null) }
    var pendingGoalDelete by remember { mutableStateOf<FinanceGoal?>(null) }
    var showAllEntries by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("finance.title"), onBack)
        ScrollBody {
            if (uiState.loadFailed) {
                InsetCard {
                    TfText(tr("finance.load_failed"), color = c.rose, size = 13.5f, lineHeight = 1.45f)
                    TfActionButton(
                        label = tr("common.retry"),
                        onClick = stateHolder::refresh,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            InsetCard(pad = 18) {
                Overline(tr("finance.this_month"))
                TfText(
                    formatMoney(snapshot.balanceMinor, snapshot.currencyCode),
                    color = if (snapshot.balanceMinor >= 0) c.fg else c.rose,
                    size = 32f,
                    weight = FontWeight.Bold,
                    family = Fonts.mono,
                    modifier = Modifier.padding(top = 5.dp),
                )
                TfText(tr("finance.month_balance"), color = c.fg2, size = 12.5f)
                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MoneyMetric(
                        tr("finance.income"),
                        formatMoney(snapshot.incomeMinor, snapshot.currencyCode),
                        c.green,
                        Modifier.weight(1f),
                    )
                    MoneyMetric(
                        tr("finance.expenses"),
                        formatMoney(snapshot.expenseMinor, snapshot.currencyCode),
                        c.rose,
                        Modifier.weight(1f),
                    )
                }
                Overline(
                    tr("finance.currency"),
                    modifier = Modifier.padding(top = 16.dp, bottom = 7.dp),
                )
                Segmented(
                    tabs = SUPPORTED_FINANCE_CURRENCIES.map { it to it },
                    value = snapshot.currencyCode,
                    onChange = { currency -> stateHolder.setCurrency(currency) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TfText(
                    tr("finance.currency_note"),
                    color = c.fg3,
                    size = 11.5f,
                    lineHeight = 1.4f,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }

            SectionTitle(tr("finance.record_title"))
            InsetCard {
                Segmented(
                    listOf(
                        FinanceEntryKind.EXPENSE.id to tr("finance.expense"),
                        FinanceEntryKind.INCOME.id to tr("finance.income"),
                    ),
                    kindId,
                    onChange = {
                        kindId = it
                        if (it == FinanceEntryKind.INCOME.id) categoryId = "income"
                        else if (categoryId == "income") categoryId = "food"
                    },
                )
                TfTextField(
                    value = amount,
                    onValueChange = { amount = it.take(16); entryErrorKey = null },
                    placeholder = tr("finance.amount_hint", snapshot.currencyCode),
                    modifier = Modifier.padding(top = 12.dp),
                )
                CategoryPicker(
                    selected = categoryId,
                    onSelect = { categoryId = it },
                    categories = FINANCE_CATEGORIES.filter {
                        if (kindId == FinanceEntryKind.INCOME.id) it.id in setOf("income", "other")
                        else it.id != "income"
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
                TfTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    placeholder = tr("finance.note_hint"),
                    modifier = Modifier.padding(top = 12.dp),
                )
                entryErrorKey?.let { errorKey ->
                    TfText(
                        tr(errorKey),
                        color = c.rose,
                        size = 12.5f,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                TfActionButton(
                    label = tr("finance.save_entry"),
                    onClick = {
                        val result = stateHolder.record(
                            kind = FinanceEntryKind.from(kindId),
                            amountInput = amount,
                            categoryId = categoryId,
                            note = note,
                            currencyCode = snapshot.currencyCode,
                        )
                        entryErrorKey = result.errorKey()
                        if (result == FinanceMutationResult.SUCCESS) {
                            amount = ""
                            note = ""
                        }
                    },
                    enabled = amount.isNotBlank(),
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            SectionTitle(tr("finance.budgets"))
            if (snapshot.categorySpend.isEmpty()) {
                InsetCard {
                    TfText(tr("finance.budgets_empty"), color = c.fg2, size = 13.5f, lineHeight = 1.45f)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    snapshot.categorySpend.forEach { category ->
                        val definition = FINANCE_CATEGORIES.firstOrNull { it.id == category.categoryId }
                        val budget = monthBudgets.firstOrNull { it.categoryId == category.categoryId }
                        val color = c.named(definition?.colorKey ?: "gold")
                        Card(Modifier.fillMaxWidth(), pad = 13) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TfText(
                                    tr(definition?.titleKey ?: "finance.category.other"),
                                    color = c.fg,
                                    size = 14f,
                                    weight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                TfText(
                                    if (category.budgetMinor == null) {
                                        formatMoney(category.spentMinor, snapshot.currencyCode)
                                    } else {
                                        tr(
                                            "finance.spent_of_budget",
                                            formatMoney(category.spentMinor, snapshot.currencyCode),
                                            formatMoney(category.budgetMinor, snapshot.currencyCode),
                                        )
                                    },
                                    color = c.fg2,
                                    size = 11.5f,
                                    family = Fonts.mono,
                                )
                            }
                            category.progress?.let { progress ->
                                ProgressBar(
                                    progress.coerceIn(0f, 1f),
                                    if (progress > 1f) c.rose else color,
                                    modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                                )
                            }
                            if (budget != null) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                        .then(pressScale({ pendingBudgetDelete = budget }, 0.95f)),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TfIcon("trash", size = 15, color = c.rose)
                                    TfText(
                                        tr("finance.delete_budget"),
                                        color = c.rose,
                                        size = 12f,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            InsetCard {
                Overline(tr("finance.set_budget"))
                CategoryPicker(
                    selected = budgetCategoryId,
                    onSelect = { budgetCategoryId = it },
                    categories = FINANCE_CATEGORIES.filter { it.id != "income" },
                    modifier = Modifier.padding(top = 10.dp),
                )
                TfTextField(
                    value = budgetAmount,
                    onValueChange = { budgetAmount = it.take(16); budgetErrorKey = null },
                    placeholder = tr("finance.budget_amount_hint", snapshot.currencyCode),
                    modifier = Modifier.padding(top = 12.dp),
                )
                budgetErrorKey?.let { errorKey ->
                    TfText(tr(errorKey), color = c.rose, size = 12.5f, modifier = Modifier.padding(top = 8.dp))
                }
                TfActionButton(
                    label = tr("finance.save_budget"),
                    onClick = {
                        val result = stateHolder.setBudget(
                            categoryId = budgetCategoryId,
                            limitInput = budgetAmount,
                            currencyCode = snapshot.currencyCode,
                        )
                        budgetErrorKey = result.errorKey()
                        if (result == FinanceMutationResult.SUCCESS) budgetAmount = ""
                    },
                    enabled = budgetAmount.isNotBlank(),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            SectionTitle(tr("finance.goals"))
            if (goals.isEmpty()) {
                InsetCard {
                    TfText(tr("finance.goals_empty"), color = c.fg2, size = 13.5f, lineHeight = 1.45f)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    goals.forEach { goal ->
                        FinanceGoalCard(
                            goal = goal,
                            contribution = goalContributionAmounts[goal.id].orEmpty(),
                            contributionErrorKey = goalContributionErrors[goal.id],
                            onContributionChange = { value ->
                                goalContributionAmounts[goal.id] = value.take(16)
                                goalContributionErrors.remove(goal.id)
                            },
                            onContribute = {
                                val result = stateHolder.contributeToGoal(
                                    goal,
                                    goalContributionAmounts[goal.id].orEmpty(),
                                )
                                val error = result.errorKey()
                                if (error == null) {
                                    goalContributionAmounts.remove(goal.id)
                                    goalContributionErrors.remove(goal.id)
                                } else {
                                    goalContributionErrors[goal.id] = error
                                }
                            },
                            onDelete = { pendingGoalDelete = goal },
                        )
                    }
                }
            }
            InsetCard {
                Overline(tr("finance.goal.create"))
                TfTextField(
                    value = goalTitle,
                    onValueChange = { goalTitle = it.take(80); goalErrorKey = null },
                    placeholder = tr("finance.goal.title_hint"),
                    modifier = Modifier.padding(top = 10.dp),
                )
                TfTextField(
                    value = goalTarget,
                    onValueChange = { goalTarget = it.take(16); goalErrorKey = null },
                    placeholder = tr("finance.goal.target_hint", snapshot.currencyCode),
                    modifier = Modifier.padding(top = 10.dp),
                )
                goalErrorKey?.let { errorKey ->
                    TfText(tr(errorKey), color = c.rose, size = 12.5f, modifier = Modifier.padding(top = 8.dp))
                }
                TfActionButton(
                    label = tr("finance.goal.save"),
                    onClick = {
                        val result = stateHolder.createGoal(
                            title = goalTitle,
                            targetInput = goalTarget,
                            currencyCode = snapshot.currencyCode,
                        )
                        goalErrorKey = result.errorKey()
                        if (result == FinanceMutationResult.SUCCESS) {
                            goalTitle = ""
                            goalTarget = ""
                        }
                    },
                    enabled = goalTitle.isNotBlank() && goalTarget.isNotBlank(),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            SectionTitle(tr("finance.history"))
            historyErrorKey?.let { errorKey ->
                InsetCard {
                    TfText(tr(errorKey), color = c.rose, size = 12.5f)
                }
            }
            if (monthEntries.isEmpty()) {
                InsetCard {
                    TfText(tr("finance.history_empty"), color = c.fg2, size = 13.5f)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val visibleEntries = if (showAllEntries) monthEntries else monthEntries.take(MAX_VISIBLE_ENTRIES)
                    visibleEntries.forEach { entry ->
                        FinanceEntryRow(
                            entry = entry,
                            todayKey = stateHolder.currentDateKey,
                            onDelete = {
                                historyErrorKey = null
                                pendingDelete = entry
                            },
                        )
                    }
                    if (!showAllEntries && monthEntries.size > MAX_VISIBLE_ENTRIES) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .then(pressScale({ showAllEntries = true }, 0.96f)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            TfText(
                                tr("finance.more_entries", monthEntries.size - MAX_VISIBLE_ENTRIES),
                                color = c.accent,
                                size = 12.5f,
                                weight = FontWeight.Medium,
                            )
                            TfIcon(
                                "chevronDown",
                                size = 15,
                                color = c.accent,
                                modifier = Modifier.padding(start = 5.dp),
                            )
                        }
                    }
                }
            }
            PrivacyFooter(tr("finance.privacy"))
        }
    }

    pendingDelete?.let { entry ->
        TfConfirmationDialog(
            title = tr("finance.delete.title"),
            message = tr(
                "finance.delete.message",
                formatMoney(entry.amountMinor, entry.currencyCode),
            ),
            confirmLabel = tr("finance.delete.confirm"),
            cancelLabel = tr("common.cancel"),
            onConfirm = {
                historyErrorKey = if (
                    stateHolder.deleteEntry(entry.id) == FinanceMutationResult.SUCCESS
                ) {
                    null
                } else {
                    "finance.delete_failed"
                }
                pendingDelete = null
            },
            onCancel = { pendingDelete = null },
        )
    }

    pendingBudgetDelete?.let { budget ->
        val category = FINANCE_CATEGORIES.firstOrNull { it.id == budget.categoryId }
        TfConfirmationDialog(
            title = tr("finance.delete_budget.title"),
            message = tr(
                "finance.delete_budget.message",
                tr(category?.titleKey ?: "finance.category.other"),
                formatMoney(budget.limitMinor, budget.currencyCode),
            ),
            confirmLabel = tr("finance.delete.confirm"),
            cancelLabel = tr("common.cancel"),
            onConfirm = {
                budgetErrorKey = if (
                    stateHolder.deleteBudget(budget.id) == FinanceMutationResult.SUCCESS
                ) {
                    null
                } else {
                    "finance.delete_budget_failed"
                }
                pendingBudgetDelete = null
            },
            onCancel = { pendingBudgetDelete = null },
        )
    }

    pendingGoalDelete?.let { goal ->
        TfConfirmationDialog(
            title = tr("finance.goal.delete.title"),
            message = tr("finance.goal.delete.message", goal.title),
            confirmLabel = tr("finance.delete.confirm"),
            cancelLabel = tr("common.cancel"),
            onConfirm = {
                if (stateHolder.deleteGoal(goal.id) != FinanceMutationResult.SUCCESS) {
                    goalContributionErrors[goal.id] = "finance.goal.delete_failed"
                }
                pendingGoalDelete = null
            },
            onCancel = { pendingGoalDelete = null },
        )
    }
}

@Composable
private fun MoneyMetric(label: String, value: String, color: Color, modifier: Modifier) {
    val c = LocalTf.current
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(color.soft(0.12f)).padding(11.dp)) {
        TfText(label, color = c.fg2, size = 11.5f)
        TfText(value, color = color, size = 14f, weight = FontWeight.SemiBold, family = Fonts.mono)
    }
}

@Composable
private fun FinanceGoalCard(
    goal: FinanceGoal,
    contribution: String,
    contributionErrorKey: String?,
    onContributionChange: (String) -> Unit,
    onContribute: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalTf.current
    Card(Modifier.fillMaxWidth(), pad = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                if (goal.isComplete) "check" else "target",
                if (goal.isComplete) c.green else c.gold,
                size = 40,
                iconSize = 18,
            )
            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                TfText(goal.title, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold, maxLines = 2)
                TfText(
                    tr(
                        "finance.goal.saved_of_target",
                        formatMoney(goal.savedMinor, goal.currencyCode),
                        formatMoney(goal.targetMinor, goal.currencyCode),
                    ),
                    color = c.fg2,
                    size = 11.5f,
                    family = Fonts.mono,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (goal.isComplete) {
                TfText(
                    tr("finance.goal.complete"),
                    color = c.green,
                    size = 11.5f,
                    weight = FontWeight.SemiBold,
                )
            }
        }
        ProgressBar(
            value = goal.progress,
            color = if (goal.isComplete) c.green else c.gold,
            modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
        )
        if (!goal.isComplete) {
            TfTextField(
                value = contribution,
                onValueChange = onContributionChange,
                placeholder = tr("finance.goal.contribution_hint", goal.currencyCode),
                modifier = Modifier.padding(top = 11.dp),
            )
            contributionErrorKey?.let { errorKey ->
                TfText(tr(errorKey), color = c.rose, size = 12f, modifier = Modifier.padding(top = 7.dp))
            }
            TfActionButton(
                label = tr("finance.goal.add_contribution"),
                onClick = onContribute,
                enabled = contribution.isNotBlank(),
                modifier = Modifier.padding(top = 10.dp),
            )
        } else if (contributionErrorKey != null) {
            TfText(
                tr(contributionErrorKey),
                color = c.rose,
                size = 12f,
                modifier = Modifier.padding(top = 7.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).then(pressScale(onDelete, 0.94f)),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TfIcon("trash", size = 15, color = c.rose)
            TfText(
                tr("finance.goal.delete"),
                color = c.rose,
                size = 12f,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun CategoryPicker(
    selected: String,
    onSelect: (String) -> Unit,
    categories: List<FinanceCategory>,
    modifier: Modifier = Modifier,
) {
    val c = LocalTf.current
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val active = category.id == selected
            Row(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (active) c.accent.soft(0.18f) else c.card3)
                    .border(0.5.dp, if (active) c.accent else c.sep, RoundedCornerShape(999.dp))
                    .then(pressScale({ onSelect(category.id) }, 0.95f))
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TfIcon(category.icon, size = 14, color = if (active) c.accent else c.fg3)
                TfText(tr(category.titleKey), color = if (active) c.accent else c.fg2, size = 12.5f)
            }
        }
    }
}

@Composable
private fun FinanceEntryRow(entry: FinanceEntry, todayKey: String, onDelete: () -> Unit) {
    val c = LocalTf.current
    val strings = LocalStrings.current
    val category = FINANCE_CATEGORIES.firstOrNull { it.id == entry.categoryId }
    val income = entry.kind == FinanceEntryKind.INCOME
    Card(Modifier.fillMaxWidth(), pad = 13) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            IconBadge(category?.icon ?: "wallet", c.named(category?.colorKey ?: "gold"), size = 40, iconSize = 18)
            Column(Modifier.weight(1f)) {
                TfText(tr(category?.titleKey ?: "finance.category.other"), color = c.fg, size = 14f, weight = FontWeight.Medium)
                TfText(
                    entry.note.ifBlank { strings.formatJournalDate(entry.entryDate, todayKey) },
                    color = c.fg2,
                    size = 12f,
                    maxLines = 1,
                )
            }
            TfText(
                (if (income) "+" else "−") + formatMoney(entry.amountMinor, entry.currencyCode),
                color = if (income) c.green else c.rose,
                size = 13f,
                family = Fonts.mono,
                weight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Row(
                Modifier.heightIn(min = 44.dp).then(pressScale(onDelete, 0.92f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TfIcon("trash", size = 15, color = c.rose)
                TfText(tr("finance.delete_entry"), color = c.rose, size = 12f)
            }
        }
    }
}

private fun FinanceMutationResult.errorKey(): String? = when (this) {
    FinanceMutationResult.SUCCESS -> null
    FinanceMutationResult.INVALID_AMOUNT -> "finance.invalid_amount"
    FinanceMutationResult.INVALID_TITLE -> "finance.goal.invalid_title"
    FinanceMutationResult.GOAL_COMPLETE -> "finance.goal.already_complete"
    FinanceMutationResult.SAVE_FAILED -> "finance.save_failed"
}

private const val MAX_VISIBLE_ENTRIES = 50
