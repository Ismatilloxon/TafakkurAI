package com.example.tafakkurai.profile.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException

/**
 * Downloads the canonical avatar from its public object URL.
 *
 * This deliberately owns a small, unauthenticated [HttpClient]. Reusing Tafakkur's API client
 * would attach the user's bearer token to the media host (and potentially to its redirects).
 * The response is streamed through a hard byte cap before it reaches the local cache, and both
 * the declared content type and file signature must be one of the formats accepted on upload.
 */
class PublicProfilePhotoDownloader(engine: HttpClientEngine) {
    private val client = HttpClient(engine) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 20_000
        }
    }

    suspend fun download(remoteUrl: String): ByteArray? {
        val safeUrl = validatedUrl(remoteUrl) ?: return null
        return try {
            client.prepareGet(safeUrl).execute { response ->
                val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declaredLength != null && declaredLength !in 1..MAX_PROFILE_PHOTO_BYTES.toLong()) {
                    return@execute null
                }

                val contentType = response.headers[HttpHeaders.ContentType]
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                if (contentType !in SUPPORTED_CONTENT_TYPES) return@execute null

                val channel = response.bodyAsChannel()
                val chunks = mutableListOf<ByteArray>()
                var total = 0
                while (!channel.isClosedForRead) {
                    val buffer = ByteArray(READ_CHUNK_BYTES)
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read < 0) break
                    if (read == 0) continue
                    if (total > MAX_PROFILE_PHOTO_BYTES - read) return@execute null
                    chunks += if (read == buffer.size) buffer else buffer.copyOf(read)
                    total += read
                }
                if (total == 0) return@execute null
                if (declaredLength != null && total.toLong() != declaredLength) return@execute null

                val bytes = ByteArray(total)
                var offset = 0
                chunks.forEach { chunk ->
                    chunk.copyInto(bytes, destinationOffset = offset)
                    offset += chunk.size
                }
                bytes.takeIf { hasSupportedSignature(it, contentType) }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Throwable) {
            null
        }
    }

    private fun validatedUrl(raw: String): Url? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH || trimmed.any { it.code < 0x20 || it.code == 0x7f }) {
            return null
        }
        val parsed = runCatching { Url(trimmed) }.getOrNull() ?: return null
        if (parsed.protocol != URLProtocol.HTTP && parsed.protocol != URLProtocol.HTTPS) return null
        if (parsed.host.isBlank() || parsed.user != null || parsed.password != null) return null
        return parsed
    }

    private fun hasSupportedSignature(bytes: ByteArray, contentType: String?): Boolean {
        val jpeg = bytes.size >= 3 &&
            bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        val png = bytes.size >= PNG_SIGNATURE.size && PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
        return when (contentType) {
            "image/jpeg" -> jpeg
            "image/png" -> png
            else -> false
        }
    }

    private companion object {
        const val MAX_PROFILE_PHOTO_BYTES = 8 * 1024 * 1024
        const val READ_CHUNK_BYTES = 64 * 1024
        const val MAX_URL_LENGTH = 2_048
        val SUPPORTED_CONTENT_TYPES = setOf("image/jpeg", "image/png")
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
        )
    }
}
