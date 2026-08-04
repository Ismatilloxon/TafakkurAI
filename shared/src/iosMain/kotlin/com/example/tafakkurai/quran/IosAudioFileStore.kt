package com.example.tafakkurai.quran

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * Downloaded recitation on iOS, kept in Library/Caches.
 *
 * Caches and not Documents: iOS may reclaim it when storage runs low, which is right for
 * audio that can be fetched again, and it keeps the download out of iCloud backup.
 *
 * Written to a `.part` name and moved into place so an interrupted download can never be
 * mistaken later for a complete ayah.
 */
@OptIn(ExperimentalForeignApi::class)
class IosAudioFileStore : AudioFileStore {

    private val fileManager = NSFileManager.defaultManager

    private val directory: String? by lazy {
        val caches = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory, NSUserDomainMask, true,
        ).firstOrNull() as? String ?: return@lazy null
        val path = "$caches/$DIRECTORY"
        if (!fileManager.fileExistsAtPath(path)) {
            fileManager.createDirectoryAtPath(path, true, null, null)
        }
        path
    }

    override fun localUrl(key: String): String? {
        val path = pathFor(key) ?: return null
        if (!fileManager.fileExistsAtPath(path)) return null
        val size = fileManager.attributesOfItemAtPath(path, null)
            ?.get("NSFileSize") as? Number
        if (size != null && size.toLong() <= 0L) return null
        return NSURL.fileURLWithPath(path).absoluteString
    }

    override fun write(key: String, bytes: ByteArray): String? {
        val path = pathFor(key) ?: return null
        val temporary = "$path.part"
        val data = bytes.toNSData() ?: return null
        if (!data.writeToFile(temporary, true)) return null
        // Replace rather than merge: a stale file at the target would otherwise win.
        if (fileManager.fileExistsAtPath(path)) fileManager.removeItemAtPath(path, null)
        if (!fileManager.moveItemAtPath(temporary, path, null)) {
            fileManager.removeItemAtPath(temporary, null)
            return null
        }
        return NSURL.fileURLWithPath(path).absoluteString
    }

    override fun sizeBytes(): Long {
        val path = directory ?: return 0L
        val names = fileManager.contentsOfDirectoryAtPath(path, null).orEmpty()
        return names.sumOf { name ->
            val size = fileManager.attributesOfItemAtPath("$path/$name", null)
                ?.get("NSFileSize") as? Number
            size?.toLong() ?: 0L
        }
    }

    override fun removeOthers(keyPrefix: String) {
        val path = directory ?: return
        fileManager.contentsOfDirectoryAtPath(path, null).orEmpty().forEach { name ->
            val fileName = name as? String ?: return@forEach
            if (!fileName.startsWith(keyPrefix)) fileManager.removeItemAtPath("$path/$fileName", null)
        }
    }

    override fun clear() {
        val path = directory ?: return
        fileManager.contentsOfDirectoryAtPath(path, null).orEmpty().forEach { name ->
            fileManager.removeItemAtPath("$path/$name", null)
        }
    }

    private fun pathFor(key: String): String? = directory?.let { "$it/$key" }

    private fun ByteArray.toNSData(): NSData? {
        if (isEmpty()) return null
        return usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }

    private companion object {
        const val DIRECTORY = "quran-audio"
    }
}
