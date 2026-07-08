package com.example.tafakkurai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/** App-wide profile image, held in memory. Avatar reads this everywhere. */
object ProfileImage {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
        private set
    /** raw bytes kept so callers can persist to disk later */
    var bytes: ByteArray? = null
        private set

    fun set(newBytes: ByteArray?, newBitmap: ImageBitmap?) {
        bytes = newBytes
        bitmap = newBitmap
    }
    fun clear() { bytes = null; bitmap = null }
}

/**
 * Returns a lambda that launches the native photo picker.
 * [onResult] gets the picked image bytes (null = cancelled/failed).
 */
@Composable
expect fun rememberImagePicker(onResult: (ByteArray?) -> Unit): () -> Unit
