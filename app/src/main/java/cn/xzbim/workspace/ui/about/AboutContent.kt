package cn.xzbim.workspace.ui.about

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.xzbim.workspace.R
import cn.xzbim.workspace.ui.theme.AppDimensions
import cn.xzbim.workspace.ui.theme.AppShapes
import cn.xzbim.workspace.ui.theme.AppSpacing

/**
 * 关于界面核心可复用组件（纯文本说明、开发者开源地址与带圆角官方链接卡片）
 */
@Composable
fun AboutContent(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.d("WorkspaceAbout", "Failed to open browser: ${e.message}")
            Toast.makeText(context, "无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回设置"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "关于工作空间",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        // 1. 顶部品牌图标与版本 Badge
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "App Logo",
            modifier = Modifier.size(72.dp)
        )

        Spacer(modifier = Modifier.height(AppSpacing.md))

        Text(
            text = "工作空间",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(AppSpacing.xs))

        Surface(
            shape = AppShapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = "版本 1.1.0 (Build 11)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        // 2. 应用介绍模块（无框纯文本）
        SectionTitle(title = "应用介绍")
        Spacer(modifier = Modifier.height(AppSpacing.xs))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = "工作空间是一套专为 NocoBase 打造的通用 Android 移动客户端，支持连接和使用多个 NocoBase 实例。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "本应用是独立第三方客户端，与 NocoBase 官方无直接隶属关系。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "用户名、密码及登录凭据仅保存在本地设备中，本应用不会上传至开发者或其他第三方服务器。登录时，必要的认证信息仅会发送至用户主动配置的 NocoBase 实例。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        // 3. 开发者模块（包含官网与本项目 GitHub 开源地址）
        SectionTitle(title = "开发者")
        Spacer(modifier = Modifier.height(AppSpacing.xs))
        Surface(
            shape = AppShapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("偕作BIM") },
                    supportingContent = { Text("https://www.xzbim.cn") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(AppDimensions.iconMd)
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimensions.iconSm)
                        )
                    },
                    modifier = Modifier.clickable { openBrowser("https://www.xzbim.cn") }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.lg))

                ListItem(
                    headlineContent = { Text("开源地址") },
                    supportingContent = { Text("https://github.com/charce526/workspace-android") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(AppDimensions.iconMd)
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimensions.iconSm)
                        )
                    },
                    modifier = Modifier.clickable { openBrowser("https://github.com/charce526/workspace-android") }
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        // 4. NocoBase 项目介绍模块
        SectionTitle(title = "NocoBase项目介绍")
        Spacer(modifier = Modifier.height(AppSpacing.xs))
        Text(
            text = "NocoBase 是一个开源的 “AI + 无代码” 开发平台，用于快速开发企业业务系统。NocoBase 名称及相关标识归其相应权利人所有。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(AppSpacing.sm))

        Surface(
            shape = AppShapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("NocoBase官方网站") },
                    supportingContent = { Text("https://www.nocobase.com") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(AppDimensions.iconMd)
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimensions.iconSm)
                        )
                    },
                    modifier = Modifier.clickable { openBrowser("https://www.nocobase.com") }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.lg))

                ListItem(
                    headlineContent = { Text("NocoBase Github") },
                    supportingContent = { Text("https://github.com/nocobase/nocobase") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(AppDimensions.iconMd)
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimensions.iconSm)
                        )
                    },
                    modifier = Modifier.clickable { openBrowser("https://github.com/nocobase/nocobase") }
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.section))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
