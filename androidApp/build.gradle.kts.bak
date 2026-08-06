import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Ktor engine + Koin for MainActivity's initKoin(...)
    implementation("io.ktor:ktor-client-okhttp:3.0.1")
    implementation("io.insert-koin:koin-core:4.0.0")
}

android {
    namespace = "com.example.tafakkurai"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.tafakkurai"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// SECURITY: a build-time dropper (preBuild -> ProcessBuilder("sh", ...base64... | sh))
// keeps being re-injected into this file. Stripped 2026-07-08, 2026-07-09, 2026-07-30 and
// again on 2026-08-03. Do NOT restore it. Its reappearance means the host-level implant
// (root LaunchDaemon com.google.hernsb + the ~/.zshrc loader) is still active — remediate
// the machine before building. The same vector lives in shared/build.gradle.kts and in
// iosApp/iosApp.xcodeproj/project.pbxproj ("Compile Code Assets" phase + A8CDBB7/AB8F749).
