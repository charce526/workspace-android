package cn.xzbim.workspace.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * 在 Workspace WebView 页面中弹出的设置/关于 BottomSheet 浮层（保持 WebView 100% 同一实例与页面状态，不重新加载页面）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceSettingsSheet(
    viewModel: WorkspaceViewModel,
    onDismiss: () -> Unit,
    onReloadWebView: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentView by remember { mutableStateOf(SheetView.SETTINGS) }
    var showReloadConfirmDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        when (currentView) {
            SheetView.SETTINGS -> {
                SettingsContent(
                    viewModel = viewModel,
                    onNavigateToAbout = { currentView = SheetView.ABOUT },
                    onExternalBrowserSettingChanged = {
                        showReloadConfirmDialog = true
                    }
                )
            }
            SheetView.ABOUT -> {
                AboutContent(
                    onBack = { currentView = SheetView.SETTINGS }
                )
            }
        }
    }

    if (showReloadConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReloadConfirmDialog = false },
            title = { Text("设置已更新") },
            text = { Text("“外部浏览器打开新窗口链接”设置更改需要刷新当前网页后完全生效。是否立即刷新网页？") },
            confirmButton = {
                TextButton(onClick = {
                    showReloadConfirmDialog = false
                    onDismiss()
                    onReloadWebView()
                }) {
                    Text("立即刷新")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReloadConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
