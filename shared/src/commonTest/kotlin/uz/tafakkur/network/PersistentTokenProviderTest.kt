package uz.tafakkur.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Token bookkeeping for the provider that keeps a session alive across cold launches.
 *
 * Runs against a fake [KeyValueStore] rather than the real Keychain/Keystore: a Kotlin/Native
 * test binary is not an app bundle, so the Keychain refuses it (`errSecNotAvailable`) and a test
 * written against the platform store would prove nothing. The platform round-trip is covered by
 * the UI walkthrough, which drives a properly signed, installed app.
 */
class PersistentTokenProviderTest {

    private class FakeStore(val values: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
        }
    }

    @Test
    fun starts_signed_out_when_the_store_is_empty() {
        val provider = PersistentTokenProvider(FakeStore())
        assertNull(provider.accessToken())
        assertNull(provider.refreshToken())
        assertFalse(provider.isLoggedIn())
    }

    @Test
    fun a_new_instance_reads_what_the_previous_one_wrote() {
        val store = FakeStore()
        PersistentTokenProvider(store).update("access-1", "refresh-1")

        // The cold-launch path: App() asks IsLoggedInUseCase before anything is fetched.
        val relaunched = PersistentTokenProvider(store)
        assertEquals("access-1", relaunched.accessToken())
        assertEquals("refresh-1", relaunched.refreshToken())
        assertTrue(relaunched.isLoggedIn())
    }

    @Test
    fun a_token_refresh_keeps_the_existing_refresh_token() {
        val store = FakeStore()
        val provider = PersistentTokenProvider(store)
        provider.update("access-1", "refresh-1")

        // POST /v1/auth/refresh returns only a new access token.
        provider.update("access-2", null)

        assertEquals("access-2", provider.accessToken())
        assertEquals("refresh-1", provider.refreshToken())
        assertEquals("refresh-1", PersistentTokenProvider(store).refreshToken())
    }

    @Test
    fun clear_removes_both_tokens_from_storage() {
        val store = FakeStore()
        val provider = PersistentTokenProvider(store)
        provider.update("access-1", "refresh-1")

        provider.clear()

        assertNull(provider.accessToken())
        assertNull(provider.refreshToken())
        assertFalse(provider.isLoggedIn())
        // Not just the in-memory mirror — the next launch must see nothing either.
        assertTrue(store.values.isEmpty(), "tokens were left behind in storage: ${store.values.keys}")
    }
}
