package cn.xzbim.workspace.ui.workspace

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import cn.xzbim.workspace.WorkspaceApplication
import cn.xzbim.workspace.R
import cn.xzbim.workspace.data.model.Workspace
import cn.xzbim.workspace.ui.workspace.components.DeleteConfirmDialog
import cn.xzbim.workspace.ui.workspace.components.WorkspaceCard
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel

/**
 * 工作空间首页（支持“本地优先、后台校验”秒开策略、状态栏反色自动重置与顶栏设置/关于入口）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceHomeScreen(
    viewModel: WorkspaceViewModel,
    onNavigateToAddWorkspace: () -> Unit,
    onNavigateToEditWorkspace: (String) -> Unit,
    onNavigateToReLogin: (String) -> Unit,
    onNavigateToWebView: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val window = (context as? Activity)?.window
    val isDark = isSystemInDarkTheme()
    val app = context.applicationContext as WorkspaceApplication

    // 恢复状态栏深浅模式图标对比度
    SideEffect {
        window?.let { w ->
            WindowInsetsControllerCompat(w, localView).isAppearanceLightStatusBars = !isDark
        }
    }

    val isInitialized by viewModel.isInitialized.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val isOpeningWorkspace by viewModel.isOpeningWorkspace.collectAsState()
    val openingWorkspaceId by viewModel.openingWorkspaceId.collectAsState()

    var deletingWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.my_workspaces_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onNavigateToAddWorkspace,
                            enabled = !isOpeningWorkspace,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加工作空间",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.settings)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateToSettings()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = R.string.about)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onNavigateToAbout()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (!isInitialized) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else if (workspaces.isEmpty()) {
            WorkspaceEmptyContent(
                onAddWorkspaceClick = onNavigateToAddWorkspace,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(id = R.string.my_workspaces),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = workspaces,
                        key = { it.id }
                    ) { workspace ->
                        val isCardLoading = (openingWorkspaceId == workspace.id)
                        val hasLocalToken = !app.credentialStore.getToken(workspace.id).isNullOrBlank()

                        WorkspaceCard(
                            workspace = workspace,
                            isLoading = isCardLoading,
                            enabled = !isOpeningWorkspace,
                            onClick = {
                                viewModel.setLastUsedWorkspace(workspace.id)

                                if (hasLocalToken) {
                                    // 已存在本地 Token 时直接打开网页
                                    onNavigateToWebView(workspace.id)
                                } else {
                                    // 未保存 Token 时发起网络校验
                                    viewModel.checkSessionAndOpen(
                                        workspaceId = workspace.id,
                                        onValid = {
                                            onNavigateToWebView(workspace.id)
                                        },
                                        onExpired = {
                                            Toast.makeText(context, "请先登录工作空间", Toast.LENGTH_SHORT).show()
                                            onNavigateToReLogin(workspace.id)
                                        },
                                        onUnavailable = {
                                            Toast.makeText(context, "暂时无法连接服务器，请稍后重试", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            },
                            onEditClick = { onNavigateToEditWorkspace(workspace.id) },
                            onDeleteClick = { deletingWorkspace = workspace },
                            onSetDefaultClick = {
                                viewModel.setDefaultWorkspace(workspace.id)
                                Toast.makeText(context, "已设为默认工作空间", Toast.LENGTH_SHORT).show()
                            },
                            onUnsetDefaultClick = {
                                viewModel.unsetDefaultWorkspace(workspace.id)
                                Toast.makeText(context, "已取消默认工作空间", Toast.LENGTH_SHORT).show()
                            },
                            onToggleNotificationCountClick = { enabled ->
                                viewModel.updateWorkspaceNotificationCountEnabled(workspace.id, enabled)
                                val msg = if (enabled) "已开启该工作空间的未读消息获取" else "已关闭该工作空间的未读消息获取"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    deletingWorkspace?.let { workspace ->
        DeleteConfirmDialog(
            workspaceName = workspace.name,
            onConfirm = {
                viewModel.deleteWorkspace(workspace.id)
                deletingWorkspace = null
            },
            onDismiss = { deletingWorkspace = null }
        )
    }
}
