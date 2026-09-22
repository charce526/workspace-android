package cn.xzbim.workspace.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cn.xzbim.workspace.R
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel

/**
 * 客户端设置 Screen（全屏设置页，共享 SettingsContent 核心配置列表组件）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WorkspaceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        SettingsContent(
            viewModel = viewModel,
            onNavigateToAbout = onNavigateToAbout,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
