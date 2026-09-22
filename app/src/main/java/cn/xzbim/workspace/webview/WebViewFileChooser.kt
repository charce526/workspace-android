package cn.xzbim.workspace.webview

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import java.io.File

/**
 * WebView 文件与附件上传处理器（支持 拍照、从相册选择、选择文件，将文件复制到 cache/upload_temps 生成带正确扩展名与 MIME 类型的 FileProvider URI，确保 NocoBase 100% 成功上传）
 */
class WebViewFileChooser {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingCameraFile: File? = null

    fun prepareFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ) {
        if (this.filePathCallback != null && this.filePathCallback != filePathCallback) {
            cancelPendingCallback()
        }
        this.filePathCallback = filePathCallback
        Log.d("NocoBaseUpload", "prepareFileChooser called | acceptTypes=${fileChooserParams?.acceptTypes?.joinToString()}")
    }

    fun takePhoto(context: Context, cameraLauncher: ActivityResultLauncher<Uri>) {
        try {
            val cameraDir = File(context.cacheDir, "camera_photos").apply { if (!exists()) mkdirs() }
            val photoFile = File(cameraDir, "photo_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            pendingCameraUri = photoUri
            pendingCameraFile = photoFile
            Log.d("NocoBaseUpload", "Launching camera photo capture...")
            cameraLauncher.launch(photoUri)
        } catch (e: Exception) {
            Log.d("NocoBaseUpload", "Failed to launch camera: ${e.message}")
            cancelPendingCallback()
        }
    }

    fun openGallery(context: Context, launcher: ActivityResultLauncher<Intent>, isMultiple: Boolean = false) {
        try {
            val intent = Intent(Intent.ACTION_PICK).apply {
                setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (isMultiple) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
            Log.d("NocoBaseUpload", "Launching gallery picker...")
            launcher.launch(intent)
        } catch (e: Exception) {
            Log.d("NocoBaseUpload", "Failed to launch gallery picker, trying fallback: ${e.message}")
            try {
                val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (isMultiple) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
                launcher.launch(fallbackIntent)
            } catch (_: Exception) {
                cancelPendingCallback()
            }
        }
    }

    fun openFilePicker(
        launcher: ActivityResultLauncher<Intent>,
        fileChooserParams: WebChromeClient.FileChooserParams?,
        acceptTypes: Array<String> = emptyArray(),
        isMultiple: Boolean = false
    ) {
        try {
            val nativeIntent = fileChooserParams?.createIntent()
            val intent = if (nativeIntent != null) {
                nativeIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                nativeIntent
            } else {
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    val mimeTypes = acceptTypes
                        .filter { it.isNotBlank() }
                        .map { parseMimeType(it) }
                        .toTypedArray()

                    if (mimeTypes.isNotEmpty()) {
                        if (mimeTypes.size == 1) {
                            type = mimeTypes[0]
                        } else {
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
                        }
                    } else {
                        type = "*/*"
                    }

                    if (isMultiple) {
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                }
            }
            Log.d("NocoBaseUpload", "Launching file picker...")
            launcher.launch(intent)
        } catch (e: Exception) {
            Log.d("NocoBaseUpload", "Failed to launch file picker: ${e.message}")
            cancelPendingCallback()
        }
    }

    fun handleCameraResult(context: Context, success: Boolean) {
        val callback = filePathCallback ?: return
        filePathCallback = null

        val uri = pendingCameraUri
        val photoFile = pendingCameraFile
        pendingCameraUri = null
        pendingCameraFile = null

        if (success && uri != null && photoFile?.exists() == true && photoFile.length() > 0L) {
            Log.d("NocoBaseUpload", "Camera photo captured successfully: $uri (size=${photoFile.length()})")
            postCallbackResult(callback, arrayOf(uri))
        } else {
            photoFile?.delete()
            Log.d("NocoBaseUpload", "Camera photo capture cancelled, empty or failed")
            postCallbackResult(callback, null)
        }
    }

    fun handleActivityResult(context: Context, resultCode: Int, data: Intent?) {
        val callback = filePathCallback ?: return
        filePathCallback = null

        val results: Array<Uri>? = try {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val parsed = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                val rawUris = if (parsed != null && parsed.isNotEmpty()) {
                    parsed
                } else {
                    val clipData = data.clipData
                    val singleData = data.data
                    when {
                        clipData != null -> Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                        singleData != null -> arrayOf(singleData)
                        else -> null
                    }
                }

                val cachedUris = rawUris?.mapNotNull { uri ->
                    copyUriToCache(context, uri)
                }?.toTypedArray()

                cachedUris
            } else {
                Log.d("NocoBaseUpload", "File selection cancelled or failed (resultCode=$resultCode)")
                null
            }
        } catch (e: Exception) {
            Log.d("NocoBaseUpload", "Error processing selected file: ${e.message}")
            null
        }

        postCallbackResult(callback, results)
    }

    private fun copyUriToCache(context: Context, sourceUri: Uri): Uri? {
        return try {
            val ext = getFileExtensionFromUri(context, sourceUri)
            val uploadDir = File(context.cacheDir, "upload_temps").apply { if (!exists()) mkdirs() }
            val tempFile = File(uploadDir, "upload_${System.currentTimeMillis()}_${(0..9999).random()}$ext")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                tempFile.delete()
                return null
            }

            val cachedUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            // FileProvider URI 属于应用自身，不能调用 takePersistableUriPermission；
            // WebView 与应用处于同一进程，可直接读取该临时 URI。
            Log.d("NocoBaseUpload", "Copied sourceUri $sourceUri to cache: $cachedUri (size=${tempFile.length()})")
            cachedUri
        } catch (e: Exception) {
            Log.d("NocoBaseUpload", "Failed to copy uri to cache: ${e.message}")
            null
        }
    }

    private fun getFileExtensionFromUri(context: Context, uri: Uri): String {
        try {
            val mimeType = context.contentResolver.getType(uri)
            if (mimeType != null) {
                when {
                    mimeType.contains("image/jpeg") -> return ".jpg"
                    mimeType.contains("image/png") -> return ".png"
                    mimeType.contains("image/gif") -> return ".gif"
                    mimeType.contains("image/webp") -> return ".webp"
                    mimeType.contains("pdf") -> return ".pdf"
                    mimeType.contains("sheet") || mimeType.contains("excel") -> return ".xlsx"
                    mimeType.contains("word") -> return ".docx"
                    mimeType.contains("zip") -> return ".zip"
                }
            }
            val path = uri.path ?: ""
            val lastDot = path.lastIndexOf('.')
            if (lastDot != -1) {
                return path.substring(lastDot)
            }
        } catch (_: Exception) {}
        return ".jpg"
    }

    fun cancelPendingCallback() {
        val callback = filePathCallback
        filePathCallback = null
        pendingCameraUri = null
        pendingCameraFile?.delete()
        pendingCameraFile = null
        postCallbackResult(callback, null)
    }

    private fun postCallbackResult(callback: ValueCallback<Array<Uri>>?, results: Array<Uri>?) {
        if (callback == null) return
        Handler(Looper.getMainLooper()).post {
            try {
                callback.onReceiveValue(results)
            } catch (e: Exception) {
                Log.d("NocoBaseUpload", "Error delivering callback result: ${e.message}")
            }
        }
    }

    private fun parseMimeType(acceptType: String): String {
        val trimmed = acceptType.trim().lowercase()
        return when {
            trimmed.startsWith(".") -> {
                when (trimmed) {
                    ".pdf" -> "application/pdf"
                    ".jpg", ".jpeg" -> "image/jpeg"
                    ".png" -> "image/png"
                    ".gif" -> "image/gif"
                    ".webp" -> "image/webp"
                    ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    ".xls" -> "application/vnd.ms-excel"
                    ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    ".doc" -> "application/msword"
                    ".txt" -> "text/plain"
                    ".zip" -> "application/zip"
                    else -> "*/*"
                }
            }
            trimmed.contains("/") -> trimmed
            else -> "*/*"
        }
    }
}
