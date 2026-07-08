package uz.tafakkur.network

/**
 * Stores the access + refresh tokens. The in-memory default is fine for a first
 * run; back it with a secure, persistent store per platform (EncryptedSharedPrefs
 * / Keychain, or multiplatform-settings) and bind that impl in Koin instead.
 */
interface TokenProvider {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun update(access: String?, refresh: String?)
    fun clear()
    fun isLoggedIn(): Boolean = accessToken() != null
}

class InMemoryTokenProvider : TokenProvider {
    private var access: String? = null
    private var refresh: String? = null

    override fun accessToken(): String? = access
    override fun refreshToken(): String? = refresh
    override fun update(access: String?, refresh: String?) {
        this.access = access
        if (refresh != null) this.refresh = refresh
    }
    override fun clear() {
        access = null
        refresh = null
    }
}
