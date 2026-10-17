package dev.huecoder.md3agent.system.health

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import dev.huecoder.md3agent.llm.STREAM_LOG_TAG

class NetworkWatch(context: Context, private val now: () -> Long = SystemClock::elapsedRealtime) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var registered = false
    private var state = UNKNOWN
    private var changes = 0
    private var changedAt = now()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = update(describe(capabilities))

        override fun onLost(network: Network) = update(OFFLINE)
    }

    @Synchronized
    fun start() {
        if (registered) return
        try {
            connectivity.registerDefaultNetworkCallback(callback)
            registered = true
        } catch (e: SecurityException) {
            Log.w(STREAM_LOG_TAG, "Network changes cannot be watched", e)
        }
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        connectivity.unregisterNetworkCallback(callback)
        registered = false
        state = UNKNOWN
    }

    @Synchronized
    fun summary(): String = "net=$state changes=$changes lastChangeAgoMs=${now() - changedAt}"

    @Synchronized
    private fun update(next: String) {
        if (next == state) return
        Log.i(STREAM_LOG_TAG, "network $state -> $next")
        if (state != UNKNOWN) changes += 1
        state = next
        changedAt = now()
    }

    private fun describe(capabilities: NetworkCapabilities): String {
        val transports = TRANSPORTS.filter { (type, _) -> capabilities.hasTransport(type) }.joinToString("+") { it.second }.ifEmpty { OTHER }
        val validated = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "validated" else "unvalidated"
        val metered = if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) "unmetered" else "metered"
        return "$transports,$validated,$metered"
    }

    private companion object {
        const val UNKNOWN = "unknown"
        const val OFFLINE = "offline"
        const val OTHER = "other"
        val TRANSPORTS = listOf(
            NetworkCapabilities.TRANSPORT_WIFI to "wifi",
            NetworkCapabilities.TRANSPORT_CELLULAR to "cellular",
            NetworkCapabilities.TRANSPORT_ETHERNET to "ethernet",
            NetworkCapabilities.TRANSPORT_BLUETOOTH to "bluetooth",
            NetworkCapabilities.TRANSPORT_VPN to "vpn",
        )
    }
}
