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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = applicationContext as WorkspaceApplication
            val viewModel: WorkspaceViewModel = viewModel(
                factory = WorkspaceViewModelFactory(app.workspaceRepository)
            )
            val themeMode by viewModel.themeMode.collectAsState()

            WorkspaceTheme(themeMode = themeMode) {
                NavGraph(workspaceViewModel = viewModel)
            }
        }
    }
}
