package cn.xzbim.workspace.webview

import android.util.Log
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * NocoBase Web App 可用状态检测器（通过 DOM 特征节点、title 与 React #root 节点长度检测页面可用就绪状态）
 */
class WebViewReadyDetector {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun reset() {
        _isReady.value = false
    }

    fun checkReadiness(webView: WebView?, onReady: (() -> Unit)? = null) {
        if (webView == null || _isReady.value) return

        val jsCode = """
            (function() {
                try {
                    var title = document.title || '';
                    var root = document.querySelector('#root') || document.querySelector('#app');
                    var rootLen = root ? root.innerHTML.length : 0;
                    var bodyLen = document.body ? document.body.innerHTML.length : 0;
                    var isReady = (title !== 'Loading...' && title.trim() !== '' && rootLen > 300) || (bodyLen > 1000);
                    return JSON.stringify({
                        isReady: isReady,
                        title: title,
                        rootLength: rootLen,
                        bodyLength: bodyLen
                    });
                } catch(e) {
                    return JSON.stringify({ isReady: false, error: e.toString() });
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
                    val ready = obj.optBoolean("isReady", false)
                    val title = obj.optString("title", "")
                    val rootLen = obj.optInt("rootLength", 0)

                    if (ready) {
                        Log.d("NocoBaseReady", "Web App Ready detected! Title: '$title' | rootLength: $rootLen")
                        _isReady.value = true
                        onReady?.invoke()
                    }
                } catch (e: Exception) {
                    Log.d("NocoBaseReady", "Error parsing readiness result: ${e.message}")
                }
            }
        }
    }
}
