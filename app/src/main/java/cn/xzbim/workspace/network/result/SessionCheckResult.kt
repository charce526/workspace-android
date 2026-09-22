package cn.xzbim.workspace.network.result

sealed class SessionCheckResult {
    data class Valid(val userId: String = "", val username: String = "") : SessionCheckResult()
    object ExpiredOrUnauthorized : SessionCheckResult()
}
