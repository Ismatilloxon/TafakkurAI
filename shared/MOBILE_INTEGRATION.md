# TafakkurAI — KMP Client Integration

Clean-architecture network layer for the shared KMP module, talking to the Go backend. Koin for DI, Ktor + kotlinx.serialization for networking.

> ⚠️ Do this on the **clean, reimaged machine**. Do **not** edit `shared/build.gradle.kts` on the compromised host (it's the malware vector). Add the dependencies below on the clean box.
>
> The code uses package `uz.tafakkur.*`. If your app uses a different base package, rename the `uz/tafakkur` folder + `package` lines to match.

## Layers

```
network/   Ktor client, TokenProvider, auth+refresh, error envelope → ApiException
data/
  dto/         @Serializable DTOs (snake_case via @SerialName)
  mapper/      DTO → domain
  remote/      Ktor API services (incl. coach SSE reader)
  repository/  domain repository implementations
domain/
  model/       platform-agnostic models (matches your UserProfile / SocialPost)
  repository/  repository interfaces
  usecase/     use cases (one call = one action)
di/          Koin modules + initKoin(...)
```

Dependency direction: `di → data → domain` and `data → network`. UI/ViewModels depend only on `domain` use cases.

## 1. Gradle dependencies (`shared/build.gradle.kts`)

```kotlin
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.0.20"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            val ktor = "3.0.1"
            implementation("io.ktor:ktor-client-core:$ktor")
            implementation("io.ktor:ktor-client-content-negotiation:$ktor")
            implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
            implementation("io.ktor:ktor-client-auth:$ktor")
            implementation("io.ktor:ktor-client-logging:$ktor")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("io.insert-koin:koin-core:4.0.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:3.0.1")
            implementation("io.insert-koin:koin-android:4.0.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.1")
        }
    }
}
```

## 2. Start Koin

**Android** (`Application`):
```kotlin
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig

class TafakkurApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            config = ApiConfig(baseUrl = "http://10.0.2.2:8080/"), // emulator → host
            engine = OkHttp.create(),
        ) { androidContext(this@TafakkurApp) }
    }
}
```

**iOS** (from Swift via a Kotlin helper in `iosMain`):
```kotlin
import io.ktor.client.engine.darwin.Darwin
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig

fun initKoinIos() = initKoin(
    config = ApiConfig(baseUrl = "http://localhost:8080/"),
    engine = Darwin.create(),
)
```
Call `MainKt.initKoinIos()` from `iOSApp.swift`.

> **TokenProvider:** the default `InMemoryTokenProvider` loses tokens on restart. Provide a persistent one (EncryptedSharedPreferences / Keychain or `multiplatform-settings`) as the 3rd arg of `initKoin(...)`.

## 3. Use it from a ViewModel

```kotlin
class TodayViewModel(
    private val getMe: GetMeUseCase,
    private val getPrayerTimes: GetPrayerTimesUseCase,
    private val trackPrayer: TrackPrayerUseCase,
) : ViewModel() {

    fun load(lat: Double, lng: Double) = viewModelScope.launch {
        val me = getMe()                              // user + gamification
        val times = getPrayerTimes(lat, lng)          // next prayer + seconds_remaining
        // update UI state...
    }
}
```

Inject with `koinViewModel()` (Compose) or resolve via Koin. Register your ViewModels in a Koin `viewModel { }` / `factory { }` module.

### Coach streaming (SSE → Flow)

```kotlin
class CoachViewModel(private val send: SendCoachMessageUseCase) : ViewModel() {
    private val _reply = MutableStateFlow("")
    val reply = _reply.asStateFlow()

    fun ask(text: String, conversationId: String? = null) = viewModelScope.launch {
        _reply.value = ""
        send(text, conversationId).collect { event ->
            when (event) {
                is CoachEvent.Delta -> _reply.value += event.text
                is CoachEvent.Done  -> { /* persist event.conversationId */ }
                is CoachEvent.Error -> { /* show error */ }
            }
        }
    }
}
```

### Sign-in

```kotlin
// phone
val verificationId = startPhoneOtp("+998901234567")
val session = signIn(AuthProvider.Phone(verificationId, "123456"))
// google / apple
val session = signIn(AuthProvider.Google(googleIdToken))
if (session.needsOnboarding) { /* go to onboarding */ } else { /* go Home */ }
```

Tokens are stored automatically on sign-in; the Ktor `Auth` plugin attaches the bearer and silently refreshes via `/v1/auth/refresh` on 401.

## Error handling

Every failed call throws `uz.tafakkur.network.ApiException(code, message, status)` — the backend's `{ error: { code, message } }` envelope. Catch it in repositories/ViewModels to show user-facing messages.
