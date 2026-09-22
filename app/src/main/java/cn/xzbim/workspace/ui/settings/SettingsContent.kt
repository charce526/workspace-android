package cn.xzbim.workspace.ui.settings

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.xzbim.workspace.R
import cn.xzbim.workspace.data.preferences.ThemeMode
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel

/**
 * 设置页面可复用核心 List/Group 组件（统一单一真实配置数据源，被 SettingsScreen 和 WorkspaceSettingsSheet 共享）
 */
@Composable
fun SettingsContent(
    viewModel: WorkspaceViewModel,
    onNavigateToAbout: () -> Unit,
    onExternalBrowserSettingChanged: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val autoEnterLastWorkspace by viewModel.autoEnterLastWorkspace.collectAsState()
    val rememberLoginState by viewModel.rememberLoginState.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val idleAlpha by viewModel.floatingBallIdleAlpha.collectAsState()
    val webViewZoomEnabled by viewModel.webViewZoomEnabled.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val openLinksInExternalBrowser by viewModel.openLinksInExternalBrowser.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearSiteDataDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 【分组 1：启动与登录】
        SectionHeader(title = stringResource(id = R.string.launch_and_login), icon = Icons.Default.Security)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("自动进入默认工作空间") },
                    supportingContent = { Text("启动应用后优先进入默认工作空间（无默认则进入上次空间）") },
                    trailingContent = {
                        Switch(
                            checked = autoEnterLastWorkspace,
                            onCheckedChange = { viewModel.setAutoEnterLastWorkspace(it) }
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("记住登录状态") },
                    supportingContent = { Text("关闭后不持久化保存加密密码，仅维持当前会话") },
                    trailingContent = {
                        Switch(
                            checked = rememberLoginState,
                            onCheckedChange = { viewModel.setRememberLoginState(it) }
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 【分组 2：外观与悬浮控制】
        SectionHeader(title = stringResource(id = R.string.appearance), icon = Icons.Default.Palette)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("外观模式") },
                    supportingContent = {
                        Text(
                            when (themeMode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色模式"
                                ThemeMode.DARK -> "深色模式"
                            }
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemeDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = { Text("悬浮球静止透明度：${(idleAlpha * 100).toInt()}%") },
                    supportingContent = {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = idleAlpha,
                                valueRange = 0.25f..0.80f,
                                onValueChange = { viewModel.saveFloatingBallIdleAlpha(it) }
                            )
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = { Text("重置悬浮球位置") },
                    supportingContent = { Text("恢复悬浮球的初始位置") },
                    trailingContent = {
                        OutlinedButton(
                            onClick = {
                                viewModel.resetFloatingBallPosition()
                                Toast.makeText(context, "已重置悬浮球位置与透明度", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("重置")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 【分组 3：浏览体验】
        SectionHeader(title = stringResource(id = R.string.browsing_experience), icon = Icons.Default.Web)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ListItem(
                    headlineContent = { Text("外部浏览器打开新窗口链接") },
                    supportingContent = { Text("开启后，非同源及新窗口链接将在外部浏览器中打开") },
                    trailingContent = {
                        Switch(
                            checked = openLinksInExternalBrowser,
                            onCheckedChange = {
                                viewModel.setOpenLinksInExternalBrowser(it)
                                onExternalBrowserSettingChanged?.invoke()
                            }
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("网页手势缩放") },
                    supportingContent = { Text("允许在 NocoBase 页面中使用双指手势缩放") },
                    trailingContent = {
                        Switch(
                            checked = webViewZoomEnabled,
                            onCheckedChange = { viewModel.setWebViewZoomEnabled(it) }
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text("保持屏幕常亮") },
                    supportingContent = { Text("仅在查看 NocoBase 页面期间防止屏幕休眠") },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOn,
                            onCheckedChange = { viewModel.setKeepScreenOn(it) }
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 【分组 4：浏览器与存储】
        SectionHeader(title = stringResource(id = R.string.browser_and_storage), icon = Icons.Default.Tune)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.clear_cache)) },
                    supportingContent = { Text("清除网页临时缓存，不会删除工作空间或登录信息") },
                    trailingContent = {
                        OutlinedButton(
                            onClick = { showClearCacheDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("清理")
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.clear_site_data), color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("清除 Cookie 和网站本地数据，可能需要重新登录") },
                    trailingContent = {
                        Button(
                            onClick = { showClearSiteDataDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("重置")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 【分组 5：关于】
        SectionHeader(title = stringResource(id = R.string.about), icon = Icons.Default.Info)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text("关于工作空间") },
                supportingContent = { Text("版本 0.2.5") },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAbout() }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // 外观模式选择弹窗
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("选择外观模式") },
            text = {
                Column {
                    ThemeOptionRow(label = "跟随系统", selected = themeMode == ThemeMode.SYSTEM) {
                        viewModel.setThemeMode(ThemeMode.SYSTEM)
                        showThemeDialog = false
                    }
                    ThemeOptionRow(label = "浅色模式", selected = themeMode == ThemeMode.LIGHT) {
                        viewModel.setThemeMode(ThemeMode.LIGHT)
                        showThemeDialog = false
                    }
                    ThemeOptionRow(label = "深色模式", selected = themeMode == ThemeMode.DARK) {
                        viewModel.setThemeMode(ThemeMode.DARK)
                        showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // 清理 WebView 缓存确认弹窗
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("清除浏览器缓存") },
            text = { Text("确定要清除浏览器缓存吗？（不会删除工作空间和登录账号数据）") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    try {
                        WebView(context).clearCache(true)
                        Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "清理缓存失败", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text(stringResource(id = R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // 清理网站数据危险弹窗
    if (showClearSiteDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearSiteDataDialog = false },
            title = { Text("清除网站数据", color = MaterialTheme.colorScheme.error) },
            text = { Text("此操作将清除网站缓存、Cookie 和 LocalStorage 本地数据，可能导致当前工作空间需要重新登录。确定继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearSiteDataDialog = false
                        try {
                            CookieManager.getInstance().removeAllCookies(null)
                            WebStorage.getInstance().deleteAllData()
                            Toast.makeText(context, "网站数据已清除", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, "清理失败", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确定重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSiteDataDialog = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
