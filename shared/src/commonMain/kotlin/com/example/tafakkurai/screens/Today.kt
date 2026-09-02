package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.*
import com.example.tafakkurai.adhkar.AdhkarCategory
import com.example.tafakkurai.adhkar.AdhkarStore
import com.example.tafakkurai.dhikr.DhikrStore
import com.example.tafakkurai.gratitude.GratitudeStore
import com.example.tafakkurai.habits.HabitStore
import com.example.tafakkurai.finance.domain.formatMoney
import com.example.tafakkurai.finance.domain.FinanceRepository
import com.example.tafakkurai.prayer.LocationPrayerScheduleRepository
import com.example.tafakkurai.prayer.PrayerMark
import com.example.tafakkurai.prayer.PrayerMarkStore
import com.example.tafakkurai.prayer.SalahProgressHero
import com.example.tafakkurai.quran.QuranProgressStore
import com.example.tafakkurai.quran.TOTAL_AYAH_COUNT
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfIconButton
import com.example.tafakkurai.ui.components.TfSecondaryButton
import com.example.tafakkurai.balance.BalanceCalculator
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.formatHeaderDate
import com.example.tafakkurai.i18n.formatJournalDate
import com.example.tafakkurai.i18n.greetingKey
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.prayer.currentSecondsOfDay
import com.example.tafakkurai.prayer.deviceDate
import com.example.tafakkurai.prayer.localizedPrayerName
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.sleep.SleepTime
import com.example.tafakkurai.sleep.SleepViewModel
import com.example.tafakkurai.social.domain.SocialRepository
import com.example.tafakkurai.tasks.data.TaskStore
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.mp.KoinPlatform.getKoin

/**
 * "Xayrli kech, Aziz" — greeting derived from the device clock, name from the signed-in
 * profile (cached by [App] so the header still reads correctly offline).
 *
 * Both used to come from the `TUSER` mock, which hardcoded "Xayrli tong" (good morning) and
 * the name "Aziz" regardless of the hour or who was signed in.
 */
@Composable
private fun greetingTitle(): String {
    // Telegram hands back "first last"; the header shows the first name only.
    val name = remember {
        getKoin().get<PrefPersistence>().get(AppPrefs.DISPLAY_NAME).orEmpty().trim().substringBefore(' ')
    }
    val greeting = tr(greetingKey(currentSecondsOfDay()))
    return if (name.isBlank()) greeting else "$greeting, $name"
}

/** Today's date in the active language — "Dushanba, 3-avgust". */
@Composable
private fun headerDate(): String = LocalStrings.current.formatHeaderDate(remember { deviceDate(0) })

private data class BlockDef(val key: String, val titleKey: String, val color: String)

private val ALL_BLOCKS = listOf(
    BlockDef("prayer", "today.block.prayer", "gold"),
    BlockDef("quran", "today.block.quran", "green"),
    BlockDef("ai", "today.block.ai", "purple"),
    BlockDef("plan", "today.block.plan", "teal"),
    BlockDef("balance", "today.block.balance", "cyan"),
    BlockDef("habits", "today.block.habits", "green"),
    BlockDef("gratitude", "today.block.gratitude", "gold"),
    BlockDef("circle", "today.block.social", "cyan"),
    BlockDef("finance", "today.block.finance", "gold"),
    BlockDef("sleep", "today.block.sleep", "green"),
)
private val DEFAULT_ORDER = listOf("prayer", "quran", "ai", "plan", "balance", "habits")
private val ALL_BLOCK_KEYS = ALL_BLOCKS.mapTo(linkedSetOf(), BlockDef::key)
private const val DASHBOARD_LAYOUT_VERSION = "v1:"

private data class RestoredDashboardLayout(
    val order: List<String>,
    val shouldPersistCanonicalValue: Boolean,
)

/**
 * Owns the user's device-level Today layout and exposes only an immutable list to Compose.
 *
 * The value is versioned so a future schema can migrate deliberately. Unversioned comma-separated
 * values are accepted as a legacy format, while an unknown future version is never overwritten by
 * an older app. Unknown/duplicate block ids in the current format are removed before rendering.
 */
@Stable
private class TodayDashboardLayout(private val prefs: PrefPersistence) {
    private val mutableOrder = mutableStateListOf<String>()
    val order: List<String> = mutableOrder

    init {
        val restored = restoreDashboardOrder(prefs.get(AppPrefs.TODAY_DASHBOARD_ORDER))
        mutableOrder.addAll(restored.order)
        if (restored.shouldPersistCanonicalValue) persist()
    }

    fun move(key: String, offset: Int): Pair<Int, Int>? {
        if (offset != -1 && offset != 1) return null
        val from = mutableOrder.indexOf(key)
        val to = from + offset
        if (from < 0 || to !in mutableOrder.indices) return null
        mutableOrder[from] = mutableOrder[to]
        mutableOrder[to] = key
        persist()
        return from to to
    }

    fun remove(key: String): Boolean {
        if (!mutableOrder.remove(key)) return false
        persist()
        return true
    }

    fun add(key: String): Boolean {
        if (key !in ALL_BLOCK_KEYS || key in mutableOrder) return false
        mutableOrder.add(key)
        persist()
        return true
    }

    fun reset() {
        mutableOrder.clear()
        mutableOrder.addAll(DEFAULT_ORDER)
        persist()
    }

    private fun persist() {
        prefs.put(
            AppPrefs.TODAY_DASHBOARD_ORDER,
            DASHBOARD_LAYOUT_VERSION + mutableOrder.joinToString(","),
        )
    }
}

private fun restoreDashboardOrder(raw: String?): RestoredDashboardLayout {
    if (raw == null) {
        return RestoredDashboardLayout(DEFAULT_ORDER, shouldPersistCanonicalValue = true)
    }

    val isCurrentVersion = raw.startsWith(DASHBOARD_LAYOUT_VERSION)
    val hasUnknownVersion = !isCurrentVersion && raw.matches(Regex("v\\d+:.*"))
    if (hasUnknownVersion) {
        return RestoredDashboardLayout(DEFAULT_ORDER, shouldPersistCanonicalValue = false)
    }

    val payload = if (isCurrentVersion) raw.removePrefix(DASHBOARD_LAYOUT_VERSION) else raw
    // `v1:` is an intentional layout with every optional block removed; a blank legacy value is
    // corrupt and migrates to the default instead.
    if (isCurrentVersion && payload.isEmpty()) {
        return RestoredDashboardLayout(emptyList(), shouldPersistCanonicalValue = false)
    }

    val validated = payload.split(',')
        .map(String::trim)
        .filter { it in ALL_BLOCK_KEYS }
        .distinct()
    val restored = validated.ifEmpty { DEFAULT_ORDER }
    val canonical = DASHBOARD_LAYOUT_VERSION + restored.joinToString(",")
    return RestoredDashboardLayout(
        order = restored,
        shouldPersistCanonicalValue = raw != canonical,
    )
}

@Composable
fun TodayScreen(
    financeStore: FinanceRepository,
    socialStore: SocialRepository,
    sleepViewModel: SleepViewModel,
    onOpenModule: (String) -> Unit,
    onCoach: () -> Unit,
    onProfile: () -> Unit = {},
    onOpenPrayer: () -> Unit = {},
    onOpenQuran: () -> Unit = {},
) {
    val c = LocalTf.current
    val activity = remember { getKoin().get<ActivityLog>() }
    val prefs = remember { getKoin().get<PrefPersistence>() }
    val dashboardLayout = remember(prefs) { TodayDashboardLayout(prefs) }
    var customize by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Overline(tr("today.bismillah"))
                TfText(
                    greetingTitle(),
                    color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display,
                    modifier = Modifier.padding(top = 3.dp),
                )
                TfText(headerDate(), color = c.fg2, size = 13f, modifier = Modifier.padding(top = 2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.then(pressScale({
                    customize = !customize
                    activity.record(
                        "today",
                        if (customize) "dashboard_customization_opened" else "dashboard_customization_closed",
                    )
                }, 0.95f))) {
                    Pill(tr(if (customize) "common.done" else "today.customize"), c.accent, c.accent.soft(0.16f))
                }
                UserAvatar(onClick = onProfile)
            }
        }

        if (customize) {
            CustomizeBoard(
                order = dashboardLayout.order,
                onMove = { key, offset ->
                    dashboardLayout.move(key, offset)?.let { (from, to) ->
                        activity.record(
                            "today",
                            "dashboard_block_moved",
                            buildJsonObject { put("block", key); put("from", from); put("to", to) },
                        )
                    }
                },
                onRemove = { key ->
                    if (dashboardLayout.remove(key)) {
                        activity.record(
                            "today",
                            "dashboard_block_removed",
                            buildJsonObject { put("block", key) },
                        )
                    }
                },
                onAdd = { key ->
                    if (dashboardLayout.add(key)) {
                        activity.record(
                            "today",
                            "dashboard_block_added",
                            buildJsonObject { put("block", key) },
                        )
                    }
                },
                onReset = {
                    dashboardLayout.reset()
                    activity.record("today", "dashboard_layout_reset")
                },
            )
        } else {
            ScrollScreen {
                // Hero ring — live, fills clockwise toward the next prayer
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    SalahProgressHero(onClick = onOpenPrayer)
                }
                TfText(
                    tr("today.salah_anchor"),
                    color = c.fg3, size = 12.5f, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                dashboardLayout.order.forEach { key ->
                    TodayBlock(
                        key = key,
                        financeStore = financeStore,
                        socialStore = socialStore,
                        sleepViewModel = sleepViewModel,
                        onOpenModule = onOpenModule,
                        onCoach = onCoach,
                        onOpenQuran = onOpenQuran,
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayBlock(
    key: String,
    financeStore: FinanceRepository,
    socialStore: SocialRepository,
    sleepViewModel: SleepViewModel,
    onOpenModule: (String) -> Unit,
    onCoach: () -> Unit,
    onOpenQuran: () -> Unit = {},
) {
    val def = ALL_BLOCKS.first { it.key == key }
    val c = LocalTf.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AspectHeading(tr(def.titleKey), c.named(def.color))
        when (key) {
            "prayer" -> InsetCard {
                // Live, GPS-calculated schedule — same source the hero ring uses, so
                // the card and the ring can never disagree. Sunrise is not a salah.
                val repo = remember { getKoin().get<LocationPrayerScheduleRepository>() }
                // Completion marks from the shared store, so the card mirrors the ring/tracker.
                val markStore = remember { getKoin().get<PrayerMarkStore>() }
                val prayers = repo.schedule(0).filter { it.key != "sunrise" }
                if (prayers.isEmpty()) {
                    TfText(tr("prayer.location.required"), color = c.fg2, size = 13f, lineHeight = 1.4f)
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    prayers.forEach { p ->
                        // Tap cycles the status: incomplete → on-time → jamaat → incomplete.
                        // Same shared store the hero ring and tracker use, so all three agree
                        // and the change is queued for backend sync.
                        val mark = markStore.stateOf(p.key)
                        val meta = PRAYER_STATES.getValue(mark.id)
                        val on = mark != PrayerMark.NONE
                        Column(
                            Modifier.weight(1f).then(pressScale({ markStore.cycle(p.key) }, 0.94f)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(if (on) meta.color.soft(0.18f) else c.card3),
                                contentAlignment = Alignment.Center,
                            ) { TfIcon(meta.icon, size = 17, color = if (on) meta.color else c.fg3) }
                            TfText(localizedPrayerName(p.key), color = if (on) c.fg else c.fg3, size = 11f, weight = FontWeight.Medium)
                            TfText(p.time, color = c.fg3, size = 10f, family = Fonts.mono)
                        }
                    }
                }
            }
            // The spiritual dashboard. These aggregates used to be repeated inside the
            // Qurʼan / Dhikr / Adhkar tabs; they live here now so those screens can answer
            // "what can I do right now?" and this one answers "how consistent have I been?".
            // Every number reads from the same shared stores the feature screens write to.
            "quran" -> {
                val quran = remember { getKoin().get<QuranProgressStore>() }
                val dhikr = remember { getKoin().get<DhikrStore>() }
                val adhkar = remember { getKoin().get<AdhkarStore>() }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatTile("${quran.overallPercent}%", tr("today.quran.read"), c.green)
                        StatTile(groupThousands(dhikr.todayTotal), tr("today.quran.dhikr_today"), c.gold)
                        StatTile("${dhikr.streak}", tr("today.quran.day_streak"), c.warn)
                    }
                    InsetCard(onClick = onOpenQuran) {
                        val last = quran.lastRead
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Overline(tr("today.quran.last_read"))
                                TfText(
                                    if (last != null) {
                                        tr("today.quran.last_read_value", last.surahName, last.ayahNumber)
                                    } else {
                                        tr("today.quran.not_started")
                                    },
                                    color = if (last != null) c.fg else c.fg2,
                                    size = 15f, weight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                TfText(
                                    tr(
                                        "today.quran.progress",
                                        quran.totalRead,
                                        TOTAL_AYAH_COUNT,
                                        quran.completedSurahCount,
                                    ),
                                    color = c.fg3, size = 11.5f, modifier = Modifier.padding(top = 2.dp),
                                )
                                ProgressBar(
                                    quran.overallProgress, c.green,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                            TfIcon("chevronRight", size = 18, color = c.fg3)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AdhkarMini(tr("today.adhkar.morning"), "sunrise", adhkar, AdhkarCategory.MORNING, onOpenQuran)
                        AdhkarMini(tr("today.adhkar.evening"), "sunset", adhkar, AdhkarCategory.EVENING, onOpenQuran)
                    }
                }
            }
            "ai" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = HPAD.dp)) {
                ProactiveCard(tr("today.ai.local_action_hint"))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.card)
                        .border(0.5.dp, c.sep, RoundedCornerShape(14.dp))
                        .then(pressScale(onCoach, 0.985f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(c.accent.soft(0.16f)), contentAlignment = Alignment.Center) {
                        TfIcon("sparkles", size = 18, color = c.accent)
                    }
                    TfText(tr("today.ai.ask"), color = c.fg, size = 14.5f, modifier = Modifier.weight(1f))
                    TfIcon("chevronRight", size = 18, color = c.fg3)
                }
            }
            "plan" -> ConnectedTodayPlan(onOpenModule)
            "balance" -> InsetCard {
                // Computed from recorded activity — prayers marked, dhikr counted, ayahs
                // read, gratitude written. Every store below keeps Compose state, so this
                // card moves the moment any of them changes.
                val balance = remember {
                    val activityLog = getKoin().get<ActivityLog>()
                    BalanceCalculator(
                        prayers = getKoin().get(),
                        quran = getKoin().get(),
                        dhikr = getKoin().get(),
                        adhkar = getKoin().get(),
                        gratitude = getKoin().get(),
                        activity = activityLog,
                    )
                }
                // ActivityLog is a Flow rather than Compose state. Collecting it here makes
                // sleep/finance/social activity move the radar immediately after the write.
                val activityEvents by remember { getKoin().get<ActivityLog>().events }.collectAsState()
                val scores = remember(activityEvents) { balance.current() }
                val untracked = scores.count { !it.isTracked }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                            RadarChart(
                                scores.map { it.value ?: 0f },
                                scores.map { balanceColor(it.key, it.isTracked, c) },
                                c.sep, size = 150,
                            )
                        }
                        Column(
                            Modifier.weight(1f).padding(start = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Tracked axes first, weakest at the top — that is the one worth
                            // acting on. Untracked axes sink to the bottom.
                            scores.sortedWith(compareBy({ !it.isTracked }, { it.value ?: 0f })).forEach { d ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Box(
                                        Modifier.size(8.dp).clip(CircleShape)
                                            .background(balanceColor(d.key, d.isTracked, c)),
                                    )
                                    TfText(
                                        tr("balance.dimension.${d.key}"),
                                        color = if (d.isTracked) c.fg2 else c.fg3,
                                        size = 12.5f, modifier = Modifier.weight(1f),
                                    )
                                    TfText(
                                        d.percent?.toString() ?: "—",
                                        color = if (d.isTracked) c.fg else c.fg3,
                                        size = 12.5f, family = Fonts.mono,
                                        weight = if (d.isTracked) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                    // The weakest tracked dimension, with the numbers behind it — so the
                    // score is explainable rather than a mood.
                    scores.filter { it.isTracked }.minByOrNull { it.value ?: 1f }?.let { weakest ->
                        TfText(
                            tr("today.balance.weakest", tr("balance.dimension.${weakest.key}")),
                            color = c.fg3, size = 11.5f, lineHeight = 1.45f,
                        )
                    }
                    if (untracked > 0) {
                        TfText(
                            tr("today.balance.untracked", untracked),
                            color = c.fg3, size = 11.5f, lineHeight = 1.45f,
                        )
                    }
                }
            }
            "habits" -> {
                // Live from the shared HabitStore — the same single instance the Habits
                // module writes, so completing a habit there moves this card immediately
                // and completing one here is real everywhere. THABITS (the four-item mock
                // this block used to render) is no longer referenced.
                val habitStore = remember { getKoin().get<HabitStore>() }
                val revision = habitStore.revision
                val todayKey = habitStore.today()
                val due = habitStore.scheduledOn(todayKey)
                val habitStats = remember(revision) { habitStore.statsAll() }
                val doneCount = due.count { habitStats[it.id]?.isDoneToday == true }

                if (due.isEmpty()) {
                    InsetCard(onClick = { onOpenModule("Habits") }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            TfIcon("activity", size = 16, color = c.green)
                            TfText(
                                tr(
                                    if (habitStore.habits.isEmpty()) {
                                        "today.habits.start"
                                    } else {
                                        "today.habits.none_scheduled"
                                    },
                                ),
                                color = c.fg2, size = 13.5f, modifier = Modifier.weight(1f),
                            )
                            TfIcon("chevronRight", size = 16, color = c.fg3)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                .then(pressScale({ onOpenModule("Habits") }, 0.99f)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TfText(
                                tr("today.habits.done", doneCount, due.size),
                                color = c.fg2, size = 13f, modifier = Modifier.weight(1f),
                            )
                            TfIcon("chevronRight", size = 16, color = c.fg3)
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = HPAD.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // The four most at-risk first: still open, longest streak. The
                            // point of this card is to catch the one about to be lost, not
                            // to list them alphabetically.
                            due.sortedWith(
                                compareBy<com.example.tafakkurai.habits.Habit> {
                                    habitStats[it.id]?.isDoneToday == true
                                }.thenByDescending { habitStats[it.id]?.current ?: 0 }
                            ).take(4).forEach { h ->
                                val hs = habitStats[h.id]
                                val tint = c.named(h.colorKey)
                                Card(
                                    Modifier.weight(1f), pad = 12,
                                    onClick = { onOpenModule("Habits") },
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        RingGauge(
                                            if (hs?.isDoneToday == true) 1f else (hs?.rate ?: 0f),
                                            tint, c.card3, strokeWidth = 4f, size = 40,
                                        ) { TfIcon(h.icon, size = 16, color = tint) }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            TfIcon("flame", size = 11, color = c.warn)
                                            TfText("${hs?.current ?: 0}", color = c.fg2, size = 11f, family = Fonts.mono)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "gratitude" -> {
                // Live from the shared journal: what the user wrote today, not a sample.
                val gratitude = remember { getKoin().get<GratitudeStore>() }
                val todays = gratitude.today()
                InsetCard(onClick = { onOpenModule("Gratitude") }) {
                    if (todays.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            TfIcon("leaf", size = 15, color = c.gold)
                            TfText(tr("today.gratitude.empty"), color = c.fg2, size = 13.5f)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            todays.take(3).forEach { entry ->
                                Pill(entry.text, c.gold, c.gold.soft(0.16f), leading = { TfIcon("leaf", size = 11, color = c.gold) })
                            }
                        }
                    }
                }
            }
            "circle" -> {
                val summary = socialStore.connectionSummaries().firstOrNull()
                InsetCard(onClick = { onOpenModule("Social") }) {
                    if (summary == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TfIcon("users", size = 18, color = c.cyan)
                            TfText(tr("today.social.empty"), color = c.fg2, size = 13.5f, modifier = Modifier.weight(1f))
                            TfIcon("chevronRight", size = 17, color = c.fg3)
                        }
                    } else {
                        val connection = summary.connection
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(connection.name, size = 38)
                            Column(Modifier.weight(1f)) {
                                TfText(connection.name, color = c.fg, size = 13.5f, weight = FontWeight.SemiBold)
                                TfText(
                                    if (connection.lastContactDate.isBlank()) {
                                        tr("social.never_checked_in")
                                    } else {
                                        tr(
                                            "social.last_check_in",
                                            LocalStrings.current.formatJournalDate(
                                                connection.lastContactDate,
                                                socialStore.currentDateKey(),
                                            ),
                                        )
                                    },
                                    color = c.fg2,
                                    size = 12.5f,
                                )
                            }
                            TfIcon("chevronRight", size = 17, color = c.fg3)
                        }
                    }
                }
            }
            "finance" -> {
                // Use the user's persisted ledger selection. Existing amounts remain in their
                // original currencies; snapshot never performs an implicit conversion.
                val snapshot = financeStore.snapshot(currencyCode = financeStore.preferredCurrency)
                InsetCard(onClick = { onOpenModule("Finance") }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        IconBadge("wallet", c.gold, size = 42)
                        Column(Modifier.weight(1f)) {
                            TfText(
                                formatMoney(snapshot.balanceMinor, snapshot.currencyCode),
                                color = if (snapshot.balanceMinor >= 0L) c.fg else c.rose,
                                size = 19f,
                                weight = FontWeight.Bold,
                                family = Fonts.mono,
                            )
                            TfText(
                                if (financeStore.loadFailed) tr("finance.load_failed") else tr("finance.month_balance"),
                                color = if (financeStore.loadFailed) c.rose else c.fg2,
                                size = 12f,
                            )
                        }
                        TfIcon("chevronRight", size = 17, color = c.fg3)
                    }
                }
            }
            "sleep" -> {
                val night = sleepViewModel.lastNight
                InsetCard(onClick = { onOpenModule("Sleep") }) {
                    if (night == null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TfIcon("moonStars", size = 18, color = c.purple)
                            TfText(tr("today.sleep.empty"), color = c.fg2, size = 13.5f, modifier = Modifier.weight(1f))
                            TfIcon("chevronRight", size = 17, color = c.fg3)
                        }
                    } else {
                        val score = sleepViewModel.sleepScore.value
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RingGauge(score / 100f, c.purple, c.card3, strokeWidth = 5f, size = 48) {
                                TfText("$score", color = c.fg, size = 15f, weight = FontWeight.Bold, family = Fonts.mono)
                            }
                            Column(Modifier.weight(1f)) {
                                TfText(
                                    tr("today.sleep.asleep", SleepTime.formatDurationCompact(night.asleepMinutes)),
                                    color = c.fg,
                                    size = 14.5f,
                                    weight = FontWeight.SemiBold,
                                )
                                TfText(
                                    tr(
                                        "today.sleep.bed_wake",
                                        SleepTime.formatClockAt(night.inBedStartMs),
                                        SleepTime.formatClockAt(night.inBedEndMs),
                                    ),
                                    color = c.fg2,
                                    size = 12.5f,
                                )
                            }
                            TfIcon("chevronRight", size = 17, color = c.fg3)
                        }
                    }
                }
            }
        }
    }
}

private data class ConnectedPlanRow(
    val id: String,
    val item: DayPlanItem,
    val route: String,
)

/**
 * Today's plan from actual stores and the durable activity stream.
 *
 * Prayer and habit source rows win over their activity events, because those stores also
 * represent undo and schedule state. Every other qualifying action is projected by
 * ActivityLog. There is no prototype fallback: an empty feature stays honestly empty.
 */
@Composable
private fun ConnectedTodayPlan(onOpenModule: (String) -> Unit) {
    val c = LocalTf.current
    val strings = LocalStrings.current
    val prayerSchedule = remember { getKoin().get<LocationPrayerScheduleRepository>() }
    val prayerMarks = remember { getKoin().get<PrayerMarkStore>() }
    val habits = remember { getKoin().get<HabitStore>() }
    val tasks = remember { getKoin().get<TaskStore>() }
    val activity = remember { getKoin().get<ActivityLog>() }
    val activityEvents by activity.events.collectAsState()
    val habitRevision = habits.revision
    val taskRevision = tasks.revision
    val date = habits.today()
    val scheduledPrayers = prayerSchedule.schedule(0).filter { it.key != "sunrise" }
    val prayerStateSnapshot = scheduledPrayers.associate { it.key to prayerMarks.stateOf(it.key) }

    val rows = remember(activityEvents, habitRevision, taskRevision, date, strings, scheduledPrayers, prayerStateSnapshot) {
        val prayerRows = scheduledPrayers.map { prayer ->
                val mark = prayerStateSnapshot[prayer.key] ?: PrayerMark.NONE
                ConnectedPlanRow(
                    id = "prayer:${prayer.key}:$date",
                    item = DayPlanItem(
                        type = "salah",
                        name = prayer.name,
                        time = prayer.time,
                        cat = "spiritual",
                        done = mark != PrayerMark.NONE,
                        state = if (mark == PrayerMark.NONE) "upcoming" else "done",
                    ),
                    route = "prayer",
                )
            }

        val habitStats = habits.statsAll()
        val habitRows = habits.scheduledOn(date).map { habit ->
            ConnectedPlanRow(
                id = "habit:${habit.id}:$date",
                item = DayPlanItem(
                    type = "task",
                    title = habit.name,
                    time = habit.timeLocal,
                    cat = habitAspect(habit.categoryId),
                    done = habitStats[habit.id]?.isDoneToday == true,
                ),
                route = "habits",
            )
        }

        val taskRows = tasks.onDate(date).map { task ->
            ConnectedPlanRow(
                id = "task:${task.id}",
                item = DayPlanItem(
                    type = "task",
                    title = task.title,
                    time = task.dueTime,
                    cat = taskAspect(task.categoryId),
                    done = task.completed,
                ),
                route = "do",
            )
        }

        val eventRows = activity.todayPlan(date)
            // Prayer and habits already come from their richer source-of-truth rows above.
            .filterNot {
                it.route == "prayer" || it.route == "habits" || it.planKey.startsWith("task:")
            }
            .map { event ->
                val title = event.titleArgument?.let { strings.format(event.titleKey, it) }
                    ?: strings[event.titleKey]
                ConnectedPlanRow(
                    id = event.id,
                    item = DayPlanItem(
                        type = "task",
                        title = title,
                        time = SleepTime.formatClockAt(event.occurredAt),
                        cat = event.aspect,
                        done = event.completed,
                    ),
                    route = event.route,
                )
            }

        (prayerRows + habitRows + taskRows + eventRows).sortedWith(
            compareBy<ConnectedPlanRow> { it.item.time.isBlank() }
                .thenBy { it.item.time.ifBlank { "99:99" } }
                .thenBy { it.item.title ?: it.item.name.orEmpty() },
        )
    }

    InsetCard {
        if (rows.isEmpty()) {
            TfText(strings["today.plan.empty"], color = c.fg2, size = 13.5f)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.take(MAX_TODAY_PLAN_ROWS).forEach { row ->
                    key(row.id) {
                        PlanRow(row.item) { onOpenModule(row.route) }
                    }
                }
                if (rows.size > MAX_TODAY_PLAN_ROWS) {
                    TfText(
                        strings.format("today.plan.more", rows.size - MAX_TODAY_PLAN_ROWS),
                        color = c.fg3,
                        size = 11.5f,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
    }
}

private fun habitAspect(categoryId: String): String = when (categoryId) {
    "health", "fitness", "sleep", "physical" -> "physical"
    "relationships", "family", "social" -> "social"
    "money", "finance", "financial" -> "financial"
    "quran", "faith", "spiritual" -> "spiritual"
    "learning", "study", "educational" -> "educational"
    else -> "productivity"
}

private fun taskAspect(categoryId: String): String = when (categoryId) {
    "health", "fitness", "sport" -> "physical"
    "family", "relationships", "social" -> "social"
    "money", "finance" -> "financial"
    "quran", "faith" -> "spiritual"
    "learning", "study" -> "educational"
    else -> "productivity"
}

private const val MAX_TODAY_PLAN_ROWS = 10

/** Compact morning/evening adhkar tile — count, ring and a way into the list. */
@Composable
private fun RowScope.AdhkarMini(
    label: String,
    icon: String,
    store: AdhkarStore,
    category: AdhkarCategory,
    onOpen: () -> Unit,
) {
    val c = LocalTf.current
    val done = store.doneCount(category)
    val total = store.totalCount(category)
    Card(Modifier.weight(1f), pad = 13, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RingGauge(store.progressOf(category), c.green, c.card3, strokeWidth = 4f, size = 36) {
                TfIcon(icon, size = 15, color = c.gold)
            }
            Column(Modifier.weight(1f)) {
                TfText("$done / $total", color = c.fg, size = 15f, weight = FontWeight.Bold, family = Fonts.mono)
                TfText(label, color = c.fg3, size = 11f, maxLines = 1)
            }
        }
    }
}

/** 1234 → "1,234". Kotlin common has no locale formatter and this only needs grouping. */
private fun groupThousands(value: Int): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return digits.reversed().chunked(3).joinToString(",").reversed()
}

@Composable
private fun PlanRow(item: DayPlanItem, onTap: () -> Unit) {
    val c = LocalTf.current
    val salah = item.type == "salah"
    val tint = when (item.cat) {
        "physical" -> c.green; "social" -> c.cyan; "financial" -> c.gold
        "educational" -> c.purple; "spiritual" -> c.gold; else -> c.accent
    }
    Row(
        Modifier.fillMaxWidth().then(pressScale(onTap, 0.985f)).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        TfText(item.time, color = if (item.current) c.accent else c.fg3, size = 12f, family = Fonts.mono, modifier = Modifier.width(42.dp))
        Box(
            Modifier.size(if (salah) 26.dp else 22.dp).clip(CircleShape)
                .background(if (salah) c.gold.soft(0.16f) else tint.soft(0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) TfIcon("check", size = 13, color = c.green)
            else TfIcon(if (salah) "moonStars" else "circle", size = 13, color = if (salah) c.gold else tint)
        }
        Column(Modifier.weight(1f)) {
            TfText(
                item.name ?: item.title ?: "", color = if (item.done) c.fg3 else c.fg, size = 14f,
                weight = if (salah) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (!salah && item.dur != null) TfText(item.dur, color = c.fg3, size = 11.5f)
        }
        if (item.current) Pill(tr("today.plan.now"), c.accent, c.accent.soft(0.16f))
        else if (salah && item.state == "next") Pill(tr("today.plan.next"), c.gold, c.gold.soft(0.16f))
    }
}

// ── Customize board ──────────────────────────────────────────────────────────
@Composable
private fun ColumnScope.CustomizeBoard(
    order: List<String>,
    onMove: (key: String, offset: Int) -> Unit,
    onRemove: (key: String) -> Unit,
    onAdd: (key: String) -> Unit,
    onReset: () -> Unit,
) {
    val c = LocalTf.current
    ScrollScreen {
        Overline(tr("today.customize.blocks"))
        Box(Modifier.padding(horizontal = 20.dp).offset(y = (-8).dp)) {
            TfText(tr("today.customize.description"), color = c.fg2, size = 13f, lineHeight = 1.5f)
        }
        order.toList().forEachIndexed { i, key ->
            val def = ALL_BLOCKS.first { it.key == key }
            InsetCard(pad = 12) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.named(def.color)))
                    val title = tr(def.titleKey)
                    TfText(title, color = c.fg, size = 15f, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TfIconButton("chevronUp", tr("today.customize.move_up", title), onClick = {
                        if (i > 0) onMove(key, -1)
                    })
                    TfIconButton("chevronDown", tr("today.customize.move_down", title), onClick = {
                        if (i < order.size - 1) onMove(key, 1)
                    })
                    TfIconButton("x", tr("today.customize.remove", title), destructive = true, onClick = {
                        onRemove(key)
                    })
                }
            }
        }
        SectionTitle(tr("today.customize.add_blocks"))
        Column(Modifier.padding(horizontal = HPAD.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ALL_BLOCKS.filter { it.key !in order }.forEach { def ->
                Card(Modifier.fillMaxWidth(), pad = 12, onClick = {
                    onAdd(def.key)
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(c.named(def.color)))
                        TfText(tr(def.titleKey), color = c.fg, size = 15f, modifier = Modifier.weight(1f))
                        TfIcon("plus", size = 18, color = c.accent)
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 4.dp)) {
            TfSecondaryButton(
                label = tr("today.customize.reset"),
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Hub launcher screen (hub nav mode root) ──────────────────────────────────
@Composable
fun HubScreen(onOpen: (String) -> Unit, onProfile: () -> Unit = {}) {
    val c = LocalTf.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline(tr("today.bismillah"))
                TfText(greetingTitle(), color = c.fg, size = 24f, weight = FontWeight.Bold, family = Fonts.display, modifier = Modifier.padding(top = 3.dp))
            }
            UserAvatar(onClick = onProfile)
        }
        ScrollScreen {
            ProactiveCard(tr("today.ai.local_action_hint"), modifier = Modifier.padding(horizontal = HPAD.dp))
            HubLauncher(onOpen)
        }
    }
}

/** Axis colours. An untracked axis is drawn in the hairline colour, not its own hue. */
private fun balanceColor(key: String, isTracked: Boolean, c: TfColors): Color {
    if (!isTracked) return c.sep
    return when (key) {
        "physical" -> c.green
        "social" -> c.cyan
        "financial" -> c.gold
        "educational" -> c.purple
        else -> c.fg
    }
}
