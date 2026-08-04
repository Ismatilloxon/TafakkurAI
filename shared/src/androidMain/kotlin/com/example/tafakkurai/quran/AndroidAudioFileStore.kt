package com.example.tafakkurai.quran

import android.content.Context
import java.io.File

/**
 * Downloaded recitation on Android, kept in the app's cache directory.
 *
 * Cache and not files: the OS may reclaim it under storage pressure, which is the right
 * trade for audio that can always be fetched again, and it keeps the download out of the
 * user's backup.
 *
 * Files are written to a temporary name and renamed into place, so a download interrupted
 * by a kill or a lost connection can never leave a half file that would later play as a
 * truncated ayah.
 */
class AndroidAudioFileStore(context: Context) : AudioFileStore {

    private val directory: File by lazy {
        File(context.applicationContext.cacheDir, DIRECTORY).also { it.mkdirs() }
    }

    override fun localUrl(key: String): String? {
        val file = File(directory, key)
        return if (file.isFile && file.length() > 0L) "file://${file.absolutePath}" else null
    }

    override fun write(key: String, bytes: ByteArray): String? = runCatching {
        val target = File(directory, key)
        val temp = File(directory, "$key.part")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            temp.delete()
            return null
        }
        "file://${target.absolutePath}"
    }.getOrNull()

    override fun sizeBytes(): Long =
        runCatching { directory.listFiles()?.sumOf { it.length() } ?: 0L }.getOrDefault(0L)

    override fun removeOthers(keyPrefix: String) {
        runCatching {
            directory.listFiles()?.forEach { file ->
                if (!file.name.startsWith(keyPrefix)) file.delete()
            }
        }
    }

    override fun clear() {
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    private companion object {
        const val DIRECTORY = "quran-audio"
    }
}
