package cn.xzbim.workspace.network

import okhttp3.OkHttpClient
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * NocoBase OkHttp 客户端与 URL 标准化工具
 */
object NocoBaseApiClient {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Never forward passwords through HTTP redirects to another endpoint.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    fun normalizeServerUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "http://$trimmed"
        } else {
            trimmed
        }

        return try {
            val uri = URI(withScheme)
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: return withScheme
            val port = if (uri.port != -1) ":${uri.port}" else ""
            val path = uri.path?.trimEnd('/') ?: ""
            "$scheme://$host$port$path"
        } catch (_: Exception) {
            withScheme.trimEnd('/')
        }
    }
}
