package cn.xzbim.workspace.network.authenticator

import okhttp3.Request

/**
 * NocoBase 认证器基类
 */
interface NocoBaseAuthenticator {
    fun applyAuth(requestBuilder: Request.Builder)
}

class BasicAuthenticator(private val token: String) : NocoBaseAuthenticator {
    override fun applyAuth(requestBuilder: Request.Builder) {
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
            requestBuilder.header("X-Authenticator", "basic")
        }
    }
}
