package com.safemode.hbconnect.data.settings

/** How the app authenticates to Homebox. */
enum class AuthMode { LOGIN, API_KEY }

/** Selectable on-device HTTP cache size limits. */
enum class CacheSize(val label: String, val bytes: Long) {
    MB_25("25 MB", 25L * 1024 * 1024),
    MB_50("50 MB", 50L * 1024 * 1024),
    MB_100("100 MB", 100L * 1024 * 1024),
    MB_250("250 MB", 250L * 1024 * 1024),
    MB_500("500 MB", 500L * 1024 * 1024),
}

/** App appearance. Midnight is a dark theme with pure-black surfaces for OLED screens. */
enum class ThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    MIDNIGHT("Midnight"),
}

/** Order for the item list. */
enum class ItemSortOrder(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first"),
}

/**
 * User-supplied connection details for a self-hosted Homebox instance (v0.26+ entities API).
 *
 * [token] is the bearer credential actually sent on every request. Under [AuthMode.LOGIN] it is
 * the "Bearer …" token returned by `/v1/users/login` (with [tokenExpiresAt] marking its expiry);
 * under [AuthMode.API_KEY] it is a long-lived API key created in Homebox under the user's profile.
 * Either way it is stored encrypted (see [KeystoreCrypto]).
 */
data class ConnectionConfig(
    val scheme: String = "https",
    val host: String = "",
    val port: String = "",
    val authMode: AuthMode = AuthMode.LOGIN,
    val token: String = "",
    val tokenExpiresAt: String = "",
    /** Display name of the signed-in user (login mode), purely informational. */
    val username: String = "",
    val cacheSize: CacheSize = CacheSize.MB_50,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val itemSortOrder: ItemSortOrder = ItemSortOrder.NAME_ASC,
) {
    /** True once there is a server and a credential to attempt a connection. */
    val isConfigured: Boolean
        get() = host.isNotBlank() && token.isNotBlank()

    /** Normalized base URL with trailing slash, e.g. https://10.0.0.5:7745/ */
    fun baseUrl(): String {
        var h = host.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        val portPart = port.trim().takeIf { it.isNotBlank() }?.let { ":$it" } ?: ""
        // If the host already contains a port, don't append another.
        if (h.substringAfterLast(':', "").toIntOrNull() != null && portPart.isNotEmpty()) {
            h = h.substringBeforeLast(':')
        }
        return "$scheme://$h$portPart/"
    }

    /** The Authorization header value, normalized to carry a single "Bearer " prefix. */
    fun authHeader(): String? {
        val t = token.trim()
        if (t.isBlank()) return null
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }
}
