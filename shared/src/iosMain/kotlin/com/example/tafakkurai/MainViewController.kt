package com.example.tafakkurai

import androidx.compose.ui.window.ComposeUIViewController
import com.example.tafakkurai.db.DatabaseDriverFactory
import com.example.tafakkurai.location.PrayerLocationManager
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig

fun MainViewController() = ComposeUIViewController {
    if (KoinPlatform.getKoinOrNull() == null) {
        initKoin(ApiConfig(baseUrl = "http://localhost:8080/"), Darwin.create()) {
            modules(module {
                single { DatabaseDriverFactory() }
                single { PrayerLocationManager() }
            })
        }
    }
    App()
}