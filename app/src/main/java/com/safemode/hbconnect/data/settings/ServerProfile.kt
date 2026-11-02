package com.safemode.hbconnect.data.settings

/**
 * One saved server connection. The app can hold several; the active one feeds the effective
 * [ConnectionConfig] that the rest of the app reads. Connection details live here (per server);
 * app-wide preferences (theme, sort, cache, …) stay global in [SettingsRepository].
 */
data class ServerProfile(
    val id: String,
    val label: String = "",
    val scheme: String = "https",
    val host: String = "",
    val port: String = "",
    val authMode: AuthMode = AuthMode.LOGIN,
    val token: String = "",
    val tokenExpiresAt: String = "",
    val username: String = "",
    val activeGroupId: String = "",
    /**
     * Id of the Homebox user this server was last verified as (from `/users/self`), blank until
     * known. Scopes queued writes to one account, and a change means another account signed in here,
     * so the old account's offline copy is dropped.
     */
    val accountId: String = "",
) {
    /** Label for the server selector: the given label, else the host, else a placeholder. */
    val displayName: String
        get() = label.ifBlank { host.ifBlank { "New server" } }
}
