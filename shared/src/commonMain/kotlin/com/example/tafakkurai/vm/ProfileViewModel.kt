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
import uz.tafakkur.domain.usecase.UploadPhotoUseCase

/** Backs ProfileScreen with the real GET /me (replaces the TUSER mock). */
class ProfileViewModel(
    private val getMe: GetMeUseCase,
    private val uploadPhotoUseCase: UploadPhotoUseCase,
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
}
