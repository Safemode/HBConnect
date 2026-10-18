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

/** How far ahead of a scheduled maintenance date to send a reminder notification. */
enum class MaintenanceLead(val label: String, val days: Long) {
    DUE_DAY("On the due date", 0),
    ONE_DAY("1 day before", 1),
    THREE_DAYS("3 days before", 3),
    ONE_WEEK("1 week before", 7),
}

/** Order for the item list. */
enum class ItemSortOrder(val label: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
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
    /** Accent colour choice, applied over the light/dark theme. */
    val accent: AccentPreset = AccentPreset.AUTO,
    /** The user-picked accent colour (ARGB), used when [accent] is [AccentPreset.CUSTOM]. */
    val accentColor: Int = 0xFF1565C0.toInt(),
    val itemSortOrder: ItemSortOrder = ItemSortOrder.NAME_ASC,
    /** Whether the detail screen offers the on-device QR code action. Off by default. */
    val showDeviceQr: Boolean = false,
    /** When true, the dashboard search row shows the filter button before the scan button. */
    val swapScanFilter: Boolean = false,
    /**
     * When true, the app keeps a WebSocket open to the server's `/api/v1/ws/events` endpoint and
     * refreshes open screens as data changes on the server. On by default; turn off if a reverse
     * proxy in front of Homebox can't forward WebSocket upgrades.
     */
    val liveUpdates: Boolean = true,
    /** When true, a background worker checks scheduled maintenance and posts due reminders. */
    val maintenanceReminders: Boolean = false,
    /**
     * When true, a notification is posted once the offline upload queue finishes draining. Only
     * fires for changes that were saved offline (ordinary online writes never enter the queue).
     */
    val queueUploadNotifications: Boolean = false,
    /** How far ahead of the scheduled date a maintenance reminder fires. */
    val maintenanceLead: MaintenanceLead = MaintenanceLead.DUE_DAY,
    /**
     * The collection (group) to operate in, sent as the `X-Tenant` header. Blank means the server's
     * default group for the signed-in user. Cleared on sign-out and on connecting to a new server.
     */
    val activeGroupId: String = "",
    /**
     * Custom order of the reorderable "More" entries, as a list of route ids. Empty means the
     * default alphabetical order. Set when the user rearranges More; cleared by the reset action.
     */
    val moreOrder: List<String> = emptyList(),
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

    /** The active collection id for the `X-Tenant` header, or null to use the server default. */
    fun tenantHeader(): String? = activeGroupId.trim().ifBlank { null }

    /** The Authorization header value, normalized to carry a single "Bearer " prefix. */
    fun authHeader(): String? {
        val t = token.trim()
        if (t.isBlank()) return null
        return if (t.startsWith("Bearer ", ignoreCase = true)) t else "Bearer $t"
    }
}
