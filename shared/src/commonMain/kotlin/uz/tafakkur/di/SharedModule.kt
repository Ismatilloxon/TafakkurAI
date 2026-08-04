package uz.tafakkur.di

import com.example.tafakkurai.adhkar.AdhkarStore
import com.example.tafakkurai.db.DatabaseDriverFactory
import com.example.tafakkurai.db.createTafakkurDb
import com.example.tafakkurai.dhikr.DhikrStore
import com.example.tafakkurai.gratitude.GratEntryRefPayload
import com.example.tafakkurai.gratitude.GratEntrySyncPayload
import com.example.tafakkurai.gratitude.GratChallengeSyncPayload
import com.example.tafakkurai.gratitude.GratSettings
import com.example.tafakkurai.gratitude.GratSettingsSyncPayload
import com.example.tafakkurai.gratitude.GratitudeStore
import com.example.tafakkurai.i18n.LanguageStore
import com.example.tafakkurai.flashcards.FlashcardStore
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
import com.example.tafakkurai.spiritual.DhikrPersistence
import com.example.tafakkurai.spiritual.PrefPersistence
import com.example.tafakkurai.spiritual.QuranPersistence
import com.example.tafakkurai.spiritual.SqlAdhkarPersistence
import com.example.tafakkurai.spiritual.SqlDhikrPersistence
import com.example.tafakkurai.spiritual.SqlPrefPersistence
import com.example.tafakkurai.spiritual.SqlQuranPersistence
import com.example.tafakkurai.prayer.PrayerMark
import com.example.tafakkurai.prayer.PrayerMarkStore
import com.example.tafakkurai.prayer.PrayerMarkSyncPayload
import com.example.tafakkurai.sync.Outbox
import com.example.tafakkurai.sync.SyncEngine
import com.example.tafakkurai.sync.SyncHandler
import com.example.tafakkurai.sync.syncJson
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
import uz.tafakkur.data.remote.TrackerApi
import uz.tafakkur.data.remote.UserApi
import uz.tafakkur.data.repository.AuthRepositoryImpl
import uz.tafakkur.data.repository.CoachRepositoryImpl
import uz.tafakkur.data.repository.FeedRepositoryImpl
import uz.tafakkur.data.repository.GratitudeRepositoryImpl
import uz.tafakkur.data.repository.LearnRepositoryImpl
import uz.tafakkur.data.repository.PrayerRepositoryImpl
import uz.tafakkur.data.repository.TrackerRepositoryImpl
import uz.tafakkur.data.repository.UserRepositoryImpl
import uz.tafakkur.domain.repository.AuthRepository
import uz.tafakkur.domain.repository.CoachRepository
import uz.tafakkur.domain.repository.FeedRepository
import uz.tafakkur.domain.repository.GratitudeRepository
import uz.tafakkur.domain.repository.LearnRepository
import uz.tafakkur.domain.repository.PrayerRepository
import uz.tafakkur.domain.repository.TrackerRepository
import uz.tafakkur.domain.repository.UserRepository
import uz.tafakkur.domain.usecase.CompleteLessonUseCase
import uz.tafakkur.domain.usecase.CompleteOnboardingUseCase
import uz.tafakkur.domain.usecase.CreateTrackerEntryUseCase
import uz.tafakkur.domain.usecase.CompleteGratitudeChallengeDayUseCase
import uz.tafakkur.domain.usecase.DeleteGratitudeEntryUseCase
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
import uz.tafakkur.domain.usecase.ToggleLikeUseCase
import uz.tafakkur.domain.usecase.TrackPrayerUseCase
import uz.tafakkur.domain.usecase.UpdatePrayerSettingsUseCase
import uz.tafakkur.domain.usecase.UpdateProfileUseCase
import uz.tafakkur.domain.usecase.UploadPhotoUseCase
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

    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<PrayerRepository> { PrayerRepositoryImpl(get(), get()) }
    single<FeedRepository> { FeedRepositoryImpl(get(), get()) }
    single<CoachRepository> { CoachRepositoryImpl(get(), get()) }
    single<LearnRepository> { LearnRepositoryImpl(get(), get()) }
    single<TrackerRepository> { TrackerRepositoryImpl(get(), get()) }
    single<GratitudeRepository> { GratitudeRepositoryImpl(get(), get()) }
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
    single { createTafakkurDb(get<DatabaseDriverFactory>()) }
    single { FlashcardStore(get()) }
    // Outbox is the shared write-through queue every local store appends its changes to.
    single { Outbox(get()) }
    // Shared single instance = the ring, card and tracker all read/write one source; the
    // Outbox arg makes every mark change queue for backend sync on the next app enter.
    single { PrayerMarkStore(get(), get()) }
    // Same single-instance rule: the Gratitude home card, history, jar and the Today
    // dashboard must read one store or they would disagree about today's entries.
    single { GratitudeStore(get(), get(), get()) }
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
    single<PrefPersistence> { SqlPrefPersistence(get()) }
    single<DhikrPersistence> { SqlDhikrPersistence(get()) }
    single<AdhkarPersistence> { SqlAdhkarPersistence(get()) }

    single { QuranProgressStore(get(), get()) }
    // Interface language. A single so the first-run picker, the profile switcher and every
    // screen read one instance — switching language must change all of them at once.
    single { LanguageStore(get()) }
    single { DhikrStore(get(), get()) }
    single { AdhkarStore(get()) }

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
 * Offline-first sync. [SyncEngine] drains the Outbox on app enter, dispatching each queued
 * op to the handler for its entity. Add a new entity by registering its handler here and
 * calling [Outbox.enqueue] from that entity's store — nothing else changes.
 */
val syncModule = module {
    single<Map<String, SyncHandler>> {
        val track = get<TrackPrayerUseCase>()
        val saveGratitude = get<SaveGratitudeEntryUseCase>()
        val favGratitude = get<SetGratitudeFavouriteUseCase>()
        val deleteGratitude = get<DeleteGratitudeEntryUseCase>()
        val joinChallenge = get<JoinGratitudeChallengeUseCase>()
        val completeDay = get<CompleteGratitudeChallengeDayUseCase>()
        val saveGratitudeSettings = get<SaveGratitudeSettingsUseCase>()
        mapOf(
            // A gratitude entry carries its client id, so create/update/replay all land
            // on the same backend row — the queue can retry as often as it needs to.
            "gratitudeEntry" to SyncHandler { op, payload ->
                when (op) {
                    "favourite" -> {
                        val p = syncJson.decodeFromString<GratEntryRefPayload>(payload)
                        favGratitude(p.id, p.favourite)
                    }
                    "delete" -> deleteGratitude(syncJson.decodeFromString<GratEntryRefPayload>(payload).id)
                    else -> saveGratitude(syncJson.decodeFromString<GratEntrySyncPayload>(payload).entry)
                }
            },
            "gratitudeChallenge" to SyncHandler { op, payload ->
                val p = syncJson.decodeFromString<GratChallengeSyncPayload>(payload)
                if (op == "complete") completeDay(p.challengeId, p.day) else joinChallenge(p.challengeId, p.joined)
            },
            "gratitudeSettings" to SyncHandler { _, payload ->
                val p = syncJson.decodeFromString<GratSettingsSyncPayload>(payload)
                saveGratitudeSettings(
                    GratSettings(p.reminderSlot, p.reminderTime, p.faceId, p.appLock, p.hidePreview, p.cloudSync),
                )
            },
            "prayerMark" to SyncHandler { _, payload ->
                val p = syncJson.decodeFromString<PrayerMarkSyncPayload>(payload)
                // ontime/jamaat = a completion the backend records; "upcoming" (un-mark) has
                // no backend verb yet, so it stays local-only until the API gains a delete.
                if (p.state != PrayerMark.NONE.id) track(p.prayer, p.date)
            },
        )
    }
    single { SyncEngine(get(), get()) }
}

val domainModule = module {
    factory { StartPhoneOtpUseCase(get()) }
    factory { SignInUseCase(get()) }
    factory { LogoutUseCase(get()) }
    factory { IsLoggedInUseCase(get()) }

    factory { GetMeUseCase(get()) }
    factory { UpdateProfileUseCase(get()) }
    factory { ReplaceInterestsUseCase(get()) }
    factory { UpdatePrayerSettingsUseCase(get()) }
    factory { CompleteOnboardingUseCase(get()) }
    factory { UploadPhotoUseCase(get()) }

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
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    appDeclaration()
    modules(
        networkModule(config, engine, tokenProvider),
        feedbackModule,
        dataModule,
        domainModule,
        localModule,
        syncModule,
        prayerModule,
        quranModule(audioPlayer, audioFileStore),
    )
}
