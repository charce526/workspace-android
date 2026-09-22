package cn.xzbim.workspace.webview

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.widget.Toast
import java.net.URI
import java.net.URLDecoder

/**
 * WebView 文件下载处理器（基于 DownloadManager，支持中文文件名、Cookie 传递、User-Agent 及 Blob 拦截提示）
 */
class WebViewDownloadHandler(private val context: Context) : DownloadListener {

    override fun onDownloadStart(
        url: String?,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        if (url.isNullOrBlank()) return

        val domain = try { URI(url).host ?: "unknown" } catch (_: Exception) { "unknown" }

        if (url.startsWith("blob:", ignoreCase = true)) {
            Log.d("NocoBaseDownload", "Blob download detected | URL domain = $domain")
            Toast.makeText(context, "当前文件使用网页临时下载方式，客户端暂未支持", Toast.LENGTH_LONG).show()
            return
        }

        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
            Toast.makeText(context, "不支持此下载链接", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = parseFileName(url, contentDisposition, mimeType)
            .substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\x00-\\x1f\\x7f]"), "_")
            .take(180).takeUnless { it.isBlank() || it == "." || it == ".." }
            ?: "download_${System.currentTimeMillis()}"

        Log.d(
            "NocoBaseDownload",
            "Download started | Domain = $domain | mimeType = $mimeType | filename = $fileName | contentLength = $contentLength"
        )

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("NocoBase 附件下载中...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                // Android 8/9 public Downloads requires a storage permission; use app Downloads there.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                }
                if (!mimeType.isNullOrBlank()) setMimeType(mimeType)

                val cookie = CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookie)
                }

                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(context, "已开始下载：$fileName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.d("NocoBaseDownload", "Download failed: ${e.message}")
            Toast.makeText(context, "无法启动文件下载", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        if (!contentDisposition.isNullOrBlank()) {
            try {
                val utf8Match = Regex("""filename\*=UTF-8''(.+)""", RegexOption.IGNORE_CASE).find(contentDisposition)
                if (utf8Match != null) {
                    val encodedName = utf8Match.groupValues[1].split(";")[0].trim()
                    return URLDecoder.decode(encodedName, "UTF-8")
                }

                val stdMatch = Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE).find(contentDisposition)
                if (stdMatch != null) {
                    return stdMatch.groupValues[1].trim()
                }
            } catch (e: Exception) {
                Log.d("NocoBaseDownload", "Content-Disposition parse error: ${e.message}")
            }
        }

        val guessedName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        if (!guessedName.isNullOrBlank() && guessedName != "downloadfile") {
            return guessedName
        }

        return "download_${System.currentTimeMillis()}"
    }
}
