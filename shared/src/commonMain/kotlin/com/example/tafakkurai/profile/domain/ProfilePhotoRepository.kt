package com.example.tafakkurai.profile.domain

import kotlinx.coroutines.flow.StateFlow

/** Outcome of reconciling the device-local avatar with the server profile. */
sealed interface ProfilePhotoUploadResult {
    data object NothingPending : ProfilePhotoUploadResult
    data object Deferred : ProfilePhotoUploadResult
    data class Uploaded(val remoteUrl: String) : ProfilePhotoUploadResult
}

/**
 * Single source of truth for the user's profile photo.
 *
 * The UI always reads [bytes] so a selected photo works immediately and offline. The repository
 * owns persistence and remote reconciliation; screens never call the upload API directly.
 */
interface ProfilePhotoRepository {
    val bytes: StateFlow<ByteArray?>

    /** Persists a selection before any network request. */
    suspend fun saveSelection(bytes: ByteArray): Boolean

    /** Uploads at most one pending revision; concurrent calls coalesce behind one request. */
    suspend fun retryPending(): ProfilePhotoUploadResult

    /**
     * Best-effort hydration from the canonical backend URL when no local selection is pending.
     * Implementations must fetch public media without the Tafakkur API Authorization header.
     */
    suspend fun refreshFromRemote(remoteUrl: String?)

    /** Removes all account-owned local photo state during sign-out. */
    suspend fun clear()
}
