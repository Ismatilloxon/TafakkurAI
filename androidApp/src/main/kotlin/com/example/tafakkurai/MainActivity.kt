package com.example.tafakkurai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.tafakkurai.db.DatabaseDriverFactory
import com.example.tafakkurai.location.PrayerLocationManager
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig

class MainActivity : ComponentActivity() {

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* result applied on next current() call */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (GlobalContext.getOrNull() == null) {
            // 10.0.2.2 = host localhost from the Android emulator.
            initKoin(ApiConfig(baseUrl = "http://10.0.2.2:8080/"), OkHttp.create()) {
                modules(module {
                    single { DatabaseDriverFactory(applicationContext) }
                    single { PrayerLocationManager(applicationContext) }
                })
            }
        }

        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}