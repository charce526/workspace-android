package cn.xzbim.workspace.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.File

/** Pass temporary read grants to WebView without copying large files on the UI thread. */
class WebViewFileChooser {
    private var callback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var cameraFile: File? = null
    private var multiple = false
    private var accepted: Array<String?> = emptyArray()

    fun cancelPendingCallback() {
        cameraUri = null
        cameraFile?.delete()
        cameraFile = null
        complete(null)
    }

    fun prepareFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ) {
        cancelPendingCallback()
        callback = filePathCallback
        multiple = fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        accepted = fileChooserParams?.acceptTypes ?: emptyArray()
    }

    fun takePhoto(context: Context, cameraLauncher: ActivityResultLauncher<Uri>) {
        try {
            val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
            cameraFile = File.createTempFile("photo_", ".jpg", dir)
            cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile!!)
            cameraLauncher.launch(cameraUri!!)
        } catch (_: Exception) {
            Toast.makeText(context, "无法启动相机，请尝试从相册选择", Toast.LENGTH_LONG).show()
            cancelPendingCallback()
        }
    }

    fun openGallery(context: Context, launcher: ActivityResultLauncher<Intent>, isMultiple: Boolean = false) {
        val types = mimeTypes(accepted).filter { it.startsWith("image/") || it.startsWith("video/") }
            .ifEmpty { listOf("image/*", "video/*") }
        try {
            launcher.launch(pickerIntent(types, isMultiple))
        } catch (_: Exception) {
            Toast.makeText(context, "无法打开媒体选择器，请尝试选择文件", Toast.LENGTH_LONG).show()
            cancelPendingCallback()
        }
    }

    fun openFilePicker(
        launcher: ActivityResultLauncher<Intent>,
        fileChooserParams: WebChromeClient.FileChooserParams?,
        acceptTypes: Array<String> = emptyArray(),
        isMultiple: Boolean = false
    ) {
        try {
            val array = acceptTypes.map { it as String? }.toTypedArray()
            launcher.launch(pickerIntent(mimeTypes(array), isMultiple))
        } catch (_: Exception) {
            cancelPendingCallback()
        }
    }

    private fun pickerIntent(types: List<String>, allowMultiple: Boolean) =
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            type = types.singleOrNull() ?: "*/*"
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        }

    private fun mimeTypes(types: Array<String?>): List<String> =
        types.filterNotNull().flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .map { value ->
                if (value.startsWith(".")) MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(value.drop(1)) ?: "*/*"
                else if ('/' in value) value else "*/*"
            }.distinct().ifEmpty { listOf("*/*") }

    fun handleCameraResult(context: Context, success: Boolean) {
        val file = cameraFile
        val uri = cameraUri
        cameraFile = null
        cameraUri = null
        if (success && uri != null && file != null && file.isFile && file.length() > 0L) {
            complete(arrayOf(uri))
        } else {
            file?.delete()
            complete(null)
        }
    }

    fun handleActivityResult(context: Context, resultCode: Int, data: Intent?) {
        if (callback == null) return
        if (resultCode != Activity.RESULT_OK || data == null) {
            complete(null)
            return
        }
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let { uris.add(it) }
        }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        // Do not let an untrusted picker nominate private app content or a file:// path.
        val safe = uris.distinct().filter {
            it.scheme == "content" && it.authority != "${context.packageName}.fileprovider"
        }.let { if (multiple) it else it.take(1) }
        if (safe.isEmpty()) {
            Toast.makeText(context, "未取得可上传文件，请重新选择", Toast.LENGTH_SHORT).show()
            complete(null)
        } else {
            complete(safe.toTypedArray())
        }
    }

    // Activity-result and WebChromeClient callbacks run on the main thread.
    private fun complete(result: Array<Uri>?) {
        val pending = callback
        callback = null
        pending?.onReceiveValue(result)
    }
}
