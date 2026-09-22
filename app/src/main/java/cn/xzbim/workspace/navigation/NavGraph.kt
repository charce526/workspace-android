package cn.xzbim.workspace.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cn.xzbim.workspace.WorkspaceApplication
import cn.xzbim.workspace.ui.about.AboutScreen
import cn.xzbim.workspace.ui.login.ReLoginScreen
import cn.xzbim.workspace.ui.settings.SettingsScreen
import cn.xzbim.workspace.ui.webview.NocoBaseWebViewScreen
import cn.xzbim.workspace.ui.workspace.AddWorkspaceScreen
import cn.xzbim.workspace.ui.workspace.EditWorkspaceScreen
import cn.xzbim.workspace.ui.workspace.WorkspaceHomeScreen
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel
import cn.xzbim.workspace.viewmodel.WorkspaceViewModelFactory

import kotlinx.coroutines.flow.first

/**
 * 集中管理应用 Compose Navigation 路线（集中调度网页容器与重新登录）
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    workspaceViewModel: WorkspaceViewModel = viewModel(
        factory = WorkspaceViewModelFactory(
            (LocalContext.current.applicationContext as WorkspaceApplication).workspaceRepository
        )
    )
) {
    val context = LocalContext.current
    val app = context.applicationContext as WorkspaceApplication

    // 仅在 App 冷启动初始化时执行一次“自动直达默认工作空间”，防止在设置页切换开关时误触发登录
    LaunchedEffect(Unit) {
        val isAutoEnterEnabled = app.workspaceRepository.autoEnterLastWorkspaceFlow.first()
        if (isAutoEnterEnabled) {
            val targetWorkspace = app.workspaceRepository.getDefaultWorkspace() ?: app.workspaceRepository.getLastUsedWorkspace()
            if (targetWorkspace != null) {
                val token = app.workspaceRepository.getToken(targetWorkspace.id)
                if (!token.isNullOrBlank()) {
                    navController.navigate(Screen.NocoBaseWebView.createRoute(targetWorkspace.id)) {
                        popUpTo(Screen.WorkspaceHome.route)
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.WorkspaceHome.route
    ) {
        composable(Screen.WorkspaceHome.route) {
            WorkspaceHomeScreen(
                viewModel = workspaceViewModel,
                onNavigateToAddWorkspace = {
                    navController.navigate(Screen.AddWorkspace.route)
                },
                onNavigateToEditWorkspace = { workspaceId ->
                    navController.navigate(Screen.EditWorkspace.createRoute(workspaceId))
                },
                onNavigateToReLogin = { workspaceId ->
                    navController.navigate(Screen.ReLogin.createRoute(workspaceId))
                },
                onNavigateToWebView = { workspaceId ->
                    navController.navigate(Screen.NocoBaseWebView.createRoute(workspaceId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }

        composable(Screen.AddWorkspace.route) {
            AddWorkspaceScreen(
                viewModel = workspaceViewModel,
                onNavigateBack = { navController.popBackStack() },
                onConnectAndLoginSuccess = { _ ->
                    navController.popBackStack(Screen.WorkspaceHome.route, false)
                }
            )
        }

        composable(
            route = Screen.EditWorkspace.route,
            arguments = listOf(navArgument("workspaceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workspaceId = backStackEntry.arguments?.getString("workspaceId") ?: ""
            EditWorkspaceScreen(
                workspaceId = workspaceId,
                viewModel = workspaceViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.ReLogin.route,
            arguments = listOf(navArgument("workspaceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workspaceId = backStackEntry.arguments?.getString("workspaceId") ?: ""
            ReLoginScreen(
                workspaceId = workspaceId,
                viewModel = workspaceViewModel,
                onNavigateBack = { navController.popBackStack() },
                onReLoginSuccess = { wsId ->
                    navController.navigate(Screen.NocoBaseWebView.createRoute(wsId)) {
                        popUpTo(Screen.WorkspaceHome.route)
                    }
                }
            )
        }

        composable(
            route = Screen.NocoBaseWebView.route,
            arguments = listOf(navArgument("workspaceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val workspaceId = backStackEntry.arguments?.getString("workspaceId") ?: ""
            NocoBaseWebViewScreen(
                workspaceId = workspaceId,
                viewModel = workspaceViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRedirectToReLogin = { wsId ->
                    navController.navigate(Screen.ReLogin.createRoute(wsId)) {
                        popUpTo(Screen.WorkspaceHome.route)
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = workspaceViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAbout = {
                    navController.navigate(Screen.About.route)
                }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
