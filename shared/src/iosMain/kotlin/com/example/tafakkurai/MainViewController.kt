package com.example.tafakkurai

import androidx.compose.ui.window.ComposeUIViewController
import com.example.tafakkurai.db.DatabaseDriverFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig

fun MainViewController() = ComposeUIViewController {
    if (GlobalContext.getOrNull() == null) {
        initKoin(ApiConfig(baseUrl = "http://localhost:8080/"), Darwin.create()) {
            modules(module { single { DatabaseDriverFactory() } })
        }
    }
    App()
}