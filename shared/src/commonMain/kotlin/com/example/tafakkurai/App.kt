package com.example.tafakkurai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.tafakkurai.flashcards.ReviewDirection
import com.example.tafakkurai.ai.domain.LOCAL_VOCABULARY_SUBJECT_ID
import com.example.tafakkurai.i18n.LanguageStore
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.Strings
import com.example.tafakkurai.prayer.LocationPrayerScheduleRepository
import com.example.tafakkurai.prayer.currentSecondsOfDay
import com.example.tafakkurai.profile.domain.ProfilePhotoRepository
import com.example.tafakkurai.profile.domain.ProfilePhotoUploadResult
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.prayer.deviceTimeZoneId
import com.example.tafakkurai.integration.ActivityIntegrationCoordinator
import com.example.tafakkurai.journal.presentation.JournalViewModel
import com.example.tafakkurai.learning.presentation.QuranQuizViewModel
import com.example.tafakkurai.sync.SyncEngine
import com.example.tafakkurai.sync.SyncOutcome
import com.example.tafakkurai.sync.SyncReason
import com.example.tafakkurai.sync.SyncScheduler
import com.example.tafakkurai.flashcards.ReviewFilter
import com.example.tafakkurai.finance.domain.FinanceRepository
import com.example.tafakkurai.habits.ui.HabitsScreen
import com.example.tafakkurai.screens.*
import com.example.tafakkurai.sleep.RoutinePhase
import com.example.tafakkurai.sleep.SleepTime
import com.example.tafakkurai.sleep.SleepViewModel
import com.example.tafakkurai.sleep.ui.SleepGoalsScreen
import com.example.tafakkurai.sleep.ui.SleepHistoryScreen
import com.example.tafakkurai.sleep.ui.SleepRoutineScreen
import com.example.tafakkurai.sleep.ui.SleepScreen
import com.example.tafakkurai.sleep.ui.SleepStatsScreen
import com.example.tafakkurai.social.domain.SocialRepository
import com.example.tafakkurai.settings.presentation.SettingsViewModel
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.LocalPlatformBackDispatcher
import com.example.tafakkurai.ui.components.PlatformBackDispatcher
import com.example.tafakkurai.ui.components.platformEdgeBackGesture
import com.example.tafakkurai.onboarding.*
import com.example.tafakkurai.vm.ThoughtsViewModel
import com.example.tafakkurai.vm.CoachViewModel
import uz.tafakkur.core.feedback.silently
import uz.tafakkur.domain.usecase.GetMeUseCase
import uz.tafakkur.domain.usecase.IsLoggedInUseCase
import uz.tafakkur.network.AuthSessionState
import uz.tafakkur.network.TokenProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform.getKoin

/** Tab roots that show the bottom tab bar. */
private val TAB_ROOTS = listOf("today", "learn", "do", "thoughts", "more")

/** Location retry budget: ~40 × 1.5s ≈ 1 minute for the user to answer the dialog. */
private const val LOCATION_ATTEMPTS = 40
private const val LOCATION_RETRY_MS = 1_500L

/** Mutable user preferences (was TWEAK_DEFAULTS + tweaks panel in the prototype). */
@Stable
class Tweaks(private val prefs: PrefPersistence? = null) {
    private var navState: String by mutableStateOf(
        prefs?.get(AppPrefs.APPEARANCE_NAV)?.takeIf { it in setOf("tabs", "hub") } ?: "tabs",
    )
    var nav: String
        get() = navState
        set(value) {
            navState = value.takeIf { it in setOf("tabs", "hub") } ?: "tabs"
            prefs?.put(AppPrefs.APPEARANCE_NAV, navState)
        }
    private var modeState: String by mutableStateOf(
        prefs?.get(AppPrefs.APPEARANCE_MODE)?.takeIf { it in setOf("dark", "light") } ?: "dark",
    )
    var mode: String
        get() = modeState
        set(value) {
            modeState = value.takeIf { it in setOf("dark", "light") } ?: "dark"
            prefs?.put(AppPrefs.APPEARANCE_MODE, modeState)
        }
    private var darkThemeState: String by mutableStateOf(
        prefs?.get(AppPrefs.APPEARANCE_DARK_THEME)?.takeIf { TfThemes.containsKey(it) } ?: "sukut",
    )
    var darkTheme: String
        get() = darkThemeState
        set(value) {
            darkThemeState = value.takeIf { TfThemes.containsKey(it) } ?: "sukut"
            prefs?.put(AppPrefs.APPEARANCE_DARK_THEME, darkThemeState)
        }
    private var lightThemeState: String by mutableStateOf(
        prefs?.get(AppPrefs.APPEARANCE_LIGHT_THEME)?.takeIf { TfThemes.containsKey(it) } ?: "noor",
    )
    var lightTheme: String
        get() = lightThemeState
        set(value) {
            lightThemeState = value.takeIf { TfThemes.containsKey(it) } ?: "noor"
            prefs?.put(AppPrefs.APPEARANCE_LIGHT_THEME, lightThemeState)
        }
    private var serifState: Boolean by mutableStateOf(prefs?.get(AppPrefs.APPEARANCE_SERIF) != "false")
    var serif: Boolean
        get() = serifState
        set(value) {
            serifState = value
            prefs?.put(AppPrefs.APPEARANCE_SERIF, value.toString())
        }
    private var proactivityState: String by mutableStateOf(
        prefs?.get(AppPrefs.AI_PROACTIVITY)?.takeIf { it in setOf("ambient", "balanced", "chatty") }
            ?: "balanced",
    )
    var proactivity: String
        get() = proactivityState
        set(value) {
            proactivityState = value.takeIf { it in setOf("ambient", "balanced", "chatty") } ?: "balanced"
            prefs?.put(AppPrefs.AI_PROACTIVITY, proactivityState)
        }

    val activeThemeKey: String get() = if (mode == "light") lightTheme else darkTheme
}

/**
 * Arms the sync platform for [uid] and starts its schedulers.
 *
 * Idempotent on purpose: it is called once from the cached account id (so an offline
 * launch captures writes immediately) and again when /v1/me confirms who is signed in.
 * [SyncEngine.attach] reinstalls triggers each time and [SyncScheduler.start] returns
 * early if its timers are already running.
 *
 * Nothing here is feature-specific, and nothing ever will be: the engine works out what
 * to synchronise by inspecting the database, so a feature added later is covered by this
 * same call without it changing.
 */
private fun attachSync(uid: String) {
    runCatching {
        getKoin().get<SyncEngine>().attach(uid, deviceTimeZoneId())
        getKoin().get<SyncScheduler>().apply {
            start()
            syncNow(SyncReason.AppStart)
        }
    }
}

@Composable
fun App() {
    val tweaks = remember { Tweaks(getKoin().get()) }
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
            getKoin().get(), getKoin().get(), getKoin().get(), getKoin().get(),
        )
    }
    val onbGate = remember { PrefOnboardingGate(getKoin().get()) }
    val isLoggedIn = remember { getKoin().get<IsLoggedInUseCase>() }
    val tokens = remember { getKoin().get<TokenProvider>() }

    // A reinstall must land on the login screen. On iOS the Keychain survives the uninstall,
    // so without this the tokens of the previous install were still there and the gate below
    // waved a brand-new install straight through. Runs before `authed` is seeded.
    remember { clearSessionIfReinstalled(getKoin().get(), getKoin().get()) }

    // Persisted interface language. Read before the gate below so a returning user never
    // sees the picker again, and a fresh install always does.
    val languages = remember { getKoin().get<LanguageStore>() }
    // Seeded from the stored token rather than false. An expired access token still counts as
    // a session: the Ktor auth plugin refreshes it on the first 401 and only clears the tokens
    // when the refresh endpoint explicitly rejects the refresh credential.
    var authed by remember { mutableStateOf(isLoggedIn()) }
    val authSessionState by tokens.sessionState.collectAsState()
    // Every successful sign-out advances the ViewModel key-space. This prevents server-backed
    // profile/feed/coach state from one account being reused by the next account in-process.
    var sessionEpoch by remember { mutableIntStateOf(0) }
    val financeStore = remember { getKoin().get<FinanceRepository>() }
    val socialStore = remember { getKoin().get<SocialRepository>() }
    val activityIntegration = remember { getKoin().get<ActivityIntegrationCoordinator>() }
    val profilePhoto = remember { getKoin().get<ProfilePhotoRepository>() }
    val profilePhotoBytes by profilePhoto.bytes.collectAsState()
    val syncScheduler = remember { getKoin().get<SyncScheduler>() }
    val appScope = rememberCoroutineScope()

    // The scheduler already knows how to flush on foreground/background; connect those
    // triggers once at the shared UI layer so both Android and iOS use the same lifecycle.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, authed) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    syncScheduler.onEnterForeground()
                    if (authed && isLoggedIn()) {
                        appScope.launch { profilePhoto.retryPending() }
                    }
                }
                Lifecycle.Event.ON_STOP -> syncScheduler.onEnterBackground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // navigation stack
    val nav = remember { mutableStateListOf("today") }
    var navDirection by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }

    fun closeAuthenticatedUi() {
        // Token clearing and the Settings callback can arrive in either order. Keep this
        // transition idempotent so one sign-out creates one fresh ViewModel key-space.
        if (!authed) return
        sessionEpoch++
        authed = false
        sheetOpen = false
        navDirection = 0
        nav.clear()
        nav.add("today")
    }

    // A refresh-token 401 is terminal. The network layer clears the secure credentials and
    // this observer closes the authenticated shell immediately, stops scheduled retries and
    // detaches sync. A transient refresh failure never changes sessionState, so cached data
    // remains usable offline and the next real reconnect can try again.
    LaunchedEffect(authSessionState) {
        if (authSessionState == AuthSessionState.SIGNED_OUT && authed) {
            syncScheduler.stop()
            getKoin().get<SyncEngine>().detach(clearAccountQueue = false)
            closeAuthenticatedUi()
        }
    }
    val thoughtsVm: ThoughtsViewModel =
        viewModel(key = "thoughts:$sessionEpoch") {
            ThoughtsViewModel(
                getKoin().get(),
                getKoin().get(),
                getKoin().get(),
                getKoin().get(),
                socialStore,
            )
        }

    // The same live, location-derived prayer source drives prayer screens and the optional
    // sleep-to-Fajr alignment. With no location fix it returns no Fajr, so the sleep planner
    // truthfully falls back to the user's target wake time.
    val prayerRepository: LocationPrayerScheduleRepository = remember { getKoin().get() }

    // One SleepViewModel for the whole module. Hoisted here rather than created per route so
    // the dashboard, statistics, history and both routines share one derivation of last night
    // — five ViewModels would be five chances for two screens to disagree about it.
    //
    val sleepVm: SleepViewModel = viewModel(key = "sleep:$sessionEpoch") {
        SleepViewModel(
            store = getKoin().get(),
            activity = getKoin().get(),
            health = getKoin().get(),
            fajrMinutesProvider = {
                // Before noon the next Fajr is today's; after noon it is tomorrow's.
                val dayOffset = if (currentSecondsOfDay() < 12 * 60 * 60) 0 else 1
                prayerRepository.schedule(dayOffset)
                    .firstOrNull { it.key == "fajr" }
                    ?.min
                    ?.let(SleepTime::toSigned)
            },
        )
    }
    val journalVm: JournalViewModel = viewModel(key = "journal:$sessionEpoch") {
        getKoin().get<JournalViewModel>()
    }
    val coachVm: CoachViewModel = viewModel(key = "coach:$sessionEpoch") {
        CoachViewModel(getKoin().get(), getKoin().get(), getKoin().get())
    }
    val settingsVm: SettingsViewModel = viewModel(key = "settings:$sessionEpoch") {
        getKoin().get<SettingsViewModel>()
    }
    val quranQuizVm: QuranQuizViewModel = viewModel(key = "quran-quizzes:$sessionEpoch") {
        getKoin().get<QuranQuizViewModel>()
    }

    // Request user location on launch — prayer times are computed from it.
    // The OS permission dialog resolves asynchronously on both platforms, so keep
    // retrying until a fix lands; until then prayer and Fajr-aware sleep planning show their
    // honest unavailable states.
    LaunchedEffect(authed) {
        // Apply the user's saved calc convention (default Uzbekistan 18°/17° + Hanafi
        // until a profile loads). Best-effort: a signed-out launch just keeps the default.
        // All launch-time backend work is best-effort and must stay invisible: `silently`
        // suppresses the global loader and any error snackbar (an offline start should not
        // greet the user with "No internet"), while still mapping and logging every failure.
        // Every call in here needs a bearer token, so skip the whole block for a signed-out
        // launch instead of firing three guaranteed 401s at the backend from the login screen.
        if (authed && isLoggedIn()) {
            val prefs = getKoin().get<PrefPersistence>()
            // Bind sync from the CACHED account id first. Capture must be armed before the
            // user can touch anything, and it must not wait on a network call: a launch in
            // aeroplane mode still has to queue every prayer marked during the flight.
            prefs.get(AppPrefs.USER_ID)?.takeIf { it.isNotBlank() }?.let { attachSync(it) }

            silently {
                runCatching { getKoin().get<GetMeUseCase>()() }.getOrNull()?.let { me ->
                    prayerRepository.setConvention(me.user.calcMethod, me.user.madhab)
                    // Cached so the Today header greets the real user on the next cold start,
                    // before /v1/me has answered — it used to read a hardcoded name from the mock.
                    prefs.put(AppPrefs.DISPLAY_NAME, me.user.displayName)
                    prefs.put(AppPrefs.USER_ID, me.user.uid)
                    // Idempotent: covers the first launch after signing in, where nothing
                    // was cached yet. Arm capture before the non-critical media hydration.
                    attachSync(me.user.uid)
                    // Restore the canonical avatar on a new device. A pending local choice is
                    // never overwritten, and the public download client carries no bearer token.
                    if (profilePhoto.retryPending() == ProfilePhotoUploadResult.NothingPending) {
                        profilePhoto.refreshFromRemote(me.user.photoUrl)
                    }
                }
            }
        }
        if (authed) {
            repeat(LOCATION_ATTEMPTS) {
                if (prayerRepository.refresh()) return@LaunchedEffect
                delay(LOCATION_RETRY_MS)
            }
        }
    }

    // A successful generic sync proves that the backend is reachable again. Reconcile a
    // device-local avatar on that same low-frequency cadence; upload failures remain pending.
    LaunchedEffect(authed) {
        if (!authed) return@LaunchedEffect
        syncScheduler.lastOutcome.filterNotNull().collect { outcome ->
            if (outcome is SyncOutcome.Success) profilePhoto.retryPending()
        }
    }

    // reset root when nav mode flips
    LaunchedEffect(tweaks.nav) {
        navDirection = 0
        nav.clear(); nav.add(if (tweaks.nav == "hub") "hub" else "today")
    }

    val top = nav.last()
    LaunchedEffect(Unit) { activityIntegration.run() }
    LaunchedEffect(languages.current) { activityIntegration.reconcile() }
    fun push(s: String) {
        navDirection = 1
        nav.add(s)
    }
    fun pop() {
        if (nav.size > 1) {
            navDirection = -1
            nav.removeAt(nav.lastIndex)
        }
    }
    fun popFromEdgeGesture() {
        if (nav.size > 1) {
            // The iOS edge gesture already drives the outgoing route interactively. Running
            // AnimatedContent's reverse push afterwards would make the screen move twice.
            navDirection = 0
            nav.removeAt(nav.lastIndex)
        }
    }
    val platformBackDispatcher = remember { PlatformBackDispatcher() }
    SideEffect { platformBackDispatcher.updateFallback(::popFromEdgeGesture) }
    fun setTab(k: String) {
        navDirection = 0
        nav.clear()
        nav.add(k)
    }
    fun openModule(k: String) {
        when {
            k == "today" -> {
                navDirection = 0
                nav.clear()
                nav.add(if (tweaks.nav == "hub") "hub" else "today")
            }
            else -> push(k)
        }
    }
    fun openByModuleName(name: String) {
        val normalized = name.lowercase()
        val exactQuizRoute = normalized.takeIf { candidate ->
            candidate == "quran:quizzes" ||
                (candidate.startsWith("quran:quiz:") &&
                    candidate.removePrefix("quran:quiz:").toIntOrNull()?.let { it in 1..114 } == true)
        }
        val route = exactQuizRoute ?: when (normalized) {
            // Connected Today plan rows carry route keys directly.
            "prayer", "quran", "dhikr", "adhkar", "hifz", "sleep", "finance", "social", "focus",
            "tafakkur", "gratitude", "habits", "learn", "sport" -> name.lowercase()
            "productivity" -> "do"; "reflect" -> "reflect"
            "thoughts" -> "thoughts"
            else -> null
        }
        if (route != null) push(route)
    }

    val isTabRoot = top in TAB_ROOTS
    val showTab = tweaks.nav == "tabs" && isTabRoot
    // The ayah reader pins its own audio player to the bottom edge — keep the FAB off it.
    // A hifz session pins its grade bar there for the same reason, and is meant to be a
    // screen you are not interrupted on.
    val showFab = top !in listOf("coach", "lesson", "compose", "appearance", "profile", "settings", "hifz:review") &&
        !top.startsWith("surah:") && !top.startsWith("quran:quiz:")

    CompositionLocalProvider(
        LocalTf provides colors,
        LocalStrings provides Strings.of(languages.current),
        LocalProactivity provides tweaks.proactivity,
        LocalSerifHead provides tweaks.serif,
        LocalNavMode provides tweaks.nav,
        LocalProfilePhotoBytes provides profilePhotoBytes,
        LocalPlatformBackDispatcher provides platformBackDispatcher,
    ) {
      // One full-size Box so the global loader + snackbar overlay sits above whichever stage
      // is showing — language picker, onboarding or the main shell.
      Box(Modifier.fillMaxSize()) {
        // Language comes before everything: the carousel, the login copy and every screen
        // after them are written in whatever is chosen here, so it cannot be asked later.
        if (!languages.hasChosen) {
            LanguageScreen(initial = languages.current, onChoose = { languages.choose(it) })
        } else if (!authed) {
            OnboardingRoot(
                auth = onbAuth,
                gate = onbGate,
                onFinished = {
                    authed = true
                    // This ViewModel is created before authentication and intentionally skips
                    // its first request while signed out. Load the real feed after login.
                    thoughtsVm.refresh(silent = true)
                },
            )
        } else Box(Modifier.fillMaxSize().background(colors.bg1)) {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .platformEdgeBackGesture(
                            enabled = nav.size > 1,
                            onBack = platformBackDispatcher::goBack,
                        ),
                ) {
                    AnimatedContent(
                        targetState = top,
                        transitionSpec = {
                            when {
                                navDirection > 0 ->
                                    (slideInHorizontally(tween(300)) { it } + fadeIn(tween(180)))
                                        .togetherWith(
                                            slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(180)),
                                        )
                                navDirection < 0 ->
                                    (slideInHorizontally(tween(300)) { -it / 3 } + fadeIn(tween(180)))
                                        .togetherWith(
                                            slideOutHorizontally(tween(300)) { it } + fadeOut(tween(180)),
                                        )
                                else -> EnterTransition.None.togetherWith(ExitTransition.None)
                            }
                        },
                        label = "screen",
                    ) { route ->
                        Box(Modifier.fillMaxSize()) {
                            ScreenHost(
                                route = route,
                                tweaks = tweaks,
                                sessionKey = sessionEpoch,
                                sleepVm = sleepVm,
                                journalVm = journalVm,
                                coachVm = coachVm,
                                settingsVm = settingsVm,
                                quranQuizVm = quranQuizVm,
                                languages = languages,
                                financeStore = financeStore,
                                socialStore = socialStore,
                                thoughts = thoughtsVm.posts,
                                thoughtsLoading = thoughtsVm.loading,
                                thoughtsLoadingMore = thoughtsVm.loadingMore,
                                thoughtsHasMore = thoughtsVm.hasMore,
                                thoughtsErrorKey = thoughtsVm.error,
                                thoughtsLoadMoreErrorKey = thoughtsVm.loadMoreError,
                                thoughtPublishing = thoughtsVm.publishing,
                                thoughtPublishErrorKey = thoughtsVm.publishError,
                                onTab = ::setTab,
                                onPush = ::push,
                                onPop = ::pop,
                                onOpenModule = ::openModule,
                                onOpenByModule = ::openByModuleName,
                                onRetryThoughts = { thoughtsVm.refresh() },
                                onLoadMoreThoughts = thoughtsVm::loadMore,
                                onPublish = { text -> thoughtsVm.publish(text) { pop() } },
                                onLike = thoughtsVm::like,
                                onLoggedOut = {
                                    closeAuthenticatedUi()
                                },
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
    sessionKey: Int,
    sleepVm: SleepViewModel,
    journalVm: JournalViewModel,
    coachVm: CoachViewModel,
    settingsVm: SettingsViewModel,
    quranQuizVm: QuranQuizViewModel,
    languages: LanguageStore,
    financeStore: FinanceRepository,
    socialStore: SocialRepository,
    thoughts: List<SocialPost>,
    thoughtsLoading: Boolean,
    thoughtsLoadingMore: Boolean,
    thoughtsHasMore: Boolean,
    thoughtsErrorKey: String?,
    thoughtsLoadMoreErrorKey: String?,
    thoughtPublishing: Boolean,
    thoughtPublishErrorKey: String?,
    onTab: (String) -> Unit,
    onPush: (String) -> Unit,
    onPop: () -> Unit,
    onOpenModule: (String) -> Unit,
    onOpenByModule: (String) -> Unit,
    onRetryThoughts: () -> Unit,
    onLoadMoreThoughts: () -> Unit,
    onPublish: (String) -> Unit,
    onLike: (String) -> Unit,
    onLoggedOut: () -> Unit,
) {
    when (route) {
        "today" -> TodayScreen(
            financeStore = financeStore,
            socialStore = socialStore,
            sleepViewModel = sleepVm,
            onOpenModule = onOpenByModule,
            onCoach = { onPush("coach") },
            onProfile = { onPush("profile") },
            onOpenPrayer = { onPush("prayer") },
            onOpenQuran = { onPush("quran") },
        )
        "hub" -> HubScreen(onOpen = onOpenModule, onProfile = { onPush("profile") })
        "profile" -> ProfileScreen(sessionKey = sessionKey, onBack = onPop)
        "learn" -> LearnScreen(
            sessionKey = sessionKey,
            onLesson = { onPush("lesson") },
            onSubject = { onPush("subject:$it") },
            onFlashcards = { onPush("decks:$LOCAL_VOCABULARY_SUBJECT_ID") },
        )
        "do" -> ProductivityScreen(onOpenHabits = { onPush("habits") })
        "thoughts" -> ThoughtsScreen(
            posts = thoughts,
            store = socialStore,
            loading = thoughtsLoading,
            loadingMore = thoughtsLoadingMore,
            hasMore = thoughtsHasMore,
            errorKey = thoughtsErrorKey,
            loadMoreErrorKey = thoughtsLoadMoreErrorKey,
            onRetry = onRetryThoughts,
            onLoadMore = onLoadMoreThoughts,
            onCompose = { onPush("compose") },
            onLike = onLike,
        )
        "compose" -> ComposeThoughtScreen(
            publishing = thoughtPublishing,
            errorKey = thoughtPublishErrorKey,
            onBack = onPop,
            onPublish = onPublish,
        )
        "more" -> MoreScreen(onOpen = onOpenModule)
        "coach" -> CoachScreen(
            viewModel = coachVm,
            onBack = onPop,
            onOpenSettings = { onPush("settings") },
        )
        "lesson" -> LessonScreen(sessionKey = sessionKey, onBack = onPop)
        "social" -> SocialScreen(store = socialStore, onBack = onPop)
        "finance" -> FinanceScreen(store = financeStore, onBack = onPop)
        "reflect" -> ReflectScreen(viewModel = journalVm, onBack = onPop, serif = tweaks.serif)
        "prayer" -> PrayerTrackerScreen(onBack = onPop)
        "tafakkur" -> TafakkurJournalScreen(viewModel = journalVm, onBack = onPop)
        "gratitude" -> GratitudeScreen(onBack = onPop)
        // Habits owns its own back stack (detail, statistics, calendar, awards,
        // create), so the app only routes to its entry point.
        "habits" -> HabitsScreen(onBack = onPop)
        "quran", "dhikr", "adhkar" -> QuranDhikrScreen(
            onBack = onPop,
            initialTab = route,
            onOpenSurah = { onPush("surah:$it") },
            onOpenHifz = { onPush("hifz") },
            quizCount = quranQuizVm.state.totalGoals,
            onOpenQuizzes = { onPush("quran:quizzes") },
        )
        "quran:quizzes" -> QuranQuizLibraryScreen(
            viewModel = quranQuizVm,
            onBack = onPop,
            onOpenQuiz = { onPush("quran:quiz:$it") },
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
        // Sleep & Rest. One ViewModel across all five routes — see where it is created above.
        "sleep" -> SleepScreen(
            vm = sleepVm,
            onBack = onPop,
            onOpenStats = { onPush("sleep:stats") },
            onOpenHistory = { onPush("sleep:history") },
            onOpenRoutine = { phase -> onPush("sleep:routine:${phase.name.lowercase()}") },
            onOpenGoals = { onPush("sleep:goals") },
            onOpenQuran = { onPush(it) },
        )
        "sleep:stats" -> SleepStatsScreen(vm = sleepVm, onBack = onPop)
        "sleep:history" -> SleepHistoryScreen(vm = sleepVm, onBack = onPop)
        "sleep:goals" -> SleepGoalsScreen(vm = sleepVm, onBack = onPop)
        "sleep:routine:bedtime" -> SleepRoutineScreen(
            vm = sleepVm,
            phase = RoutinePhase.BEDTIME,
            onBack = onPop,
            onOpenQuran = { onPush(it) },
        )
        "sleep:routine:morning" -> SleepRoutineScreen(
            vm = sleepVm,
            phase = RoutinePhase.MORNING,
            onBack = onPop,
            onOpenQuran = { onPush(it) },
        )
        "sport" -> SportGymScreen(onBack = onPop)
        "focus" -> FocusScreen(onBack = onPop)
        "appearance" -> AppearanceScreen(tweaks = tweaks, onBack = onPop)
        "settings" -> SettingsScreen(
            viewModel = settingsVm,
            languages = languages,
            tweaks = tweaks,
            onBack = onPop,
            onOpenAppearance = { onPush("appearance") },
            onLoggedOut = onLoggedOut,
        )
        else -> when {
            // Qurʼan reader — one route per surah so the back stack restores the right one.
            route.startsWith("surah:") -> {
                val number = route.removePrefix("surah:").toIntOrNull()
                if (number != null && number in 1..114) {
                    AyahReaderScreen(surahNumber = number, sessionKey = sessionKey, onBack = onPop)
                }
                else QuranDhikrScreen(
                    onBack = onPop,
                    onOpenSurah = { onPush("surah:$it") },
                    onOpenHifz = { onPush("hifz") },
                    quizCount = quranQuizVm.state.totalGoals,
                    onOpenQuizzes = { onPush("quran:quizzes") },
                )
            }
            route.startsWith("quran:quiz:") -> {
                val number = route.removePrefix("quran:quiz:").toIntOrNull()
                if (number != null && number in 1..114) {
                    QuranQuizSessionScreen(number, quranQuizVm, onPop)
                } else {
                    QuranQuizLibraryScreen(quranQuizVm, onPop) { onPush("quran:quiz:$it") }
                }
            }
            route.startsWith("subject:") -> {
                val sid = route.removePrefix("subject:")
                SubjectScreen(
                    subjectId = sid,
                    sessionKey = sessionKey,
                    onBack = onPop,
                    onLesson = { onPush("lesson") },
                    onFlashcards = { onPush("decks:$sid") },
                )
            }
            route.startsWith("decks:") -> FlashcardDecksScreen(
                subjectId = route.removePrefix("decks:"),
                sessionKey = sessionKey,
                onBack = onPop,
                onDeck = { onPush("deck:$it") },
            )
            route.startsWith("deck:") -> {
                val deckId = route.removePrefix("deck:")
                FlashcardDeckScreen(
                    deckId = deckId,
                    sessionKey = sessionKey,
                    onBack = onPop,
                    onStudy = { filter, dir -> onPush("review:$deckId:${filter.name}:${dir.name}") },
                )
            }
            route.startsWith("review:") -> {
                val parts = route.removePrefix("review:").split(":")
                FlashcardReviewScreen(
                    deckId = parts[0],
                    sessionKey = sessionKey,
                    filter = ReviewFilter.valueOf(parts.getOrElse(1) { "DUE" }),
                    direction = ReviewDirection.valueOf(parts.getOrElse(2) { "FRONT_BACK" }),
                    onBack = onPop,
                )
            }
            else -> {
                TodayScreen(
                    financeStore = financeStore,
                    socialStore = socialStore,
                    sleepViewModel = sleepVm,
                    onOpenModule = onOpenByModule,
                    onCoach = { onPush("coach") },
                    onOpenPrayer = { onPush("prayer") },
                    onOpenQuran = { onPush("quran") },
                )
            }
        }
    }
}
