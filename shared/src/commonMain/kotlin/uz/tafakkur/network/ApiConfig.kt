package uz.tafakkur.network

/**
 * Runtime networking config. [baseUrl] MUST end with a trailing slash so Ktor
 * resolves relative request paths (e.g. "v1/me") against it.
 */
data class ApiConfig(
    val baseUrl: String = "http://10.0.2.2:8080/", // Android emulator -> host localhost
)
