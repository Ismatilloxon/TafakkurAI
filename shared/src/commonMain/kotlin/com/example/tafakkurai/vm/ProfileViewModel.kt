package com.example.tafakkurai.vm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import uz.tafakkur.domain.model.Gamification
import uz.tafakkur.domain.model.UserProfile
import uz.tafakkur.domain.usecase.GetMeUseCase
import uz.tafakkur.domain.usecase.SetEmailUseCase
import uz.tafakkur.domain.usecase.StartPhoneAttachUseCase
import uz.tafakkur.domain.usecase.UploadPhotoUseCase
import uz.tafakkur.domain.usecase.VerifyPhoneAttachUseCase

/** Where the "finish your profile" card is in its two-step phone flow. */
enum class ContactStep { IDLE, CODE_SENT, DONE }

/** Backs ProfileScreen with the real GET /me (replaces the TUSER mock). */
class ProfileViewModel(
    private val getMe: GetMeUseCase,
    private val uploadPhotoUseCase: UploadPhotoUseCase,
    private val setEmailUseCase: SetEmailUseCase,
    private val startPhoneAttach: StartPhoneAttachUseCase,
    private val verifyPhoneAttach: VerifyPhoneAttachUseCase,
) : ViewModel() {

    var user by mutableStateOf<UserProfile?>(null)
        private set
    var gamification by mutableStateOf<Gamification?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                val me = getMe()
                user = me.user
                gamification = me.gamification
            } catch (e: Exception) {
                error = e.message ?: "Failed to load profile"
            } finally {
                loading = false
            }
        }
    }

    fun uploadPhoto(bytes: ByteArray) {
        viewModelScope.launch {
            try {
                val url = uploadPhotoUseCase(bytes, "avatar.jpg", "image/jpeg")
                user = user?.copy(photoUrl = url)
            } catch (e: Exception) {
                error = e.message ?: "Photo upload failed"
            }
        }
    }

    // ── Completing the contact the provider could not supply ─────────────────
    // Which of the two the card asks for is decided by the server (needs_phone /
    // needs_email on the profile), never guessed from the provider name here.

    var contactBusy by mutableStateOf(false)
        private set
    var contactStep by mutableStateOf(ContactStep.IDLE)
        private set

    /** Set while the entry is rejected — a wrong code, a taken number, a bad address. */
    var contactError by mutableStateOf<String?>(null)
        private set

    private var phoneVerificationId: String? = null

    fun saveEmail(email: String) {
        val trimmed = email.trim()
        // Cheap local check so an obvious typo never costs a round trip; the server
        // validates properly either way.
        if (!trimmed.contains('@') || !trimmed.substringAfterLast('@').contains('.')) {
            contactError = "profile.contact.email_invalid"
            return
        }
        run(onSuccess = { contactStep = ContactStep.DONE }) {
            user = setEmailUseCase(trimmed)
        }
    }

    fun sendPhoneCode(phoneE164: String) = run(onSuccess = { contactStep = ContactStep.CODE_SENT }) {
        phoneVerificationId = startPhoneAttach(phoneE164.trim())
    }

    fun verifyPhoneCode(code: String) {
        val id = phoneVerificationId
        if (id == null) {
            contactStep = ContactStep.IDLE
            return
        }
        run(onSuccess = { contactStep = ContactStep.DONE }) {
            user = verifyPhoneAttach(id, code.trim())
            phoneVerificationId = null
        }
    }

    /** Back to the number field after a wrong code or a change of mind. */
    fun resetContact() {
        contactStep = ContactStep.IDLE
        contactError = null
        phoneVerificationId = null
    }

    private fun run(onSuccess: () -> Unit, block: suspend () -> Unit) {
        if (contactBusy) return
        viewModelScope.launch {
            contactBusy = true
            contactError = null
            try {
                block()
                onSuccess()
            } catch (e: Exception) {
                // NetworkExecutor has already mapped and logged it; the card only needs
                // to keep the user on the field with something to read.
                contactError = e.message ?: "profile.contact.failed"
            } finally {
                contactBusy = false
            }
        }
    }
}
