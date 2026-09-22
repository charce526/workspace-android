package cn.xzbim.workspace.navigation

/**
 * 应用 Compose Navigation 路由定义
 */
sealed class Screen(val route: String) {
    object WorkspaceHome : Screen("workspace_home")
    object AddWorkspace : Screen("add_workspace")
    object EditWorkspace : Screen("edit_workspace/{workspaceId}") {
        fun createRoute(workspaceId: String) = "edit_workspace/$workspaceId"
    }
    object ReLogin : Screen("re_login/{workspaceId}") {
        fun createRoute(workspaceId: String) = "re_login/$workspaceId"
    }
    object NocoBaseWebView : Screen("webview/{workspaceId}") {
        fun createRoute(workspaceId: String) = "webview/$workspaceId"
    }
    object Settings : Screen("settings")
    object About : Screen("about")
}
