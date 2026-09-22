package cn.xzbim.workspace.security

import android.content.Context
import android.content.SharedPreferences

/**
 * 安全凭据存储仓库，将加密后的 Password 与 Token 存储于私有 SharedPreferences 中
 */
class SecureCredentialStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("workspace_credentials_store", Context.MODE_PRIVATE)

    fun savePassword(workspaceId: String, password: String) {
        val encrypted = KeystoreManager.encrypt(password)
        prefs.edit().putString("pwd_$workspaceId", encrypted).apply()
    }

    fun getPassword(workspaceId: String): String? {
        val encrypted = prefs.getString("pwd_$workspaceId", null) ?: return null
        return KeystoreManager.decrypt(encrypted)
    }

    fun saveToken(workspaceId: String, token: String) {
        val encrypted = KeystoreManager.encrypt(token)
        prefs.edit().putString("token_$workspaceId", encrypted).apply()
    }

    fun getToken(workspaceId: String): String? {
        val encrypted = prefs.getString("token_$workspaceId", null) ?: return null
        return KeystoreManager.decrypt(encrypted)
    }

    fun deleteCredentials(workspaceId: String) {
        prefs.edit()
            .remove("pwd_$workspaceId")
            .remove("token_$workspaceId")
            .apply()
    }

    fun clearCredential(workspaceId: String) {
        deleteCredentials(workspaceId)
    }
}
