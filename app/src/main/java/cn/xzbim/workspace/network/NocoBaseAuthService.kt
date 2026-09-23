package cn.xzbim.workspace.network

import android.util.Log
import cn.xzbim.workspace.network.result.LoginResult
import cn.xzbim.workspace.network.result.SessionCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * NocoBase 2.0+ 网络认证服务 (POST /api/auth:signIn, GET /api/auth:check, X-Authenticator: basic)
 */
class NocoBaseAuthService {

    companion object {
        private const val TAG = "NocoBaseAuthService"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    suspend fun signIn(
        serverUrl: String,
        account: String,
        password: String
    ): LoginResult = withContext(Dispatchers.IO) {
        val formattedUrl = NocoBaseApiClient.normalizeServerUrl(serverUrl)
        val endpoint = "$formattedUrl/api/auth:signIn"
        Log.d(TAG, "signIn -> Endpoint: $endpoint | Account: $account")

        val jsonBody = JSONObject().apply {
            put("account", account)
            put("password", password)
        }.toString()

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("X-Authenticator", "basic")
                .header("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            NocoBaseApiClient.client.newCall(request).execute().use { response ->
                val code = response.code
                val responseBodyStr = response.body?.string() ?: ""
                Log.d(TAG, "signIn -> HTTP Status: $code")

                if (response.isSuccessful) {
                    val json = JSONObject(responseBodyStr)
                    val dataObj = json.optJSONObject("data")
                    val token = dataObj?.optString("token") ?: json.optString("token") ?: ""

                    if (token.isNotBlank()) {
                        LoginResult.Success(
                            token = token,
                            userId = dataObj?.optString("id") ?: "",
                            username = account
                        )
                    } else {
                        LoginResult.ServerError(code, "登录响应未包含有效 Token")
                    }
                } else if (code == 400 || code == 401 || code == 422) {
                    LoginResult.InvalidCredentials("用户名或密码错误")
                } else {
                    LoginResult.ServerError(code, "服务器错误 (HTTP $code)")
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "signIn -> NetworkError: ${e.message}")
            LoginResult.NetworkError("无法连接到服务器，请检查地址或网络环境")
        } catch (e: Exception) {
            Log.d(TAG, "signIn -> Exception: ${e.message}")
            LoginResult.NetworkError("登录验证时发生未知错误")
        }
    }

    suspend fun checkSession(
        serverUrl: String,
        token: String
    ): SessionCheckResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext SessionCheckResult.ExpiredOrUnauthorized

        val formattedUrl = NocoBaseApiClient.normalizeServerUrl(serverUrl)
        val endpoint = "$formattedUrl/api/auth:check"
        Log.d(TAG, "checkSession -> Endpoint: $endpoint")

        try {
            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", "Bearer $token")
                .header("X-Authenticator", "basic")
                .get()
                .build()

            NocoBaseApiClient.client.newCall(request).execute().use { response ->
                val code = response.code
                Log.d(TAG, "checkSession -> HTTP Status: $code")

                if (response.isSuccessful) {
                    SessionCheckResult.Valid()
                } else if (code == 401 || code == 403) {
                    SessionCheckResult.ExpiredOrUnauthorized
                } else {
                    SessionCheckResult.Unavailable("服务器暂时无法验证会话 (HTTP $code)")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "checkSession -> Exception: ${e.message}")
            SessionCheckResult.Unavailable("会话验证失败，原因：${e.message}")
        }
    }
}
