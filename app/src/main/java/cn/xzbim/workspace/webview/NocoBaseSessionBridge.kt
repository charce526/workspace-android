package cn.xzbim.workspace.webview

import android.util.Log
import android.webkit.WebView
import org.json.JSONObject

/**
 * NocoBase Web Client Session Bridge 工具
 */
object NocoBaseSessionBridge {

    fun injectAndCheckStorage(
        webView: WebView,
        token: String,
        onResult: (matches: Boolean, writeSuccess: Boolean) -> Unit
    ) {
        if (token.isBlank()) {
            onResult(false, false)
            return
        }

        val jsonToken = JSONObject.quote(token)
        val jsCode = """
            (function() {
                try {
                    var existing = localStorage.getItem("NOCOBASE_TOKEN");
                    var matches = (existing === $jsonToken);
                    localStorage.setItem("NOCOBASE_TOKEN", $jsonToken);
                    var reRead = localStorage.getItem("NOCOBASE_TOKEN");
                    var writeSuccess = (reRead === $jsonToken);
                    return JSON.stringify({
                        existingAvailable: !!existing,
                        matches: matches,
                        writeSuccess: writeSuccess
                    });
                } catch(e) {
                    return JSON.stringify({ error: e.toString() });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode) { rawResult ->
            if (!rawResult.isNullOrBlank() && rawResult != "null") {
                try {
                    val unquoted = if (rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                        rawResult.substring(1, rawResult.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                    } else {
                        rawResult
                    }
                    val obj = JSONObject(unquoted)
                    val matches = obj.optBoolean("matches", false)
                    val writeSuccess = obj.optBoolean("writeSuccess", false)

                    Log.d("NocoBaseBridge", "Existing token matches current token: $matches | writeSuccess: $writeSuccess")
                    onResult(matches, writeSuccess)
                } catch (e: Exception) {
                    Log.d("NocoBaseBridge", "Error parsing bridge result: ${e.message}")
                    onResult(false, false)
                }
            } else {
                onResult(false, false)
            }
        }
    }
}
