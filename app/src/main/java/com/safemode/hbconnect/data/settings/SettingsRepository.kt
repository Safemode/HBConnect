package com.safemode.hbconnect.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hbconnect_settings")

/**
 * Persists the connection configuration. The bearer token / API key is encrypted with an Android
 * Keystore key via [KeystoreCrypto] before being written to DataStore, and decrypted on read; all
 * other fields are stored as plain text.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SCHEME = stringPreferencesKey("scheme")
        val HOST = stringPreferencesKey("host")
        val PORT = stringPreferencesKey("port")
        val AUTH_MODE = stringPreferencesKey("auth_mode")
        val TOKEN = stringPreferencesKey("token")
        val TOKEN_EXPIRES = stringPreferencesKey("token_expires")
        val USERNAME = stringPreferencesKey("username")
        val CACHE_SIZE = stringPreferencesKey("cache_size")
        val THEME = stringPreferencesKey("theme_preference")
        val SORT_ORDER = stringPreferencesKey("item_sort_order")
        val SHOW_DEVICE_QR = booleanPreferencesKey("show_device_qr")
        val SWAP_SCAN_FILTER = booleanPreferencesKey("swap_scan_filter")
        val LIVE_UPDATES = booleanPreferencesKey("live_updates")
    }

    val config: Flow<ConnectionConfig> = context.dataStore.data.map { prefs ->
        ConnectionConfig(
            scheme = prefs[Keys.SCHEME] ?: "https",
            host = prefs[Keys.HOST] ?: "",
            port = prefs[Keys.PORT] ?: "",
            authMode = runCatching { AuthMode.valueOf(prefs[Keys.AUTH_MODE] ?: "LOGIN") }
                .getOrDefault(AuthMode.LOGIN),
            token = KeystoreCrypto.decrypt(prefs[Keys.TOKEN] ?: ""),
            tokenExpiresAt = prefs[Keys.TOKEN_EXPIRES] ?: "",
            username = prefs[Keys.USERNAME] ?: "",
            cacheSize = runCatching {
                CacheSize.valueOf(prefs[Keys.CACHE_SIZE] ?: "MB_50")
            }.getOrDefault(CacheSize.MB_50),
            themePreference = runCatching {
                ThemePreference.valueOf(prefs[Keys.THEME] ?: "SYSTEM")
            }.getOrDefault(ThemePreference.SYSTEM),
            itemSortOrder = runCatching {
                ItemSortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: "NAME_ASC")
            }.getOrDefault(ItemSortOrder.NAME_ASC),
            showDeviceQr = prefs[Keys.SHOW_DEVICE_QR] ?: false,
            swapScanFilter = prefs[Keys.SWAP_SCAN_FILTER] ?: false,
            liveUpdates = prefs[Keys.LIVE_UPDATES] ?: true,
        )
    }

    suspend fun save(config: ConnectionConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCHEME] = config.scheme
            prefs[Keys.HOST] = config.host
            prefs[Keys.PORT] = config.port
            prefs[Keys.AUTH_MODE] = config.authMode.name
            prefs[Keys.TOKEN] = KeystoreCrypto.encrypt(config.token)
            prefs[Keys.TOKEN_EXPIRES] = config.tokenExpiresAt
            prefs[Keys.USERNAME] = config.username
            prefs[Keys.CACHE_SIZE] = config.cacheSize.name
            prefs[Keys.THEME] = config.themePreference.name
            prefs[Keys.SORT_ORDER] = config.itemSortOrder.name
            prefs[Keys.SHOW_DEVICE_QR] = config.showDeviceQr
            prefs[Keys.SWAP_SCAN_FILTER] = config.swapScanFilter
            prefs[Keys.LIVE_UPDATES] = config.liveUpdates
        }
    }
}
