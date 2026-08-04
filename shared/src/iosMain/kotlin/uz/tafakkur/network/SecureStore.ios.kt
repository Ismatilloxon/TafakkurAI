package uz.tafakkur.network

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Keychain-backed [SecureStore].
 *
 * Items use `kSecAttrAccessibleAfterFirstUnlock` so a token refresh right after a reboot can
 * still read the value, while the data stays encrypted until the user unlocks once.
 *
 * Strings cross the boundary as UTF-8 bytes rather than via `NSString`: in Kotlin/Native a
 * Kotlin `String` and an `NSString` are distinct types and casting between them compiles to a
 * cast that never succeeds, which silently turned every read into `null`.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureStore : KeyValueStore {

    actual override fun get(key: String): String? = memScoped {
        val query = newQuery(key)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val out = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, out.ptr)
        CFRelease(query)
        if (status != STATUS_SUCCESS) {
            // errSecItemNotFound is the normal "nothing stored yet" answer; anything else means
            // the Keychain is refusing us and the session will silently fail to persist.
            if (status != STATUS_ITEM_NOT_FOUND) warn("read '$key'", status)
            return@memScoped null
        }

        // CFBridgingRelease takes ownership of the +1 reference SecItemCopyMatching returned.
        val data = CFBridgingRelease(out.value) as? NSData ?: return@memScoped null
        data.toUtf8String()
    }

    actual override fun put(key: String, value: String?) {
        // Delete-then-add rather than SecItemUpdate: one code path for insert and replace, and
        // it is also how a null value deletes the entry.
        val existing = newQuery(key)
        SecItemDelete(existing)
        CFRelease(existing)
        if (value == null) return

        memScoped {
            val add = newQuery(key)
            val payload = CFBridgingRetain(value.toNSData())
            CFDictionaryAddValue(add, kSecValueData, payload)
            CFDictionaryAddValue(add, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            val status = SecItemAdd(add, null)
            payload?.let { CFRelease(it) }
            CFRelease(add)
            if (status != STATUS_SUCCESS) warn("write '$key'", status)
        }
    }

    /** Class + service + account, the three attributes that identify one of our entries. */
    private fun newQuery(key: String): CFMutableDictionaryRef {
        // Capacity 0 = unbounded. A fixed capacity caps how many attributes the dictionary will
        // hold, turning an over-full query into one that matches nothing rather than an error.
        val query = CFDictionaryCreateMutable(
            null,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )!!
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        // CFBridgingRetain bridges the Kotlin string to an NSString itself — no cast needed, and
        // the dictionary retains what it stores, so drop our own +1 straight after adding.
        val service: COpaquePointer? = CFBridgingRetain(SERVICE)
        val account: COpaquePointer? = CFBridgingRetain(key)
        CFDictionaryAddValue(query, kSecAttrService, service)
        CFDictionaryAddValue(query, kSecAttrAccount, account)
        service?.let { CFRelease(it) }
        account?.let { CFRelease(it) }
        return query
    }

    private fun warn(what: String, status: Int) {
        println("SecureStore: Keychain failed to $what (OSStatus $status)")
    }

    private companion object {
        const val SERVICE = "uz.tafakkur.auth"

        /** The Security cinterop bindings do not export these as constants. */
        const val STATUS_SUCCESS = 0
        const val STATUS_ITEM_NOT_FOUND = -25300
    }
}

/** UTF-8 bytes as an [NSData]; `dataWithBytes:length:` copies, so the scratch buffer can go. */
@OptIn(ExperimentalForeignApi::class)
private fun String.toNSData(): NSData = memScoped {
    val bytes = encodeToByteArray()
    NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toUtf8String(): String? {
    val size = length.toInt()
    if (size == 0) return ""
    val start = bytes?.reinterpret<ByteVar>() ?: return null
    return ByteArray(size) { start[it] }.decodeToString()
}
