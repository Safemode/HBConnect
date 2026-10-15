package com.safemode.hbconnect.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/**
 * Watches device connectivity in the background via [ConnectivityManager] so the app knows
 * instantly whether a network is available — no per-request probing. When there's no network,
 * requests skip the network entirely and go straight to cache (see [OfflineInterceptor]).
 *
 * Note: we only check that *a* network is connected, not that it's internet-validated, because
 * Homebox is typically a LAN host that may be reachable without internet access.
 */
class NetworkMonitor(context: Context, private val status: ServerStatus) {
    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Volatile
    private var online: Boolean = computeOnline()

    fun isOnline(): Boolean = online

    init {
        // If there's no network at all, it's definitively unreachable; otherwise leave the status
        // as CHECKING and let the background probe determine whether the server is actually up.
        if (!online) status.report(false)
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = true
                // A network appeared — unknown whether the server is reachable until we probe.
                status.reportChecking()
            }

            override fun onLost(network: Network) {
                online = false
                status.report(false)
            }

            override fun onUnavailable() {
                online = false
                status.report(false)
            }
        })
    }

    private fun computeOnline(): Boolean {
        val active = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(active) != null
    }
}
