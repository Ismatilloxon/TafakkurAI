package uz.tafakkur.di

import com.example.tafakkurai.db.DatabaseDriverFactory
import com.example.tafakkurai.db.createTafakkurDb
import com.example.tafakkurai.flashcards.FlashcardStore
import com.example.tafakkurai.prayer.LocationPrayerScheduleRepository
import com.example.tafakkurai.prayer.PrayerCalculator
import com.example.tafakkurai.prayer.PrayerScheduleRepository
import com.example.tafakkurai.prayer.PrayerTimelineService
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import uz.tafakkur.data.remote.AuthApi
import uz.tafakkur.data.remote.CoachApi
import uz.tafakkur.data.remote.FeedApi
import uz.tafakkur.data.remote.LearnApi
import uz.tafakkur.data.remote.PrayerApi
import uz.tafakkur.data.remote.TrackerApi
import uz.tafakkur.data.remote.UserApi
import uz.tafakkur.data.repository.AuthRepositoryImpl
import uz.tafakkur.data.repository.CoachRepositoryImpl
import uz.tafakkur.data.repository.FeedRepositoryImpl
import uz.tafakkur.data.repository.LearnRepositoryImpl
import uz.tafakkur.data.repository.PrayerRepositoryImpl
import uz.tafakkur.data.repository.TrackerRepositoryImpl
import uz.tafakkur.data.repository.UserRepositoryImpl
import uz.tafakkur.domain.repository.AuthRepository
import uz.tafakkur.domain.repository.CoachRepository
import uz.tafakkur.domain.repository.FeedRepository
import uz.tafakkur.domain.repository.LearnRepository
import uz.tafakkur.domain.repository.PrayerRepository
import uz.tafakkur.domain.repository.TrackerRepository
import uz.tafakkur.domain.repository.UserRepository
import uz.tafakkur.domain.usecase.CompleteLessonUseCase
import uz.tafakkur.domain.usecase.CompleteOnboardingUseCase
import uz.tafakkur.domain.usecase.CreateTrackerEntryUseCase
import uz.tafakkur.domain.usecase.GetCoachHistoryUseCase
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

val dataModule = module {
    single { AuthApi(get()) }
    single { UserApi(get()) }
    single { PrayerApi(get()) }
    single { FeedApi(get()) }
    single { CoachApi(get()) }
    single { LearnApi(get()) }
    single { TrackerApi(get()) }

    single<AuthRepository> { AuthRepositoryImpl(get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<PrayerRepository> { PrayerRepositoryImpl(get()) }
    single<FeedRepository> { FeedRepositoryImpl(get()) }
    single<CoachRepository> { CoachRepositoryImpl(get()) }
    single<LearnRepository> { LearnRepositoryImpl(get()) }
    single<TrackerRepository> { TrackerRepositoryImpl(get()) }
}

/**
 * Prayer times computed on-device from the user's coordinates. The platform layer
 * must register a PrayerLocationManager (Android needs the application Context).
 * Everything shares one repository instance so a single fix updates every screen.
 */
val prayerModule = module {
    // Muslim World League angles, Standard (non-Hanafi) Asr, middle-of-night rule at
    // high latitudes. Swap the convention here, e.g.
    // PrayerCalculator(CalculationMethod.KARACHI.copy(asr = AsrJuristic.HANAFI)).
    single { PrayerCalculator() }
    single { LocationPrayerScheduleRepository(get(), get()) }
    single<PrayerScheduleRepository> { get<LocationPrayerScheduleRepository>() }
    single { PrayerTimelineService(get()) }
}

/** Local persistence (SQLDelight). Platform layer must register a DatabaseDriverFactory. */
val localModule = module {
    single { createTafakkurDb(get<DatabaseDriverFactory>()) }
    single { FlashcardStore(get()) }
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
}

/**
 * Start Koin from the platform layer. Pass a platform HttpClientEngine
 * (OkHttp on Android, Darwin on iOS) and, optionally, a persistent TokenProvider.
 *
 * Android: initKoin(ApiConfig(baseUrl), OkHttp.create()) { androidContext(this@App) }
 * iOS:     initKoin(ApiConfig(baseUrl), Darwin.create())
 */
fun initKoin(
    config: ApiConfig,
    engine: HttpClientEngine,
    tokenProvider: TokenProvider = InMemoryTokenProvider(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication = startKoin {
    appDeclaration()
    modules(
        networkModule(config, engine, tokenProvider),
        dataModule,
        domainModule,
        localModule,
        prayerModule,
    )
}
