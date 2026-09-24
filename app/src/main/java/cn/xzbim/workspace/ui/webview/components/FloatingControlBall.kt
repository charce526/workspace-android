package cn.xzbim.workspace.ui.webview.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import cn.xzbim.workspace.R
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 客户端 52dp 悬浮控制球组件（动态绑定 statusBars, navigationBars, ime 安全边界，支持自由拖拽磁吸）
 */
@Composable
fun FloatingControlBall(
    savedIsRightSide: Boolean,
    savedVerticalRatio: Float,
    savedIdleAlpha: Float = 0.65f,
    containerColor: Color? = null,
    onReload: () -> Unit,
    onGoHome: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWorkspaces: () -> Unit,
    onPositionSaved: (isRightSide: Boolean, verticalRatio: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current

    val ballBgColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val luminance = 0.299f * ballBgColor.red + 0.587f * ballBgColor.green + 0.114f * ballBgColor.blue
    val ballIconTint = if (luminance > 0.5f) Color(0xFF1C1B1F) else Color(0xFFFFFFFF)

    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val navBarBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()

    val ballSizePx = with(density) { 52.dp.toPx() }
    val marginPx = with(density) { 12.dp.toPx() }

    val leftSnapPx = marginPx
    val touchSlopPx = viewConfig.touchSlop

    var isRightSide by remember { mutableStateOf(savedIsRightSide) }
    var lastSavedIsRightSide by remember { mutableStateOf(savedIsRightSide) }
    var lastSavedVerticalRatio by remember { mutableFloatStateOf(savedVerticalRatio) }

    var isDragging by remember { mutableStateOf(false) }

    var isInteracting by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val targetAlpha = if (isInteracting || isDragging || menuExpanded) 1.0f else savedIdleAlpha.coerceIn(0.20f, 0.85f)
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(500),
        label = "ballAlpha"
    )

    LaunchedEffect(isInteracting, menuExpanded, isDragging) {
        if (isInteracting && !menuExpanded && !isDragging) {
            delay(2000)
            isInteracting = false
        }
    }

    var totalDragDistance by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        // Use the overlay's actual bounds rather than display metrics. The Compose window
        // may be shorter/wider under edge-to-edge, split-screen, cutouts, or IME resizing.
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val safeTopPx = statusBarTopPx + with(density) { 12.dp.toPx() }
        val safeBottomInsetPx = maxOf(navBarBottomPx, imeBottomPx)
        val safeBottomLimitPx = (
            screenHeightPx - ballSizePx - safeBottomInsetPx - with(density) { 16.dp.toPx() }
        ).coerceAtLeast(safeTopPx)
        val availableSafeHeight = (safeBottomLimitPx - safeTopPx).coerceAtLeast(1f)
        val rightSnapPx = (screenWidthPx - ballSizePx - marginPx).coerceAtLeast(leftSnapPx)
        val initialRatio =
            if (savedVerticalRatio <= 0f || savedVerticalRatio == 0.68f || savedVerticalRatio == 0.85f) {
                0.96f
            } else {
                savedVerticalRatio
            }
        val initialYPx = (safeTopPx + initialRatio * availableSafeHeight)
            .coerceIn(safeTopPx, safeBottomLimitPx)
        var currentXPx by remember { mutableFloatStateOf(if (isRightSide) rightSnapPx else leftSnapPx) }
        var currentYPx by remember { mutableFloatStateOf(initialYPx) }

        LaunchedEffect(savedIsRightSide, savedVerticalRatio, screenWidthPx, screenHeightPx, safeTopPx, safeBottomLimitPx) {
            if (!isDragging) {
                lastSavedIsRightSide = savedIsRightSide
                lastSavedVerticalRatio = savedVerticalRatio
                isRightSide = savedIsRightSide
                currentXPx = if (isRightSide) rightSnapPx else leftSnapPx
                currentYPx = (safeTopPx + initialRatio.coerceIn(0f, 1f) * availableSafeHeight)
                    .coerceIn(safeTopPx, safeBottomLimitPx)
            } else {
                currentXPx = currentXPx.coerceIn(leftSnapPx, rightSnapPx)
                currentYPx = currentYPx.coerceIn(safeTopPx, safeBottomLimitPx)
            }
        }

        val renderXPx = currentXPx.coerceIn(leftSnapPx, rightSnapPx)
        val renderYPx = currentYPx.coerceIn(safeTopPx, safeBottomLimitPx)

        Box(
            modifier = Modifier
                .offset { IntOffset(renderXPx.roundToInt(), renderYPx.roundToInt()) }
                .size(52.dp)
                .alpha(animatedAlpha)
                .clip(CircleShape)
                .pointerInput(screenWidthPx, screenHeightPx, safeTopPx, safeBottomLimitPx) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            isInteracting = true
                            totalDragDistance = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragDistance += abs(dragAmount.x) + abs(dragAmount.y)
                            currentXPx = (currentXPx + dragAmount.x).coerceIn(leftSnapPx, rightSnapPx)
                            currentYPx = (currentYPx + dragAmount.y).coerceIn(safeTopPx, safeBottomLimitPx)
                        },
                        onDragCancel = {
                            isRightSide = lastSavedIsRightSide
                            currentXPx = if (lastSavedIsRightSide) rightSnapPx else leftSnapPx
                            val cancelRatio =
                                if (lastSavedVerticalRatio <= 0f || lastSavedVerticalRatio == 0.68f || lastSavedVerticalRatio == 0.85f) {
                                    0.96f
                                } else {
                                    lastSavedVerticalRatio.coerceIn(0f, 1f)
                                }
                            currentYPx = (safeTopPx + cancelRatio * availableSafeHeight)
                                .coerceIn(safeTopPx, safeBottomLimitPx)
                            isDragging = false
                        },
                        onDragEnd = {
                            isInteracting = true
                            if (totalDragDistance < touchSlopPx) {
                                isDragging = false
                                menuExpanded = !menuExpanded
                            } else {
                                val newIsRightSide = (currentXPx + ballSizePx / 2) > (screenWidthPx / 2)
                                val newRatio = ((currentYPx - safeTopPx) / availableSafeHeight).coerceIn(0.0f, 1.0f)
                                val snapX = if (newIsRightSide) rightSnapPx else leftSnapPx

                                // 先将本地坐标与方向锁定更新至最终吸附位置，再关闭拖拽状态，确保无闪烁无晃动
                                currentXPx = snapX
                                isRightSide = newIsRightSide
                                lastSavedIsRightSide = newIsRightSide
                                lastSavedVerticalRatio = newRatio
                                isDragging = false

                                onPositionSaved(newIsRightSide, newRatio)
                            }
                        }
                    )
                }
        ) {
            Surface(
                shape = CircleShape,
                color = ballBgColor,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isInteracting = true
                        menuExpanded = !menuExpanded
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = "客户端控制球",
                        tint = ballIconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 菜单显示位置计算
        if (menuExpanded) {
            val menuWidthPx = with(density) { 140.dp.toPx() }
            val menuHeightPx = with(density) { 200.dp.toPx() }
            val menuSpacingPx = with(density) { 8.dp.toPx() }

            val popupOffsetY = (renderYPx - menuHeightPx - menuSpacingPx).coerceAtLeast(safeTopPx).roundToInt()
            val popupOffsetX = if (isRightSide) {
                (renderXPx + ballSizePx - menuWidthPx).coerceAtLeast(leftSnapPx).roundToInt()
            } else {
                renderXPx.roundToInt()
            }

            Popup(
                offset = IntOffset(popupOffsetX, popupOffsetY),
                onDismissRequest = {
                    menuExpanded = false
                    isInteracting = false
                },
                properties = PopupProperties(focusable = true)
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ballBgColor
                    ),
                    modifier = Modifier.width(140.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        MenuItemRow(
                            icon = Icons.Default.Refresh,
                            label = stringResource(id = R.string.refresh),
                            contentColor = ballIconTint,
                            onClick = {
                                menuExpanded = false
                                isInteracting = false
                                onReload()
                            }
                        )
                        MenuItemRow(
                            icon = Icons.Default.Home,
                            label = stringResource(id = R.string.home),
                            contentColor = ballIconTint,
                            onClick = {
                                menuExpanded = false
                                isInteracting = false
                                onGoHome()
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = ballIconTint.copy(alpha = 0.15f)
                        )

                        MenuItemRow(
                            icon = Icons.Default.Settings,
                            label = stringResource(id = R.string.settings),
                            contentColor = ballIconTint,
                            onClick = {
                                menuExpanded = false
                                isInteracting = false
                                onNavigateToSettings()
                            }
                        )
                        MenuItemRow(
                            icon = Icons.Default.Dashboard,
                            label = stringResource(id = R.string.exit_workspace),
                            contentColor = ballIconTint,
                            onClick = {
                                menuExpanded = false
                                isInteracting = false
                                onNavigateToWorkspaces()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}
