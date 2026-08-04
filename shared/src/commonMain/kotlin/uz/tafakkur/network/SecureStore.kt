package uz.tafakkur.network

/**
 * Small key/value store backed by the platform's secure storage: the Keychain on iOS, and on
 * Android an AES-GCM key held in the hardware-backed AndroidKeyStore.
 *
 * Deliberately not SQLDelight. The refresh token outlives the access token by weeks, so it has
 * to sit in something the OS protects rather than in the app's own database file.
 *
 * No constructor is declared here because the Android implementation needs a `Context`; the
 * platform layer builds the instance and hands it to `initKoin`, the same way
 * `DatabaseDriverFactory` and `PrayerLocationManager` are wired.
 */
/**
 * The seam [PersistentTokenProvider] actually depends on.
 *
 * Exists so the token bookkeeping can be tested without a platform keychain: a Kotlin/Native
 * test binary is not an app bundle, so the real Keychain refuses it with `errSecNotAvailable`
 * and a test written against [SecureStore] would prove nothing.
 */
interface KeyValueStore {
    fun get(key: String): String?

    /** A null [value] deletes the entry. */
    fun put(key: String, value: String?)
}

expect class SecureStore : KeyValueStore {
    override fun get(key: String): String?
    override fun put(key: String, value: String?)
}

/**
 * [TokenProvider] that survives cold launches — the piece that was missing while the app
 * defaulted to [InMemoryTokenProvider] and therefore asked every returning user to sign in again.
 *
 * Reads are served from an in-memory mirror loaded once at construction, so the Ktor auth plugin
 * never blocks on Keychain/Keystore I/O on the request path; writes go through to the store.
 */
class PersistentTokenProvider(private val store: KeyValueStore) : TokenProvider {

    private var access: String? = store.get(KEY_ACCESS)
    private var refresh: String? = store.get(KEY_REFRESH)

    override fun accessToken(): String? = access

    override fun refreshToken(): String? = refresh

    override fun update(access: String?, refresh: String?) {
        this.access = access
        store.put(KEY_ACCESS, access)
        // A refresh response carries only a new access token; keep the existing refresh token
        // rather than wiping it, matching InMemoryTokenProvider's contract.
        if (refresh != null) {
            this.refresh = refresh
            store.put(KEY_REFRESH, refresh)
        }
    }

    override fun clear() {
        access = null
        refresh = null
        store.put(KEY_ACCESS, null)
        store.put(KEY_REFRESH, null)
    }

    private companion object {
        const val KEY_ACCESS = "auth.accessToken"
        const val KEY_REFRESH = "auth.refreshToken"
    }
}
