package cn.xzbim.workspace.ui.webview

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cn.xzbim.workspace.WorkspaceApplication
import cn.xzbim.workspace.data.model.Workspace
import cn.xzbim.workspace.network.NocoBaseApiClient
import cn.xzbim.workspace.network.NetworkMonitor
import cn.xzbim.workspace.network.result.SessionCheckResult
import cn.xzbim.workspace.security.WebUrlPolicy
import cn.xzbim.workspace.ui.launch.WorkspaceLaunchOverlay
import cn.xzbim.workspace.ui.settings.WorkspaceSettingsSheet
import cn.xzbim.workspace.ui.webview.components.FileUploadOptionsSheet
import cn.xzbim.workspace.ui.webview.components.FloatingControlBall
import cn.xzbim.workspace.viewmodel.WorkspaceViewModel
import cn.xzbim.workspace.webview.NocoBaseSessionBridge
import cn.xzbim.workspace.webview.NocoBaseWebViewManager
import cn.xzbim.workspace.webview.WebViewDownloadHandler
import cn.xzbim.workspace.webview.WebViewFileChooser
import cn.xzbim.workspace.webview.WebViewReadyDetector
import cn.xzbim.workspace.webview.WebViewUrlHandler
import kotlinx.coroutines.delay

/**
 * NocoBase 核心沉浸式 WebView 容器（分层无遮罩视图架构，全屏 WebView 延伸渲染与安全区域悬浮球）
 */
@Composable
fun NocoBaseWebViewScreen(
    workspaceId: String,
    viewModel: WorkspaceViewModel,
    onNavigateBack: () -> Unit,
    onRedirectToReLogin: (String) -> Unit
) {
    val context = LocalContext.current
    val window = (context as? Activity)?.window
    val app = context.applicationContext as WorkspaceApplication

    var workspace by remember { mutableStateOf<Workspace?>(null) }
    var token by remember { mutableStateOf("") }
    var sessionInjected by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }

    // 主框架加载完成状态：防止子资源错误误杀全屏
    var mainFrameLoaded by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    var lastBackTime by remember { mutableStateOf(0L) }
    var redirectCount by remember { mutableIntStateOf(0) }

    var showRestoredToast by remember { mutableStateOf(false) }
    var previousNetworkState by remember { mutableStateOf(true) }

    // 主题与交互配置
    val openLinksInExternalBrowser by viewModel.openLinksInExternalBrowser.collectAsState()
    val webViewZoomEnabled by viewModel.webViewZoomEnabled.collectAsState()
    val idleAlpha by viewModel.floatingBallIdleAlpha.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val (savedIsRightSide, savedVerticalRatio) = viewModel.floatingBallPosition.collectAsState().value

    // 设置弹窗状态
    var showSettingsSheet by remember { mutableStateOf(false) }

    // 上传选项弹窗状态
    var showFileUploadOptionsSheet by remember { mutableStateOf(false) }
    var currentFileChooserParams by remember { mutableStateOf<WebChromeClient.FileChooserParams?>(null) }
    var isOptionPicked by remember { mutableStateOf(false) }

    // 屏幕常亮状态管理
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 页面就绪检测器
    val readyDetector = remember { WebViewReadyDetector() }
    var isLaunchOverlayVisible by remember { mutableStateOf(true) }

    // 网络连通性监听
    val networkMonitor = remember(context) { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnline.collectAsState()

    DisposableEffect(networkMonitor) {
        networkMonitor.startMonitoring()
        onDispose {
            networkMonitor.stopMonitoring()
        }
    }

    LaunchedEffect(isOnline) {
        if (previousNetworkState && !isOnline) {
            Log.d("NocoBaseWebView", "Network disconnected")
        } else if (!previousNetworkState && isOnline) {
            Log.d("NocoBaseWebView", "Network restored")
            showRestoredToast = true
            delay(3000)
            showRestoredToast = false
        }
        previousNetworkState = isOnline
    }

    // 文件选择与拍照
    val fileChooser = remember { WebViewFileChooser() }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        fileChooser.handleCameraResult(context, success)
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        fileChooser.handleActivityResult(context, result.resultCode, result.data)
    }

    // 文件下载
    val downloadHandler = remember(context) { WebViewDownloadHandler(context) }

    // URL 路由处理
    val urlHandler = remember(context, workspace?.serverUrl, openLinksInExternalBrowser) {
        WebViewUrlHandler(
            context = context,
            serverUrl = workspace?.serverUrl ?: "",
            openInExternalBrowser = openLinksInExternalBrowser,
            onRedirectToReLogin = { onRedirectToReLogin(workspaceId) }
        )
    }
    val currentUrlHandler = rememberUpdatedState(urlHandler)

    // 状态栏颜色同步
    var statusBarBgColor by remember { mutableStateOf(Color.Transparent) }
    var lastDetectedColor by remember { mutableStateOf<Color?>(null) }

    val webView: WebView = remember(context) {
        WebView(context).apply {
            NocoBaseWebViewManager.configureSettings(this)
            setDownloadListener(downloadHandler)

            addJavascriptInterface(object {
                @JavascriptInterface
                fun onTopColorChanged(rgbStr: String?) {
                    val mainHandler = Handler(Looper.getMainLooper())
                    mainHandler.post {
                        if (!rgbStr.isNullOrBlank() && rgbStr != "transparent") {
                            parseAndApplyColor(rgbStr) { color, _ ->
                                val prev = lastDetectedColor
                                if (prev == null || isSignificantColorChange(prev, color)) {
                                    lastDetectedColor = color
                                    statusBarBgColor = color
                                }
                            }
                        }
                    }
                }
            }, "NocoBaseColorBridge")
        }
    }

    DisposableEffect(webView) {
        onDispose {
            try {
                webView.stopLoading()
                webView.destroy()
            } catch (_: Exception) {}
        }
    }

    // Compose 设置变更即时同步到当前 WebView 实例，无需刷新页面。
    LaunchedEffect(webViewZoomEnabled) {
        webView.settings.setSupportZoom(webViewZoomEnabled)
        webView.settings.builtInZoomControls = webViewZoomEnabled
        webView.settings.displayZoomControls = false
    }

    // 启动加载配置与冷启动遮罩 5 秒超时解封控制
    LaunchedEffect(workspaceId) {
        val loadedWs = viewModel.getWorkspaceById(workspaceId)
        workspace = loadedWs
        val savedToken = app.credentialStore.getToken(workspaceId) ?: ""
        token = savedToken

        val targetUrl = loadedWs?.let { NocoBaseApiClient.normalizeServerUrl(it.serverUrl) } ?: ""

        if (targetUrl.isNotBlank() && webView.url.isNullOrBlank()) {
            Log.d("NocoBaseWebView", "loadUrl called: $targetUrl")
            webView.loadUrl(targetUrl)
        }
        sessionInjected = true

        // 启动遮罩超时保护：最多展示 5 秒，或者在主页面就绪后立即自动解封销毁
        val startTime = System.currentTimeMillis()
        while (isLaunchOverlayVisible && (System.currentTimeMillis() - startTime) < 5000L) {
            if (readyDetector.isReady.value || !webView.url.isNullOrBlank()) {
                delay(200L)
                break
            }
            delay(200L)
        }
        isLoading = false
        isLaunchOverlayVisible = false
    }

    // 会话状态异步校验
    var sessionReloadPerformed by remember { mutableStateOf(false) }
    var disposed by remember { mutableStateOf(false) }

    DisposableEffect(workspaceId) {
        onDispose {
            disposed = true
        }
    }

    LaunchedEffect(workspaceId) {
        viewModel.checkSession(workspaceId) { result ->
            if (disposed) return@checkSession

            if (result is SessionCheckResult.ExpiredOrUnauthorized) {
                Log.d("NocoBaseWebView", "Session expired, redirecting to re-login")
                Toast.makeText(context, "登录会话已过期，请重新登录", Toast.LENGTH_SHORT).show()
                onRedirectToReLogin(workspaceId)
            } else if (result is SessionCheckResult.Valid) {
                val refreshedToken = app.credentialStore.getToken(workspaceId) ?: ""
                if (refreshedToken.isNotBlank() && refreshedToken != token) {
                    token = refreshedToken
                    Log.d("NocoBaseWebView", "Refreshed token retrieved after re-login")

                    val currentUrl = webView.url
                    if (!sessionReloadPerformed && !currentUrl.isNullOrBlank() && WebUrlPolicy.sameOrigin(workspace?.serverUrl, currentUrl)) {
                        sessionReloadPerformed = true
                        NocoBaseSessionBridge.injectAndCheckStorage(webView, refreshedToken, workspace?.serverUrl.orEmpty()) { _, written ->
                            if (!disposed && written) {
                                webView.reload()
                            }
                        }
                    }
                }
            }
        }
    }

    // 原生返回键处理
    BackHandler(enabled = true) {
        if (showFileUploadOptionsSheet) {
            showFileUploadOptionsSheet = false
            fileChooser.cancelPendingCallback()
        } else if (showSettingsSheet) {
            showSettingsSheet = false
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackTime < 2000) {
                onNavigateBack()
            } else {
                lastBackTime = currentTime
                Toast.makeText(context, "再按一次返回键退出工作空间", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 图层 1：底层无遮罩 View 结构（全屏延伸 WebView 网页画布，绝无全屏白色遮罩）
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 状态栏顶部避让盒子
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(statusBarBgColor)
            )

            // WebView 主画幅（100% 占满，延伸绘制至最底端）
            AndroidView(
                factory = {
                    webView.apply {
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                if (request?.isForMainFrame != true) return false
                                val url = request.url.toString()
                                redirectCount++
                                if (!sessionInjected && WebUrlPolicy.isSignIn(url, workspace?.serverUrl)) return false
                                return currentUrlHandler.value.handleUrlLoading(url)
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                injectColorObserver(view)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                isLaunchOverlayVisible = false // 页面加载完成立即销毁启动遮罩
                                if (!url.isNullOrBlank() && url != "about:blank") {
                                    mainFrameLoaded = true
                                    isError = false
                                }
                                readyDetector.checkReadiness(view)

                                if (view != null && token.isNotBlank() && WebUrlPolicy.sameOrigin(workspace?.serverUrl, url) && !sessionInjected) {
                                    NocoBaseSessionBridge.injectAndCheckStorage(view, token, workspace?.serverUrl.orEmpty()) { _, written ->
                                        if (!disposed && written) {
                                            sessionInjected = true
                                            Log.d("NocoBaseWebView", "Token successfully injected on page finished")
                                        }
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                // 严格限制：只有主框架请求失败且页面尚未成功加载过，才判定为主框架错误
                                if (request?.isForMainFrame == true && !mainFrameLoaded) {
                                    Log.d("NocoBaseWebView", "Main frame error: ${error?.description}")
                                    isError = true
                                } else {
                                    Log.d("NocoBaseWebView", "Sub-resource error ignored: ${request?.url}")
                                }
                                isLoading = false
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                val errorUrl = error?.url ?: ""
                                val isMainFrame = WebUrlPolicy.sameOrigin(workspace?.serverUrl, errorUrl)
                                Log.d("NocoBaseWebView", "onReceivedSslError | Primary Domain Match = $isMainFrame | URL = $errorUrl")

                                // 安全合规：安全取消证书异常的请求，绝不 proceed 盲目放行
                                handler?.cancel()
                                isLoading = false

                                // 只有主框架 SSL 证书失败且尚未加载成功过，才允许提示主框架错误
                                if (isMainFrame && !mainFrameLoaded) {
                                    isError = true
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                progress = newProgress
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileChooser.prepareFileChooser(filePathCallback, fileChooserParams)
                                currentFileChooserParams = fileChooserParams
                                isOptionPicked = false
                                showFileUploadOptionsSheet = true
                                return true
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                if (resultMsg == null) return false
                                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

                                val popupWebView = WebView(context).apply {
                                    NocoBaseWebViewManager.configureSettings(this)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            req: WebResourceRequest?
                                        ): Boolean {
                                            val targetUrl = req?.url?.toString() ?: return false
                                            val handled = currentUrlHandler.value.handleUrlLoading(targetUrl)
                                            try {
                                                v?.destroy()
                                            } catch (_: Exception) {}
                                            return handled || targetUrl.isBlank() || targetUrl == "about:blank"
                                        }
                                    }
                                }

                                transport.webView = popupWebView
                                resultMsg.sendToTarget()

                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        val url = popupWebView.url
                                        if (!url.isNullOrBlank() && url != "about:blank") {
                                            currentUrlHandler.value.handleUrlLoading(url)
                                        }
                                        popupWebView.destroy()
                                    } catch (_: Exception) {}
                                }, 1000)

                                return true
                            }

                            override fun onCloseWindow(window: WebView?) {
                                super.onCloseWindow(window)
                                try {
                                    window?.destroy()
                                } catch (_: Exception) {}
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                return true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 图层 2：顶部细线性加载进度条（仅 3dp 高度，绝不遮挡网页）
        if (isLoading && progress < 100) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // 图层 3：网络断开非阻塞提示条
        if (!isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "网络连接已断开，请检查网络",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        // 图层 4：网络恢复提示条
        if (showRestoredToast && isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "网络已恢复",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 图层 5：悬浮控制球 (浮于最上层，基于 WindowInsets.statusBars, navigationBars, ime 算出 safe 边界)
        FloatingControlBall(
            savedIsRightSide = savedIsRightSide,
            savedVerticalRatio = savedVerticalRatio,
            savedIdleAlpha = idleAlpha,
            containerColor = statusBarBgColor,
            onReload = {
                isError = false
                isLoading = true
                if (webView.url.isNullOrBlank()) {
                    val targetUrl = workspace?.let { NocoBaseApiClient.normalizeServerUrl(it.serverUrl) } ?: ""
                    if (targetUrl.isNotBlank()) {
                        webView.loadUrl(targetUrl)
                    }
                } else {
                    webView.reload()
                }
            },
            onGoHome = {
                val rootUrl = workspace?.let { NocoBaseApiClient.normalizeServerUrl(it.serverUrl) } ?: ""
                if (rootUrl.isNotBlank()) {
                    isError = false
                    isLoading = true
                    webView.loadUrl(rootUrl)
                }
            },
            onNavigateToSettings = { showSettingsSheet = true },
            onNavigateToWorkspaces = { onNavigateBack() },
            onPositionSaved = { isRight, ratio ->
                viewModel.saveFloatingBallPosition(isRight, ratio)
            }
        )

        // 上传方式选择弹窗
        if (showFileUploadOptionsSheet) {
            FileUploadOptionsSheet(
                onDismiss = {
                    showFileUploadOptionsSheet = false
                    if (!isOptionPicked) {
                        fileChooser.cancelPendingCallback()
                    }
                },
                onTakePhoto = {
                    isOptionPicked = true
                    showFileUploadOptionsSheet = false
                    fileChooser.takePhoto(context, cameraLauncher)
                },
                onSelectGallery = {
                    isOptionPicked = true
                    showFileUploadOptionsSheet = false
                    val isMultiple = currentFileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                    fileChooser.openGallery(context, fileChooserLauncher, isMultiple)
                },
                onSelectFile = {
                    isOptionPicked = true
                    showFileUploadOptionsSheet = false
                    val acceptTypes = currentFileChooserParams?.acceptTypes ?: emptyArray()
                    val isMultiple = currentFileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
                    fileChooser.openFilePicker(fileChooserLauncher, currentFileChooserParams, acceptTypes, isMultiple)
                }
            )
        }

        // 设置弹窗
        if (showSettingsSheet) {
            WorkspaceSettingsSheet(
                viewModel = viewModel,
                onDismiss = { showSettingsSheet = false }
            )
        }

        // 冷启动过渡遮罩 (设置 5 秒最长超时，在 onPageFinished 时立即销毁，销毁后绝不重新出现)
        if (isLaunchOverlayVisible) {
            WorkspaceLaunchOverlay(
                visible = isLaunchOverlayVisible,
                workspaceName = workspace?.name ?: "工作空间",
                serverUrl = workspace?.serverUrl ?: ""
            )
        }

        // 主框架致命加载错误遮罩 (仅在主框架加载失败且页面未成功载入时呈现)
        if (isError && !mainFrameLoaded) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (!isOnline) "无法连接网络" else "无法加载页面",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "页面加载失败，请检查网络连接或服务器配置。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("返回工作空间")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            isError = false
                            isLoading = true
                            val targetUrl = workspace?.let { NocoBaseApiClient.normalizeServerUrl(it.serverUrl) } ?: ""
                            if (webView.url.isNullOrBlank() && targetUrl.isNotBlank()) {
                                webView.loadUrl(targetUrl)
                            } else {
                                webView.reload()
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

private fun injectColorObserver(webView: WebView?) {
    if (webView == null) return

    val js = """
        (function() {
            if (window.__nocoColorObserverInjected) return;
            window.__nocoColorObserverInjected = true;

            function parseRgb(colorStr) {
                if (!colorStr) return null;
                colorStr = colorStr.trim();

                if (colorStr.indexOf('rgb') === 0) {
                    var matches = colorStr.match(/\d+/g);
                    if (matches && matches.length >= 3) {
                        return matches.slice(0, 3).join(',');
                    }
                } else if (colorStr.indexOf('#') === 0) {
                    var hex = colorStr.substring(1);
                    if (hex.length === 3) {
                        hex = hex[0]+hex[0] + hex[1]+hex[1] + hex[2]+hex[2];
                    }
                    if (hex.length === 6) {
                        var r = parseInt(hex.substring(0, 2), 16);
                        var g = parseInt(hex.substring(2, 4), 16);
                        var b = parseInt(hex.substring(4, 6), 16);
                        if (!isNaN(r) && !isNaN(g) && !isNaN(b)) {
                            return r + ',' + g + ',' + b;
                        }
                    }
                }
                return null;
            }

            function getTopColor() {
                var el = document.querySelector('header, .ant-layout-header, [class*="header"], [class*="Header"], nav, .ant-layout-sider-logo');
                if (el) {
                    var bg = window.getComputedStyle(el).backgroundColor;
                    var parsed = parseRgb(bg);
                    if (parsed && parsed !== '0,0,0,0' && parsed !== '255,255,255,0') return parsed;
                }

                var bodyBg = window.getComputedStyle(document.body).backgroundColor;
                var parsedBody = parseRgb(bodyBg);
                if (parsedBody && parsedBody !== '0,0,0,0') return parsedBody;

                return null;
            }

            var lastRgb = null;
            function checkAndNotify() {
                var rgb = getTopColor();
                if (rgb && rgb !== lastRgb) {
                    lastRgb = rgb;
                    if (window.NocoBaseColorBridge && window.NocoBaseColorBridge.onTopColorChanged) {
                        window.NocoBaseColorBridge.onTopColorChanged(rgb);
                    }
                }
            }

            checkAndNotify();
            var observer = new MutationObserver(function() {
                checkAndNotify();
            });
            observer.observe(document.documentElement, { childList: true, subtree: true, attributes: true });

            setInterval(checkAndNotify, 1000);
        })();
    """.trimIndent()

    try {
        webView.evaluateJavascript(js, null)
    } catch (e: Exception) {
        Log.d("NocoBaseWebView", "Failed to inject color observer: ${e.message}")
    }
}

private fun parseAndApplyColor(
    rgbStr: String,
    onResult: (color: Color, isLightBg: Boolean) -> Unit
) {
    try {
        val parts = rgbStr.split(",").map { it.trim().toInt() }
        if (parts.size >= 3) {
            val r = parts[0].coerceIn(0, 255)
            val g = parts[1].coerceIn(0, 255)
            val b = parts[2].coerceIn(0, 255)

            val luminance = 0.299f * r + 0.587f * g + 0.114f * b
            val isLightBg = luminance > 128f

            onResult(Color(r, g, b), isLightBg)
        }
    } catch (_: Exception) {}
}

private fun isSignificantColorChange(c1: Color, c2: Color): Boolean {
    val dr = Math.abs(c1.red - c2.red)
    val dg = Math.abs(c1.green - c2.green)
    val db = Math.abs(c1.blue - c2.blue)
    return (dr + dg + db) > 0.05f
}
