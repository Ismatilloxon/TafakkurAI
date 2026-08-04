package com.example.tafakkurai

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.tafakkurai.db.DatabaseDriverFactory
import com.example.tafakkurai.location.PrayerLocationManager
import com.example.tafakkurai.quran.AndroidAudioFileStore
import com.example.tafakkurai.quran.AndroidAyahAudioPlayer
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig
import uz.tafakkur.network.PersistentTokenProvider
import uz.tafakkur.network.SecureStore

class MainActivity : ComponentActivity() {

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* result applied on next current() call */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (GlobalContext.getOrNull() == null) {
            // 10.0.2.2 = host localhost from the Android emulator.
            initKoin(
                ApiConfig(baseUrl = "http://10.0.2.2:8080/"),
                OkHttp.create(),
                // AndroidKeyStore-backed, so the session outlives the process. The default
                // InMemoryTokenProvider dropped it on every launch.
                tokenProvider = PersistentTokenProvider(SecureStore(applicationContext)),
                audioPlayer = AndroidAyahAudioPlayer(applicationContext),
                audioFileStore = AndroidAudioFileStore(applicationContext),
            ) {
                modules(module {
                    single { DatabaseDriverFactory(applicationContext) }
                    single { PrayerLocationManager(applicationContext) }
                })
            }
        }

        locationPermission.launch(
            buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                // Without it the recitation notification is silent-but-invisible on 13+;
                // playback itself still works either way.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
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