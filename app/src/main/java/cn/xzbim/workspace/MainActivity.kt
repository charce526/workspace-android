package cn.xzbim.workspace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import cn.xzbim.workspace.navigation.NavGraph
import cn.xzbim.workspace.ui.theme.WorkspaceTheme
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel
import cn.xzbim.workspace.viewmodel.WorkspaceViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val app = applicationContext as WorkspaceApplication

        enableEdgeToEdge()
        setContent {
            val viewModel: WorkspaceViewModel = viewModel(
                factory = WorkspaceViewModelFactory(app.workspaceRepository)
            )

            // 维持系统 SplashScreen 直到本地 Room 数据库和 DataStore 完成初始化读取
            splashScreen.setKeepOnScreenCondition {
                !viewModel.isInitialized.value
            }

            val themeMode by viewModel.themeMode.collectAsState()

            WorkspaceTheme(themeMode = themeMode) {
                NavGraph(workspaceViewModel = viewModel)
            }
        }
    }
}
