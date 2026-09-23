# Workspace（工作空间）

[![Android SDK](https://img.shields.io/badge/Android%20SDK-26%2B-blue.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Android-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![NocoBase](https://img.shields.io/badge/NocoBase-2.0%2B-orange.svg)](https://www.nocobase.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Workspace 是一套面向 NocoBase 的独立第三方 Android 客户端，与 NocoBase 官方没有隶属关系。

应用支持连接和管理多个 NocoBase 实例，在 Android 手机上以原生应用的方式打开 NocoBase 移动端界面。项目优先关注客户端体验、登录状态管理、安全存储和移动端交互，不重复实现 NocoBase 的业务页面，也不要求 NocoBase 服务端额外安装专用插件。

## 项目定位

Workspace 采用“原生 Android 外壳 + NocoBase Web 客户端”的实现方式：

- 原生部分负责实例管理、登录认证、凭据存储、主题设置、文件选择、下载和悬浮球等系统级能力。
- WebView 负责加载 NocoBase 官方移动端页面（通常为 `/mobile`），保留 NocoBase 原有的页面、权限和业务逻辑。
- 应用通过 NocoBase 官方标准认证接口获取登录状态，再将当前会话安全地交给对应实例的 WebView 使用。

## 主要功能

- 多实例管理：添加、编辑、删除和设置默认 NocoBase 实例。
- 多账号支持：同一实例可以保存不同账号的独立配置。
- 自动登录：在本地凭据和会话仍然有效时，启动后自动恢复登录状态。
- 原生认证：使用 NocoBase 标准用户名/密码认证接口，不依赖特定服务端插件。
- 原生 WebView：加载 NocoBase 移动端页面，支持返回、刷新、外部链接控制和页面状态恢复。
- 文件上传：支持拍照、从相册选择、系统文件选择器以及多文件选择。
- 文件下载：使用 Android DownloadManager 处理附件下载。
- 悬浮控制球：支持拖拽、左右吸附、位置记忆、透明度调节和快捷操作。
- 深色模式：基于 Jetpack Compose Material 3，支持浅色和深色主题。
- 中英文界面：部分系统和应用文案支持本地化。

## 技术架构

| 模块 | 实现方式 |
| --- | --- |
| 开发语言 | Kotlin |
| 界面框架 | Jetpack Compose + Material 3 |
| 实例与账号配置 | Room / DataStore |
| 密码和 Token 保护 | Android Keystore + AES-GCM 加密 |
| 网络请求 | OkHttp |
| NocoBase 页面 | Android WebView 加载 `/mobile` |
| 文件选择 | Android Activity Result API |
| 文件下载 | Android DownloadManager |
| 最低系统版本 | Android 8.0（API 26） |
| 目标系统版本 | Android API 37 |

## 登录与会话实现

Workspace 使用 NocoBase 官方标准认证接口完成登录和会话检查。

### 1. 登录认证

用户在应用中输入实例地址、用户名和密码后，应用通过 OkHttp 向用户配置的 NocoBase 实例发送：

```http
POST /api/auth:signIn
Content-Type: application/json
X-Authenticator: basic
```

请求体使用标准账号密码字段：

```json
{
  "account": "用户名",
  "password": "密码"
}
```

认证成功后，应用读取 NocoBase 返回的登录 Token，并将其与实例、账号建立对应关系。

### 2. 会话校验

应用会调用：

```http
GET /api/auth:check
```

用于判断当前 Token 是否仍然有效，并区分正常响应、明确的 401/403 未授权和网络异常。网络超时、服务器错误等情况不会被误判为密码错误。

### 3. WebView 登录状态

登录成功后，应用只会在匹配当前实例来源的 WebView 页面中写入会话信息，并限制 Token 注入到配置的 HTTP(S) 来源。外部网站不会获得当前 NocoBase 实例的登录 Token。

WebView 加载的是 NocoBase 官方移动端页面，默认路径为：

```
/mobile
```

具体页面、菜单、数据权限和业务能力仍由 NocoBase 实例本身决定。

## 数据存储与安全说明

- 用户名、密码、Token 和 WebView 相关数据均保存在本地设备。
- 密码和 Token 不会上传到 Workspace 开发者或其他第三方服务器。
- 登录认证时，必要的账号密码信息会发送到用户主动配置的 NocoBase 实例，这是完成 NocoBase 登录所必需的。
- 密码和 Token 使用 Android Keystore 保护的密钥进行 AES-GCM 加密存储。
- 应用不会把凭据写入普通日志。
- 外部网站、未配置的实例和不匹配的来源不会被注入当前实例的 Token。
- 使用 HTTP 实例时，账号、密码和 Token 可能在传输过程中被窃听。生产环境建议使用 HTTPS，并正确配置反向代理和证书。
- 本项目不承诺提供服务器端安全防护；实例本身的账号权限、HTTPS、反向代理和 NocoBase 安全配置仍由使用者负责。

## 兼容性

- NocoBase：2.0 及以上版本
- Android：8.0（API 26）及以上
- 认证方式：NocoBase 标准 Basic Authenticator 接口
- NocoBase 的 SSO、MFA、验证码以及第三方认证插件未作完整验证

## 安装

1. 从 [Releases](../../releases) 下载 APK。
2. 在 Android 设备上安装。
3. 添加 NocoBase 实例地址，并输入账号密码登录。

应用不提供公共服务端，使用前需要准备一个可从 Android 设备访问的 NocoBase 实例。

## 本地编译

### 环境要求

- Windows 10/11 或其他支持 Android Studio 的开发环境
- Android Studio（建议使用支持当前 Android Gradle Plugin 的版本）
- Java 25
- Gradle Wrapper 9.6.0
- Android SDK API 37

仓库已包含 `gradle/wrapper/gradle-wrapper.jar`，通常不需要手动生成 Gradle Wrapper。

### 编译 Debug 版本

```bash
git clone https://github.com/charce526/workspace-android.git
cd workspace-android

./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Windows PowerShell 或命令提示符可以使用：

```powershell
.\\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug APK 输出位置：

```
app/build/outputs/apk/debug/app-debug.apk
```

### 编译 Release 版本

Release 签名配置不应提交到 Git。编译前：

1. 将 `keystore.properties.example` 复制为项目根目录下的 `keystore.properties`。
2. 填入现有签名文件、别名及密码配置。
3. 确保签名文件和真实配置没有被提交到仓库。
4. 执行：

```bash
./gradlew :app:assembleRelease
```

Release APK 输出位置：

```
app/build/outputs/apk/release/app-release.apk
```

如果没有配置签名信息，Debug 构建仍可用于开发测试，但 Release 版本不适合直接发布或覆盖安装已有正式版本。

## 当前范围与限制

- 当前项目优先支持 Android，不包含 iOS 客户端。
- NocoBase 页面能力取决于实例版本、已安装插件、用户权限和移动端适配情况。
- Blob 下载、仅依赖 Token 的特殊下载场景尚未作为通用能力处理。
- 进程被系统强制终止后的上传续传尚未实现。
- 多账号 WebView Cookie 和 LocalStorage 的完全隔离仍需要更多设备验证。
- 通知、推送和后台消息同步暂不属于当前版本范围。

## 开源许可

本项目基于 [MIT License](LICENSE) 开源。

Workspace 是独立第三方客户端项目，与 NocoBase 官方没有直接隶属关系。NocoBase 及相关商标归其各自权利人所有。
