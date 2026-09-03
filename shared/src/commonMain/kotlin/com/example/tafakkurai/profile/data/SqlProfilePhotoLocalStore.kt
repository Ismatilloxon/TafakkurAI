package com.example.tafakkurai.profile.data

import com.example.tafakkurai.db.TafakkurDb
import com.example.tafakkurai.flashcards.epochMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Immutable upload work item. [revision] prevents an older response clearing a newer photo. */
internal data class PendingProfilePhoto(
    val bytes: ByteArray,
    val revision: Long,
)

/** SQLDelight data source for the private, device-local avatar cache and upload checkpoint. */
class SqlProfilePhotoLocalStore(
    private val db: TafakkurDb,
    private val now: () -> Long = ::epochMillis,
) {
    private data class LocalState(
        val bytes: ByteArray,
        val revision: Long,
        val uploadedRevision: Long,
        val remoteUrl: String?,
    )

    private val gate = Mutex()
    private val mutableBytes = MutableStateFlow(loadState()?.bytes)
    val bytes: StateFlow<ByteArray?> = mutableBytes.asStateFlow()

    /**
     * Saves one bounded, already-compressed image and durably marks only new content pending.
     * Selecting the same bytes again leaves the revision unchanged, so it cannot enqueue a
     * duplicate upload.
     */
    suspend fun save(value: ByteArray): Boolean {
        if (value.isEmpty() || value.size > MAX_PROFILE_PHOTO_BYTES) return false
        val copy = value.copyOf()
        return gate.withLock {
            runCatching {
                val current = loadState()
                if (current?.bytes?.contentEquals(copy) == true) return@runCatching true

                val revision = (current?.revision ?: 0L) + 1L
                db.profilePhotoQueries.savePhoto(
                    copy,
                    now(),
                    revision,
                    current?.uploadedRevision ?: 0L,
                    current?.remoteUrl,
                )
                mutableBytes.value = copy
                true
            }.getOrDefault(false)
        }
    }

    /** Returns a defensive copy so networking never owns the database/cache byte array. */
    internal suspend fun pending(): PendingProfilePhoto? = gate.withLock {
        val state = loadState() ?: return@withLock null
        if (state.uploadedRevision >= state.revision) return@withLock null
        PendingProfilePhoto(state.bytes.copyOf(), state.revision)
    }

    /**
     * A successful response may acknowledge only the revision it uploaded. If the user picked
     * another image while that request was in flight, the newer revision stays pending.
     */
    internal suspend fun markUploaded(revision: Long, remoteUrl: String): Boolean = gate.withLock {
        runCatching {
            db.profilePhotoQueries.markPhotoUploaded(remoteUrl, revision)
            val current = loadState()
            current?.revision == revision && current.uploadedRevision == revision
        }.getOrDefault(false)
    }

    /**
     * Whether [remoteUrl] represents a canonical image that is not already cached. A pending
     * local choice always wins: server state can be stale while that offline-first write waits.
     */
    internal suspend fun needsRemote(remoteUrl: String): Boolean = gate.withLock {
        val current = loadState()
        when {
            current != null && current.uploadedRevision < current.revision -> false
            current?.remoteUrl == remoteUrl -> false
            else -> true
        }
    }

    /**
     * Commits downloaded canonical bytes as an already-uploaded revision. The pending check is
     * repeated under the same lock as the write, so a selection made during the download cannot
     * be overwritten by the older server image.
     */
    internal suspend fun cacheRemote(remoteUrl: String, value: ByteArray): Boolean {
        if (value.isEmpty() || value.size > MAX_PROFILE_PHOTO_BYTES) return false
        val copy = value.copyOf()
        return gate.withLock {
            runCatching {
                val current = loadState()
                if (current != null && current.uploadedRevision < current.revision) {
                    return@runCatching false
                }
                if (current?.remoteUrl == remoteUrl) return@runCatching true

                val revision = (current?.revision ?: 0L) + 1L
                db.profilePhotoQueries.savePhoto(
                    copy,
                    now(),
                    revision,
                    revision,
                    remoteUrl,
                )
                mutableBytes.value = copy
                true
            }.getOrDefault(false)
        }
    }

    suspend fun clear() = gate.withLock {
        // Account teardown is a privacy boundary. Do not report an empty in-memory avatar while
        // silently leaving another account's BLOB on disk to reappear after a process restart.
        db.profilePhotoQueries.clearPhoto()
        mutableBytes.value = null
    }

    private fun loadState(): LocalState? = runCatching {
        db.profilePhotoQueries.selectPhotoState().executeAsOneOrNull()?.let {
            LocalState(
                bytes = it.bytes.copyOf(),
                revision = it.revision,
                uploadedRevision = it.uploadedRevision,
                remoteUrl = it.remoteUrl,
            )
        }
    }.getOrNull()

    private companion object {
        const val MAX_PROFILE_PHOTO_BYTES: Int = 8 * 1024 * 1024
    }
}
