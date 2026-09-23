# Workspace (工作空间)

[![Android SDK](https://img.shields.io/badge/Android%20SDK-26%2B-blue.svg)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![NocoBase Compatibility](https://img.shields.io/badge/NocoBase-2.0%2B-orange.svg)](https://www.nocobase.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📖 项目介绍 (Project Introduction)

**Workspace (工作空间)** 是一套专为 **NocoBase** 打造的通用 Android 移动客户端。应用采用纯原生 Jetpack Compose (Material 3) 现代声明式 UI 架构构筑，旨在为移动端用户提供流畅、轻量、企业工具级的 NocoBase 服务接入与管理体验。

---

## ✨ 核心功能 (Features)

* 🌐 **多工作空间管理**：支持添加、编辑、删除、设置默认工作空间，多服务器一键快速切换。
* 🚀 **本地优先与安全凭据**：结合 Room 数据库与 AES-256 GCM 硬件级 Android Keystore 加密存储，实现启动秒开与无感会话恢复。
* 💡 **沉浸式 WebView 容器**：支持网页 SPA 顶部背景色智能跟随、网页手势缩放、全屏常亮。
* 🎯 **52dp 悬浮控制球**：支持自由拖拽磁吸、静止透明度无级调节，内置 4 项快捷菜单（刷新、首页、设置、退出空间）。
* 📂 **全功能文件与附件处理**：完美支持**拍照**、**从相册选择**、**文件选择器**上传，以及 `DownloadManager` 附件下载与链接路由分流。
* 🎨 **Material 3 现代美学**：Clean Minimal 风格，支持深色模式 (Dark Theme)、浅色模式 (Light Theme) 与中英文本地化。

---

## 🔌 NocoBase 兼容版本 (Compatibility)

* 兼容 **NocoBase 2.0 及以上版本**（完整支持官方标准认证接口 `/api/auth:signIn` 与 `/api/auth:check`）。

---

## 📱 Android 最低版本 (Minimum Android Version)

* **Minimum SDK: 26** (Android 8.0 Oreo 及以上)
* **Target SDK: 37**

---

## 📦 安装方式 (Installation)

1. 在 [Releases](../../releases) 页面下载最新编译签名的 `app-release.apk`。
2. 在 Android 手机上安装并打开。

---

## 🛠️ 编译方法 (Build Instructions)

### 环境要求
* 可支持 Android API 37 与 AGP 9.4.1 的 Android Studio
* **Gradle JVM**: Java 25（以 `gradle/gradle-daemon-jvm.properties` 为准）
* **Gradle Wrapper**: 9.6.0（以 `gradle-wrapper.properties` 为准）

### 编译步骤
1. 克隆仓库到本地：
   ```bash
   git clone https://github.com/charce526/workspace-android.git
   ```
2. 使用 Android Studio 打开项目文件夹。
3. 确认仓库包含 `gradle/wrapper/gradle-wrapper.jar`。如果缺失，请使用可信的本地 Gradle 9.6.0 执行 `gradle wrapper --gradle-version 9.6.0` 生成并提交该文件。
4. 等待 Gradle 同步完成后，通过终端或 Android Studio 执行编译与检查：
   * 编译 Debug 测试包：
     ```bash
     ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
     ```
   * 编译正式 Release 签名包：
     ```bash
     ./gradlew :app:assembleRelease
     ```
5. Release 编译前，将 `keystore.properties.example` 复制为仓库根目录下的 `keystore.properties`，填入现有签名文件的信息。真实配置和 `.jks` 均不得提交到 Git，也不要重新生成签名身份。
6. 编译产物输出路径：
   * Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
   * Release APK: `app/build/outputs/apk/release/app-release.apk`

---

## 📄 License

本项目基于 [MIT License](LICENSE) 开源协议发布。
