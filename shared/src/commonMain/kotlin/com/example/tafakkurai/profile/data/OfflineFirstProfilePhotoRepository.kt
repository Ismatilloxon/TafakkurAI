package com.example.tafakkurai.profile.data

import com.example.tafakkurai.profile.domain.ProfilePhotoRepository
import com.example.tafakkurai.profile.domain.ProfilePhotoUploadResult
import com.example.tafakkurai.sync.Connectivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.tafakkur.core.feedback.silently
import uz.tafakkur.domain.repository.UserRepository

/**
 * Offline-first profile-photo reconciliation.
 *
 * A local revision is committed before upload. Only that revision can acknowledge the response,
 * while [uploadGate] prevents foreground, profile-refresh and reconnect triggers from sending the
 * same bytes concurrently. Failures deliberately leave the durable revision pending.
 */
class OfflineFirstProfilePhotoRepository(
    private val local: SqlProfilePhotoLocalStore,
    private val remote: UserRepository,
    private val downloader: PublicProfilePhotoDownloader,
    private val connectivity: Connectivity,
    appScope: CoroutineScope,
) : ProfilePhotoRepository {
    private val uploadGate = Mutex()
    private val downloadGate = Mutex()

    override val bytes: StateFlow<ByteArray?> = local.bytes

    init {
        // Reachability is only a trigger, never a source of truth. A false positive simply leaves
        // the revision pending; a false negative is covered by foreground/profile refreshes.
        appScope.launch {
            connectivity.online.filter { it }.collect { retryPending() }
        }
    }

    override suspend fun saveSelection(bytes: ByteArray): Boolean = local.save(bytes)

    override suspend fun retryPending(): ProfilePhotoUploadResult = uploadGate.withLock {
        val pending = local.pending() ?: return@withLock ProfilePhotoUploadResult.NothingPending
        if (!connectivity.isOnline()) return@withLock ProfilePhotoUploadResult.Deferred

        val url = try {
            // ProfileScreen owns its localized inline progress/error state. Suppressing global
            // network feedback also keeps reconnect/foreground retries invisible.
            silently {
                remote.uploadPhoto(pending.bytes, "avatar.jpg", "image/jpeg")
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            return@withLock ProfilePhotoUploadResult.Deferred
        }

        if (local.markUploaded(pending.revision, url)) {
            ProfilePhotoUploadResult.Uploaded(url)
        } else {
            // A newer selection won the race or persistence could not acknowledge the response.
            // Never clear that work; the already-queued/current lifecycle trigger retries it.
            ProfilePhotoUploadResult.Deferred
        }
    }

    override suspend fun refreshFromRemote(remoteUrl: String?) {
        val canonicalUrl = remoteUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
        downloadGate.withLock {
            if (!connectivity.isOnline() || !local.needsRemote(canonicalUrl)) return@withLock

            // Downloading is intentionally outside the upload gate: the local store repeats its
            // pending-revision check atomically when committing, so a photo selected while this
            // is in flight always wins without making user actions wait for a public CDN.
            val downloaded = downloader.download(canonicalUrl) ?: return@withLock
            local.cacheRemote(canonicalUrl, downloaded)
        }
    }

    override suspend fun clear() = local.clear()
}
