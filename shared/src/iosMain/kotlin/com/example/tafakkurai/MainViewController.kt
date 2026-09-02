package com.example.tafakkurai

import androidx.compose.ui.window.ComposeUIViewController
import com.example.tafakkurai.db.DatabaseDriverFactory
import com.example.tafakkurai.location.PrayerLocationManager
import com.example.tafakkurai.quran.IosAudioFileStore
import com.example.tafakkurai.quran.IosAyahAudioPlayer
import com.example.tafakkurai.sleep.health.HealthKitSleepBridge
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig
import uz.tafakkur.network.PersistentTokenProvider
import uz.tafakkur.network.SecureStore

private const val FALLBACK_IOS_API_BASE_URL = "http://localhost:8080/"

/**
 * Resolves the backend address at packaging/launch time instead of treating device loopback as
 * the developer Mac. Xcode launch environments take precedence, then Info.plist supplies the
 * shared local-development address. The production package can replace the same key with HTTPS.
 */
private fun iosApiBaseUrl(): String {
    val environmentUrl = NSProcessInfo.processInfo.environment["TAFAKKUR_API_BASE_URL"]
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val packagedUrl = (NSBundle.mainBundle.objectForInfoDictionaryKey("TafakkurAPIBaseURL") as? String)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    val candidate = environmentUrl ?: packagedUrl ?: FALLBACK_IOS_API_BASE_URL
    val allowed = candidate.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: FALLBACK_IOS_API_BASE_URL
    return if (allowed.endsWith('/')) allowed else "$allowed/"
}

fun MainViewController() = ComposeUIViewController {
    if (KoinPlatform.getKoinOrNull() == null) {
        initKoin(
            ApiConfig(baseUrl = iosApiBaseUrl()),
            Darwin.create(),
            // Keychain-backed, so the session outlives the process. The default
            // InMemoryTokenProvider dropped it on every launch.
            tokenProvider = PersistentTokenProvider(SecureStore()),
            audioPlayer = IosAyahAudioPlayer(),
            audioFileStore = IosAudioFileStore(),
            healthBridge = HealthKitSleepBridge(),
        ) {
            modules(module {
                single { DatabaseDriverFactory() }
                single { PrayerLocationManager() }
            })
        }
    }
    App()
}
