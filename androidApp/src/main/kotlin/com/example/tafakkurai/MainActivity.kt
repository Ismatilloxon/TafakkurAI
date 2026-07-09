package com.example.tafakkurai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.tafakkurai.db.DatabaseDriverFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import uz.tafakkur.di.initKoin
import uz.tafakkur.network.ApiConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (GlobalContext.getOrNull() == null) {
            // 10.0.2.2 = host localhost from the Android emulator.
            initKoin(ApiConfig(baseUrl = "http://10.0.2.2:8080/"), OkHttp.create()) {
                modules(module { single { DatabaseDriverFactory(applicationContext) } })
            }
        }

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