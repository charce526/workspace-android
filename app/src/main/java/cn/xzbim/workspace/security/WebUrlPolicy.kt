package cn.xzbim.workspace.security

import java.net.URI
import java.util.Locale

/** Pure URL policy shared by navigation and credential injection. Invalid URLs never match. */
object WebUrlPolicy {
    fun origin(url: String?): String? = try {
        val uri = URI(url ?: "")
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (scheme !in listOf("http", "https") || host.isNullOrBlank() || uri.rawUserInfo != null) null
        else {
            val port = if (uri.port == -1) { if (scheme == "https") 443 else 80 } else uri.port
            "$scheme://$host:$port"
        }
    } catch (_: Exception) { null }

    fun sameOrigin(first: String?, second: String?): Boolean {
        val expected = origin(first) ?: return false
        return expected == origin(second)
    }

    fun isSignIn(url: String?, serverUrl: String?): Boolean {
        if (!sameOrigin(url, serverUrl)) return false
        val path = try { URI(url).path.trimEnd('/') } catch (_: Exception) { return false }
        return path == "/signin" || path.endsWith("/signin")
    }
}
