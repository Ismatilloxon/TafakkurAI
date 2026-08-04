package com.example.tafakkurai

import androidx.compose.ui.graphics.Color

// Tafakkur AI — mock data ported from data.js (single source of truth for the prototype).
// English-primary copy with natural Uzbek touches.

data class TUser(
    val name: String, val handle: String, val greetingUz: String, val greetingEn: String,
    val streak: Int, val level: Int, val xp: Int, val xpNext: Int, val today: String, val coach: String,
)

val TUSER = TUser(
    name = "Aziz", handle = "@aziz", greetingUz = "Xayrli tong", greetingEn = "Good morning",
    streak = 23, level = 7, xp = 1840, xpNext = 2200, today = "Tuesday, June 10", coach = "Murabbiy",
)

data class Signal(val icon: String, val text: String)
data class Brief(val line: String, val signals: List<Signal>)

val TBRIEF = Brief(
    line = "You've got a clear morning. I moved your TypeScript lesson before deep work, and protected 90 minutes for it — your focus scores are highest before noon.",
    signals = listOf(
        Signal("sun", "Light day — 3 meetings cleared"),
        Signal("target", "2 of 4 weekly goals on track"),
        Signal("moon", "Slept 7h 10m — well rested"),
    ),
)

data class PlanItem(
    val id: String, val time: String, val dur: String, val kind: String,
    val title: String, val sub: String, val done: Boolean, val current: Boolean = false, val module: String,
)

val TPLAN = listOf(
    PlanItem("p1", "07:30", "15m", "reflect", "Morning tafakkur", "Gratitude — 3 things", true, module = "Reflect"),
    PlanItem("p2", "08:00", "45m", "focus", "Deep work — API refactor", "Focus session · Pomodoro", true, module = "Productivity"),
    PlanItem("p3", "09:30", "20m", "learn", "TypeScript — generics", "AI lesson + quiz", false, current = true, module = "Learn"),
    PlanItem("p4", "12:00", "10m", "task", "Reply to mentor email", "Career · interview follow-up", false, module = "Career"),
    PlanItem("p5", "18:00", "25m", "learn", "English — speaking drill", "Pronunciation with AI", false, module = "Learn"),
    PlanItem("p6", "21:30", "10m", "reflect", "Evening reflection", "Review the day", false, module = "Reflect"),
)

data class Habit(val id: String, val name: String, val icon: String, val streak: Int, val week: List<Int>, val done: Boolean, val accent: String)

val THABITS = listOf(
    Habit("h1", "Morning reflection", "sunrise", 23, listOf(1, 1, 1, 1, 1, 0, 0), true, "gold"),
    Habit("h2", "Read 20 pages", "book", 8, listOf(1, 1, 0, 1, 1, 0, 0), false, "blue"),
    Habit("h3", "Code 1 hour", "code", 14, listOf(1, 1, 1, 1, 1, 0, 0), false, "blue"),
    Habit("h4", "Walk 8k steps", "activity", 5, listOf(1, 0, 1, 1, 0, 0, 0), false, "green"),
    Habit("h5", "No screens after 22:00", "moon", 3, listOf(1, 1, 1, 0, 1, 0, 0), false, "gold"),
)

data class TaskItem(val id: String, val title: String, val tag: String, val priority: String, val done: Boolean, val due: String)

val TTASKS = listOf(
    TaskItem("t1", "Refactor auth middleware", "Work", "high", false, "Today"),
    TaskItem("t2", "Prepare interview answers — system design", "Career", "high", false, "Today"),
    TaskItem("t3", "Update CV — add Rust project", "Career", "med", false, "Tomorrow"),
    TaskItem("t4", "Buy groceries", "Life", "low", false, "Tomorrow"),
    TaskItem("t5", "Review pull requests", "Work", "med", true, "Today"),
    TaskItem("t6", "Call family", "Life", "med", true, "Today"),
)

data class Goal(val id: String, val title: String, val progress: Float)

val TWEEK_GOALS = listOf(
    Goal("g1", "Finish TypeScript module 3", 0.6f),
    Goal("g2", "Solve 10 algorithm problems", 0.4f),
    Goal("g3", "Submit 3 job applications", 0.33f),
)

data class Subject(
    val id: String, val name: String, val native: String, val icon: String, val seed: Int,
    val level: String, val progress: Float, val next: String, val streak: Int, val accent: String,
)

val TSUBJECTS = listOf(
    Subject("s-en", "English", "Ingliz tili", "languages", 4, "B2 · Upper-intermediate", 0.72f, "Speaking — describing trends", 19, "cyan"),
    Subject("s-prog", "Programming", "Dasturlash", "terminal", 0, "TypeScript · Intermediate", 0.45f, "Generics in practice", 14, "blue"),
    Subject("s-math", "Mathematics", "Matematika", "sigma", 2, "Calculus I", 0.30f, "Derivatives — chain rule", 6, "purple"),
    Subject("s-fin", "Finance basics", "Moliya asoslari", "lineChart", 6, "Foundations", 0.18f, "Compound interest", 3, "gold"),
)

data class LessonBlock(val type: String, val heading: String? = null, val body: String? = null, val lang: String? = null, val code: String? = null)
data class Lesson(val subject: String, val module: String, val index: String, val title: String, val minutes: Int, val intro: String, val blocks: List<LessonBlock>)

val TLESSON = Lesson(
    subject = "Programming", module = "Module 03", index = "Lesson 02", title = "Generics in practice", minutes = 12,
    intro = "Generics let you write a function once and reuse it for many types without losing type safety. Think of them as type-level parameters. I generated this lesson from where you left off — you were comfortable with interfaces but hadn't applied them to reusable utilities.",
    blocks = listOf(
        LessonBlock("concept", heading = "The problem", body = "Without generics you either duplicate a function per type, or fall back to `any` and lose all safety. Generics give you one definition that adapts to the caller."),
        LessonBlock("code", lang = "typescript", code = "function first<T>(arr: T[]): T | undefined {\n  return arr[0];\n}\n\nconst n = first([1, 2, 3]);      // number | undefined\nconst s = first(['a', 'b']);     // string | undefined"),
        LessonBlock("concept", heading = "Constraints", body = "Use `extends` to require that a type has certain shape — here, anything with a `.length`."),
        LessonBlock("code", lang = "typescript", code = "function longest<T extends { length: number }>(a: T, b: T): T {\n  return a.length >= b.length ? a : b;\n}"),
    ),
)

data class QuizItem(val q: String, val options: List<String>, val answer: Int, val explain: String)

val TQUIZ = listOf(
    QuizItem(
        "What does the type parameter `T` represent in `function first<T>(arr: T[]): T`?",
        listOf("A fixed type chosen by the library", "A placeholder type the caller fills in", "Always `any`", "A runtime value"),
        1, "T is a type-level parameter — TypeScript infers or you supply it at the call site.",
    ),
    QuizItem(
        "Why use `T extends { length: number }`?",
        listOf("To make T faster", "To constrain T to types that have a length", "To convert T to a string", "It is only decoration"),
        1, "Constraints guarantee the shape you rely on inside the function.",
    ),
)

data class ChatMsg(val from: String, val text: String, val time: String)

val TCHAT = listOf(
    ChatMsg("ai", "Xayrli tong, Aziz. You finished your morning reflection — nice. Want me to set up today around your TypeScript goal, or keep it open?", "07:34"),
    ChatMsg("me", "Set it up around TypeScript. But I have an interview Thursday I'm nervous about.", "07:35"),
    ChatMsg("ai", "Understood. I'll protect 90 minutes this morning for generics, then add two short interview-prep blocks before Thursday — one on system design, one mock with me. Nerves usually drop once you've rehearsed out loud. Sound good?", "07:35"),
)

val TCHAT_SUGGEST = listOf("Plan my day", "Quiz me on generics", "Reflect on yesterday", "Prep for Thursday interview")

data class MemoryItem(val icon: String, val label: String, val text: String)

val TMEMORY = listOf(
    MemoryItem("target", "Goal", "Land a backend role by autumn"),
    MemoryItem("clock", "Pattern", "Focus peaks 8–11am"),
    MemoryItem("book", "Learning", "TypeScript intermediate · English B2"),
    MemoryItem("heart", "Value", "Daily tafakkur matters to you"),
)

val TPROMPTS = listOf(
    "What is one thing you are grateful for this morning?",
    "Where did you notice patience being tested yesterday?",
    "What small good deed can you do today?",
    "What did today teach you about yourself?",
)

data class JournalEntry(val id: String, val date: String, val mood: String, val title: String, val excerpt: String, val gratitude: List<String>)

val TJOURNAL = listOf(
    JournalEntry("j1", "Yesterday", "calm", "On patience", "I lost my temper in a code review and regretted it. Tomorrow I'll pause before replying — a breath of sabr.", listOf("Health", "A patient mentor", "Quiet morning")),
    JournalEntry("j2", "Jun 8", "grateful", "Small wins", "Shipped the feature I'd been avoiding. Reminded myself that consistency beats intensity.", listOf("Family", "Work that grows me", "Rain")),
    JournalEntry("j3", "Jun 7", "tired", "Slowing down", "Pushed too hard this week. Rest is not laziness — it is part of the work.", listOf("A good night sleep", "Tea", "A friend who listened")),
)

val TGRATITUDE_TODAY = listOf("Health", "Time to learn", "A roof and warm food")

data class RoadmapPhase(val phase: String, val title: String, val items: List<String>, val done: Boolean, val current: Boolean = false)
data class Interview(val id: String, val kind: String, val q: String, val difficulty: String)
data class University(val name: String, val program: String, val deadline: String, val match: Int)
data class Career(
    val role: String, val cvScore: Int, val cvTips: List<String>,
    val roadmap: List<RoadmapPhase>, val interviews: List<Interview>, val universities: List<University>,
)

val TCAREER = Career(
    role = "Backend Engineer", cvScore = 78,
    cvTips = listOf("Add metrics to your Rust project", "Lead with impact, not tasks", "Trim summary to 2 lines"),
    roadmap = listOf(
        RoadmapPhase("Now", "Strengthen fundamentals", listOf("Data structures", "SQL & Postgres", "System design basics"), true),
        RoadmapPhase("Next 60 days", "Build proof of work", listOf("Ship 2 backend projects", "Write 3 technical posts"), false, current = true),
        RoadmapPhase("Quarter", "Apply & interview", listOf("Target 15 companies", "Mock interviews", "Negotiation prep"), false),
    ),
    interviews = listOf(
        Interview("iv1", "System design", "Design a URL shortener", "Medium"),
        Interview("iv2", "Behavioral", "Tell me about a conflict you resolved", "Easy"),
        Interview("iv3", "Coding", "Two-sum, then scale it", "Easy"),
    ),
    universities = listOf(
        University("TU Munich", "MSc Informatics", "Jan 15", 86),
        University("KAIST", "MSc Computer Science", "Mar 01", 79),
    ),
)

data class SocialPost(val id: String, val author: String, val handle: String, val avatarSeed: Int, val time: String, val text: String, val likes: Int, val comments: Int, val tag: String)

val TSOCIAL = listOf(
    SocialPost("so1", "Dilnoza", "@dilnoza", 6, "2h", "Tafakkur for builders: the bug you avoid is the one you most need to face. Sat with a nasty race condition this morning — felt like a small jihad against my own impatience.", 42, 7, "Mindset"),
    SocialPost("so2", "Jasur", "@jasur", 0, "5h", "Day 100 of learning English with 20 minutes a day. Consistency really is a quiet superpower. Aziz, your speaking drills inspired me to start.", 88, 14, "Learning"),
    SocialPost("so3", "Madina", "@madina", 3, "1d", "Finished my first Postgres project. Six months ago I couldn't write a JOIN. Grateful for this community keeping me accountable.", 61, 9, "Wins"),
)

data class Group(val id: String, val name: String, val members: Int, val icon: String, val active: Boolean)

val TGROUPS = listOf(
    Group("gr1", "Backend learners", 1240, "server", true),
    Group("gr2", "Daily tafakkur", 860, "moon", true),
    Group("gr3", "English speaking club", 2100, "languages", false),
)

data class Allocation(val name: String, val pct: Int, val color: String, val value: Int)
data class FinanceHabit(val id: String, val name: String, val progress: Float, val status: String)
data class Finance(val net: Int, val change: Float, val changeAbs: Int, val allocations: List<Allocation>, val habits: List<FinanceHabit>, val insights: List<String>)

val TFINANCE = Finance(
    net = 8420, change = 4.2f, changeAbs = 340,
    allocations = listOf(
        Allocation("Index funds", 52, "blue", 4378),
        Allocation("Cash savings", 28, "green", 2358),
        Allocation("Tech stocks", 14, "gold", 1179),
        Allocation("Crypto", 6, "purple", 505),
    ),
    habits = listOf(
        FinanceHabit("f1", "Save 20% of income", 0.8f, "on track"),
        FinanceHabit("f2", "No impulse buys this week", 0.6f, "watch"),
        FinanceHabit("f3", "Track every expense", 1.0f, "done"),
    ),
    insights = listOf(
        "Your savings rate is above your 6-month average — steady.",
        "Crypto is 6% of your portfolio. That sits inside the risk band you set.",
        "Subscriptions rose 12% this month. Worth a review.",
    ),
)

// ── Salah / prayer times (Tashkent). "Now" = 09:41. ──────────────────────────
data class PrayerTime(val key: String, val name: String, val short: String, val time: String, val min: Int, val icon: String)

val TPRAYERS = listOf(
    PrayerTime("fajr", "Fajr", "Fajr", "04:35", 275, "sunrise"),
    PrayerTime("sunrise", "Sunrise", "Sunr", "05:58", 358, "sun"),
    PrayerTime("dhuhr", "Dhuhr", "Dhuh", "12:51", 771, "sun"),
    PrayerTime("asr", "Asr", "Asr", "17:18", 1038, "sun"),
    PrayerTime("maghrib", "Maghrib", "Magh", "20:42", 1242, "sunset"),
    PrayerTime("isha", "Isha", "Isha", "22:11", 1331, "moon"),
)
val TNOW_MIN = 581
val TNOW_LABEL = "09:41"
data class NextPrayer(val name: String, val inLabel: String, val at: String)
val TNEXT = NextPrayer("Dhuhr", "3h 10m", "12:51")
val THIJRI = "22 Dhul-Qa'dah"
val TCITY = "Tashkent"

data class BalanceDim(val key: String, val label: String, val value: Float, val color: Color, val desc: String)

/**
 * Design reference only — the real radar is computed from recorded activity by
 * `com.example.tafakkurai.balance.computeBalance`. Do NOT render these numbers: they never
 * move, which is exactly the bug they caused on Today.
 */
val TBALANCE = listOf(
    BalanceDim("physical", "Physical", 0.70f, Color(0xFFA8C36A), "Walk, rest, train, drink."),
    BalanceDim("social", "Social", 0.55f, Color(0xFF4CC2FF), "Family, ummah, ties of kinship."),
    BalanceDim("financial", "Financial", 0.62f, Color(0xFFC9A24E), "Earn lawfully, give sadaqah."),
    BalanceDim("educational", "Educational", 0.42f, Color(0xFFA78BFA), "Read, study, teach."),
    BalanceDim("spiritual", "Spiritual", 0.84f, Color(0xFFE6E1D6), "Salah, Qur'an, dhikr."),
)

data class Rec(val icon: String, val tone: String, val tag: String, val text: String)

val TRECS = listOf(
    Rec("clock", "gold", "Before Dhuhr", "You have 3h 10m until Dhuhr — your focus peaks now. I protected 90 minutes for deep work before salah."),
    Rec("book", "purple", "Rebalance", "Educational is your lowest dimension this month. 20 minutes of reading after Isha would bring it back into balance."),
)

data class DayPlanItem(
    val type: String, val name: String? = null, val title: String? = null, val time: String,
    val dur: String? = null, val cat: String? = null, val done: Boolean = false, val current: Boolean = false, val state: String? = null,
)

val TDAYPLAN = listOf(
    DayPlanItem("salah", name = "Fajr", time = "04:35", state = "done"),
    DayPlanItem("task", title = "Deep work", time = "06:00", dur = "3h", cat = "financial", done = true),
    DayPlanItem("task", title = "Gym", time = "10:00", dur = "1h", cat = "physical", done = false, current = true),
    DayPlanItem("salah", name = "Dhuhr", time = "12:51", state = "next"),
    DayPlanItem("task", title = "Family check-in", time = "13:30", dur = "15m", cat = "social", done = false),
    DayPlanItem("salah", name = "Asr", time = "17:18", state = "upcoming"),
    DayPlanItem("task", title = "Read · Hadis & Hayot", time = "18:00", dur = "20m", cat = "educational", done = false),
    DayPlanItem("salah", name = "Maghrib", time = "20:42", state = "upcoming"),
    DayPlanItem("task", title = "Recite Surah Yasin", time = "21:00", dur = "15m", cat = "spiritual", done = false),
    DayPlanItem("salah", name = "Isha", time = "22:11", state = "upcoming"),
)

// ── Spiritual module data ─────────────────────────────────────────────────────
data class PrayerToday(val key: String, val name: String, val arabic: String, val time: String, val state: String)

val TPRAYER_TODAY = listOf(
    PrayerToday("fajr", "Fajr", "الفجر", "04:35", "jamaat"),
    PrayerToday("dhuhr", "Dhuhr", "الظهر", "12:51", "ontime"),
    PrayerToday("asr", "Asr", "العصر", "17:18", "upcoming"),
    PrayerToday("maghrib", "Maghrib", "المغرب", "20:42", "upcoming"),
    PrayerToday("isha", "Isha", "العشاء", "22:11", "upcoming"),
)

data class PrayerStateMeta(val label: String, val short: String, val color: Color, val icon: String)

val PRAYER_STATES = mapOf(
    "jamaat" to PrayerStateMeta("Jamaat", "Jamoat", Color(0xFF2BB673), "users"),
    "ontime" to PrayerStateMeta("On time", "Vaqtida", Color(0xFFC9A24E), "check"),
    "late" to PrayerStateMeta("Late", "Kechikib", Color(0xFFE0A93B), "clock"),
    "qada" to PrayerStateMeta("Qada", "Qazo", Color(0xFFE5746B), "repeat"),
    "upcoming" to PrayerStateMeta("Upcoming", "Keyin", Color(0xFF7E8799), "circle"),
)

// 35-day completion grid (0 none .. 5 all five). Most recent last.
val TPRAYER_MONTH = listOf(5, 5, 4, 5, 5, 3, 5, 5, 5, 5, 4, 5, 5, 5, 4, 5, 5, 5, 3, 5, 5, 5, 4, 5, 5, 5, 5, 4, 5, 5, 5, 5, 2, 0, 0)
data class PrayerStats(val streak: Int, val jamaat: Int, val onTime: Int, val qada: Int, val monthPct: Int)
val TPRAYER_STATS = PrayerStats(18, 62, 84, 3, 91)

data class Dhikr(val key: String, val arabic: String, val translit: String, val meaning: String, val count: Int, val target: Int, val color: Color)

val TDHIKR = listOf(
    Dhikr("subhan", "سُبْحَانَ ٱللَّٰه", "SubhanAllah", "Glory be to Allah", 21, 33, Color(0xFF4CC2FF)),
    Dhikr("hamd", "ٱلْحَمْدُ لِلَّٰه", "Alhamdulillah", "All praise to Allah", 33, 33, Color(0xFF2BB673)),
    Dhikr("akbar", "ٱللَّٰهُ أَكْبَر", "Allahu akbar", "Allah is the greatest", 12, 34, Color(0xFFC9A24E)),
    Dhikr("istighfar", "أَسْتَغْفِرُ ٱللَّٰه", "Astaghfirullah", "I seek forgiveness", 7, 100, Color(0xFFA78BFA)),
)

data class Adhkar(val key: String, val label: String, val native: String, val icon: String, val total: Int, val done: Int, val color: Color)

val TADHKAR = listOf(
    Adhkar("morning", "Morning adhkar", "Tongги zikr", "sunrise", 12, 12, Color(0xFFC9A24E)),
    Adhkar("evening", "Evening adhkar", "Kechki zikr", "sunset", 12, 4, Color(0xFF7C5CFF)),
)

data class QuranLast(val surah: String, val surahNo: Int, val ayah: Int, val juz: Int, val pct: Float, val arabic: String)
val TQURAN_LAST = QuranLast("Al-Mulk", 67, 14, 29, 0.46f, "الملك")

data class Surah(val no: Int, val name: String, val arabic: String, val meaning: String, val ayahs: Int, val place: String, val read: Float)

val TQURAN_SURAHS = listOf(
    Surah(67, "Al-Mulk", "الملك", "The Sovereignty", 30, "Meccan", 0.46f),
    Surah(36, "Ya-Sin", "يٰسٓ", "Ya Sin", 83, "Meccan", 1f),
    Surah(18, "Al-Kahf", "الكهف", "The Cave", 110, "Meccan", 0.2f),
    Surah(55, "Ar-Rahman", "الرحمٰن", "The Beneficent", 78, "Medinan", 0f),
    Surah(2, "Al-Baqarah", "البقرة", "The Cow", 286, "Medinan", 0.08f),
)

data class QuranVerse(val arabic: String, val translit: String, val meaning: String, val ref: String)
val TQURAN_VERSE = QuranVerse(
    arabic = "وَمَن يَتَّقِ ٱللَّٰهَ يَجْعَل لَّهُۥ مَخْرَجًا",
    translit = "Wa man yattaqi-llaha yaj'al lahu makhraja",
    meaning = "And whoever is mindful of Allah — He will make a way out for them.",
    ref = "At-Talaq · 65:2",
)

// ── Physical: Sleep & Sport ───────────────────────────────────────────────────
data class SleepStage(val s: String, val m: Int)
data class SleepBreakdown(val s: String, val label: String, val sub: String, val min: Int, val pct: Float, val color: Color)
data class SleepMetric(val label: String, val value: String, val unit: String, val icon: String, val color: Color)
data class Sleep(
    val score: Int, val asleep: String, val inBed: String, val bedtime: String, val wake: String, val goalH: Int,
    val stages: List<SleepStage>, val breakdown: List<SleepBreakdown>, val metrics: List<SleepMetric>,
    val week: List<Float>, val debt: String,
)

val TSLEEP = Sleep(
    score = 82, asleep = "7h 10m", inBed = "7h 45m", bedtime = "23:20", wake = "06:30", goalH = 8,
    stages = listOf(
        SleepStage("awake", 8), SleepStage("core", 50), SleepStage("deep", 42), SleepStage("rem", 22),
        SleepStage("core", 58), SleepStage("deep", 23), SleepStage("rem", 40), SleepStage("core", 48),
        SleepStage("awake", 7), SleepStage("rem", 38), SleepStage("core", 54), SleepStage("deep", 0),
    ),
    breakdown = listOf(
        SleepBreakdown("deep", "Deep", "Restoration", 65, 0.15f, Color(0xFF3D5CCC)),
        SleepBreakdown("rem", "REM", "Dreaming", 100, 0.23f, Color(0xFF7C5CFF)),
        SleepBreakdown("core", "Core", "Light sleep", 245, 0.57f, Color(0xFF4CC2FF)),
        SleepBreakdown("awake", "Awake", "Brief stirs", 20, 0.05f, Color(0xFFE0A93B)),
    ),
    metrics = listOf(
        SleepMetric("Resting HR", "54", "bpm", "heart", Color(0xFFE5746B)),
        SleepMetric("Restful", "88", "%", "activity", Color(0xFF2BB673)),
        SleepMetric("Consistency", "High", "", "repeat", Color(0xFF4CC2FF)),
    ),
    week = listOf(6.5f, 7.2f, 6.8f, 7.5f, 7.0f, 8.1f, 7.17f),
    debt = "50m",
)

val SLEEP_COL = mapOf(
    "awake" to Color(0xFFE0A93B), "rem" to Color(0xFF7C5CFF), "core" to Color(0xFF4CC2FF), "deep" to Color(0xFF3D5CCC),
)

data class SportType(val key: String, val name: String, val icon: String, val met: Float, val color: Color)

val TSPORT_TYPES = listOf(
    SportType("run", "Running", "activity", 9.8f, Color(0xFFE5746B)),
    SportType("gym", "Strength", "dumbbell", 6.0f, Color(0xFFC9A24E)),
    SportType("football", "Football", "target", 7.0f, Color(0xFF2BB673)),
    SportType("cycling", "Cycling", "repeat", 7.5f, Color(0xFF4CC2FF)),
    SportType("swim", "Swimming", "activity", 8.0f, Color(0xFF7C5CFF)),
    SportType("walk", "Walking", "activity", 3.5f, Color(0xFFA8C36A)),
)
val TSPORT_GOALS = listOf("Lose weight", "Stay active", "Build strength")
val TSPORT_DURATIONS = listOf(15, 30, 45, 60, 90)

data class SportActivity(val id: String, val key: String, val name: String, val icon: String, val color: Color, val min: Int, val kcal: Int, val time: String)
data class SportState(val activities: List<SportActivity>, val weekMin: List<Int>)

val TSPORT_DEFAULT = SportState(
    activities = listOf(SportActivity("a1", "gym", "Strength", "dumbbell", Color(0xFFC9A24E), 45, 284, "06:00")),
    weekMin = listOf(40, 0, 55, 30, 0, 45, 45),
)

/** aiReply — companion canned responses (kit.jsx). */
fun aiReply(t: String): String {
    val s = t.lowercase()
    return when {
        s.contains("plan") -> "Here's your day: 90 min generics this morning, a 25-min English drill at 18:00, and a short reflection tonight. I kept the afternoon light for your interview prep. Want me to add the mock interview now?"
        s.contains("quiz") || s.contains("generic") -> "Let's do it. Quick one: in `function first<T>(arr: T[]): T`, what is `T`? a) a fixed type, b) a placeholder the caller fills in, c) always any."
        s.contains("reflect") || s.contains("yesterday") -> "Yesterday you noted impatience in a code review. A small practice: before replying, take one breath and ask 'is this true, is it kind, is it needed?' Want a prompt for tonight's tafakkur?"
        s.contains("interview") || s.contains("thursday") -> "Thursday is system design. We'll rehearse one out loud — start with a URL shortener. Take 30 seconds: how would you map a long URL to a short code?"
        else -> "Got it. I'll fold that into your plan and keep an eye on your goals. Anything you want to tackle first — learning, focus, or reflection?"
    }
}
