<div align="center">

<img src="docs/images/logo.png" height="96" alt="BiliPod" />

# BiliPod

**把 B 站当播客听的 Android 客户端**

<img src="https://img.shields.io/badge/Version-0.1.0%20(1)-00A8EC?style=flat-square" alt="Version 0.1.0 (1)" />
<img src="https://img.shields.io/badge/Android-8.0%2B-34C759?style=flat-square" alt="Android 8.0+" />
<img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square" alt="Kotlin 2.1" />
<img src="https://img.shields.io/badge/Compose-Material3-4285F4?style=flat-square" alt="Compose Material3" />

[下载](#下载与安装) · [开发记录](docs/DEVLOG.md) · [问题反馈](https://github.com/ChenL-eon/BiliPod/issues)

</div>

基于 Kotlin 与 Jetpack Compose 打造的原生客户端，把 B 站的长视频当播客来听：只留下适合音频消费的内容，砍掉纯视觉驱动的分区，播放页按封面取色做模糊背景，整套界面只用一种品牌蓝。

## 主要功能

| 功能 | 说明 |
| --- | --- |
| 内容筛选 | 屏蔽 MV、音乐区、鬼畜、游戏实况、舞蹈、美妆穿搭、萌宠、吃播，过滤 10 分钟以下的短视频与付费稿件，剩下的才是能听的 |
| 发现流 | 推荐、榜单、分区 chips、下拉刷新；已经在发现页时再点"发现"才刷新，从其他页回来不重刷 |
| 播放页 | 封面模糊背景、波形进度条、倍速、睡眠定时（含"定时到本集结束"）、后退 15 秒 / 前进 30 秒、连续播放 |
| 三种登录 | 短信验证码、账号密码、扫码同页切换；极验用悬浮卡片承载，不再跳二级页 |
| 收听记录 | 本地 Room 存历史与收藏，可以单独开关"同步 B 站历史 / 收藏"，默认纯本地 |
| 缓存 | 边听边存、离线播放、缓存管理页 |
| 外观 | 浅色 / 深色，品牌色 `#00A8EC`，播放页随封面取色 |
| 未登录可用 | 首启直接进首页，点播放时才提示登录，浏览和缓存不受影响 |

## 界面预览

<div align="center">

<!-- 截图待补：首页发现流 / 播放页 / 登录页三 tab / 我的播客 -->

截图待补。

</div>

## 下载与安装

- 系统要求：Android 8.0+（minSdk 26）
- 安装包：打 tag 后从 [Releases](https://github.com/ChenL-eon/BiliPod/releases) 下载，或从 [Actions](https://github.com/ChenL-eon/BiliPod/actions) 里 `app-debug` 产物拿
- 目前是 debug 签名，只适合自用安装，不能上架分发
- 登录 Cookie 用 Keystore AES 加密后落盘，不随包体或备份离开设备

## 最近更新

- 首次启动不再强制跳登录页，直接进首页；点播放才弹"播放需要登录"
- 登录页合并成三个 tab 同页切换（验证码 / 密码 / 扫码），二维码 bitmap 提到页面顶层复用，不再跟着 tab 重建
- 登录成功的回调换成 `onLoginStateChanged()`，不再触发首页骨架屏闪烁
- `lintDebug` 从 2 errors / 15 warnings 收到 0 errors / 7 warnings
- 接入 GitHub Actions：main 分支编译 + lint，打 tag 自动出包并挂 Release

完整开发过程见 [docs/DEVLOG.md](docs/DEVLOG.md)。

## 构建

环境：JDK 21、Android SDK 35、Gradle 8.9。

```bash
git clone https://github.com/ChenL-eon/BiliPod.git
cd BiliPod
./gradlew assembleDebug
```

Windows 下工程路径如果含中文，`gradle.properties` 里要保留 `android.overridePathCheck=true`。APK 输出在 `app/build/outputs/apk/debug/`。

## 技术栈

Kotlin 2.1 + Jetpack Compose（Material3）+ MVVM + StateFlow + Room 2.7.1 + Media3 1.5.1 + OkHttp / Retrofit + Coil。单 Activity，`ServiceLocator` 手写依赖注入，没有引入 Hilt。

## 参与贡献

欢迎在 [Issues](https://github.com/ChenL-eon/BiliPod/issues) 反馈问题、提建议，也欢迎提交 PR。

## 致谢

- [Kotlin](https://kotlinlang.org/) — 开发语言与协程
- [Jetpack Compose](https://developer.android.com/jetpack/compose) — 声明式 UI
- [AndroidX Media3](https://github.com/androidx/media) — 播放引擎与 MediaSession
- [Room](https://developer.android.com/training/data-storage/room) — 本地历史与收藏
- [Retrofit](https://github.com/square/retrofit) / [OkHttp](https://github.com/square/okhttp) — 网络请求与 Cookie
- [Coil](https://github.com/coil-kt/coil) — 图片加载
- [BiliBili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect) — B 站接口文档
- [BiliPai](https://github.com/jay3-yy/BiliPai) — README 结构与登录方式的参考

## 声明

本项目是非官方客户端，与哔哩哔哩无隶属关系，数据来自 B 站公开接口。仅个人学习使用，不商用、不分发，不含任何付费或广告逻辑。内容版权归对应权利方所有，使用时请遵守平台规则与当地法律。

<div align="center">

<sub>版本 0.1.0（1） · 更新于 2026-11-17 · Made by [ChenL-eon](https://github.com/ChenL-eon)</sub>

</div>
