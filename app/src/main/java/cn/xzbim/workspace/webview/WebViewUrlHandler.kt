package cn.xzbim.workspace.webview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import cn.xzbim.workspace.network.NocoBaseApiClient
import java.net.URI
import cn.xzbim.workspace.security.WebUrlPolicy

/**
 * WebView URL 链接与 Schema 路由处理器（支持用户自定义：非同源链接在本应用内加载还是在外部浏览器中打开）
 */
class WebViewUrlHandler(
    private val context: Context,
    private val serverUrl: String,
    private val openInExternalBrowser: Boolean = false,
    private val onRedirectToReLogin: () -> Unit
) {

    private val normalizedServerUrl = NocoBaseApiClient.normalizeServerUrl(serverUrl)
    private val serverOrigin = extractOrigin(normalizedServerUrl)

    fun handleUrlLoading(url: String, isNewWindow: Boolean = false): Boolean {
        if (url.isBlank()) return false

        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase() ?: ""

        if (scheme in listOf("file", "content", "data", "javascript", "intent", "about", "blob")) return true

        when (scheme) {
            "tel" -> {
                Log.d("NocoBaseUrl", "Special scheme | Scheme = tel")
                launchIntent(Intent(Intent.ACTION_DIAL, uri))
                return true
            }
            "mailto" -> {
                Log.d("NocoBaseUrl", "Special scheme | Scheme = mailto")
                launchIntent(Intent(Intent.ACTION_SENDTO, uri))
                return true
            }
            "sms" -> {
                Log.d("NocoBaseUrl", "Special scheme | Scheme = sms")
                launchIntent(Intent(Intent.ACTION_SENDTO, uri))
                return true
            }
        }

        if (scheme != "http" && scheme != "https") {
            Log.d("NocoBaseUrl", "Special scheme | Scheme = $scheme")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (!launchIntent(intent)) {
                Toast.makeText(context, "无法打开此链接", Toast.LENGTH_SHORT).show()
            }
            return true
        }

        if (WebUrlPolicy.isSignIn(url, serverUrl)) {
            Log.d("NocoBaseUrl", "Redirected to signin: $url")
            onRedirectToReLogin()
            return true
        }

        val currentOrigin = extractOrigin(url)
        val isSameOrigin = WebUrlPolicy.sameOrigin(url, serverUrl)

        if (isSameOrigin && !(isNewWindow && openInExternalBrowser)) {
            return false
        } else {
            if (openInExternalBrowser) {
                val domain = try { URI(url).host ?: "external" } catch (_: Exception) { "external" }
                Log.d("NocoBaseUrl", "External URL opened in browser | Domain = $domain")

                val intent = Intent(Intent.ACTION_VIEW, uri)
                if (!launchIntent(intent)) {
                    Toast.makeText(context, "无法在外部浏览器中打开链接", Toast.LENGTH_SHORT).show()
                }
                return true
            } else {
                Log.d("NocoBaseUrl", "External URL allowed in-app | URL = $url")
                return false
            }
        }
    }

    private fun launchIntent(intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.d("NocoBaseUrl", "Failed to launch intent: ${e.message}")
            false
        }
    }

    private fun extractOrigin(url: String): String {
        return try {
            val javaUri = URI(url)
            val scheme = javaUri.scheme ?: "http"
            val authority = javaUri.authority ?: ""
            "$scheme://$authority".lowercase()
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun isNocoBaseSigninUrl(url: String): Boolean {
        val path = try {
            URI(url).path ?: ""
        } catch (_: Exception) {
            url
        }
        return path == "/signin" || path.endsWith("/signin") || path.contains("/auth/signin") || path.contains("/admin/signin")
    }
}
