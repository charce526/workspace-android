package cn.xzbim.workspace.network

import android.util.Log
import cn.xzbim.workspace.network.result.ConnectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException

/**
 * NocoBase 2.0+ 服务器基础网络与 HTTP/HTTPS 连通性测试服务
 */
class NocoBaseConnectionService {

    companion object {
        private const val TAG = "NocoBaseConnService"
    }

    suspend fun testConnection(serverUrl: String): ConnectionResult = withContext(Dispatchers.IO) {
        val formattedUrl = NocoBaseApiClient.normalizeServerUrl(serverUrl)
        Log.d(TAG, "Testing connection to: $formattedUrl")

        try {
            val request = Request.Builder()
                .url(formattedUrl)
                .get()
                .build()

            NocoBaseApiClient.client.newCall(request).execute().use { response ->
                val code = response.code
                Log.d(TAG, "Connection response code: $code")

                if (response.isSuccessful || code in 200..499) {
                    ConnectionResult.Success(
                        serverUrl = formattedUrl,
                        version = "2.0+"
                    )
                } else {
                    ConnectionResult.ServerError(
                        statusCode = code,
                        message = "服务器响应异常 (HTTP $code)"
                    )
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "Connection failed: ${e.message}")
            ConnectionResult.NetworkError("无法连接到服务器，请检查地址或网络环境")
        } catch (e: Exception) {
            Log.d(TAG, "Connection unexpected error: ${e.message}")
            ConnectionResult.NetworkError("建立连接时发生未知错误")
        }
    }
}
