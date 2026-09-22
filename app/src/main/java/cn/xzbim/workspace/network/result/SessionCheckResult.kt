package cn.xzbim.workspace.network.result

sealed class SessionCheckResult {
    data class Valid(val userId: String = "", val username: String = "") : SessionCheckResult()
    object ExpiredOrUnauthorized : SessionCheckResult()
    data class Unavailable(val message: String = "暂时无法验证登录状态，请检查网络后重试") : SessionCheckResult()
}
