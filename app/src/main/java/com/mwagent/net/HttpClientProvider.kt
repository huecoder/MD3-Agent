package com.mwagent.net

import android.util.Log
import com.mwagent.data.settings.NetworkSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.util.concurrent.TimeUnit

class HttpClientProvider(networkSettings: Flow<NetworkSettings>, scope: CoroutineScope) {
    private val baseClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val client: StateFlow<OkHttpClient> = networkSettings
        .map(::buildClient)
        .stateIn(scope, SharingStarted.Eagerly, baseClient)

    fun current(): OkHttpClient = client.value

    private fun buildClient(settings: NetworkSettings): OkHttpClient {
        if (!settings.dohEnabled) return baseClient
        val dohUrl = DohUrl.parse(settings.dohUrl)
        if (dohUrl == null) {
            Log.w(TAG, "DoH is enabled but '${settings.dohUrl}' is not a valid URL, using system DNS")
            return baseClient
        }
        val dns = DnsOverHttps.Builder()
            .client(baseClient)
            .url(dohUrl)
            .includeIPv6(true)
            .build()
        return baseClient.newBuilder().dns(dns).build()
    }

    private companion object {
        const val TAG = "HttpClientProvider"
        const val CONNECT_TIMEOUT_SECONDS = 20L
        const val READ_TIMEOUT_SECONDS = 180L
        const val WRITE_TIMEOUT_SECONDS = 60L
    }
}
