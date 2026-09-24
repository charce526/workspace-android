package cn.xzbim.workspace.ui.webview

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import cn.xzbim.workspace.WorkspaceApplication
import cn.xzbim.workspace.R
import cn.xzbim.workspace.security.WebUrlPolicy
import cn.xzbim.workspace.data.model.Workspace
import cn.xzbim.workspace.network.NetworkMonitor
import cn.xzbim.workspace.network.NocoBaseApiClient
import cn.xzbim.workspace.network.result.SessionCheckResult
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
import org.json.JSONObject
import java.net.URI
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * 核心 WebView 页面容器（提供页面渲染、状态栏同步、悬浮控制与文件处理功能）
 */
@Composable
fun NocoBaseWebViewScreen(
    workspaceId: String,
    viewModel: WorkspaceViewModel,
    onNavigateBack: () -> Unit,
    onRedirectToReLogin: (String) -> Unit
) {
    val context = LocalContext.current
    val localView = LocalView.current
    val window = (context as? Activity)?.window
    val app = context.applicationContext as WorkspaceApplication

    val defaultSurfaceColor = MaterialTheme.colorScheme.surface
    var statusBarBgColor by remember { mutableStateOf(defaultSurfaceColor) }

    var workspace by remember { mutableStateOf<Workspace?>(null) }
    var token by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var sessionInjected by remember(workspaceId) { mutableStateOf(false) }
    var sessionReloadPerformed by remember(workspaceId) { mutableStateOf(false) }
    var redirectCount by remember { mutableIntStateOf(0) }
    var lastDetectedColor by remember { mutableStateOf<Color?>(null) }

    // 设置弹窗状态
    var showSettingsSheet by remember { mutableStateOf(false) }

    // 上传选项弹窗状态
    var showFileUploadOptionsSheet by remember { mutableStateOf(false) }
    var currentFileChooserParams by remember { mutableStateOf<WebChromeClient.FileChooserParams?>(null) }

    val webViewZoomEnabled by viewModel.webViewZoomEnabled.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()

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

    var wasOffline by remember { mutableStateOf(false) }
    var showRestoredToast by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            wasOffline = true
        } else if (wasOffline) {
            showRestoredToast = true
            delay(2500)
            showRestoredToast = false
            wasOffline = false
        }
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

    val openLinksInExternalBrowser by viewModel.openLinksInExternalBrowser.collectAsState()

    // URL 路由处理
    val urlHandler = remember(context, workspace?.serverUrl, openLinksInExternalBrowser) {
        WebViewUrlHandler(
            context = context,
            serverUrl = workspace?.serverUrl ?: "",
            openInExternalBrowser = openLinksInExternalBrowser,
            onRedirectToReLogin = { onRedirectToReLogin(workspaceId) }
        )
    }
    // WebViewClient 只创建一次，因此通过 updated state 始终读取最新的工作空间地址和设置。
    val currentUrlHandler = rememberUpdatedState(urlHandler)

    val (savedIsRightSide, savedVerticalRatio) = viewModel.floatingBallPosition.collectAsState().value
    val idleAlpha by viewModel.floatingBallIdleAlpha.collectAsState()

    // 初始化透明状态栏
    LaunchedEffect(Unit) {
        window?.let { w ->
            WindowCompat.setDecorFitsSystemWindows(w, false)
            w.statusBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    // 保持 WebView 实例持久化
    val callbackHandler = remember { Handler(Looper.getMainLooper()) }
    val popupWebViews = remember { mutableSetOf<WebView>() }
    var disposed by remember { mutableStateOf(false) }
    val webView = remember(context, workspaceId) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            NocoBaseWebViewManager.configureSettings(this)

            settings.setSupportZoom(webViewZoomEnabled)
            settings.builtInZoomControls = webViewZoomEnabled
            settings.displayZoomControls = false

            setDownloadListener(downloadHandler)

            addJavascriptInterface(object {
                @JavascriptInterface
                fun onTopColorChanged(rgbStr: String?) {
                    callbackHandler.post {
                        if (disposed) return@post
                        if (!rgbStr.isNullOrBlank() && rgbStr != "transparent") {
                            parseAndApplyColor(rgbStr) { color, isLightBg ->
                                if (color != null) {
                                    val prev = lastDetectedColor
                                    if (prev == null || isSignificantColorChange(prev, color)) {
                                        lastDetectedColor = color
                                        statusBarBgColor = color
                                        window?.let { w ->
                                            val controller = WindowInsetsControllerCompat(w, localView)
                                            controller.isAppearanceLightStatusBars = isLightBg
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }, "NocoBaseColorBridge")

            Log.d("NocoBaseWebView", "WebView created | visibility=$visibility alpha=$alpha width=$width height=$height")
        }
    }

    // Compose 设置变更即时同步到当前 WebView 实例，无需刷新页面。
    LaunchedEffect(webViewZoomEnabled) {
        webView.settings.setSupportZoom(webViewZoomEnabled)
        webView.settings.builtInZoomControls = webViewZoomEnabled
        webView.settings.displayZoomControls = false
    }

    // 启动过渡控制
    LaunchedEffect(workspaceId) {
        val loadedWs = viewModel.getWorkspaceById(workspaceId)
        workspace = loadedWs
        val savedToken = app.credentialStore.getToken(workspaceId) ?: ""
        token = savedToken

        val targetUrl = loadedWs?.let { NocoBaseApiClient.normalizeServerUrl(it.serverUrl) } ?: ""

        if (targetUrl.isNotBlank() && webView.url.isNullOrBlank()) {
            Log.d("NocoBaseWebView", "loadUrl called: $targetUrl")
            webView.loadUrl(targetUrl)

            val startTime = System.currentTimeMillis()
            while (!readyDetector.isReady.value && (System.currentTimeMillis() - startTime) < 10000 && !isError) {
                readyDetector.checkReadiness(webView)
                delay(300)
            }

            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime < 1000) {
                delay(1000 - elapsedTime)
            }
        } else {
            sessionInjected = true
            sessionReloadPerformed = true
            isLoading = false
        }
        isLaunchOverlayVisible = false

        // 会话状态异步校验
        if (savedToken.isNotBlank()) {
            viewModel.checkSession(workspaceId) { result ->
                if (disposed) return@checkSession
                if (result is SessionCheckResult.ExpiredOrUnauthorized) {
                    Log.d("NocoBaseWebView", "Background Session Check: Expired/Invalid -> Redirecting to ReLogin")
                    Toast.makeText(context, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show()
                    onRedirectToReLogin(workspaceId)
                } else if (result is SessionCheckResult.Valid && !disposed) {
                    val refreshedToken = app.credentialStore.getToken(workspaceId).orEmpty()
                    if (refreshedToken.isNotBlank() && refreshedToken != token) {
                        token = refreshedToken
                        sessionInjected = false
                        sessionReloadPerformed = false
                        webView.reload()
                    }
                    Log.d("NocoBaseWebView", "Background Session Check: Valid")
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            disposed = true
            fileChooser.cancelPendingCallback()
            callbackHandler.removeCallbacksAndMessages(null)
            popupWebViews.toList().forEach { it.stopLoading(); it.destroy() }
            popupWebViews.clear()
            webView.stopLoading()
            webView.removeJavascriptInterface("NocoBaseColorBridge")
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
            Log.d("NocoBaseWebView", "NocoBaseWebViewScreen disposed")
        }
    }

    var lastBackTime by remember { mutableStateOf(0L) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        // 状态栏安全区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(statusBarBgColor)
        )

        // 网页容器
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
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
                                // Allow the initial sign-in document to receive this workspace's token.
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
                                isError = false

                                injectColorObserver(view)

                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false

                                readyDetector.checkReadiness(view)

                                if (view != null && token.isNotBlank() && WebUrlPolicy.sameOrigin(workspace?.serverUrl, url) && !sessionInjected) {
                                    NocoBaseSessionBridge.injectAndCheckStorage(view, token, workspace?.serverUrl.orEmpty()) { matches, written ->
                                        if (disposed || !written) return@injectAndCheckStorage
                                        sessionInjected = true
                                        if (!matches && !sessionReloadPerformed) {
                                            sessionReloadPerformed = true
                                            if (WebUrlPolicy.isSignIn(view.url, workspace?.serverUrl)) {
                                                view.loadUrl(workspace!!.serverUrl)
                                            } else {
                                                view.reload()
                                            }
                                        }
                                    }
                                }

                                injectColorObserver(view)

                                val mainHandler = callbackHandler
                                mainHandler.postDelayed({
                                    inspectDomHealth(view, "DOM +2s")
                                }, 2000)

                                mainHandler.postDelayed({
                                    inspectDomHealth(view, "DOM +5s")
                                }, 5000)

                                if (sessionInjected && WebUrlPolicy.isSignIn(url, workspace?.serverUrl)) {
                                    onRedirectToReLogin(workspaceId)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    val errCode = error?.errorCode ?: 0
                                    val errDesc = error?.description ?: ""
                                    Log.d("NocoBaseWebView", "onReceivedError: $errCode - $errDesc")
                                    isError = true
                                    isLoading = false
                                    isLaunchOverlayVisible = false
                                    errorMessage = "无法加载页面 ($errCode)"
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                val statusCode = errorResponse?.statusCode ?: 0
                                val reqUrl = request?.url?.toString() ?: ""
                                val isMainFrame = request?.isForMainFrame == true

                                if (statusCode in listOf(401, 403, 404, 500, 502, 503) || reqUrl.contains("/api/")) {
                                    val path = try { URI(reqUrl).path ?: reqUrl } catch (_: Exception) { reqUrl }
                                    Log.d("NocoBaseHTTP", "status=$statusCode url=$path mainFrame=$isMainFrame")
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                Log.d("NocoBaseWebView", "SSL error: ${error?.primaryError}")
                                handler?.cancel()
                                isError = true
                                isLoading = false
                                isLaunchOverlayVisible = false
                                errorMessage = "SSL 证书不受信任，加载已被取消"
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 30) {
                                    injectColorObserver(view)
                                    readyDetector.checkReadiness(view)
                                }
                                if (newProgress >= 100) {
                                    isLoading = false
                                    readyDetector.checkReadiness(view)
                                }
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val message = resultMsg ?: return false
                                val transport = message.obj as? WebView.WebViewTransport ?: return false
                                if (!isUserGesture) return false
                                val popupWebView = WebView(context)
                                popupWebViews.add(popupWebView)
                                var targetHandled = false

                                fun handleTarget(url: String?) {
                                    if (targetHandled || url.isNullOrBlank() || url == "about:blank") return
                                    targetHandled = true

                                    // 开启外部浏览器设置时由处理器拉起系统浏览器；
                                    // 关闭时（以及同源链接）在当前 WebView 中继续打开。
                                    if (!currentUrlHandler.value.handleUrlLoading(url, isNewWindow = true)) {
                                        view?.loadUrl(url)
                                    }

                                    callbackHandler.post {
                                        if (popupWebViews.remove(popupWebView)) {
                                            popupWebView.stopLoading()
                                            popupWebView.destroy()
                                        }
                                    }
                                }

                                popupWebView.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        popupView: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        handleTarget(request?.url?.toString())
                                        return true
                                    }

                                    override fun onPageStarted(
                                        popupView: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        handleTarget(url)
                                    }
                                }

                                callbackHandler.postDelayed({
                                    if (popupWebViews.remove(popupWebView)) {
                                        popupWebView.stopLoading()
                                        popupWebView.destroy()
                                    }
                                }, 15000)
                                transport.webView = popupWebView
                                message.sendToTarget()
                                return true
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileChooser.prepareFileChooser(filePathCallback, fileChooserParams)
                                currentFileChooserParams = fileChooserParams
                                showFileUploadOptionsSheet = true
                                return true
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage != null && (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                                    Log.d(
                                        "NocoBaseJS",
                                        "[JS ${consoleMessage.messageLevel()}] ${consoleMessage.message()} (at ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                                    )
                                }
                                return true
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 加载进度条
            if (isLoading && !isError) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
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

            // 网络断开提示
            if (!isOnline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
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

            // 网络恢复提示
            if (showRestoredToast && isOnline) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
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

            // 悬浮控制球
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
                onNavigateToSettings = {
                    showSettingsSheet = true
                },
                onNavigateToWorkspaces = onNavigateBack,
                onPositionSaved = { isRightSide, verticalRatio ->
                    viewModel.saveFloatingBallPosition(isRightSide, verticalRatio)
                }
            )

            // 上传方式选择弹窗
            if (showFileUploadOptionsSheet) {
                var isOptionPicked by remember { mutableStateOf(false) }
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

            // 冷启动过渡遮罩
            if (isLaunchOverlayVisible) {
                WorkspaceLaunchOverlay(
                    visible = isLaunchOverlayVisible,
                    workspaceName = workspace?.name ?: "工作空间",
                    serverUrl = workspace?.serverUrl ?: ""
                )
            }

            // 页面加载错误遮罩
            if (isError) {
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
                        text = if (!isOnline) "请检查网络连接后重试" else (errorMessage ?: "请检查网络或服务器地址"),
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
}

/**
 * 网页顶部颜色提取脚本
 */
private fun injectColorObserver(webView: WebView?) {
    if (webView == null) return

    val jsCode = """
        (function() {
            function getTopBgColor() {
                try {
                    // 采样屏幕顶部点位
                    var w = window.innerWidth || document.documentElement.clientWidth || 360;
                    var pts = [w * 0.1, w * 0.3, w * 0.5, w * 0.7, w * 0.9];
                    for (var i = 0; i < pts.length; i++) {
                        var el = document.elementFromPoint(pts[i], 10);
                        while (el && el !== document.documentElement) {
                            var style = window.getComputedStyle(el);
                            var bg = style ? style.backgroundColor : '';
                            if (bg && bg !== 'transparent' && bg !== 'rgba(0, 0, 0, 0)') {
                                return bg;
                            }
                            el = el.parentElement;
                        }
                    }

                    // 检索导航栏元素
                    var headers = document.querySelectorAll('header, .ant-layout-header, [role="banner"], .nocobase-header, .ant-menu-horizontal, .nb-header');
                    for (var j = 0; j < headers.length; j++) {
                        var hStyle = window.getComputedStyle(headers[j]);
                        var hBg = hStyle ? hStyle.backgroundColor : '';
                        if (hBg && hBg !== 'transparent' && hBg !== 'rgba(0, 0, 0, 0)') {
                            return hBg;
                        }
                    }

                    // 回退检索根容器
                    var containers = [
                        document.querySelector('#root'),
                        document.querySelector('.ant-app'),
                        document.querySelector('.ant-layout'),
                        document.body,
                        document.documentElement
                    ];
                    for (var k = 0; k < containers.length; k++) {
                        if (containers[k]) {
                            var cStyle = window.getComputedStyle(containers[k]);
                            var cBg = cStyle ? cStyle.backgroundColor : '';
                            if (cBg && cBg !== 'transparent' && cBg !== 'rgba(0, 0, 0, 0)') {
                                return cBg;
                            }
                        }
                    }

                    return 'transparent';
                } catch(e) {
                    return 'transparent';
                }
            }

            function checkAndNotify() {
                var color = getTopBgColor();
                if (color && window.NocoBaseColorBridge && window.NocoBaseColorBridge.onTopColorChanged) {
                    window.NocoBaseColorBridge.onTopColorChanged(color);
                }
            }

            checkAndNotify();

            if (!window.__nocobaseColorObserverInjected) {
                window.__nocobaseColorObserverInjected = true;
                setInterval(checkAndNotify, 800);

                try {
                    var observer = new MutationObserver(function() {
                        checkAndNotify();
                    });
                    var targets = [document.body, document.querySelector('#root'), document.querySelector('header')].filter(Boolean);
                    targets.forEach(function(target) {
                        observer.observe(target, { childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style'] });
                    });
                } catch(e) {}
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(jsCode, null)
}

private fun parseAndApplyColor(
    rgbStr: String,
    onResult: (color: Color?, isLightBg: Boolean) -> Unit
) {
    try {
        val clean = rgbStr.replace("\"", "").trim()
        val match = Regex("""rgba?\((\d+),\s*(\d+),\s*(\d+)""").find(clean)
        if (match != null) {
            val (rStr, gStr, bStr) = match.destructured
            val r = rStr.toInt()
            val g = gStr.toInt()
            val b = bStr.toInt()

            val color = Color(r, g, b)
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            val isLightBg = luminance > 128

            onResult(color, isLightBg)
        } else {
            onResult(null, false)
        }
    } catch (e: Exception) {
        onResult(null, false)
    }
}

private fun inspectDomHealth(webView: WebView?, tagPrefix: String) {
    if (webView == null) return
    val jsCode = """
        (function() {
            try {
                var root = document.querySelector('#root') || document.querySelector('#app');
                return JSON.stringify({
                    readyState: document.readyState,
                    title: document.title || '',
                    bodyLength: document.body ? document.body.innerHTML.length : 0,
                    rootExists: !!root,
                    rootLength: root ? root.innerHTML.length : 0,
                    href: location.href
                });
            } catch(e) {
                return JSON.stringify({ error: e.toString() });
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(jsCode) { result ->
        if (!result.isNullOrBlank() && result != "null") {
            try {
                val unquoted = if (result.startsWith("\"") && result.endsWith("\"")) {
                    result.substring(1, result.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                } else {
                    result
                }
                val obj = JSONObject(unquoted)
                val readyState = obj.optString("readyState")
                val title = obj.optString("title")
                val bodyLength = obj.optInt("bodyLength")
                val rootExists = obj.optBoolean("rootExists")
                val rootLength = obj.optInt("rootLength")

                Log.d(
                    "NocoBaseDOM",
                    "$tagPrefix: readyState=$readyState | title=$title | bodyLength=$bodyLength | rootExists=$rootExists | rootLength=$rootLength"
                )
            } catch (_: Exception) {
                Log.d("NocoBaseDOM", "$tagPrefix Raw Result: $result")
            }
        }
    }
}

private fun isSignificantColorChange(c1: Color, c2: Color): Boolean {
    val dr = abs(c1.red - c2.red)
    val dg = abs(c1.green - c2.green)
    val db = abs(c1.blue - c2.blue)
    return (dr + dg + db) > 0.08f
}

private fun extractOrigin(url: String?): String {
    if (url.isNullOrBlank()) return "unknown"
    return try {
        val uri = URI(url)
        "${uri.scheme}://${uri.authority}"
    } catch (_: Exception) {
        "unknown"
    }
}

private fun isHttpUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}

private fun isNocoBaseSigninUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val path = try {
        URI(url).path ?: ""
    } catch (_: Exception) {
        url
    }
    return path == "/signin" || path.endsWith("/signin") || path.contains("/auth/signin") || path.contains("/admin/signin")
}
