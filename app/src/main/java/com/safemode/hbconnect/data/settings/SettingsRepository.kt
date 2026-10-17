package com.safemode.hbconnect.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hbconnect_settings")

/**
 * Persists app settings. Server connections are stored as a list of [ServerProfile]s (serialized to
 * an encrypted JSON blob via [KeystoreCrypto], since each carries a bearer token / API key) with one
 * marked active; the active profile plus the app-wide preferences form the effective
 * [ConnectionConfig] the rest of the app reads. App-wide preferences are stored as plain keys.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        // App-wide preferences.
        val CACHE_SIZE = stringPreferencesKey("cache_size")
        val THEME = stringPreferencesKey("theme_preference")
        val ACCENT = stringPreferencesKey("accent")
        val ACCENT_COLOR = intPreferencesKey("accent_color")
        val SORT_ORDER = stringPreferencesKey("item_sort_order")
        val SHOW_DEVICE_QR = booleanPreferencesKey("show_device_qr")
        val SWAP_SCAN_FILTER = booleanPreferencesKey("swap_scan_filter")
        val LIVE_UPDATES = booleanPreferencesKey("live_updates")
        val MAINTENANCE_REMINDERS = booleanPreferencesKey("maintenance_reminders")
        val MAINTENANCE_LEAD = stringPreferencesKey("maintenance_lead")
        val MORE_ORDER = stringPreferencesKey("more_order")

        // Servers.
        val SERVERS_BLOB = stringPreferencesKey("servers_blob")
        val ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")

        // Legacy single-connection keys, read once to migrate into a profile.
        val SCHEME = stringPreferencesKey("scheme")
        val HOST = stringPreferencesKey("host")
        val PORT = stringPreferencesKey("port")
        val AUTH_MODE = stringPreferencesKey("auth_mode")
        val TOKEN = stringPreferencesKey("token")
        val TOKEN_EXPIRES = stringPreferencesKey("token_expires")
        val USERNAME = stringPreferencesKey("username")
        val ACTIVE_GROUP_ID = stringPreferencesKey("active_group_id")
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val serversAdapter = moshi.adapter<List<ServerProfile>>(
        Types.newParameterizedType(List::class.java, ServerProfile::class.java),
    )

    /** The effective connection: the active server profile merged with the app-wide preferences. */
    val config: Flow<ConnectionConfig> = context.dataStore.data.map { prefs ->
        val servers = readServers(prefs)
        val active = servers.firstOrNull { it.id == readActiveId(prefs, servers) } ?: ServerProfile(id = "")
        ConnectionConfig(
            scheme = active.scheme,
            host = active.host,
            port = active.port,
            authMode = active.authMode,
            token = active.token,
            tokenExpiresAt = active.tokenExpiresAt,
            username = active.username,
            activeGroupId = active.activeGroupId,
            cacheSize = runCatching { CacheSize.valueOf(prefs[Keys.CACHE_SIZE] ?: "MB_50") }
                .getOrDefault(CacheSize.MB_50),
            themePreference = runCatching { ThemePreference.valueOf(prefs[Keys.THEME] ?: "SYSTEM") }
                .getOrDefault(ThemePreference.SYSTEM),
            accent = runCatching { AccentPreset.valueOf(prefs[Keys.ACCENT] ?: "AUTO") }
                .getOrDefault(AccentPreset.AUTO),
            accentColor = prefs[Keys.ACCENT_COLOR] ?: 0xFF1565C0.toInt(),
            itemSortOrder = runCatching { ItemSortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: "NAME_ASC") }
                .getOrDefault(ItemSortOrder.NAME_ASC),
            showDeviceQr = prefs[Keys.SHOW_DEVICE_QR] ?: false,
            swapScanFilter = prefs[Keys.SWAP_SCAN_FILTER] ?: false,
            liveUpdates = prefs[Keys.LIVE_UPDATES] ?: true,
            maintenanceReminders = prefs[Keys.MAINTENANCE_REMINDERS] ?: false,
            maintenanceLead = runCatching { MaintenanceLead.valueOf(prefs[Keys.MAINTENANCE_LEAD] ?: "DUE_DAY") }
                .getOrDefault(MaintenanceLead.DUE_DAY),
            moreOrder = prefs[Keys.MORE_ORDER]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        )
    }

    /** All saved servers, for the server selector. */
    val servers: Flow<List<ServerProfile>> = context.dataStore.data.map { readServers(it) }

    /** The id of the active server (falls back to the first saved one). */
    val activeServerId: Flow<String> = context.dataStore.data.map { prefs ->
        readActiveId(prefs, readServers(prefs))
    }

    /**
     * Persists app-wide preferences and folds the connection fields of [config] into the active
     * server profile (creating one if there is none yet but connection details are present).
     */
    suspend fun save(config: ConnectionConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHE_SIZE] = config.cacheSize.name
            prefs[Keys.THEME] = config.themePreference.name
            prefs[Keys.ACCENT] = config.accent.name
            prefs[Keys.ACCENT_COLOR] = config.accentColor
            prefs[Keys.SORT_ORDER] = config.itemSortOrder.name
            prefs[Keys.SHOW_DEVICE_QR] = config.showDeviceQr
            prefs[Keys.SWAP_SCAN_FILTER] = config.swapScanFilter
            prefs[Keys.LIVE_UPDATES] = config.liveUpdates
            prefs[Keys.MAINTENANCE_REMINDERS] = config.maintenanceReminders
            prefs[Keys.MAINTENANCE_LEAD] = config.maintenanceLead.name
            prefs[Keys.MORE_ORDER] = config.moreOrder.joinToString(",")

            val servers = readServers(prefs).toMutableList()
            val activeId = readActiveId(prefs, servers)
            val index = servers.indexOfFirst { it.id == activeId }
            if (index >= 0) {
                val existing = servers[index]
                servers[index] = existing.copy(
                    scheme = config.scheme,
                    host = config.host,
                    port = config.port,
                    authMode = config.authMode,
                    token = config.token,
                    tokenExpiresAt = config.tokenExpiresAt,
                    username = config.username,
                    activeGroupId = config.activeGroupId,
                    label = existing.label.ifBlank { config.host },
                )
                writeServers(prefs, servers)
            } else if (config.host.isNotBlank() || config.token.isNotBlank()) {
                val profile = config.toProfile(UUID.randomUUID().toString())
                servers.add(profile)
                prefs[Keys.ACTIVE_SERVER_ID] = profile.id
                writeServers(prefs, servers)
            }
        }
    }

    /** Adds [profile] and makes it the active server. */
    suspend fun addServer(profile: ServerProfile) {
        context.dataStore.edit { prefs ->
            val servers = readServers(prefs).toMutableList()
            servers.add(profile)
            writeServers(prefs, servers)
            prefs[Keys.ACTIVE_SERVER_ID] = profile.id
        }
    }

    /** Switches which saved server is active. */
    suspend fun selectServer(id: String) {
        context.dataStore.edit { prefs -> prefs[Keys.ACTIVE_SERVER_ID] = id }
    }

    /** Removes a saved server; if it was active, the first remaining one becomes active. */
    suspend fun deleteServer(id: String) {
        context.dataStore.edit { prefs ->
            val servers = readServers(prefs).filterNot { it.id == id }
            writeServers(prefs, servers)
            if (prefs[Keys.ACTIVE_SERVER_ID] == id) {
                prefs[Keys.ACTIVE_SERVER_ID] = servers.firstOrNull()?.id ?: ""
            }
        }
    }

    private fun readServers(prefs: Preferences): List<ServerProfile> {
        val blob = prefs[Keys.SERVERS_BLOB]
        if (!blob.isNullOrBlank()) {
            val json = KeystoreCrypto.decrypt(blob)
            if (json.isNotBlank()) {
                runCatching { serversAdapter.fromJson(json) }.getOrNull()?.let { return it }
            }
        }
        // Migrate a pre-multi-server single connection into one profile.
        val host = prefs[Keys.HOST].orEmpty()
        val token = KeystoreCrypto.decrypt(prefs[Keys.TOKEN] ?: "")
        if (host.isNotBlank() || token.isNotBlank()) {
            return listOf(
                ServerProfile(
                    id = "default",
                    label = host,
                    scheme = prefs[Keys.SCHEME] ?: "https",
                    host = host,
                    port = prefs[Keys.PORT].orEmpty(),
                    authMode = runCatching { AuthMode.valueOf(prefs[Keys.AUTH_MODE] ?: "LOGIN") }
                        .getOrDefault(AuthMode.LOGIN),
                    token = token,
                    tokenExpiresAt = prefs[Keys.TOKEN_EXPIRES].orEmpty(),
                    username = prefs[Keys.USERNAME].orEmpty(),
                    activeGroupId = prefs[Keys.ACTIVE_GROUP_ID].orEmpty(),
                ),
            )
        }
        return emptyList()
    }

    private fun readActiveId(prefs: Preferences, servers: List<ServerProfile>): String {
        val saved = prefs[Keys.ACTIVE_SERVER_ID]
        if (saved != null && servers.any { it.id == saved }) return saved
        return servers.firstOrNull()?.id ?: ""
    }

    private fun writeServers(prefs: MutablePreferences, servers: List<ServerProfile>) {
        prefs[Keys.SERVERS_BLOB] = KeystoreCrypto.encrypt(serversAdapter.toJson(servers))
    }

    private fun ConnectionConfig.toProfile(id: String) = ServerProfile(
        id = id,
        label = host,
        scheme = scheme,
        host = host,
        port = port,
        authMode = authMode,
        token = token,
        tokenExpiresAt = tokenExpiresAt,
        username = username,
        activeGroupId = activeGroupId,
    )
}
