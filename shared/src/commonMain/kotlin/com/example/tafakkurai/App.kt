package com.example.tafakkurai

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.tafakkurai.screens.*
import com.example.tafakkurai.ui.*

/** Tab roots that show the bottom tab bar. */
private val TAB_ROOTS = listOf("today", "learn", "do", "thoughts", "more")

/** Mutable user preferences (was TWEAK_DEFAULTS + tweaks panel in the prototype). */
@Stable
class Tweaks {
    var nav by mutableStateOf("tabs")            // tabs | hub
    var mode by mutableStateOf("dark")           // dark | light
    var darkTheme by mutableStateOf("sukut")
    var lightTheme by mutableStateOf("noor")
    var serif by mutableStateOf(true)
    var proactivity by mutableStateOf("balanced") // ambient | balanced | chatty

    val activeThemeKey: String get() = if (mode == "light") lightTheme else darkTheme
}

@Composable
fun App() {
    val tweaks = remember { Tweaks() }
    val colors = TfThemes[tweaks.activeThemeKey] ?: TfThemes.getValue("sukut")

    // navigation stack
    val nav = remember { mutableStateListOf("today") }
    var sheetOpen by remember { mutableStateOf(false) }
    val thoughts = remember { mutableStateListOf<SocialPost>().apply { addAll(TSOCIAL) } }

    // reset root when nav mode flips
    LaunchedEffect(tweaks.nav) {
        nav.clear(); nav.add(if (tweaks.nav == "hub") "hub" else "today")
    }

    val top = nav.last()
    fun push(s: String) { nav.add(s) }
    fun pop() { if (nav.size > 1) nav.removeAt(nav.lastIndex) }
    fun setTab(k: String) { nav.clear(); nav.add(k) }
    fun openModule(k: String) {
        when {
            k == "today" -> { nav.clear(); nav.add(if (tweaks.nav == "hub") "hub" else "today") }
            else -> push(k)
        }
    }
    fun openByModuleName(name: String) {
        val route = when (name) {
            "Productivity" -> "do"; "Learn" -> "learn"; "Reflect" -> "reflect"
            "Career" -> "career"; "Thoughts" -> "thoughts"; "Finance" -> "finance"
            else -> null
        }
        if (route != null) push(route)
    }

    val isTabRoot = top in TAB_ROOTS
    val showTab = tweaks.nav == "tabs" && isTabRoot
    val showFab = top !in listOf("coach", "lesson", "compose", "appearance", "profile")

    CompositionLocalProvider(
        LocalTf provides colors,
        LocalProactivity provides tweaks.proactivity,
        LocalSerifHead provides tweaks.serif,
        LocalNavMode provides tweaks.nav,
    ) {
        Box(Modifier.fillMaxSize().background(colors.bg1)) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    Crossfade(targetState = top, animationSpec = tween(220), label = "screen") { route ->
                        Box(Modifier.fillMaxSize()) {
                            ScreenHost(
                                route = route,
                                tweaks = tweaks,
                                thoughts = thoughts,
                                onTab = ::setTab,
                                onPush = ::push,
                                onPop = ::pop,
                                onOpenModule = ::openModule,
                                onOpenByModule = ::openByModuleName,
                                onPublish = { post -> thoughts.add(0, post); pop() },
                            )
                        }
                    }
                }
                if (showTab) TTabBar(active = top, onChange = ::setTab)
            }

            if (showFab) CompanionFab(onClick = { sheetOpen = true }, raised = showTab)
            CompanionSheet(
                open = sheetOpen,
                onClose = { sheetOpen = false },
                onFull = { sheetOpen = false; push("coach") },
            )
        }
    }
}

/** Routes a navigation key to its screen. */
@Composable
private fun ScreenHost(
    route: String,
    tweaks: Tweaks,
    thoughts: List<SocialPost>,
    onTab: (String) -> Unit,
    onPush: (String) -> Unit,
    onPop: () -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenByModule: (String) -> Unit,
    onPublish: (SocialPost) -> Unit,
) {
    when (route) {
        "today" -> TodayScreen(onOpenModule = onOpenByModule, onCoach = { onPush("coach") }, onProfile = { onPush("profile") })
        "hub" -> HubScreen(onOpen = onOpenModule, onProfile = { onPush("profile") })
        "profile" -> ProfileScreen(onBack = onPop)
        "learn" -> LearnScreen(onLesson = { onPush("lesson") }, onSubject = { onPush("subject:${it.id}") })
        "do" -> ProductivityScreen()
        "thoughts" -> ThoughtsScreen(posts = thoughts, onCompose = { onPush("compose") })
        "compose" -> ComposeThoughtScreen(onBack = onPop, onPublish = onPublish)
        "more" -> MoreScreen(onOpen = onOpenModule)
        "coach" -> CoachScreen(onBack = onPop)
        "lesson" -> LessonScreen(onBack = onPop)
        "career" -> CareerScreen(onBack = onPop)
        "social" -> SocialScreen(onBack = onPop)
        "finance" -> FinanceScreen(onBack = onPop)
        "reflect" -> ReflectScreen(serif = tweaks.serif)
        "prayer" -> PrayerTrackerScreen(onBack = onPop)
        "tafakkur" -> TafakkurJournalScreen(onBack = onPop)
        "gratitude" -> GratitudeScreen(onBack = onPop)
        "quran" -> QuranDhikrScreen(onBack = onPop)
        "sleep" -> SleepScreen(onBack = onPop)
        "sport" -> SportGymScreen(onBack = onPop)
        "focus" -> FocusScreen(onBack = onPop)
        "appearance" -> AppearanceScreen(tweaks = tweaks, onBack = onPop)
        else -> when {
            route.startsWith("subject:") -> {
                val sub = TSUBJECTS.firstOrNull { it.id == route.removePrefix("subject:") }
                if (sub != null) SubjectScreen(s = sub, onBack = onPop, onLesson = { onPush("lesson") })
                else TodayScreen(onOpenModule = onOpenByModule, onCoach = { onPush("coach") })
            }
            else -> {
                val info = STUB_INFO[route]
                if (info != null) StubScreen(info = info, onBack = onPop)
                else TodayScreen(onOpenModule = onOpenByModule, onCoach = { onPush("coach") })
            }
        }
    }
}
