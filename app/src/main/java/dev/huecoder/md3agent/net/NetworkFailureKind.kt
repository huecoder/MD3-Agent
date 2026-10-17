package dev.huecoder.md3agent.net

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

enum class NetworkFailureKind(val isTransient: Boolean) {
    DROPPED(isTransient = true),
    UNREACHABLE(isTransient = true),
    STALLED(isTransient = false),
    INSECURE(isTransient = false);

    companion object {
        fun of(cause: Throwable, responseStarted: Boolean = false): NetworkFailureKind = when {
            cause is SocketTimeoutException -> if (responseStarted) STALLED else UNREACHABLE
            cause is UnknownHostException || cause is ConnectException || cause is NoRouteToHostException -> UNREACHABLE
            cause is SSLPeerUnverifiedException -> INSECURE
            cause is SSLHandshakeException && cause.cause is CertificateException -> INSECURE
            else -> DROPPED
        }
    }
}
