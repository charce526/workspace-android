package cn.xzbim.workspace.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "workspace_settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

/**
 * DataStore Preferences 用于管理应用配置（主题模式、启动跳转、记住登录、悬浮球设置、缩放、屏幕常亮及外部链接处理等）
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        val KEY_AUTO_ENTER_LAST_WORKSPACE = booleanPreferencesKey("auto_enter_last_workspace")
        val KEY_REMEMBER_LOGIN_STATE = booleanPreferencesKey("remember_login_state")
        val KEY_BALL_IS_RIGHT_SIDE = booleanPreferencesKey("ball_is_right_side")
        val KEY_BALL_VERTICAL_RATIO = floatPreferencesKey("ball_vertical_ratio")
        val KEY_BALL_IDLE_ALPHA = floatPreferencesKey("ball_idle_alpha")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_WEBVIEW_ZOOM_ENABLED = booleanPreferencesKey("webview_zoom_enabled")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_OPEN_LINKS_IN_EXTERNAL_BROWSER = booleanPreferencesKey("open_links_in_external_browser")
    }

    val autoEnterLastWorkspaceFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_ENTER_LAST_WORKSPACE] ?: false
    }

    val rememberLoginStateFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_REMEMBER_LOGIN_STATE] ?: true
    }

    val floatingBallPositionFlow: Flow<Pair<Boolean, Float>> = context.dataStore.data.map { preferences ->
        Pair(
            preferences[KEY_BALL_IS_RIGHT_SIDE] ?: true,
            preferences[KEY_BALL_VERTICAL_RATIO] ?: 0.96f
        )
    }

    val floatingBallIdleAlphaFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_BALL_IDLE_ALPHA] ?: 0.65f
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        try {
            val name = preferences[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val webViewZoomEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WEBVIEW_ZOOM_ENABLED] ?: true
    }

    val keepScreenOnFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_KEEP_SCREEN_ON] ?: false
    }

    val openLinksInExternalBrowserFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_OPEN_LINKS_IN_EXTERNAL_BROWSER] ?: false
    }

    suspend fun setAutoEnterLastWorkspace(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_ENTER_LAST_WORKSPACE] = enabled
        }
    }

    suspend fun setRememberLoginState(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_REMEMBER_LOGIN_STATE] = enabled
        }
    }

    suspend fun saveFloatingBallPosition(isRightSide: Boolean, verticalRatio: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BALL_IS_RIGHT_SIDE] = isRightSide
            preferences[KEY_BALL_VERTICAL_RATIO] = verticalRatio.coerceIn(0.0f, 1.0f)
        }
    }

    suspend fun saveFloatingBallIdleAlpha(alpha: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BALL_IDLE_ALPHA] = alpha.coerceIn(0.25f, 0.80f)
        }
    }

    suspend fun resetFloatingBallPosition() {
        context.dataStore.edit { preferences ->
            preferences[KEY_BALL_IS_RIGHT_SIDE] = true
            preferences[KEY_BALL_VERTICAL_RATIO] = 0.96f
            preferences[KEY_BALL_IDLE_ALPHA] = 0.65f
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }

    suspend fun setWebViewZoomEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_WEBVIEW_ZOOM_ENABLED] = enabled
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setOpenLinksInExternalBrowser(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_OPEN_LINKS_IN_EXTERNAL_BROWSER] = enabled
        }
    }
}
