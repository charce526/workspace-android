package cn.xzbim.workspace.network

import android.util.Log
import cn.xzbim.workspace.network.result.NotificationCountResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.TimeZone

/**
 * NocoBase 站内消息未读数量 API 服务 (GET /api/myInAppMessages:count)
 */
class NocoBaseNotificationService {

    companion object {
        private const val TAG = "NocoBaseNotifService"
    }

    suspend fun fetchUnreadCount(
        serverUrl: String,
        token: String
    ): NotificationCountResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext NotificationCountResult.AuthRequired

        val formattedUrl = NocoBaseApiClient.normalizeServerUrl(serverUrl)
        val endpoint = "$formattedUrl/api/myInAppMessages:count"
        Log.d(TAG, "fetchUnreadCount -> Requesting unread count endpoint")

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .header("X-Authenticator", "basic")
                .header("X-Locale", "zh-CN")
                .header("X-Timezone", TimeZone.getDefault().id)
                .header("X-App", "main")
                .get()
                .build()

            NocoBaseApiClient.client.newCall(request).execute().use { response ->
                val code = response.code
                Log.d(TAG, "fetchUnreadCount -> HTTP Status: $code")

                when (code) {
                    200, 201 -> {
                        val bodyStr = response.body?.string() ?: ""
                        parseUnreadCountJson(bodyStr)
                    }
                    401, 403 -> NotificationCountResult.AuthRequired
                    404, 405 -> NotificationCountResult.Unsupported
                    429 -> {
                        val retryAfterStr = response.header("Retry-After")
                        val retrySeconds = retryAfterStr?.toLongOrNull()
                        NotificationCountResult.RateLimited(retrySeconds)
                    }
                    else -> NotificationCountResult.ServerError(code)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.d(TAG, "fetchUnreadCount -> NetworkError")
            NotificationCountResult.NetworkError
        } catch (e: Exception) {
            Log.d(TAG, "fetchUnreadCount -> Exception")
            NotificationCountResult.InvalidResponse
        }
    }

    fun parseUnreadCountJson(jsonStr: String): NotificationCountResult {
        if (jsonStr.isBlank()) return NotificationCountResult.InvalidResponse

        return try {
            val json = JSONObject(jsonStr)
            val dataObj = json.optJSONObject("data")

            val count = when {
                dataObj != null && dataObj.has("count") -> dataObj.optInt("count", -1)
                json.has("count") -> json.optInt("count", -1)
                else -> -1
            }

            if (count >= 0) {
                NotificationCountResult.Success(count)
            } else {
                NotificationCountResult.InvalidResponse
            }
        } catch (_: Exception) {
            NotificationCountResult.InvalidResponse
        }
    }
}
