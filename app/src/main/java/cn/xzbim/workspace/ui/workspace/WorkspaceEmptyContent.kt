package cn.xzbim.workspace.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.xzbim.workspace.R
import cn.xzbim.workspace.ui.theme.AppDimensions
import cn.xzbim.workspace.ui.theme.AppShapes
import cn.xzbim.workspace.ui.theme.AppSpacing

/**
 * 工作空间无数据空状态 UI（简洁 Outline 风格，位置偏页面上半部）
 */
@Composable
fun WorkspaceEmptyContent(
    onAddWorkspaceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(AppDimensions.iconXl)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        Text(
            text = "还没有工作空间",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        Text(
            text = "添加你的 NocoBase 服务，以后可以直接从这里进入。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        Button(
            onClick = onAddWorkspaceClick,
            shape = AppShapes.small,
            modifier = Modifier.height(AppDimensions.buttonHeightMd)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(AppDimensions.iconSm)
            )
            Spacer(modifier = Modifier.padding(horizontal = AppSpacing.xs))
            Text(text = stringResource(id = R.string.add_workspace), style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}
