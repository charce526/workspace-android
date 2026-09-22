package cn.xzbim.workspace.ui.workspace

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cn.xzbim.workspace.R
import cn.xzbim.workspace.network.result.LoginResult
import cn.xzbim.workspace.ui.theme.AppDimensions
import cn.xzbim.workspace.ui.theme.AppShapes
import cn.xzbim.workspace.ui.theme.AppSpacing
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel

/**
 * 添加工作空间页面（控制表单间距与按钮尺寸，克制 M3 样式）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWorkspaceScreen(
    viewModel: WorkspaceViewModel,
    onNavigateBack: () -> Unit,
    onConnectAndLoginSuccess: (String) -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }

    fun handleConnectAndLogin() {
        if (serverUrl.isBlank()) {
            Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
            return
        }
        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(context, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        viewModel.signInAndSaveWorkspace(
            name = name,
            serverUrl = serverUrl,
            username = username,
            password = password
        ) { result ->
            isLoading = false
            when (result) {
                is LoginResult.Success -> {
                    Toast.makeText(context, "连接并登录成功", Toast.LENGTH_SHORT).show()
                    onConnectAndLoginSuccess(result.workspaceId)
                }
                is LoginResult.InvalidCredentials -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
                is LoginResult.ServerError -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
                is LoginResult.NetworkError -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.add_workspace), style = MaterialTheme.typography.titleMedium) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.screenHorizontal, vertical = AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("工作空间名称 (选填)") },
                placeholder = { Text("例如：我的团队项目") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(AppDimensions.iconMd)) },
                singleLine = true,
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("服务器地址") },
                supportingText = { Text("示例：https://nocobase.example.com:3000") },
                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(AppDimensions.iconMd)) },
                singleLine = true,
                shape = AppShapes.small,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名 / 邮箱") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(AppDimensions.iconMd)) },
                singleLine = true,
                shape = AppShapes.small,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(AppDimensions.iconMd)) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(AppDimensions.iconMd)
                        )
                    }
                },
                singleLine = true,
                shape = AppShapes.small,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppSpacing.section))

            Button(
                onClick = { handleConnectAndLogin() },
                enabled = !isLoading,
                shape = AppShapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppDimensions.buttonHeightLg)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(text = "连接并登录", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
