package cn.xzbim.workspace.network.result

sealed class LoginResult {
    data class Success(
        val token: String,
        val userId: String = "",
        val username: String = "",
        val workspaceId: String = ""
    ) : LoginResult()

    data class InvalidCredentials(val message: String = "用户名或密码错误") : LoginResult()
    data class ServerError(val statusCode: Int, val message: String) : LoginResult()
    data class NetworkError(val message: String) : LoginResult()
}
