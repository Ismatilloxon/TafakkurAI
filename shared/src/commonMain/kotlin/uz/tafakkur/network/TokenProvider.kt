package uz.tafakkur.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable authentication state used by the app shell to close an expired session. */
enum class AuthSessionState { AUTHENTICATED, SIGNED_OUT }

/**
 * Stores the access + refresh tokens. The in-memory default is fine for a first
 * run; back it with a secure, persistent store per platform (EncryptedSharedPrefs
 * / Keychain, or multiplatform-settings) and bind that impl in Koin instead.
 */
interface TokenProvider {
    val sessionState: StateFlow<AuthSessionState>
    fun accessToken(): String?
    fun refreshToken(): String?
    fun update(access: String?, refresh: String?)
    fun clear()
    fun isLoggedIn(): Boolean = accessToken() != null
}

class InMemoryTokenProvider : TokenProvider {
    private var access: String? = null
    private var refresh: String? = null

    private val mutableSessionState = MutableStateFlow(AuthSessionState.SIGNED_OUT)
    override val sessionState: StateFlow<AuthSessionState> = mutableSessionState.asStateFlow()

    override fun accessToken(): String? = access
    override fun refreshToken(): String? = refresh
    override fun update(access: String?, refresh: String?) {
        this.access = access
        if (refresh != null) this.refresh = refresh
        mutableSessionState.value = if (access != null) {
            AuthSessionState.AUTHENTICATED
        } else {
            AuthSessionState.SIGNED_OUT
        }
    }
    override fun clear() {
        if (access == null && refresh == null) return
        access = null
        refresh = null
        mutableSessionState.value = AuthSessionState.SIGNED_OUT
    }
}
