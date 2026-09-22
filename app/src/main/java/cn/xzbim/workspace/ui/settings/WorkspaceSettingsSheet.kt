package cn.xzbim.workspace.ui.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.xzbim.workspace.ui.about.AboutContent
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel

enum class SheetView {
    SETTINGS,
    ABOUT
}

/**
 * 在 Workspace WebView 页面中弹出的设置/关于 BottomSheet 浮层（保持 WebView 100% 同一实例与页面状态）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSettingsSheet(
    viewModel: WorkspaceViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf(SheetView.SETTINGS) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        when (currentView) {
            SheetView.SETTINGS -> {
                SettingsContent(
                    viewModel = viewModel,
                    onNavigateToAbout = { currentView = SheetView.ABOUT }
                )
            }
            SheetView.ABOUT -> {
                AboutContent(
                    onBack = { currentView = SheetView.SETTINGS }
                )
            }
        }
    }
}
