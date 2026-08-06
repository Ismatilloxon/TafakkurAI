package com.example.tafakkurai

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.tafakkurai.flashcards.ReviewDirection
import com.example.tafakkurai.i18n.LanguageStore
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.Strings
import com.example.tafakkurai.prayer.LocationPrayerScheduleRepository
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.sync.SyncEngine
import com.example.tafakkurai.flashcards.ReviewFilter
import com.example.tafakkurai.screens.*
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.onboarding.*
import com.example.tafakkurai.vm.ThoughtsViewModel
import uz.tafakkur.core.feedback.silently
import uz.tafakkur.domain.usecase.GetMeUseCase
import uz.tafakkur.domain.usecase.IsLoggedInUseCase
import uz.tafakkur.domain.usecase.PullGratitudeChangesUseCase
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.koin.mp.KoinPlatform.getKoin

/** Tab roots that show the bottom tab bar. */
private val TAB_ROOTS = listOf("today", "learn", "do", "thoughts", "more")

/** Location retry budget: ~40 × 1.5s ≈ 1 minute for the user to answer the dialog. */
private const val LOCATION_ATTEMPTS = 40
private const val LOCATION_RETRY_MS = 1_500L

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

    // Onboarding + auth gate, both persisted.
    //
    // The token lives in the platform secure store (Keychain / AndroidKeyStore) and the
    // "carousel seen" flag in the local database, so the three questions this gate asks —
    // has a language been chosen, has the welcome been seen, is there a session — all survive
    // a cold launch. While all three were in-memory, every relaunch replayed the whole
    // onboarding even for a user who had just signed in.
    val onbAuth = remember {
        BackendOnboardingAuth(
            getKoin().get(), getKoin().get(), getKoin().get(),
            getKoin().get(), getKoin().get(), getKoin().get(),
        )
    }
    val onbGate = remember { PrefOnboardingGate(getKoin().get()) }
    val isLoggedIn = remember { getKoin().get<IsLoggedInUseCase>() }

    // A reinstall must land on the login screen. On iOS the Keychain survives the uninstall,
    // so without this the tokens of the previous install were still there and the gate below
    // waved a brand-new install straight through. Runs before `authed` is seeded.
    remember { clearSessionIfReinstalled(getKoin().get(), getKoin().get()) }

    // Persisted interface language. Read before the gate below so a returning user never
    // sees the picker again, and a fresh install always does.
    val languages = remember { getKoin().get<LanguageStore>() }
    // Seeded from the stored token rather than false. An expired access token still counts as
    // a session: the Ktor auth plugin refreshes it on the first 401 and only clears the tokens
    // when the refresh itself fails.
    var authed by remember { mutableStateOf(isLoggedIn()) }

    // navigation stack
    val nav = remember { mutableStateListOf("today") }
    var sheetOpen by remember { mutableStateOf(false) }
    val thoughtsVm: ThoughtsViewModel =
        viewModel { ThoughtsViewModel(getKoin().get(), getKoin().get(), getKoin().get(), getKoin().get()) }

    // Request user location on launch — prayer times are computed from it.
    // The OS permission dialog resolves asynchronously on both platforms, so keep
    // retrying until a fix lands; until then the schedule uses the bundled fallback.
    val prayerRepository: LocationPrayerScheduleRepository = remember { getKoin().get() }
    LaunchedEffect(Unit) {
        // Apply the user's saved calc convention (default Uzbekistan 18°/17° + Hanafi
        // until a profile loads). Best-effort: a signed-out launch just keeps the default.
        // All launch-time backend work is best-effort and must stay invisible: `silently`
        // suppresses the global loader and any error snackbar (an offline start should not
        // greet the user with "No internet"), while still mapping and logging every failure.
        // Every call in here needs a bearer token, so skip the whole block for a signed-out
        // launch instead of firing three guaranteed 401s at the backend from the login screen.
        if (isLoggedIn()) silently {
            val prefs = getKoin().get<PrefPersistence>()
            runCatching { getKoin().get<GetMeUseCase>()() }.getOrNull()?.let { me ->
                prayerRepository.setConvention(me.user.calcMethod, me.user.madhab)
                // Cached so the Today header greets the real user on the next cold start,
                // before /v1/me has answered — it used to read a hardcoded name from the mock.
                prefs.put(AppPrefs.DISPLAY_NAME, me.user.displayName)
            }
            // Offline-first: on every app enter, push changes queued while offline to the
            // backend, then pull what other devices changed. Both are best-effort — a
            // signed-out or offline launch keeps working entirely from the local database.
            runCatching { getKoin().get<SyncEngine>().flush() }
            runCatching {
                val cursor = getKoin().get<PullGratitudeChangesUseCase>()(prefs.get(AppPrefs.GRATITUDE_CURSOR))
                prefs.put(AppPrefs.GRATITUDE_CURSOR, cursor)
            }
        }
        repeat(LOCATION_ATTEMPTS) {
            if (prayerRepository.refresh()) return@LaunchedEffect
            delay(LOCATION_RETRY_MS)
        }
    }

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
            "Gratitude" -> "gratitude"
            else -> null
        }
        if (route != null) push(route)
    }

    val isTabRoot = top in TAB_ROOTS
    val showTab = tweaks.nav == "tabs" && isTabRoot
    // The ayah reader pins its own audio player to the bottom edge — keep the FAB off it.
    // A hifz session pins its grade bar there for the same reason, and is meant to be a
    // screen you are not interrupted on.
    val showFab = top !in listOf("coach", "lesson", "compose", "appearance", "profile", "hifz:review") &&
        !top.startsWith("surah:")

    CompositionLocalProvider(
        LocalTf provides colors,
        LocalStrings provides Strings.of(languages.current),
        LocalProactivity provides tweaks.proactivity,
        LocalSerifHead provides tweaks.serif,
        LocalNavMode provides tweaks.nav,
    ) {
      // One full-size Box so the global loader + snackbar overlay sits above whichever stage
      // is showing — language picker, onboarding or the main shell.
      Box(Modifier.fillMaxSize()) {
        // Language comes before everything: the carousel, the login copy and every screen
        // after them are written in whatever is chosen here, so it cannot be asked later.
        if (false && !languages.hasChosen) { // TEMPORARY: skip language picker
            LanguageScreen(initial = languages.current, onChoose = { languages.choose(it) })
        } else if (!authed) {
            OnboardingRoot(auth = onbAuth, gate = onbGate, onFinished = { authed = true })
        } else Box(Modifier.fillMaxSize().background(colors.bg1)) {
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
                                thoughts = thoughtsVm.posts,
                                onTab = ::setTab,
                                onPush = ::push,
                                onPop = ::pop,
                                onOpenModule = ::openModule,
                                onOpenByModule = ::openByModuleName,
                                onPublish = { post -> thoughtsVm.publish(post.text); pop() },
                                onLike = thoughtsVm::like,
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

        // Global loader + snackbar, mounted last so it draws above every screen.
        GlobalFeedbackHost()
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
    onLike: (String) -> Unit,
) {
    when (route) {
        "today" -> TodayScreen(onOpenModule = onOpenByModule, onCoach = { onPush("coach") }, onProfile = { onPush("profile") }, onOpenPrayer = { onPush("prayer") }, onOpenQuran = { onPush("quran") })
        "hub" -> HubScreen(onOpen = onOpenModule, onProfile = { onPush("profile") })
        "profile" -> ProfileScreen(onBack = onPop)
        "learn" -> LearnScreen(onLesson = { onPush("lesson") }, onSubject = { onPush("subject:${it.id}") })
        "do" -> ProductivityScreen()
        "thoughts" -> ThoughtsScreen(posts = thoughts, onCompose = { onPush("compose") }, onLike = onLike)
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
        "quran" -> QuranDhikrScreen(
            onBack = onPop,
            onOpenSurah = { onPush("surah:$it") },
            onOpenHifz = { onPush("hifz") },
        )
        // Memorization. Home, the enrolment picker and a running session are separate routes
        // so the back stack behaves: leaving a session returns to the home, not to setup.
        "hifz" -> HifzHomeScreen(
            onBack = onPop,
            onSetup = { onPush("hifz:setup") },
            onReview = { onPush("hifz:review") },
            onOpenSurah = { onPush("surah:$it") },
        )
        "hifz:setup" -> HifzSetupScreen(onBack = onPop, onDone = onPop)
        "hifz:review" -> HifzReviewScreen(onBack = onPop)
        "sleep" -> SleepScreen(onBack = onPop)
        "sport" -> SportGymScreen(onBack = onPop)
        "focus" -> FocusScreen(onBack = onPop)
        "appearance" -> AppearanceScreen(tweaks = tweaks, onBack = onPop)
        else -> when {
            // Qurʼan reader — one route per surah so the back stack restores the right one.
            route.startsWith("surah:") -> {
                val number = route.removePrefix("surah:").toIntOrNull()
                if (number != null && number in 1..114) AyahReaderScreen(surahNumber = number, onBack = onPop)
                else QuranDhikrScreen(
                    onBack = onPop,
                    onOpenSurah = { onPush("surah:$it") },
                    onOpenHifz = { onPush("hifz") },
                )
            }
            route.startsWith("subject:") -> {
                val sid = route.removePrefix("subject:")
                // Backend subjects aren't in TSUBJECTS — fall back to a minimal model so the screen still opens.
                val sub = TSUBJECTS.firstOrNull { it.id == sid }
                    ?: Subject(sid, "Subject", "", "book", 0, "", 0f, "Start learning", 0, "cyan")
                SubjectScreen(s = sub, onBack = onPop, onLesson = { onPush("lesson") }, onFlashcards = { onPush("decks:$sid") })
            }
            route.startsWith("decks:") -> FlashcardDecksScreen(
                subjectId = route.removePrefix("decks:"),
                onBack = onPop,
                onDeck = { onPush("deck:$it") },
            )
            route.startsWith("deck:") -> {
                val deckId = route.removePrefix("deck:")
                FlashcardDeckScreen(
                    deckId = deckId,
                    onBack = onPop,
                    onStudy = { filter, dir -> onPush("review:$deckId:${filter.name}:${dir.name}") },
                )
            }
            route.startsWith("review:") -> {
                val parts = route.removePrefix("review:").split(":")
                FlashcardReviewScreen(
                    deckId = parts[0],
                    filter = ReviewFilter.valueOf(parts.getOrElse(1) { "DUE" }),
                    direction = ReviewDirection.valueOf(parts.getOrElse(2) { "FRONT_BACK" }),
                    onBack = onPop,
                )
            }
            else -> {
                val info = STUB_INFO[route]
                if (info != null) StubScreen(info = info, onBack = onPop)
                else TodayScreen(onOpenModule = onOpenByModule, onCoach = { onPush("coach") }, onOpenPrayer = { onPush("prayer") }, onOpenQuran = { onPush("quran") })
            }
        }
    }
}
