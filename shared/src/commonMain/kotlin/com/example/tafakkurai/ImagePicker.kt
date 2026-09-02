package com.example.tafakkurai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/** Supplied once by App so every avatar reads the same persisted photo without Koin lookup. */
val LocalProfilePhotoBytes: ProvidableCompositionLocal<ByteArray?> =
    staticCompositionLocalOf { null }

/**
 * Returns a lambda that launches the native photo picker.
 * [onResult] gets the picked image bytes (null = cancelled/failed).
 */
@Composable
expect fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit
