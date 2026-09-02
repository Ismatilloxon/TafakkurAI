package uz.tafakkur.di

import com.example.tafakkurai.adhkar.AdhkarStore
import com.example.tafakkurai.ai.data.OnDeviceActionRouter
import com.example.tafakkurai.ai.data.VocabularySourceStore
import com.example.tafakkurai.ai.domain.LocalActionParser
import com.example.tafakkurai.profile.data.OfflineFirstProfilePhotoRepository
import com.example.tafakkurai.profile.data.SqlProfilePhotoLocalStore
import com.example.tafakkurai.profile.domain.ProfilePhotoRepository
import app.cash.sqldelight.db.SqlDriver
import com.example.tafakkurai.db.DatabaseDriverFactory
import com.example.tafakkurai.getPlatform
import com.example.tafakkurai.db.createTafakkurDb
import com.example.tafakkurai.dhikr.DhikrStore
import com.example.tafakkurai.gratitude.GratEntryRefPayload
import com.example.tafakkurai.gratitude.GratEntrySyncPayload
import com.example.tafakkurai.gratitude.GratChallengeSyncPayload
import com.example.tafakkurai.gratitude.GratSettings
import com.example.tafakkurai.gratitude.GratSettingsSyncPayload
import com.example.tafakkurai.gratitude.GratitudeStore
import com.example.tafakkurai.habits.HabitStore
import com.example.tafakkurai.i18n.LanguageStore
import com.example.tafakkurai.i18n.Strings
import com.example.tafakkurai.integration.ActivityIntegrationCoordinator
import com.example.tafakkurai.journal.data.JournalStore
import com.example.tafakkurai.journal.domain.JournalRepository
import com.example.tafakkurai.journal.presentation.JournalViewModel
import com.example.tafakkurai.learning.data.QuranQuizStore
import com.example.tafakkurai.learning.domain.QuranQuizRepository
import com.example.tafakkurai.learning.presentation.QuranQuizViewModel
import com.example.tafakkurai.flashcards.FlashcardStore
import com.example.tafakkurai.finance.data.FinanceStore
import com.example.tafakkurai.finance.domain.FinanceRepository
import com.example.tafakkurai.quran.hifz.HifzGradeSyncPayload
import com.example.tafakkurai.quran.hifz.HifzPlanner
import com.example.tafakkurai.quran.hifz.HifzStore
import com.example.tafakkurai.quran.AudioFileStore
import com.example.tafakkurai.quran.AyahAudioCache
import com.example.tafakkurai.quran.AyahAudioPlayer
import com.example.tafakkurai.quran.BundledQuranRepository
import com.example.tafakkurai.quran.NoOpAudioFileStore
import com.example.tafakkurai.quran.NoOpAyahAudioPlayer
import com.example.tafakkurai.quran.QuranProgressStore
import com.example.tafakkurai.quran.QuranRepository
import com.example.tafakkurai.quran.MushafPageRepository
import com.example.tafakkurai.quran.QuranComMushafPageRepository
import com.example.tafakkurai.quran.RemoteQuranRepository
import com.example.tafakkurai.spiritual.AdhkarPersistence
import com.example.tafakkurai.spiritual.AdhkarWriteTransaction
import com.example.tafakkurai.spiritual.DhikrPersistence
import com.example.tafakkurai.spiritual.DhikrWriteTransaction
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.spiritual.QuranPersistence
import com.example.tafakkurai.spiritual.QuranWriteTransaction
import com.example.tafakkurai.spiritual.SqlAdhkarPersistence
import com.example.tafakkurai.spiritual.SqlAdhkarWriteTransaction
import com.example.tafakkurai.spiritual.SqlDhikrPersistence
import com.example.tafakkurai.spiritual.SqlDhikrWriteTransaction
import com.example.tafakkurai.spiritual.SqlPrefPersistence
import com.example.tafakkurai.spiritual.SqlQuranPersistence
import com.example.tafakkurai.spiritual.SqlQuranWriteTransaction
import com.example.tafakkurai.prayer.PrayerMark
import com.example.tafakkurai.prayer.PrayerMarkStore
import com.example.tafakkurai.sleep.SleepActivityLog
import com.example.tafakkurai.social.data.SocialStore
import com.example.tafakkurai.social.domain.SocialRepository
import com.example.tafakkurai.sync.ActivityLog
import com.example.tafakkurai.tasks.data.TaskStore
import com.example.tafakkurai.sleep.SleepStore
import com.example.tafakkurai.sleep.health.HealthSleepBridge
import com.example.tafakkurai.sleep.health.NoOpHealthSleepBridge
import com.example.tafakkurai.settings.data.LocalSettingsRepository
import com.example.tafakkurai.settings.data.LocalAccountMemoryResetter
import com.example.tafakkurai.settings.data.PrivateAccountGateway
import com.example.tafakkurai.settings.data.SchedulerSettingsSyncGateway
import com.example.tafakkurai.settings.domain.GetSettingsUseCase
import com.example.tafakkurai.settings.domain.IsCloudConversationEnabledUseCase
import com.example.tafakkurai.settings.domain.ObserveSettingsSyncUseCase
import com.example.tafakkurai.settings.domain.SetCloudConversationUseCase
import com.example.tafakkurai.settings.domain.SettingsAccountGateway
import com.example.tafakkurai.settings.domain.SettingsRepository
import com.example.tafakkurai.settings.domain.SettingsSyncGateway
import com.example.tafakkurai.settings.domain.SignOutSettingsUseCase
import com.example.tafakkurai.settings.domain.SyncSettingsNowUseCase
import com.example.tafakkurai.settings.presentation.SettingsViewModel
import com.example.tafakkurai.sync.Connectivity
import com.example.tafakkurai.sync.OptimisticConnectivity
import com.example.tafakkurai.sync.SyncApi
import com.example.tafakkurai.sync.SyncEngine
import com.example.tafakkurai.sync.SyncOutbox
import com.example.tafakkurai.sync.SyncScheduler
import com.example.tafakkurai.sync.syncMetadata
import kotlinx.serialization.decodeFromString
import com.example.tafakkurai.prayer.LocationPrayerScheduleRepository
import com.example.tafakkurai.prayer.PrayerScheduleRepository
import com.example.tafakkurai.prayer.PrayerTimelineService
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uz.tafakkur.core.feedback.DefaultLoadingController
import uz.tafakkur.core.feedback.DefaultNetworkErrorMapper
import uz.tafakkur.core.feedback.DefaultSnackbarController
import uz.tafakkur.core.feedback.LoadingController
import uz.tafakkur.core.feedback.NetworkErrorMapper
import uz.tafakkur.core.feedback.NetworkExecutor
import uz.tafakkur.core.feedback.NetworkLogger
import uz.tafakkur.core.feedback.PrintlnNetworkLogger
import uz.tafakkur.core.feedback.SnackbarController
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import uz.tafakkur.data.remote.AuthApi
import uz.tafakkur.data.remote.CoachApi
import uz.tafakkur.data.remote.FeedApi
import uz.tafakkur.data.remote.GratitudeApi
import uz.tafakkur.data.remote.LearnApi
import uz.tafakkur.data.remote.PrayerApi
import uz.tafakkur.data.remote.QuranApi
import uz.tafakkur.data.remote.TrackerApi
import uz.tafakkur.data.remote.UserApi
import uz.tafakkur.data.repository.AuthRepositoryImpl
import uz.tafakkur.data.repository.CoachRepositoryImpl
import uz.tafakkur.data.repository.FeedRepositoryImpl
import uz.tafakkur.data.repository.GratitudeRepositoryImpl
import uz.tafakkur.data.repository.HifzRepositoryImpl
import uz.tafakkur.data.repository.LearnRepositoryImpl
import uz.tafakkur.data.repository.PrayerRepositoryImpl
import uz.tafakkur.data.repository.TrackerRepositoryImpl
import uz.tafakkur.data.repository.UserRepositoryImpl
import uz.tafakkur.domain.repository.AuthRepository
import uz.tafakkur.domain.repository.CoachRepository
import uz.tafakkur.domain.repository.FeedRepository
import uz.tafakkur.domain.repository.GratitudeRepository
import uz.tafakkur.domain.repository.HifzRepository
import uz.tafakkur.domain.repository.LearnRepository
import uz.tafakkur.domain.repository.PrayerRepository
import uz.tafakkur.domain.repository.TrackerRepository
import uz.tafakkur.domain.repository.UserRepository
import uz.tafakkur.domain.usecase.CompleteLessonUseCase
import uz.tafakkur.domain.usecase.CompleteOnboardingUseCase
import uz.tafakkur.domain.usecase.CreateTrackerEntryUseCase
import uz.tafakkur.domain.usecase.CompleteGratitudeChallengeDayUseCase
import uz.tafakkur.domain.usecase.DeleteGratitudeEntryUseCase
import uz.tafakkur.domain.usecase.GradeHifzUseCase
import uz.tafakkur.domain.usecase.GetCoachHistoryUseCase
import uz.tafakkur.domain.usecase.GetGratitudeInsightsUseCase
import uz.tafakkur.domain.usecase.GetGratitudePromptsUseCase
import uz.tafakkur.domain.usecase.JoinGratitudeChallengeUseCase
import uz.tafakkur.domain.usecase.PullGratitudeChangesUseCase
import uz.tafakkur.domain.usecase.SaveGratitudeEntryUseCase
import uz.tafakkur.domain.usecase.SaveGratitudeSettingsUseCase
import uz.tafakkur.domain.usecase.SetGratitudeFavouriteUseCase
import uz.tafakkur.domain.usecase.GetFeedUseCase
import uz.tafakkur.domain.usecase.GetLessonUseCase
import uz.tafakkur.domain.usecase.GetLessonsUseCase
import uz.tafakkur.domain.usecase.GetMeUseCase
import uz.tafakkur.domain.usecase.GetPrayerTimesUseCase
import uz.tafakkur.domain.usecase.GetSubjectsUseCase
import uz.tafakkur.domain.usecase.GetTrackerEntriesUseCase
import uz.tafakkur.domain.usecase.IsLoggedInUseCase
import uz.tafakkur.domain.usecase.LogoutUseCase
import uz.tafakkur.domain.usecase.PublishThoughtUseCase
import uz.tafakkur.domain.usecase.ReplaceInterestsUseCase
import uz.tafakkur.domain.usecase.SendCoachMessageUseCase
import uz.tafakkur.domain.usecase.SignInUseCase
import uz.tafakkur.domain.usecase.StartPhoneOtpUseCase
import uz.tafakkur.domain.usecase.GetTelegramBotUseCase
import uz.tafakkur.domain.usecase.VerifyTelegramCodeUseCase
import uz.tafakkur.domain.usecase.StartGoogleLoginUseCase
import uz.tafakkur.domain.usecase.PollGoogleLoginUseCase
import uz.tafakkur.domain.usecase.ToggleLikeUseCase
import uz.tafakkur.domain.usecase.TrackPrayerUseCase
import uz.tafakkur.domain.usecase.UpdatePrayerSettingsUseCase
import uz.tafakkur.domain.usecase.UpdateProfileUseCase
import uz.tafakkur.domain.usecase.UploadPhotoUseCase
import uz.tafakkur.domain.usecase.SetEmailUseCase
import uz.tafakkur.domain.usecase.StartPhoneAttachUseCase
import uz.tafakkur.domain.usecase.VerifyPhoneAttachUseCase
import uz.tafakkur.network.ApiConfig
import uz.tafakkur.network.InMemoryTokenProvider
import uz.tafakkur.network.TokenProvider
import uz.tafakkur.network.createHttpClient

fun networkModule(config: ApiConfig, engine: HttpClientEngine, tokens: TokenProvider) = module {
    single { config }
    single<TokenProvider> { tokens }
    single<HttpClientEngine> { engine }
    single { createHttpClient(get(), get(), get()) }
}

/**
 * Cross-cutting UI feedback: the global loader, the snackbar bus, the error mapper/logger and
 * the [NetworkExecutor] every repository funnels its calls through. Registered before
 * [dataModule] because the repositories depend on the executor.
 *
 * Each controller gets its own long-lived scope (SupervisorJob so one failure never tears the
 * others down); Dispatchers.Default keeps it off the main thread and available in tests.
 */
val feedbackModule = module {
    single<LoadingController> { DefaultLoadingController(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<SnackbarController> { DefaultSnackbarController(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single<NetworkErrorMapper> { DefaultNetworkErrorMapper() }
    single<NetworkLogger> { PrintlnNetworkLogger() }
    single { NetworkExecutor(get(), get(), get(), get()) }
}

val dataModule = module {
    single { AuthApi(get()) }
    single { UserApi(get()) }
    single { PrayerApi(get()) }
    single { FeedApi(get()) }
    single { CoachApi(get()) }
    single { LearnApi(get()) }
    single { TrackerApi(get()) }
    single { GratitudeApi(get()) }
    single { QuranApi(get()) }

    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<PrayerRepository> { PrayerRepositoryImpl(get(), get()) }
    single<FeedRepository> { FeedRepositoryImpl(get(), get()) }
    single<CoachRepository> { CoachRepositoryImpl(get(), get()) }
    single<LearnRepository> { LearnRepositoryImpl(get(), get()) }
    single<TrackerRepository> { TrackerRepositoryImpl(get(), get()) }
    single<GratitudeRepository> { GratitudeRepositoryImpl(get(), get()) }
    single<HifzRepository> { HifzRepositoryImpl(get(), get()) }
}

/**
 * Prayer times computed on-device from the user's coordinates. The platform layer
 * must register a PrayerLocationManager (Android needs the application Context).
 * Everything shares one repository instance so a single fix updates every screen.
 */
val prayerModule = module {
    // Calc convention lives inside the repository (default Uzbekistan 18°/17° + Hanafi
    // Asr, user-changeable via setConvention). Coordinates drive the actual times.
    single { LocationPrayerScheduleRepository(get()) }
    single<PrayerScheduleRepository> { get<LocationPrayerScheduleRepository>() }
    single { PrayerTimelineService(get()) }
}

/** Local persistence (SQLDelight). Platform layer must register a DatabaseDriverFactory. */
val localModule = module {
    // The driver is a binding of its own because the sync platform works against the raw
    // schema — discovering tables, installing triggers, assembling payloads — which the
    // generated TafakkurDb API cannot express. Everything else keeps using TafakkurDb.
    single<SqlDriver> { get<DatabaseDriverFactory>().createDriver() }
    single { createTafakkurDb(get<SqlDriver>()) }
    single { SqlProfilePhotoLocalStore(get()) }
    single<ProfilePhotoRepository> {
        OfflineFirstProfilePhotoRepository(
            local = get(),
            remote = get(),
            connectivity = get(),
            appScope = get(),
        )
    }
    single { FlashcardStore(get(), activity = get()) }
    single {
        FinanceStore(
            db = get(),
            activity = get(),
            remoteChanges = get<SyncEngine>().remoteChanges,
            scope = get(),
        )
    }
    single<FinanceRepository> { get<FinanceStore>() }
    single { SocialStore(get(), activity = get()) }
    single<SocialRepository> { get<SocialStore>() }
    single { TaskStore(get(), activity = get()) }
    single { VocabularySourceStore(get(), activity = get()) }
    single { LocalActionParser() }
    single {
        OnDeviceActionRouter(
            parser = get(),
            tasks = get(),
            habits = get(),
            gratitude = get(),
            hifz = get(),
            flashcards = get(),
            sources = get(),
        )
    }
    // Shared single instance = the ring, card and tracker all read/write one source.
    // No sync argument: marking a prayer is a local write, and the database itself
    // notices it (see SyncSchema).
    single { PrayerMarkStore(get(), activity = get()) }
    // Same single-instance rule: the Gratitude home card, history, jar and the Today
    // dashboard must read one store or they would disagree about today's entries.
    single { GratitudeStore(get(), get(), activity = get()) }
    // Habits. A single for the same reason: the Today block, the habits home, the detail
    // screen and the statistics all derive their streaks from one entry table, and a
    // second instance would let two of them disagree about whether today is done.
    single { HabitStore(get(), activity = get()) }
}

/**
 * Tafakkur Journal owns its data and presentation wiring as one feature module.
 *
 * Both public routes receive the same app-hoisted ViewModel. Its repository is a single
 * SQL source of truth; only the state holder is a factory so tests and future scoped hosts
 * can request an isolated composer without constructing SQL or localization dependencies.
 */
val journalModule = module {
    single<JournalRepository> {
        JournalStore(
            db = get(),
            activity = get(),
            remoteChanges = get<SyncEngine>().remoteChanges,
            scope = get(),
        )
    }
    factory {
        val languages = get<LanguageStore>()
        JournalViewModel(
            repository = get(),
            localizedLabel = { key -> Strings.of(languages.current)[key] },
        )
    }
}

/**
 * Qurʼan, dhikr and adhkar.
 *
 * The three stores are singles for the same reason `PrayerMarkStore` is: the Qurʼan tab,
 * the ayah reader and the Today dashboard must read one instance, or they would show
 * different percentages for the same data.
 *
 * [QuranRepository] is bound to the remote implementation with the bundled excerpt as its
 * fallback, so the reader works offline and improves when there is a connection.
 *
 * @param audioPlayer platform recitation player. Defaults to [NoOpAyahAudioPlayer], which
 *  makes the player UI report "audio unavailable" rather than showing dead controls.
 *  Android passes `AndroidAyahAudioPlayer(context)`, iOS passes `IosAyahAudioPlayer()`.
 * @param fileStore where downloaded recitation is written. Defaults to [NoOpAudioFileStore],
 *  which stores nothing and leaves every ayah streaming.
 */
fun quranModule(
    audioPlayer: AyahAudioPlayer,
    fileStore: AudioFileStore = NoOpAudioFileStore(),
) = module {
    single<QuranPersistence> { SqlQuranPersistence(get()) }
    single<QuranWriteTransaction> { SqlQuranWriteTransaction(get()) }
    single<PrefPersistence> { SqlPrefPersistence(get()) }
    single<DhikrPersistence> { SqlDhikrPersistence(get()) }
    single<DhikrWriteTransaction> { SqlDhikrWriteTransaction(get()) }
    single<AdhkarPersistence> { SqlAdhkarPersistence(get()) }
    single<AdhkarWriteTransaction> { SqlAdhkarWriteTransaction(get()) }

    // Interface language. A single so the first-run picker, the profile switcher and every
    // screen read one instance — switching language must change all of them at once.
    single { LanguageStore(get()) }
    single {
        val languages = get<LanguageStore>()
        QuranProgressStore(
            get(), get(), activity = get(), writes = get(),
            currentLanguageCode = { languages.current.code },
        )
    }
    single { DhikrStore(get(), get(), activity = get(), writes = get()) }
    single { AdhkarStore(get(), activity = get(), writes = get()) }

    // Memorization. Singles for the same reason the stores above are: the hifz home, the
    // setup screen and a running session must read one card table, or the home would show
    // counts a session had already changed.
    single { HifzStore(get(), activity = get()) }
    single { HifzPlanner(get(), get()) }
    single<QuranQuizRepository> {
        QuranQuizStore(
            db = get(),
            activity = get(),
            remoteChanges = get<SyncEngine>().remoteChanges,
            scope = get(),
        )
    }
    factory { QuranQuizViewModel(get()) }

    // Reading the qari through a lambda means changing it takes effect on the next fetch
    // without rebuilding the repository graph.
    single { BundledQuranRepository { get<QuranProgressStore>().reciter } }
    single<QuranRepository> {
        RemoteQuranRepository(
            engine = get(),
            fallback = get(),
            currentReciter = { get<QuranProgressStore>().reciter },
            currentTranslation = { get<QuranProgressStore>().translation },
            currentMushaf = { get<QuranProgressStore>().mushaf },
        )
    }

    // The printed page layout — line and page metadata for the mushaf reading mode. A
    // separate source from the ayah text: only quran.com carries where the words are set.
    single<MushafPageRepository> { QuranComMushafPageRepository(engine = get()) }

    single<AyahAudioPlayer> { audioPlayer }

    // Downloaded recitation. With the default no-op store nothing is written to disk and
    // every ayah streams, so a platform that has not bound a store still plays.
    single<AudioFileStore> { fileStore }
    single { AyahAudioCache(get(), get()) }
}

/**
 * The generic sync platform.
 *
 * Compare this with what it replaced: a `Map<String, SyncHandler>` holding a hand-written
 * lambda per entity, plus a `when` over operation names inside several of them. Every new
 * feature meant a new handler, a new payload class and an edit to this file.
 *
 * There is now nothing here to edit. [SyncEngine] discovers the tables in the local
 * database at startup, installs triggers on them, and synchronises whatever it finds. A
 * feature added in six months registers itself by existing.
 */
fun syncModule(connectivity: Connectivity = OptimisticConnectivity()) = module {
    single {
        ActivityLog(
            db = get(),
            remoteChanges = get<SyncEngine>().remoteChanges,
            scope = get(),
        )
    }
    single { SleepActivityLog(get()) }
    single { ActivityIntegrationCoordinator(get(), get(), get(), get()) }
    single { SyncOutbox(get()) }
    single { SyncApi(get()) }

    // Reachability is optional: the default assumes a network and lets failure plus
    // backoff do the work. Android passes ConnectivityManager-backed reachability so
    // an offline queue is flushed as soon as a validated network returns.
    single<Connectivity> { connectivity }

    single {
        SyncEngine(
            driver = get(),
            db = get(),
            outbox = get(),
            api = get(),
            metadata = syncMetadata(appVersion = "shared", platform = getPlatform().name),
        )
    }

    // One scope for the scheduler's timers, cancelled with the app rather than with any
    // screen: a sync that starts as the user backgrounds the app must not be cancelled by
    // the composable that happened to trigger it going away.
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { SyncScheduler(engine = get(), scope = get(), connectivity = get()) }
}

/** App-wide privacy, sync and account settings behind domain ports. */
val settingsModule = module {
    single<SettingsRepository> { LocalSettingsRepository(get()) }
    single<SettingsSyncGateway> { SchedulerSettingsSyncGateway(get(), get(), get()) }
    single {
        LocalAccountMemoryResetter(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
    single<SettingsAccountGateway> { PrivateAccountGateway(get(), get(), get(), get(), get(), get()) }

    factory { GetSettingsUseCase(get()) }
    factory { SetCloudConversationUseCase(get()) }
    factory { IsCloudConversationEnabledUseCase(get()) }
    factory { ObserveSettingsSyncUseCase(get()) }
    factory { SyncSettingsNowUseCase(get()) }
    factory { SignOutSettingsUseCase(get()) }
    factory { SettingsViewModel(get(), get(), get(), get(), get()) }
}

/**
 * Sleep & Rest.
 *
 * Nothing here registers anything with the sync platform, and that is the point: `sleepSession`
 * and `sleepRoutineMark` are ordinary tables, so [SyncEngine] discovers them at the next
 * `attach()`, installs triggers and backfills them without a line of configuration.
 *
 * @param healthBridge the platform health store. Defaults to [NoOpHealthSleepBridge], which
 *  leaves the module fully usable — manual logging, routines, smart bedtime and the coach all
 *  work without a health store; only the automatic import is absent. iOS passes
 *  `HealthKitSleepBridge()`. Android deliberately keeps the no-op until its Health Connect
 *  dependency, read permissions, and Activity result contract can be added together.
 */
fun sleepModule(healthBridge: HealthSleepBridge = NoOpHealthSleepBridge()) = module {
    // A single for the same reason PrayerMarkStore and GratitudeStore are: the dashboard, the
    // statistics screen, the routine checklist and the Today card must read one instance, or
    // they would disagree about last night.
    single { SleepStore(get(), get(), get()) }

    // ActivityLog and SleepActivityLog are app-wide singles owned by syncModule. Keeping
    // their bindings there gives every feature one immutable event stream and avoids Koin
    // replacing those definitions when the sleep module is loaded.

    single<HealthSleepBridge> { healthBridge }
}

val domainModule = module {
    factory { StartPhoneOtpUseCase(get()) }
    factory { SignInUseCase(get()) }
    factory { GetTelegramBotUseCase(get()) }
    factory { VerifyTelegramCodeUseCase(get()) }
    factory { StartGoogleLoginUseCase(get()) }
    factory { PollGoogleLoginUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { IsLoggedInUseCase(get()) }

    factory { GetMeUseCase(get()) }
    factory { UpdateProfileUseCase(get()) }
    factory { ReplaceInterestsUseCase(get()) }
    factory { UpdatePrayerSettingsUseCase(get()) }
    factory { CompleteOnboardingUseCase(get()) }
    factory { UploadPhotoUseCase(get()) }
    factory { SetEmailUseCase(get()) }
    factory { StartPhoneAttachUseCase(get()) }
    factory { VerifyPhoneAttachUseCase(get()) }

    factory { GetPrayerTimesUseCase(get()) }
    factory { TrackPrayerUseCase(get()) }

    factory { GetFeedUseCase(get()) }
    factory { PublishThoughtUseCase(get()) }
    factory { ToggleLikeUseCase(get()) }

    factory { SendCoachMessageUseCase(get()) }
    factory { GetCoachHistoryUseCase(get()) }

    factory { GetSubjectsUseCase(get()) }
    factory { GetLessonsUseCase(get()) }
    factory { GetLessonUseCase(get()) }
    factory { CompleteLessonUseCase(get()) }

    factory { CreateTrackerEntryUseCase(get()) }
    factory { GetTrackerEntriesUseCase(get()) }

    factory { SaveGratitudeEntryUseCase(get()) }
    factory { SetGratitudeFavouriteUseCase(get()) }
    factory { DeleteGratitudeEntryUseCase(get()) }
    factory { GetGratitudeInsightsUseCase(get()) }
    factory { GetGratitudePromptsUseCase(get()) }
    factory { JoinGratitudeChallengeUseCase(get()) }
    factory { CompleteGratitudeChallengeDayUseCase(get()) }
    factory { SaveGratitudeSettingsUseCase(get()) }
    factory { PullGratitudeChangesUseCase(get(), get()) }

    factory { GradeHifzUseCase(get()) }
}

/**
 * Start Koin from the platform layer. Pass a platform HttpClientEngine
 * (OkHttp on Android, Darwin on iOS) and, optionally, a persistent TokenProvider.
 *
 * Android: initKoin(ApiConfig(baseUrl), OkHttp.create(), audioPlayer = AndroidAyahAudioPlayer()) { androidContext(this@App) }
 * iOS:     initKoin(ApiConfig(baseUrl), Darwin.create(), audioPlayer = IosAyahAudioPlayer())
 *
 * Leaving [audioPlayer] at its default keeps everything else working and makes the
 * Qurʼan player report that recitation is unavailable on this build.
 */
fun initKoin(
    config: ApiConfig,
    engine: HttpClientEngine,
    tokenProvider: TokenProvider = InMemoryTokenProvider(),
    audioPlayer: AyahAudioPlayer = NoOpAyahAudioPlayer(),
    audioFileStore: AudioFileStore = NoOpAudioFileStore(),
    connectivity: Connectivity = OptimisticConnectivity(),
    healthBridge: HealthSleepBridge = NoOpHealthSleepBridge(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    appDeclaration()
    modules(
        networkModule(config, engine, tokenProvider),
        feedbackModule,
        dataModule,
        domainModule,
        localModule,
        syncModule(connectivity),
        journalModule,
        prayerModule,
        quranModule(audioPlayer, audioFileStore),
        sleepModule(healthBridge),
        settingsModule,
    )
}
