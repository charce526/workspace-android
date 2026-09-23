package cn.xzbim.workspace.network.result

/**
 * NocoBase 站内消息未读数量获取结果密封类
 */
sealed class NotificationCountResult {
    /** 成功获取未读数量 */
    data class Success(val count: Int) : NotificationCountResult()

    /** Token 失效或权限不足 (401/403) */
    object AuthRequired : NotificationCountResult()

    /** 插件未安装或接口不支持 (404/405) */
    object Unsupported : NotificationCountResult()

    /** 触发限流 (429) */
    data class RateLimited(val retryAfterSeconds: Long? = null) : NotificationCountResult()

    /** 服务器响应异常 (5xx) */
    data class ServerError(val statusCode: Int) : NotificationCountResult()

    /** 网络不可用或请求超时 */
    object NetworkError : NotificationCountResult()

    /** 响应 JSON 结构不兼容或非合法数字 */
    object InvalidResponse : NotificationCountResult()
}
