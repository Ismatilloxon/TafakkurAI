package com.example.tafakkurai

import androidx.compose.ui.window.ComposeUIViewController
import com.example.tafakkurai.db.DatabaseDriverFactory
import com.example.tafakkurai.location.PrayerLocationManager
import com.example.tafakkurai.quran.IosAudioFileStore
import com.example.tafakkurai.quran.IosAyahAudioPlayer
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig
import uz.tafakkur.network.PersistentTokenProvider
import uz.tafakkur.network.SecureStore

fun MainViewController() = ComposeUIViewController {
    if (KoinPlatform.getKoinOrNull() == null) {
        initKoin(
            // Dev: the Go backend runs on this Mac. A physical device cannot use
            // localhost, so point it at the Mac's LAN address (same Wi-Fi).
            ApiConfig(baseUrl = "http://192.168.202.27:8080/"),
            Darwin.create(),
            // Keychain-backed, so the session outlives the process. The default
            // InMemoryTokenProvider dropped it on every launch.
            tokenProvider = PersistentTokenProvider(SecureStore()),
            audioPlayer = IosAyahAudioPlayer(),
            audioFileStore = IosAudioFileStore(),
        ) {
            modules(module {
                single { DatabaseDriverFactory() }
                single { PrayerLocationManager() }
            })
        }
    }
    App()
}