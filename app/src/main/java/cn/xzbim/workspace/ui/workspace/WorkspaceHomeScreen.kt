package cn.xzbim.workspace.ui.workspace

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsControllerCompat
import cn.xzbim.workspace.WorkspaceApplication
import cn.xzbim.workspace.R
import cn.xzbim.workspace.data.model.Workspace
import cn.xzbim.workspace.repository.ManualRefreshResult
import cn.xzbim.workspace.ui.workspace.components.DeleteConfirmDialog
import cn.xzbim.workspace.ui.workspace.components.WorkspaceCard
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel
import kotlin.math.roundToInt

/**
 * 工作空间首页（固定占位 + 独立拖动浮层架构：零漂移、零抖动、跟随手指拖拽排序与未读数量 Badge 标签）
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
    val density = LocalDensity.current
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
    val isRefreshingNotifications by viewModel.isRefreshingNotifications.collectAsState()
    val globalNotificationEnabled by viewModel.globalNotificationCountEnabled.collectAsState()
    val notificationStates by viewModel.notificationStates.collectAsState()

    val lazyListState = rememberLazyListState()

    var localWorkspaces by remember(workspaces) { mutableStateOf(workspaces) }

    // 独立拖动浮层架构的核心状态
    var draggedWorkspaceId by remember { mutableStateOf<String?>(null) }
    var draggedCardInitialTopY by remember { mutableFloatStateOf(0f) }
    var draggedCardTotalY by remember { mutableFloatStateOf(0f) }
    var draggedCardHeightPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(workspaces) {
        if (draggedWorkspaceId == null) {
            localWorkspaces = workspaces
        }
    }

    var deletingWorkspace by remember { mutableStateOf<Workspace?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    // 从网页退出返回工作空间首页时，自动触发后台静默刷新（不弹提示条）
    LaunchedEffect(Unit) {
        viewModel.syncNotificationCountsOnStartOrForeground()
    }

    fun handleManualOrPullRefresh() {
        Toast.makeText(context, "正在获取未读站内消息数量…", Toast.LENGTH_SHORT).show()
        viewModel.refreshNotificationCountsManually { result ->
            val msg = when (result) {
                ManualRefreshResult.ALL_SUCCESS -> "未读消息数量已更新"
                ManualRefreshResult.PARTIAL_FAILED -> "部分工作空间刷新失败"
                ManualRefreshResult.ALL_FAILED -> "未能获取未读消息数量，请稍后重试"
                ManualRefreshResult.GLOBAL_DISABLED -> "请先在设置中开启“获取未读站内消息数量”"
                ManualRefreshResult.NO_ELIGIBLE_WORKSPACES -> "没有已开启未读消息获取的工作空间"
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

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

                        Spacer(modifier = Modifier.width(2.dp))

                        IconButton(
                            onClick = { handleManualOrPullRefresh() },
                            enabled = !isRefreshingNotifications && !isOpeningWorkspace,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isRefreshingNotifications) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "刷新未读数量",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(2.dp))

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
            PullToRefreshBox(
                isRefreshing = isRefreshingNotifications,
                onRefresh = { handleManualOrPullRefresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
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
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(
                                items = localWorkspaces,
                                key = { workspace -> workspace.id }
                            ) { workspace ->
                                val isCardLoading = (openingWorkspaceId == workspace.id)
                                val hasLocalToken = !app.credentialStore.getToken(workspace.id).isNullOrBlank()

                                val notifState = notificationStates[workspace.id]
                                val count = notifState?.unreadCount ?: 0
                                val shouldShowBadge = globalNotificationEnabled &&
                                        workspace.notificationCountEnabled &&
                                        (notifState != null && notifState.lastSuccessAt > 0L) &&
                                        count > 0

                                val badgeText = if (shouldShowBadge) {
                                    if (count > 99) "99+" else count.toString()
                                } else null

                                val isBeingDragged = (workspace.id == draggedWorkspaceId)

                                WorkspaceCard(
                                    workspace = workspace,
                                    isLoading = isCardLoading,
                                    enabled = !isOpeningWorkspace && draggedWorkspaceId == null,
                                    badgeText = badgeText,
                                    isDragging = false,
                                    modifier = Modifier
                                        .animateItem()
                                        .then(
                                            if (isBeingDragged) Modifier.alpha(0.15f) else Modifier
                                        )
                                        .pointerInput(workspace.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                                    val itemInfo = visibleItems.firstOrNull { it.key == workspace.id }
                                                    if (itemInfo != null) {
                                                        draggedWorkspaceId = workspace.id
                                                        draggedCardInitialTopY = itemInfo.offset.toFloat()
                                                        draggedCardTotalY = 0f
                                                        draggedCardHeightPx = itemInfo.size.toFloat()
                                                    }
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val currentId = draggedWorkspaceId ?: return@detectDragGesturesAfterLongPress
                                                    draggedCardTotalY += dragAmount.y

                                                    val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
                                                    val currentIndex = localWorkspaces.indexOfFirst { it.id == currentId }
                                                    if (currentIndex == -1) return@detectDragGesturesAfterLongPress

                                                    // 使用固定浮层的真实几何中心点与列表中相邻项的物理中心点做交叉比较
                                                    val floatingCenterY = draggedCardInitialTopY + draggedCardTotalY + draggedCardHeightPx / 2f
                                                    val hysteresisPx = with(density) { 8.dp.toPx() }

                                                    if (dragAmount.y > 0 && currentIndex < localWorkspaces.size - 1) {
                                                        val nextWorkspace = localWorkspaces[currentIndex + 1]
                                                        val nextInfo = visibleItems.firstOrNull { it.key == nextWorkspace.id }
                                                        if (nextInfo != null) {
                                                            val nextCenterY = nextInfo.offset + nextInfo.size / 2f
                                                            if (floatingCenterY > nextCenterY + hysteresisPx) {
                                                                val mutable = localWorkspaces.toMutableList()
                                                                val item = mutable.removeAt(currentIndex)
                                                                mutable.add(currentIndex + 1, item)
                                                                localWorkspaces = mutable
                                                            }
                                                        }
                                                    } else if (dragAmount.y < 0 && currentIndex > 0) {
                                                        val prevWorkspace = localWorkspaces[currentIndex - 1]
                                                        val prevInfo = visibleItems.firstOrNull { it.key == prevWorkspace.id }
                                                        if (prevInfo != null) {
                                                            val prevCenterY = prevInfo.offset + prevInfo.size / 2f
                                                            if (floatingCenterY < prevCenterY - hysteresisPx) {
                                                                val mutable = localWorkspaces.toMutableList()
                                                                val item = mutable.removeAt(currentIndex)
                                                                mutable.add(currentIndex - 1, item)
                                                                localWorkspaces = mutable
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    if (draggedWorkspaceId != null) {
                                                        draggedWorkspaceId = null
                                                        draggedCardTotalY = 0f
                                                        viewModel.reorderWorkspaces(localWorkspaces.map { it.id })
                                                        Toast.makeText(context, "工作空间排序已更新", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                onDragCancel = {
                                                    draggedWorkspaceId = null
                                                    draggedCardTotalY = 0f
                                                    localWorkspaces = workspaces
                                                }
                                            )
                                        },
                                    onClick = {
                                        viewModel.setLastUsedWorkspace(workspace.id)

                                        if (hasLocalToken) {
                                            onNavigateToWebView(workspace.id)
                                        } else {
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

                    // 独立拖动浮层：完全由 (draggedCardInitialTopY + draggedCardTotalY) 绝对控制，零偏移漂移，绝对跟手
                    val draggedWorkspace = localWorkspaces.firstOrNull { it.id == draggedWorkspaceId }
                    if (draggedWorkspace != null) {
                        val notifState = notificationStates[draggedWorkspace.id]
                        val count = notifState?.unreadCount ?: 0
                        val shouldShowBadge = globalNotificationEnabled &&
                                draggedWorkspace.notificationCountEnabled &&
                                (notifState != null && notifState.lastSuccessAt > 0L) &&
                                count > 0

                        val badgeText = if (shouldShowBadge) {
                            if (count > 99) "99+" else count.toString()
                        } else null

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset {
                                    IntOffset(
                                        x = 0,
                                        y = (draggedCardInitialTopY + draggedCardTotalY).roundToInt()
                                    )
                                }
                                .zIndex(100f)
                                .graphicsLayer {
                                    scaleX = 1.03f
                                    scaleY = 1.03f
                                    shadowElevation = 8.dp.toPx()
                                }
                        ) {
                            WorkspaceCard(
                                workspace = draggedWorkspace,
                                isLoading = false,
                                enabled = false,
                                badgeText = badgeText,
                                isDragging = true,
                                onClick = {},
                                onEditClick = {},
                                onDeleteClick = {},
                                onSetDefaultClick = {},
                                onUnsetDefaultClick = {}
                            )
                        }
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
