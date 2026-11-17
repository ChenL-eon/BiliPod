# BiliPod · 开发记录

> 这里按时间堆叠每一次改动、验证过程和踩到的坑，是项目史而不是使用说明。项目介绍、功能与构建方式见 [README](../README.md)。

Android 原生播客客户端，数据源为 B 站公开接口。**仅个人学习使用，不商用、不分发、不含任何付费/广告逻辑。**

- 包名 / applicationId：`com.bilipod.app`
- 应用显示名：`BiliPod`（`res/values/strings.xml` 的 `app_name`）

- 平台：Android 单端（minSdk 26 / targetSdk 35 / compileSdk 35）
- 语言：Kotlin 2.1，UI：Jetpack Compose Material3
- 架构：MVVM + Repository + Coroutine + StateFlow
- 数据库：Room 2.7.1（KSP）
- 网络：OkHttp 4.12 + Retrofit 2.11，自维护 Cookie / UA / Referer / wbi 签名
- 播放：Media3 1.5.1 ExoPlayer + MediaSessionService（前台服务 + 通知栏控制器）
- 图片：Coil 2.7
- DI：手动注入（ServiceLocator），不引入 Hilt

> 说明：`screenshots/` 是历次真机/模拟器验收的截图目录，已在 2026-11-17 清空删除（339 个文件、约 240MB）。下文各条记录末尾的截图文件名只作为当时的验收依据留档，文件本身不再随仓库分发；需要复核时按对应条目重新跑一遍即可复现（命令与坐标都写在各条的"实测"段落里）。

---

## 一、目录结构

```
哔哩宇宙/
├─ settings.gradle.kts            # 仓库与模块声明
├─ build.gradle.kts               # 插件版本（AGP 8.7.3 / Kotlin 2.1.0 / KSP）
├─ gradle.properties
├─ local.properties               # 本机 SDK 路径（已被 .gitignore 忽略）
├─ gradlew / gradlew.bat          # wrapper 脚本（已生成，可直接命令行构建）
├─ gradle/wrapper/                # gradle-wrapper.jar + properties（8.9，腾讯云镜像）
├─ .gitignore
└─ app/
   ├─ build.gradle.kts            # 依赖与 compileSdk/targetSdk/minSdk
   ├─ proguard-rules.pro
   └─ src/main/
      ├─ AndroidManifest.xml      # 前台服务、通知权限、网络权限
      ├─ res/values/{strings,themes}.xml、res/mipmap-*/ic_launcher_bilipod.png（自适应图标见 mipmap-anydpi-v26）
      └─ java/com/bilipod/podcast/
         ├─ BiliPodApp.kt     # Application，初始化 ServiceLocator
         ├─ MainActivity.kt       # 单 Activity + 极简页面栈 + 通知权限申请
         │
         ├─ common/
         │  ├─ Constants.kt        UA / Referer / 轮询间隔 / 缓存上限 / 伪协议
         │  ├─ AppResult.kt        Success / Failure(业务码) / Error(异常)
         │  ├─ Formatters.kt       时长、字节格式化
         │  ├─ SecureCookieStore.kt 加密 Cookie 容器 + OkHttp CookieJar
         │  └─ ui/{Theme,Components,BiliImage}.kt
         │       Theme：极浅冷灰 / 炭灰 + BiliPod 蓝（#00A8EC）+ 形状/字体规范
         │       Components：节目卡片、安静标签、骨架屏、空状态
         │       BiliImage：带 Referer 的图片加载
         │
         ├─ biliapi/               # 只负责"怎么和 B 站说话"
         │  ├─ ApiResponse.kt      统一响应体 code/message/data
         │  ├─ BiliApiService.kt   主站接口（nav/详情/playurl/空间/收藏/历史/搜索）
         │  ├─ AuthApiService.kt   passport 扫码接口
         │  ├─ BiliApiClient.kt    OkHttp + Retrofit 组装
         │  ├─ WbiSigner.kt        wbi 签名算法（无状态工具，与业务解耦）
         │  ├─ WbiKeyStore.kt      密钥获取与 12 小时缓存
         │  ├─ interceptor/{Header,WbiSign}Interceptor.kt
         │  └─ model/{Auth,Content,Play}Models.kt
         │
         ├─ repository/            # 领域模型 + 数据聚合 + 错误归一化
         │  ├─ AuthRepository.kt   二维码登录、buvid、用户信息
         │  ├─ ContentRepository.kt 搜索/历史/收藏夹/UP 主投稿/合集/详情
         │  ├─ PlayUrlRepository.kt 播放前实时解析音频直链（绝不落库）
         │  └─ model/{Episode,EntityMappers}.kt
         │
         ├─ database/              # 只放 Room，与 UI 完全解耦
         │  ├─ AppDatabase.kt
         │  ├─ entity/{PlayHistory,Favorite,Cache}Entity.kt
         │  └─ dao/{PlayHistory,Favorite,Cache}Dao.kt
         │
         ├─ login/                 # 扫码登录
         │  ├─ LoginViewModel.kt   轮询状态机（2s/次，180s 上限）
         │  ├─ LoginScreen.kt
         │  └─ QrCodeUtil.kt       ZXing 生成二维码
         │
         ├─ home/                  # 首页：本地历史 / B 站历史 / 收藏 / 搜索 / 选集
         │  ├─ HomeViewModel.kt
         │  └─ HomeScreen.kt
         │
         ├─ player/                # 播放核心（最高优先级）
         │  ├─ PlayerService.kt    MediaSessionService + ExoPlayer + 进度落库 + 队列恢复
         │  ├─ QueueStore.kt       播放队列持久化（SharedPreferences player_queue）
         │  ├─ MediaItemMappers.kt Episode ⇄ bili:// 伪协议 MediaItem
         │  ├─ PlayerConnection.kt MediaController 封装，StateFlow 状态
         │  ├─ PlayerViewModel.kt  倍速、睡眠定时、收藏、缓存状态
         │  ├─ MiniPlayerBar.kt    全局常驻底部迷你播放条
         │  └─ PlayerScreen.kt     全屏播放器浮层（上滑展开 / 下滑收起）
         │
         ├─ cache/                 # 音频缓存
         │  ├─ CacheManager.kt     filesDir 下载、断点续传、进度、容量淘汰
         │  ├─ CacheState.kt       等待/下载中/成功/失败/取消
         │  ├─ CacheViewModel.kt
         │  └─ CacheScreen.kt
         │
         ├─ settings/              # 设置页
         │  ├─ SettingsStore.kt    app_settings：默认倍速
         │  ├─ SettingsViewModel.kt 缓存上限 / 倍速 / 本地历史清理
         │  └─ SettingsScreen.kt
         │
         └─ di/ServiceLocator.kt   Application 级单例容器
```

---

## 二、构建

工程已自带 Gradle wrapper（`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`），可以直接命令行构建，也可以用 Android Studio 打开 `E:\Demo\哔哩宇宙` 让它 Sync。

环境要求：

- JDK 17 或以上（本机验证用的是 JDK 21）；
- Gradle 8.9（wrapper 已指向腾讯云镜像，首次构建会下载 136MB 发行包，实测约 3 秒）；
- Android SDK Platform 35 + Build Tools 36.0.0（`app/build.gradle.kts` 显式指定了 36.0.0，避免 AGP 再去下载 35.0.0）。

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
.\gradlew.bat assembleDebug --console=plain
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（约 20.4MB）。

**本机已验证**：`assembleDebug` 于 2026-11-14 通过（BUILD SUCCESSFUL），首个 APK 已生成，Kotlin 编译无错误。

首次构建时修掉的三个环境问题，换机器时可能还会遇到：

1. **Gradle 发行包下载**：官方 `services.gradle.org` 实测 0 字节/超时，`gradle-wrapper.properties` 里的 `distributionUrl` 已改为 `https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip`。若不用镜像，改回官方地址即可。
2. **仓库模式冲突**：本机 `~/.gradle/init.d/cn-mirrors.gradle` 会往所有工程的 project 仓库插镜像，而工程原先用 `FAIL_ON_PROJECT_REPOS`，会直接报 "repository was added by unknown code"。已把 `settings.gradle.kts` 改成 `PREFER_SETTINGS`，以 settings 仓库为准，init 脚本注入的 settings 级镜像仍然生效。
3. **中文路径**：AGP 默认拒绝含非 ASCII 的工程路径（`E:\Demo\哔哩宇宙`），已在 `gradle.properties` 加 `android.overridePathCheck=true`。如果后续 aapt2、NDK 报中文路径相关的错，最省事的做法是把工程挪到纯英文目录。

构建日志留在根目录 `build-debug.log`，出问题可以直接对照里面的任务顺序。

---

## 三、关键链路设计

### 1. 扫码登录（login + repository/AuthRepository）

`generate` 拿 `qrcode_key` 与登录 url → ZXing 画二维码 → 每 2 秒 `poll` 一次：

| data.code | 含义 |
|---|---|
| 0 | 登录成功 |
| 86101 | 未扫码 |
| 86090 | 已扫码待确认 |
| 86038 | 二维码过期 |

登录成功时服务端通过 `Set-Cookie` 下发 `SESSDATA / bili_jct / DedeUserID`，由 OkHttp 的 `CookieJar`（即 `SecureCookieStore`）自动接收，业务层不解析 query。

**Cookie 加密**：落盘前整体用 AndroidKeyStore 里的 AES-256-GCM 密钥加密，SharedPreferences 里只有密文；密钥生成失败时降级为内存态，绝不写明文。

### 2. wbi 签名（biliapi/WbiSigner + WbiKeyStore + WbiSignInterceptor）

- `WbiKeyStore` 从 `x/web-interface/nav` 取 `wbi_img.img_url / sub_url`，取文件名得到 imgKey/subKey，缓存 12 小时；`refreshWbiKeys()` 可在 403 后强制刷新。
- `WbiSigner` 用固定 64 位混排表生成 32 位 mixinKey，参数按 key 升序、剔除 `!'()*`、URL 编码后拼 `wts`，`md5(query + mixinKey)` 得到 `w_rid`。
- `WbiSignInterceptor` 只对 `api.bilibili.com` 上 `/wbi/` 路径与白名单接口补签名，业务代码完全感知不到。

### 3. 播放（player/PlayerService）

这是本项目最关键的一处设计：**MediaItem 里不放真实音频地址**，而是 `bili://play?key=…&bvid=…&cid=…`。

```
MediaController.setMediaItems(伪协议 MediaItem)
        ↓
ExoPlayer 真正开流
        ↓
ResolvingDataSource.Resolver
        ├─ 命中 filesDir 缓存 → file:// 直接播
        └─ 未命中 → runBlocking 调 playurl 实时解析 → 真实 CDN 地址
```

好处：过期 token 永远不会被记住；每次开流都会重新解析，天然实现"链接过期自动重解析"；缓存命中不需要任何额外判断。

- 音频焦点：`setAudioAttributes(attrs, /* handleAudioFocus = */ true)`，来电/其他 App 播放自动暂停；
- 耳机拔出：`setHandleAudioBecomingNoisy(true)`；
- 通知栏：`MediaSessionService` 自带播放/暂停/上一集/下一集；
- 进度：Service 内每 5 秒 + 暂停/切集/销毁时写 Room（`PlayerService.persistScope` 不随 Service 取消，保证最后一次进度不丢）；不足 3 秒不记录，避免误点污染历史；
- 倍速：`setPlaybackSpeed(speed.coerceIn(0.5f, 3f))`。

### 4. 缓存（cache/CacheManager）

- 目录：`filesDir/audio_cache`，只走 App 私有目录，不需要存储权限；
- 状态：`WAITING / DOWNLOADING / SUCCESS / FAILED / CANCELED`，同时落 Room（`CacheEntity.status`）；
- 下载前实时解析直链（同样不存直链），先写 `.part` 再 rename，失败/取消会删除残留；
- 重复点击：`enqueue()` 在补齐 cid 之前先同步占一个可取消 Job，连点不会重复请求 `videoDetail`、也不会并发写同一个 `.part`；取消占位会连带取消补齐协程（详见第八节第 11 条 ①）；
- 容量：默认 1GB，可在缓存页切 512MB/1GB/2GB/3GB；超限按 `lastAccessAt` 升序淘汰最久未播放的缓存；播放命中缓存会刷新 `lastAccessAt`。

### 5. 本地记录（Room）

- `play_history`：`episodeKey` 主键，一集一条，存位置实现断点续播；
- `favorite_episode`：播放页小心心收藏单集；
- `audio_cache`：缓存状态与文件信息。

---

## 四、UI 规范落地

整体参考小宇宙：安静阅读向，留白优先，不用 Material3 默认的高饱和配色、厚重阴影和大轮廓强调。

**颜色**（`common/ui/Theme.kt`）

浅色背景 `#FAF7F2`（柔和米白），卡片 `#FCFAF6` 只比背景亮一点点；主色 `#C17B52`（低饱和暖橙）；正文 `#2B2A27`，次要文字用 `onSurfaceVariant` 再降 alpha，分割线只在必要时用 `#EAE4DA`。
深色背景 `#171614`（炭灰，不是纯黑），卡片 `#201F1C` 轻微上浮；主色 `#D29B78`，保证不刺眼。`values-night/themes.xml` 同步了窗口底色，冷启动不闪白。

**形状**：节目卡片 20dp、封面 16dp、按钮与输入框 12dp、进度线与轨道 100dp 圆角。

**间距**：列表水平 padding 16dp、卡片内边距 14dp，卡片之间用 10dp + 留白区分，不用分割线。

**字体**：系统默认无衬线，标题加粗放大（`headlineSmall` 22sp / `titleSmall` 15sp 加粗），说明文字 11–12sp 且透明度降到 0.5–0.65。

**交互**

- 全局常驻底部迷你播放条（`MiniPlayerBar`）挂在 AppRoot，首页、缓存页都可见；顶部一条 2dp 极细进度线，点击整条展开全屏播放器。
- 全屏播放器（`PlayerOverlay`）是盖在内容之上的浮层，`slideInVertically + fadeIn` 平缓进出；支持下滑手势收起（超过 140dp 阈值），也可点顶部箭头收起；只有这一层保留 4dp 极弱阴影。
- 全屏布局：大圆形封面居中（190dp）→ 进度条占宽 → 上一曲/播放/下一曲 → 倍速与定时两行安静标签。
- 加载态统一骨架屏（`ShimmerEpisodeList`），2.2 秒线性循环的柔和渐变；空页面用圆形图标 + 一句主文案 + 一句淡说明。
- 禁止项：没有多彩标签、没有卡片阴影、没有花哨动效，页签只用字重 + 2dp 细线区分。

---

## 五、风险点（代码注释里也标了）

1. **风控**：`playurl`、`space/wbi/arc/search`、`search/type` 都是风控重点。不要循环预解析整季地址，不要并发轮询二维码，失败就停，不做无限重试。搜索风控包的特征是 `data` 里只有 `v_voucher`、没有 `result`，判定必须认这个字段 —— 拿"result 为空"当风控会把正常的零结果误报成风险（详见第八节第 11 条 ②）。
2. **直链时效**：音频 CDN 地址约 2 小时失效，且带 IP/UA 校验。任何地方都不要把直链写库或写文件。
3. **403 / -352 / -412**：优先检查 Cookie 是否过期、Referer 是否为 `https://www.bilibili.com/`、UA 是否被改坏；其次刷新 wbi 密钥。
4. **音频区已整体下线**：`audio/music-service-c/web/url` 返回的 `cdns` 与稿件 `dash.audio` 是两套链路。2026-11-15 实测音频区上游已停运（`search_type=audio` 返回 `-1200`，`audio/url` 返回 `4511001`，合集 `totalSize=0`），因此不加 UI 入口；2026-11-16 清理孤儿代码时把音频区接口、DTO、`Episode.audio()` 与 `resolveAudioZone` 兜底分支一并删除，只剩稿件链路（`dash.audio` / `durl`）。
5. **图片防盗链**：`i0.hdslb.com` 不带 Referer 会 403，统一走 `BiliImage`。
6. **安卓 13+ 通知**：未授予 `POST_NOTIFICATIONS` 时前台服务通知不显示，`MainActivity` 已做首次启动申请。
7. **`MediaSessionService` 与播放器线程**：`resolve()` 在加载线程执行，里面用 `runBlocking` 是安全的；千万不要在 UI 线程做同样的事。
8. **单集 key 约定**：视频类单集的 key 是 `v_{bvid}_{cid}`（cid 未知时退回 `v_{bvid}`）。这样多分 P 稿件每集独立缓存与独立断点，910 集的合集不会互相串台；搜索/收藏拿到的是 bvid 级 key，由 `CacheManager.aliases` 与 `HomeViewModel.withBvidAliases()` 兜一层映射回 UI。详见第八节第 7.5 条。

---

## 六、当前实现边界

已实现：
登录扫码 + 加密 Cookie、wbi 签名、历史/收藏夹/搜索结果/合集选集/详情、前台播放服务 + 通知栏 + 音频焦点 + 倍速 + 睡眠定时 + 断点续播、缓存下载/状态/容量淘汰/优先播放、本地历史与收藏；搜索 / 收藏夹 / B 站历史三处手动分页（`LoadMoreFooter`）、播放队列持久化与重启恢复、播放页缓存状态胶囊与下载入口、下载中断 Range 续传、收藏 key 兼容（写入 cid 级、读取 bvid 级回退）、设置页（缓存上限 / 默认倍速 / 本地播放历史清理）。

待补（按依赖排序，2026-11-16 状态）：
- B 站历史与收藏夹翻页只有代码，未登录态验不了第二页（`cursor.max / view_at` 的兜底逻辑已写，需要真机登录后补测）；
- 清空缓存确认框的 `count` 口径待拍板：现在只统计 `SUCCESS`，`FAILED / CANCELED` 的遗留条目不进总数，但它们占着 `.part` 残片；
- 详情页仍是半屏，是否改全屏待定；
- UP 主页粉丝数 / 简介头部（未获批准，暂不做）；
- 音频区**不实现且已删除**：2026-11-15 实测上游已停运；2026-11-16 清理时把相关接口、DTO、构造器与兜底分支全部移除，只保留稿件 `dash.audio` 链路，详见第八节第 7 条与第 11 条。

---

## 七、模拟器实测记录（2026-11-14）

环境：本机 AVD `phase05-api35`（API 35 / x86_64 / 软件渲染），`emulator-5554`，APK 通过 `adb install -r` 安装。

跑通的链路：登录页 → "先不登录" → 首页 → 搜索（20 条真实结果，封面正常）→ 点卡片播放 → 迷你播放条 → 全屏播放器（进度、倍速 1.5x、暂停/继续、下滑收起、顶部箭头收起）→ 切后台继续播放（`MediaSession` 状态 `PLAYING`，前台通知常驻，带上一集/播放/下一集三个 action）→ "本地历史"出现"上次听到 04:44 · 06:29"的断点记录。

过程中定位并修掉的四个问题（都是真机/模拟器才会暴露的）：

1. **搜索返回空（`data.result=null`）**
   - 未登录时 `nav` 返回 `code=-101`，但 `data.wbi_img` 是有效的；原先 `WbiKeyStore.fetch()` 要求 `code==0` 才解析密钥，导致请求根本没带 `wts/w_rid`。改为只要能取到 `wbi_img` 就解析（`WbiKeyStore.kt`）。
   - 签名补上后仍被风控，用宿主机 curl 做对照实验发现：把 `spi` 返回的 `b_3` 手工塞成 `buvid3` 会被判为"未激活设备指纹"，返回 `code=0` 但只有 `v_voucher`。
2. **playurl 直链 403（`Source error` / `InvalidResponseCodeException: 403`）**
   - 第一条线索：直链里的 `buvid` 参数为空 → CDN 直接拒。原因是设备指纹不是服务端下发的那一份。
   - 修复：`AuthRepository.ensureBuvid()` 先访问一次 `https://www.bilibili.com/`，让 `Set-Cookie` 下发真正的 `buvid3 / buvid4 / b_nut`（`BiliApiClient.warmupHome()`），直链随即带上 `buvid=...infoc`。
   - 第二条线索（决定性）：同一条直链在宿主机 curl 里，桌面 UA 返回 **206**，移动端 UA 返回 **403**。`playurl` 是按 `platform=pc` 签发的，CDN 会校验 UA 与 platform 一致。
   - 修复：接口请求继续用移动端 `Constants.UA`，取音频流/下载改用 `Constants.MEDIA_UA`（桌面 UA），见 `PlayUrlRepository.playbackHeaders()` 与 `PlayerService.buildPlayer()`。
3. **搜索封面全空**：搜索接口的 `pic` 是协议相对地址（`//i1.hdslb.com/...`），Coil 不认没有 scheme 的 url。`BiliImage` 统一补 https，并加了 `surfaceVariant` 占位底色、关掉淡入。
4. **全屏播放器顶部箭头点不动**：浮层内容没有避让状态栏，收起按钮被系统状态栏区域吃掉点击。`PlayerOverlay` 的根 `Column` 加了 `statusBarsPadding()`。

另外加了一层风控兜底：未登录高频搜索会收到"code=0 但只有 v_voucher"的包，`ContentRepository.search` 检测到后去掉 `buvid3` 重查一次（只重试一次，不做轮询），仍失败则返回错误码 `-1001` 由 UI 明确提示"搜索被风控"，不再伪装成"没有找到相关稿件"。

仍未实测（当时受条件限制）：真实扫码登录（需要手机扫，当时只验证到"二维码生成 + 等待扫码"）、登录后的收藏/历史拉取。**这两项已在 2026-11-15 真机补测通过**，结果见第八节第 9 条。

### 缓存模块实测（2026-11-14，同一 AVD）

跑通的链路：搜索 → 点下载图标 → 落盘 `filesDir/audio_cache/v_{bvid}.m4a` → 缓存页显示"已缓存 · 大小" → 播放该集 → 缓存页"清空 / 删除 / 取消"可用 → 容量超限自动淘汰。

实测发现并修掉的问题（5 个，全部是跑起来才暴露的）：

1. **缓存下载 403**：`CacheManager.downloadToTemp` 走 `apiClient.httpClient`，而 `HeaderInterceptor` 无条件覆盖 `User-Agent`，把业务层设好的桌面 UA（`Constants.MEDIA_UA`）换回移动端 UA，CDN 直接 403。改为拦截器只补默认值：请求里已有 `User-Agent / Referer / Origin` 就不覆盖（`HeaderInterceptor.kt`）。
2. **进程重启后永远卡在"下载中"**：`jobs` 是内存态，重启即空，DB 里遗留的 `WAITING/DOWNLOADING` 没人修正。加 `recoverStaleStates()`：按磁盘实际情况改判为 `SUCCESS` 或 `FAILED`（附 message "上次下载中断"）。实测强杀进程后重建，`.part` 被清掉、状态变 `FAILED`。
3. **自愈逻辑没被触发**：`CacheManager` 在 `ServiceLocator` 里是 `by lazy`，启动时没人取它，`init` 里的恢复根本没跑（第一版实测就卡在这里）。加 `ServiceLocator.warmUpCache()` 并在 `BiliPodApp.onCreate` 调用。
4. **取消无效、状态写不进库**：取消时协程已处于取消状态，catch 分支里直接调 suspend 的 `upsert` 会立刻再抛 `CancellationException`，`CANCELED` 永远落不了库，界面停在"等待下载"。修复：清理与落库包在 `withContext(NonCancellable)` 里。实测把模拟器网络降到 EDGE（`adb emu network speed edge`，57.8KB/s）后再取消，DB 正确写 `CANCELED`、无 `.part` 残留。
5. **垃圾缓存记录**：搜索结果里偶尔有 `bvid` 为空的条目，会生成一条 `episodeKey = "v_"` 的记录。`enqueue()` 加防护，缺 `bvid/sid` 直接忽略并打日志。

另外三处结构性修正：并发下载加 `Semaphore(2)`（与类注释一致，避免 CDN 限速）；取消/失败统一走 `cleanupFiles()` 同时删最终文件与 `.part`（原来取消删错对象、失败不删 temp，空间泄漏）；容量淘汰改用磁盘真实文件长度统计（`diskUsageBytes()`），并在拿到 `contentLength` 之后再预腾空间（原来在请求前用 0 调用，等于没预判）。容量淘汰实测：把上限临时改成 50MB 后下载新条目，总量从 94MB 降到 46MB，最久未访问的 4 条被删，磁盘与 DB 一致（测完已把上限恢复默认 1GB）。

**已知未修（留给下一轮）**：`HomeScreen` / `CacheScreen` 顶部没有 `statusBarsPadding()`，标题和右上角图标被状态栏压住，实际点击区域与视觉位置错位（截图 `screenshots/cache1.png` 里"清空"已经和状态栏图标重叠）；`EpisodeList` 的下载按钮仍是固定图标，不订阅下载状态，列表里点下载没有进度反馈；"本地历史"卡片没有下载入口（`EpisodeCard` 未传 `trailing`）。**这三项已在 2026-11-14 ~ 11-15 修掉，结果见第八节第 0 条。**

---

## 八、迭代清单实测记录（第 0–12 条，2026-11-14 ~ 11-16）

环境：模拟器 `phase05-api35`（API 35 / x86_64 / 软件渲染 / 1080×2340，`emulator-5554`）；真机 `Redmi 旗舰机`（Android 16 / SDK 37 / 1440×3200 / `A1`）。

约束：每轮只改当轮点名的那部分文件，不扩范围；上一节留下的三个"已知未修"已被第 0、1 条修掉。

### 第 0 条 状态栏遮挡 + 下载按钮四态

改 `HomeScreen` / `CacheScreen` / `LoginScreen` 三处顶栏加 `statusBarsPadding()`；`HomeViewModel` 暴露 `cacheStates` / `cacheStatuses` / `cancelCache()`；`DownloadAction` 拆成四态（可下载 / 环形进度 + 百分比 / 已缓存 / 失败重试），Room 里的 `SUCCESS` 优先级最高。

实测：缓存图标一次点击即进缓存页，不再错位；点下载出现环形进度，缓存页同条目同步变化；下载中点取消回到下载图标；完成后变 ✓，重启保持。

顺带记录：模拟器 `adb emu network speed edge` 档下载会卡死 socket，记入第 5 条处理。

### 第 1 条 搜索卡进详情 / 骨架屏 / 返回

只动 `HomeScreen.kt`：搜索卡统一走 `openDetail`、加载态换 `ShimmerEpisodeList`、列表点击接 `onCardClick`、详情页接缓存四态、加 `BackHandler`、点 tab 自动关详情。

实测：搜索 → 点第一张卡 → 选集详情 → 返回键 / 左上角返回都能退；详情页内每集都有独立的下载状态。

遗留待拍板：单集稿件也要多点一次才进详情；详情页仍显示搜索框与 tab 行（"半屏详情"）是否改全屏。

### 第 2 条 账号入口与退出确认

只动 `HomeScreen.kt`：账号图标按 `loggedIn` 分流；新增 `LogoutConfirmDialog`（20dp 圆角、无阴影、正文"昵称 · 退出后仅清除本机登录凭据…"），确认时先 `closeDetail()` 再 `logout()`。

实测：未登录点头像进登录页；登录后点出弹层，确认后回到未登录态（截图 `screenshots/logout_dialog.png`）。

### 第 3 条 播放器 403 自愈

只动 `player/PlayerService.kt`：

- 解析失败 → `onForbidden()` → 重解析一次；
- `onPlayerError` 挖 `InvalidResponseCodeException.responseCode`，403/410 且本轮未重试过 → 刷密钥 + `prepare` + `seek` + `play`；
- `onIsPlayingChanged` / `onMediaItemTransition` 重置 `expiredRetryKey`。

真实 403 在模拟器上复现不了，用临时故障注入验证通过后删掉注入代码。另外实测到 ExoPlayer 自身对加载层 `IOException` 会内部重试 3~5 次，所以外部重试只做一次就够。

这一条顺带发现了第 7.5 条的根因：910 集的稿件里 `Episode.key` 只有 bvid，全部共用一个 key。

### 第 4 条 清空缓存二次确认

只动 `cache/CacheScreen.kt`：`ClearConfirmDialog(count, totalBytes)`，正文"将删除 N 项缓存（X），正在下载的任务会被取消…"。

实测：14 项 / 80.9 MB → 确认后 DB 0 行、`files/audio_cache` 目录清空（截图 `screenshots/clear_confirm.png`）。

遗留待拍板：`count` 目前把 `CANCELED` / `FAILED` 也算进去，是否只统计 `SUCCESS`。

### 第 5 条 下载取消与停滞看门狗

只动 `cache/CacheManager.kt`：

- `calls` 跟踪在途 Call，`cancel` / `delete` / `clearAll` 都 `call.cancel()`；
- `runDownload` 的 catch 分流：`CancellationException` → `markCanceled`，`catch(Throwable)` 先判 `isActive`；
- `downloadToTemp` 注册 `calls`，2 秒 tick + 30 秒停滞看门狗（`AtomicLong lastByteAt`、`AtomicReference stallReason`）；
- 常量 `WATCHDOG_TICK_MS = 2_000L`、`STALL_TIMEOUT_MS = 30_000L`。

实测：限速档卡住点取消，2 秒内变 `CANCELED` 且无 `.part`；放着不管 30 秒 → `java.io.IOException: timeout` → `FAILED` 且 `.part` 被删；再点重试 → `SUCCESS`。

结论：多数停滞其实是 OkHttp 自己的 `readTimeout(30s)` 先触发，看门狗是兜底。

### 第 6 条 UP 主页投稿分页

碰 `HomeViewModel`（`UpSpace` / `openSpace` / `loadMoreSpace` / `closeSpace`，`SPACE_PAGE_SIZE = 30`）、`HomeScreen`（UP 名可点入口、`UpSpaceContent`、层级「详情 > 主页 > tab」、`AUTO_LOAD_MAX_PAGE = 2`）、`ContentRepository`（`spaceVideos` 412 回退：`refreshWbiKeys()` + `delay(800)` + 临时 `skipBuvid3` 重试一次，`isPreconditionError`）。

实测：韩顺平 24 条；黑马 30 → 60 → 90，页脚"已加载 90 条投稿"（截图 `screenshots/up_space_loadmore.png`）；尚硅谷同样。

三个坑：space 分支判断必须放在 detail 之后；`LazyColumn` 必须 `weight(1f)` 否则完全滑不动；黑马主页真 412 两次，单刷密钥无效，加"去 buvid3"才过。

### 第 7 条 音频区停运确认 + 转做合集

先花一轮验证音频区是否可用，结论是上游已停：

- `search_type=audio` → `-1200`；
- `audio/music-service-c/web/menu/index`、`/menu/new`、`/song/upper` → `code=0` 但 `data` 空；
- `audio/url?sid=1` → `4511001 试听出错，音频未找到或已下架`；
- `song/of-menu?sid=1` → `totalSize=0`。

所以不加音频区 UI 入口，当时只保留 `resolveAudioZone` 兜底（2026-11-16 清理孤儿代码时已连兜底一起删除）。改为做合集体验：`PodcastDetail.isSeason`（`seasonEpisodes.isNotEmpty()`）、详情文案区分「合集 · 共 N 集」/「共 N 集」、`EpisodeCard` 加 `highlight`（淡主色底 7% + 左侧 3dp 竖线）、`PlayerUiState` 加 `currentIndex` / `queueSize` 并用队列索引精确定位 + `scrollToItem`。

实测：麦块老陈稿显示"合集 · 共 457 集"；播第 15 集后重进详情首行即第 15 集；高亮只亮一张。

坑：第一版按 `key == currentKey` 判高亮，因 910 集共用 key 导致一屏三张全亮 → 改用索引 + 队列长度校验。

### 第 7.5 条 单集 key 串台修复

`Episode.video()` 改用 `videoKey(bvid, cid)`；新增 `bvidOfKey()` / `bvidLevelKey()`；`PlayUrlRepository` 暴露 `firstCidOf(bvid)`；`CacheDao.listAll()` / `PlayHistoryDao.listAll()` 供迁移；`CacheManager` 加 `normalize()`（cid 未知先补）、`migrateLegacyKeys()`（旧 key 迁移 + 文件改名 + 补 cid 列）、`aliases` 映射、`cachedFile()` 前缀唯一候选回退；`ServiceLocator` 注入 `historyDao`；`HomeViewModel` 对 `cacheStates` / `cacheStatuses` 套 `withBvidAliases()`（仅当该 bvid 在表中只出现一次才补 bvid 级别名）。

实测：启动日志四条迁移记录（`v_BV1fh411y7R8 -> v_BV1fh411y7R8_287962692` 等），三条磁盘文件同步改名，cid 为空的 `v_BV1TJxCzSEEZ` 靠 `firstCidOf` 补到 `32961267332`；同一 `BV1fh411y7R8` 三条独立记录（287962692 / 299710389 / 287962381，对应 9.6MB / 34.3MB / 1.77MB）；历史每集一条独立断点；搜索卡（cid 未知）下载后记录落 `v_BV17F411T7Ao_25761288170` 且卡片出现 ✓；韩顺平卡因已有 3 条缓存不给别名，仍显示下载图标；重启后 ✓ 保持。

编译坑：`ServiceLocator` 里 DAO 方法名是 `historyDao()`，不是 `playHistoryDao()`。

### 第 8 条 搜索 UP 主

参数先做过一轮实测筛选：只有 `search_type=bili_user` 可用（返回 code=0、6 条，字段 `mid/uname/upic/usign/fans/videos/level`）；`bilibili_user` / `user` / `all` 全部 `-1200`；非 wbi 版 `/x/web-interface/search/type` 对 `bili_user` 直接 HTTP 412。

改动：`ContentModels.kt` 加 `UserSearchData` / `UserSearchItem`；`BiliApiService.searchUser()`（默认 `bili_user`）；新增 `repository/model/UserHit.kt`；`ContentRepository.searchUsers()` 带与稿件搜索一致的风控回退；`Formatters.count()`（496873 → "49.7万"）；`HomeViewModel` 加 `userResults` / `searchingUsers` / `searchedUsers` + `searchUsers()`；`HomeScreen` 加 `searchScope`（稿件 / UP 主）、放大镜按档位分流、`UserHitCard`（圆形头像 + 名字 + 简介 + 粉丝/投稿数）。

实测：`Java` → 5 条（java 62 粉丝、马士兵java课堂 6269 粉丝 444 投稿、余胜军说java编程 74.3万 粉丝 2988 投稿…）；点马士兵java课堂 → "已加载 30 条投稿"；临时把关键词硬编码成"韩顺平"构建一版，得到"韩顺平 49.7万 粉丝 · 24 投稿"，点进主页"已加载 24 条投稿"（与第 6 条一致，确认 mid 解析正确），验完已还原为空串。

范围外没做：UP 主搜索不分页（接口不给可靠总页数）。

### 第 9 条 真机扫码登录

真机装 debug 包（`install -r -g` 一次给全权限），启动 → 登录页二维码正常渲染。二维码在手机自己屏幕上没法自扫，截屏后在电脑上显示，用手机 B 站 App 扫电脑屏幕 → 手机确认 → App 自动退出登录页。

实测结果：

- 顶栏立刻变昵称，副标题"未登录 · 部分内容受限"消失；
- B 站历史拉到真实记录（草台班子、太吾车神、失眠的树、给我十杯拿铁…）；
- 收藏夹列表拉到真实四个夹子（默认收藏夹 / 设计 / 摩擦音 / 鹅毛棒），内容可拉取；
- Cookie 只以密文落盘：`shared_prefs/bili_secure_cookie.xml` 1001 字节，键 `cookie_cipher`，值约 930 字符，全文搜不到 `SESSDATA`；
- `force-stop` 重启不弹登录页，说明 AndroidKeyStore 解密恢复链路在 Android 16 上正常。

这一轮抓到的问题：收藏夹内容偶发 30 秒 `readTimeout`（`收藏夹内容获取失败：timeout`），且失败时与"这个收藏夹是空的"空态叠加，见第 10 条。

### 第 10 条 收藏夹超时修复 + 通知栏确认

收藏夹（三处）：

- `ContentRepository.favEpisodes()` 对 `SocketTimeoutException` 退避 500ms 重试一次（`allowTimeoutRetry` 参数防止无限递归）；
- `HomeViewModel` 加 `folderFailed`，`selectFolder` 先清空 `folderEpisodes` 再置 loading；
- `HomeScreen` 的 `FavContent` 在 `failed` 时显示"收藏夹内容拉取失败 / 网络抖了一下，点这里重试"（可点击），不再落到"这个收藏夹是空的"。

实测：断网（`svc wifi disable` + `svc data disable`）点收藏夹 → 正确显示失败重试文案，错误为 `收藏夹内容获取失败：connection closed`；恢复网络后点"点这里重试" → 正常加载出内容。

通知栏（真机）：播放时通知 `id=1001`、`channel=default_channel_id`、`category=transport`、`flags=NO_CLEAR|FOREGROUND_SERVICE`，卡片显示标题 / UP 主 / 封面 / 上一集·暂停·下一集 / `00:22 · 18:06`。

- 点暂停 → `state=PAUSED, position=49537`；
- 点下一集 → `active item id=1`，metadata 切换为"攻防超神！保罗乔治…"；
- 点播放 → 回到 `PLAYING`；
- 回桌面 8 秒仍 `PLAYING`（position 2.8s → 11.8s），`PlayerService isForeground=true foregroundId=1001 types=0x2`；
- 熄屏再亮屏，锁屏媒体卡片在（标题 + 进度 + 三个控制）；
- 回到 App，底部迷你条与通知栏切集结果一致。

修掉的问题：通知栏封面原本是黑色圆形占位图标。原因：Media3 默认 `DefaultBitmapLoader` 拉图不带 Referer / UA，`i0.hdslb.com` 防盗链 403。新增 `player/BiliBitmapLoader.kt`（复用 App 的 OkHttp，补 Referer / UA / 协议相对地址处理），并在 `PlayerService` 里 `.setBitmapLoader(...)` 挂上，封面恢复正常。

未验：暂停后从最近任务划掉 App，`onTaskRemoved` 是否按预期 `stopSelf` 并清掉通知（需要手动划卡片，自动化容易误触）。

### 第 11 条 审查后七项修复（2026-11-15 ~ 11-16）

代码审查结论（52 个 Kotlin 文件通读、关键符号引用计数、`compileDebugKotlin --rerun-tasks` 无 warning）出 7 条 bug，按审查顺序修完，`assembleDebug` 通过、无 `e:` / `w:`。逐条如下。

**① `CacheManager.enqueue` 双下载 / 重复请求**

成因：`jobs[normalized.key]` 要等 `normalize()`（一次 `videoDetail` 请求）返回后才写入，这段窗口里连点会重复打 view 接口，并发下还会起两个任务写同一个 `.part`。

改法：入口先同步占一个可取消的 `Job` 当"准备中"标记，再以它为父 Job 启动补齐协程。

```kotlin
val slot = Job()
jobs[requestedKey] = slot
scope.launch(slot) {
    try {
        val normalized = normalize(episode)
        val existing = jobs[normalized.key]
        // key 没变时 jobs[normalized.key] 就是自己的占位，不能误判成"已在下载"
        if (existing != null && existing !== slot && existing.isActive) return@launch
        if (requestedKey != normalized.key) aliases[requestedKey] = normalized.key
        val job = scope.launch(start = CoroutineStart.LAZY) { runDownload(normalized) }
        jobs[normalized.key] = job
        job.start()
    } finally {
        if (jobs[requestedKey] === slot) jobs.remove(requestedKey)
    }
}
```

取消语义：`cancel(episodeKey)` 取消 `slot` 会连带取消补齐协程，下载不会"取消了却偷偷开始"。

第一版试过 `scope.launch(start = LAZY) { ... prepareJob ... }` 自引用，编译报 `e: CacheManager.kt:160:60 Unresolved reference 'prepareJob'`，改 slot 方案后通过。

实测（emulator-5554，`adb -s emulator-5554 emu network speed edge`）：点未缓存卡，`normalize` 在慢网下拖了约 30 秒，期间反复点同一按钮 —— 落地只有 1 条 `WAITING`、目录只有 1 个 `.m4a.part`。下载中点同一按钮 → `audio_cache` 变 `CANCELED`、`.part` 被 `cleanupFiles` 删掉、按钮回到"缓存"态。

**② 搜索空结果误判风控**

成因：`search` / `searchUsers` 拿 `resp.data?.result.isNullOrEmpty()` 当风控包特征，"真的没搜到"也会走 `delay(600)` + 去掉 buvid3 重试 + 报 `搜索失败：搜索被风控`。

改法：`SearchData` / `UserSearchData` 各补 `@SerializedName("v_voucher") val vVoucher: String? = null`（风控包的真正特征字段），判定改成 `resp.data?.vVoucher != null`；空结果直接 `Success(emptyList())` 交给 UI 走空态。

实测（模拟器，UP 主档位搜 `zzqqxxyyqqxxzzvvtt`）：显示"没有找到相关 UP 主 / 换个关键词，或切回稿件档位搜"，无红色风控行。注：稿件档搜同类乱码，B 站仍会回模糊结果（`Yhgggg` / `？？？？`），空 `result` 本来就少见，所以这条换 UP 主档位验。

**③ 切夹子 / 连续搜索旧请求覆盖新请求**

成因：`selectFolder` / `search` / `searchUsers` / `openDetail` / `openSpace` 没有请求序号，慢请求（收藏夹超时重试、UP 主页 412 退避）晚回会盖掉新结果。

改法：`HomeViewModel` 加 `folderRequestId` / `detailRequestId` / `searchRequestId` / `userSearchRequestId` / `spaceRequestId`，落地前比对：

```kotlin
val requestId = ++folderRequestId
viewModelScope.launch {
    val result = content.favEpisodes(folderId)
    if (requestId != folderRequestId) return@launch   // 已不是最新一次切换，丢弃
    when (result) { ... }
}
```

`loadMoreSpace` 三个分支都补 `if (current.mid != space.mid) return@update s`，防旧页追加到新 UP 列表。

实测：模拟器 edge 下先发 `Python`（慢），900ms 后切 full 搜 `Java`（快）→ 列表是黑马 Java 结果，35 秒后仍是 Java；真机先点"设计"、250ms 后点"默认收藏夹" → 16 秒与 34 秒两次 dump 都是默认收藏夹的三条内容。

**④ 重启后收藏按钮失效**

成因：`PlayerViewModel.queue` 只在 `play()` 赋值，Activity/ViewModel 重建后 queue 为空 → `currentEpisode` 为 null → `toggleFavorite()` 直接 return。

改法：`init` 里收 `currentKey` 时用播放器快照兜底构造 `Episode`（`uiState.value` 的 title / cover / upName，`cid` 从 key 尾部解析）。

实测（真机 A1，MIUI 上 `always_finish_activities` 不生效，改用强制新任务）：`am start -n com.bilipod.app/.MainActivity -f 0x10008000` → logcat events 抓到 `wm_on_create_called [0,205744072,...]` 新 token + `wm_on_destroy_called [0,159968937,...]` 旧 token、同进程 9748，`media_session` 仍 `PLAYING position=264290` → 点红心由描边变实心（`favorited` 取自 `favoriteDao.exists(key)`，实心即入库成功）。模拟器用 `always_finish_activities=1` 复现同一场景，`favorite_episode` 新增 `v_BV17F411T7Ao_25761288170`。

`cid` 用 `k.removePrefix("v_").substringAfter('_', "").toLongOrNull()` 从 key 尾部解析，对没有 cid 的 `v_{bvid}` 返回 null，符合预期。

**⑤ `BiliBitmapLoader` 全尺寸解码 + 不判状态码**

成因：`body.bytes()` + `decodeByteArray` 直接全尺寸解码（B 站封面常用 1920×1080，ARGB_8888 约 8MB），且不看 `response.isSuccessful`，403 错误页也会被当图解码。

改法：`onResponse` 先 `if (!it.isSuccessful) future.setException(IOException("HTTP ${it.code}"))`；解码统一走新增的 `decodeSampled()`：`inJustDecodeBounds` 量尺寸 → `while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2` → `inSampleSize` 解码，`MAX_SIDE = 512` 放在 `private companion object`。`decodeBitmap(data)` 也改用它。

实测：真机与模拟器通知栏媒体卡片封面都是真实缩略图（未变黑占位），说明状态码判断没误杀正常图、采样没破坏渲染。

**⑥ `ContentRepository` 吞 `CancellationException`**

成因：8 个方法都是 `catch (t: Throwable)`，协程取消被当成网络错误回填，切夹子/放弃请求时会冒出"加载失败"。

改法：8 处统一前置 `catch (c: kotlinx.coroutines.CancellationException) { throw c }`。

实测：③ 的竞态场景里旧请求是被静默丢弃，没有错误文案回填。

**⑦ `HomeViewModel.openDetail` 格式**

`fun openDetail(bvid: String) {        _state.update {...}` 花括号与语句同行，随 ③ 的 requestId 改写一并拆行。

复验命令（模拟器）：

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"; $d="emulator-5554"
& $adb -s $d emu network speed edge          # 注意 emu 是 adb 子命令，不要写成 shell emu
& $adb -s $d shell "run-as com.bilipod.app sqlite3 databases/bilipod.db 'select episodeKey, status, downloadedBytes from audio_cache;'"
& $adb -s $d shell "run-as com.bilipod.app ls files/audio_cache"
& $adb -s $d emu network speed full
```

真机侧限制：Android 16 上 `run-as com.bilipod.app sqlite3` 报 `exec failed for sqlite3: Permission denied`，真机只能靠 UI 状态（红心实心 / 列表内容）取证；要查库得 `run-as ... cat databases/bilipod.db` 导出到本地再解析，二进制别走 PowerShell 管道。

**孤儿清理（2026-11-16，已完成）**：审查清单里的孤儿代码与孤儿文件已全部删除，`assembleDebug` 复编通过（BUILD SUCCESSFUL）。

删除的代码：`BiliApiService.audioUrl` / `audioMenuSongs` / `seasonArchives` 三个接口、`PlayModels` 的 `AudioUrlData` / `AudioSongPageData` / `AudioSong`、`ContentModels` 的 `SeasonArchiveData` / `SeasonArchive` / `HistoryCursor`，以及 `UgcSeason.id/title/cover`、`UgcSection.title`、`SpaceArc.created`、`FavResourceData.hasMore`、`HistoryData.cursor`、`HistoryItem.authorMid/viewAt`、`UserSearchData.numResults`、`UserSearchItem.level` 这些未消费字段；`Episode.audio()` / `isAudioZone`、`PlayUrlRepository.resolveAudioUrl` 的 sid 分支与 `resolveAudioZone()`、`ContentRepository.seasonEpisodes()`、`CacheDao.observeCached()` / `totalBytes()`、`CacheManager.observeCached()`。

删除的文件 / 改名：`player/MediaItemFactory.kt` → `player/MediaItemMappers.kt`（内容只有 `Episode.toMediaItem()` 与 `Uri.toEpisodeOrNull()` 两个扩展，从来没有 `MediaItemFactory` 类，标识符全工程 0 引用）；`emu-start.log`（400.8 KB）、`emu-start.log.err`（2.5 KB）已删。`build-debug.log` 按约定保留。

刻意保留：`Episode.sid` 字段与 `CacheEntity` / `PlayHistoryEntity` / `FavoriteEntity` 的 `sid` 列不动 —— 删字段会改 Room 表结构，`AppDatabase` 是 `version=1` + `fallbackToDestructiveMigration(dropAllTables=true)`，一旦表结构变会触发整库重建、清掉真机上已有的缓存记录。`sid` 现在恒为 null，只作为历史列存在；`CacheManager` / `PlayerViewModel` 里对 `a_` 前缀的防御判断同理保留（兼容旧库里可能存在的音频区记录）。

复核方式：

```powershell
$src="E:\Demo\哔哩宇宙\app\src\main\java"; $pats=@('audioMenuSongs','AudioSongPageData','AudioUrlData','SeasonArchive','isAudioZone','resolveAudioZone','seasonArchives','observeCached','totalBytes\(\)','numResults','HistoryCursor','MediaItemFactory'); foreach($p in $pats){ "{0,-22} {1}" -f $p,(Get-ChildItem -LiteralPath $src -Recurse -Filter *.kt | Select-String -Pattern $p | Measure-Object).Count }
```

预期全部为 0（`seasonEpisodes` 还会剩 3 处，是 `videoDetail` 里的同名局部变量，不是孤儿函数）。

### 第 12 条 P0 + P1 六项补齐（2026-11-16）

判定依据（当时只读核对代码得出）：`AppScreen { LOGIN, HOME, CACHE }` 只有三条路由；`HomeViewModel.search()` 只调 `content.search(trimmed)` 没有翻页参数；`favEpisodes(mediaId)` 的 pn/ps 全默认、`history()` 没有游标；`PlayerScreen.kt` 全文 0 处 `cache` 引用；`recoverStaleStates` 会把中断任务删掉 `.part` 转 FAILED；收藏写 `v_{bvid}_{cid}` 而收藏夹用 bvid 级 key。据此定 P0 三项（分页、队列持久化、播放页缓存态）+ P1 三项（续传、key 统一、设置页）。

**① 搜索 / 收藏夹 / B 站历史三处列表分页**

`ContentModels` 把三个分页必需字段加回来（`FavResourceData.hasMore`、`HistoryItem.viewAt`、`HistoryData.cursor` + `HistoryCursor(max, viewAt)`）。`ContentRepository` 新增顶部类型 `PageResult<T>(items, hasMore)` 与 `HistoryPage(items, hasMore, nextMax, nextViewAt)`：

```kotlin
// search(keyword, page)：页码参与 hasMore 判定，条数也兜底（接口偶发多给一条）
hasMore = (data.numPages ?: 0) > page || list.size >= SEARCH_PAGE_SIZE

// favEpisodes(mediaId, page, allowTimeoutRetry)：pn = page，超时重试递归也要带 page
hasMore = data.hasMore == true || list.size >= FAV_PAGE_SIZE

// history(max = max, viewAt = viewAt)：注意参数名是 viewAt（注解 @Query("view_at")）
nextMax = cursor.max ?: lastViewAt
nextViewAt = cursor.viewAt ?: lastViewAt
hasMore = list.isNotEmpty() && nextMax > 0          // max=0 视为到底
```

`HomeViewModel` 的 `HomeUiState` 加 `searchPage/searchHasMore/searchLoadingMore/folderPage/folderHasMore/folderLoadingMore/historyHasMore/historyLoadingMore` 八个字段，`loadMoreSearch()` 用 `searchRequestId` 丢弃改词后的旧页、`loadMoreFolder()` 比对 `selectedFolderId` 丢弃切夹子后的旧页、`loadMoreHistory()` 续用 `historyMax/historyViewAt`。`HomeScreen` 抽出 `LoadMoreFooter(loadingMore, onLoadMore)`，`EpisodeList` 加 `loadingMore/hasMore/onLoadMore` 默认参数，B 站历史 tab、搜索稿件档、收藏夹内容（仅非"本地收藏"）三处接上。

翻页一律做成手动"加载更多"而不是滚到底自动触发：这三个接口都有风控，自动触发容易连发。

**② 播放队列持久化**

新增 `player/QueueStore.kt`（`SavedQueue(items, index)` + `object QueueStore`，SharedPreferences 名 `player_queue`，Gson `TypeToken<List<Episode>>` 序列化，`load()/clear()` 都套 `runCatching`）。`PlayerConnection.play()` 在 `setMediaItems` 之后 `QueueStore.save(context, episodes, index)`；`PlayerService.onMediaItemTransition` 里 `QueueStore.saveIndex(...)` 只改索引，避免每次切集重写整个队列。

`PlayerService.onCreate` 末尾 `restoreQueue()`：`if (player.mediaItemCount > 0) return`，读盘后 `setMediaItems(items.map { it.toMediaItem() }, saved.index, 0L)` 但**不 prepare** —— 停在 `STATE_IDLE`，等用户点播放才去解析直链，避免启动即打 playurl。日志 `已恢复播放队列 N 集，定位第 M 集`。

副作用（已知、可接受）：重启后 `PlayerUiState.durationMs = 0`，进度条暂时不能拖，点播放解析出时长后恢复。

**③ 播放页缓存状态与下载入口**

`PlayerViewModel` 把 `currentEpisode` 从普通字段升为 `MutableStateFlow`（对外 `StateFlow<Episode?>`，内部所有使用点改 `_currentEpisode.value`），新增 `cacheStates`（`cacheManager.states`）、`cacheStatuses`（`observeAll().map { key to Status }`）、`cacheCurrent()`、`cancelCache(key)`。

`PlayerScreen` 在 upName 下方插入 `CacheStateChip` 四态：未缓存 = Download + "加入缓存"；下载中 = 环形进度 / 不确定态 + "N% · 点击取消"（无总长时显示"排队中"或体积）；已缓存 = Check + "已缓存"（不可点）；失败 = Refresh + "下载失败 · 点击重试"。另加 `Map<String, T>.forEpisode(episode)` 扩展：精确 key 查不到时按 `Episode.bvidOfKey` 回退，否则从收藏夹（bvid 级 key）点进来永远显示"加入缓存"。

**④ 下载中断续传（Range）**

`CacheManager.downloadToTemp`：`resumeFrom = temp.length()`，有残片就 `header("Range", "bytes=$resumeFrom-")`；`resumed = response.code == 206 && resumeFrom > 0`、`totalBytes = resumeFrom + contentLength`（206 的 contentLength 只是剩余长度）、`downloaded` 从 `resumeFrom` 起算、非 206 先 `temp.delete()` 再从头来、`FileOutputStream(temp, resumed)` 追加写。

失败分支不再 `cleanupFiles`（那是取消/删除才做的事），改为读残片长度后记 `FAILED + downloadedBytes = tempSize`；`recoverStaleStates` 从"删 .part 记 FAILED"改成"只有 .part → 保留残片 + FAILED + message = 上次下载中断，点重试可续传"。

**⑤ 收藏 key 统一**

`FavoriteDao` 新增 `findKey(key)`（精确命中返回实际 key）与 `findKeyByBvid(bvid)`（稿件级回退，`ORDER BY addedAt DESC LIMIT 1`）；原来只有布尔判断的 `exists()` 已删（被 findKey 取代，留着就是孤儿）。

`PlayerViewModel.toggleFavorite()`：先 `findKey(episode.key) ?: findKeyByBvid(bvid)`，命中就删那一条；写入时尽量落到 cid 级 —— `realCid = episode.cid ?: playUrlRepository.firstCidOf(bvid)`，`key = Episode.videoKey(bvid, realCid)`，cid 实在拿不到才退回 `episode.key`。`refreshFavorite()` 也改成双查，从收藏夹（bvid 级）点进来显示实心。

**⑥ 设置页**

新增 `settings/SettingsStore.kt`（SharedPreferences `app_settings`，`defaultSpeed()` / `setDefaultSpeed()`，`SPEED_MIN=0.5 / SPEED_MAX=3.0` 做钳制）、`SettingsViewModel.kt`（缓存上限读写 `CacheManager.maxBytes`、倍速读写 SettingsStore、`cacheUsage` 只累加 `SUCCESS` 的 `totalBytes`、`historyCount` 观察 `observeRecent(500)`、`clearLocalHistory()`）、`SettingsScreen.kt`（缓存上限 512MB/1GB/2GB/4GB + "已用 X · 超出上限会自动删除最久未播放的缓存"、默认倍速 0.5x–3x、"本地播放历史 N 条 + 清空"确认弹层、底部说明队列恢复行为）。

`MainActivity` 的 `AppScreen` 加 `SETTINGS`（唯一新增路由），`HomeScreen` 顶栏在缓存图标旁加 `Icons.Default.Settings`（content-desc "设置"）。`PlayerViewModel.init` 里 `uiState.map { it.connected }.distinctUntilChanged().collect { if (connected) connection.setSpeed(SettingsStore.defaultSpeed(app)) }`，让偏好倍速在连接建立后立刻生效；在设置页选倍速时还会立即 `onApplySpeed` 作用到正在播的播放器。

**实测（模拟器 emulator-5554，AVD phase05-api35，1080×2340，未登录）**

- 顶栏三个图标一次命中：`缓存管理[689,229][755,295]`、`设置[821,229][887,295]`、`账号[953,229][1019,295]`；点 (854,262) 进设置页，dump 出"缓存上限 / 已用 59.5 MB / 512.0 MB · 1.0 GB · 2.0 GB · 4.0 GB / 默认倍速 / 0.5x–2x / 数据 / 本地播放历史 6 条记录 / 清空"。
- 设置点 (784,953) 选 1.5x → `run-as ... cat shared_prefs/app_settings.xml` 得到 `<float name="default_speed" value="1.5" />`；点 (117,217) 返回首页正常。
- 点本地历史首卡 (500,815) 播放成功，迷你条"黑马程序员 · 正在播放"，`shared_prefs/player_queue.xml` 写入 5 条 items + `<int name="index" value="0" />`。
- `am force-stop` + `am start` → logcat `PlayerService: 已恢复播放队列 6 集，定位第 1 集`；跳过登录后迷你条显示"Java入门-01-Java学习介绍 / 黑马程序员 · 已暂停"（恢复态不自动播，点播放才解析直链），全屏播放器能正常出时长 08:19、下一集/上一集、倍速行，缓存胶囊显示"已缓存"。
- 搜索 `Java` → 滚到列表底部出现"加载更多"[431,1983][649,2115]，点 (540,2049) 后出现第二页新条目（`JAVA自行车骑行顺畅操控灵活`），无崩溃。
- 首页 trailing 三态实测到"已缓存"、"缓存"（未缓存）、以及失败态 `content-desc="重试下载"`[898,1640][964,1706]。
- 续传：手工把 `v_BV1LYWR6kE2p_26034311385.m4a` 截成 5,242,880 字节的 `.m4a.part`、删掉完整文件、DB 改 `status='DOWNLOADING', downloadedBytes=5242880`，重启后缓存页显示 `下载失败 · 10.2 MB · 上次下载中断，点重试可续传`；点重试 → logcat `CacheManager: 续传 v_BV1LYWR6kE2p_26034311385，从 5242880 字节继续`，最终 `SUCCESS|10678760|10678760`，落盘文件正好 10,678,760 字节。
- 收藏 key：当前集（cid 级）点红心 → `favorite_episode` 得到 `v_BV17F411T7Ao_25761288170`；再点 → 行数回到 0；手工插一条 bvid 级 `v_BV17F411T7Ao` → 重进同一集播放页显示"取消收藏"（findKeyByBvid 命中），点它把这条 bvid 级记录删掉、行数 0、图标回空心。`PlayerScreen.kt` 的收藏按钮 `contentDescription` 顺手改成 `if (favorited) "取消收藏" else "收藏单集"`，否则 dump 取证看不出状态。
- 孤儿复核：`favoriteDao.exists` 已无调用点、方法已删；`QueueStore.clear()` 目前无调用点，作为换账号/退出登录时的清队列入保留（不删，避免以后再加回来）。

**自查（同日）修掉四处**：

1. **历史末页被当成"还有下一页"**。`HistoryCursor.max` 是非空字段、默认 0，原来的 `cursor?.max?.takeIf { it > 0L } ?: lastViewAt` 把"0 = 到底"误判成"游标缺失"，于是回退到 `lastViewAt`，`hasMore` 恒为 true —— 末页之后每点一次"加载更多"都会把最后一页再追加一遍（列表出现重复条目）。改成 cursor 存在就以它的原值为准、只有接口完全没给 cursor 才用 `view_at` 兜底。
2. **播放页缓存胶囊按 bvid 无条件回退**。`forEpisode` 原来在精确 key 未命中时直接找"任意同 bvid 的 key"，910 集的稿件只要有一集缓存，其它集全部显示"已缓存"且按钮不可点。改成 `cid > 0` 时只认精确 key，只有 cid 未知（key 本身是 `v_{bvid}`）才回退。
3. **416 死循环**。若上次数据已写完但 `renameTo` 之前进程被杀，残片长度等于完整资源，下次续传发 `Range: bytes=<total>-` 会拿到 416，之后每次重试都是 416、永远修不好。改成收到 416 时先删残片，再按"没有残片"从头下。
4. **历史翻页与刷新竞态**。`loadRemoteHistory()` 与 `loadMoreHistory()` 原来没有任何序号校验，翻页途中切 tab 触发一次刷新，旧页会追加到新列表（重复条目）。补了 `historyRequestId`，`loadRemoteHistory` 自增、两处回来都比对。

第 2 条的实测：删掉 `v_BV1fh411y7R8_287962381` 的缓存行与文件（同稿件另外 3 集仍是 SUCCESS），重启后从队列第 1 集点"下一集"到这一集，胶囊显示"加入缓存"；再点"下一集"到同稿件已缓存的 `0001_韩顺平Java_学习前的话`，显示"已缓存"，说明精确 key 命中没被改坏。第 1、3、4 条只有代码层修复，端到端需要登录态或构造 416，本轮未验。

**本轮未覆盖**：B 站历史与收藏夹的分页（模拟器未登录，接口要 cookie 才有数据）；"下载中环形进度"的截图取证 —— 模拟器本地网络太快（3.7 MB 在 0.3 秒内落完），UI 抓帧窗口小于一次 `uiautomator dump`，只能靠代码分支与 CacheScreen 同源状态推断，建议真机或大文件复验。

复验命令：

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"; $d="emulator-5554"
& $adb -s $d shell "run-as com.bilipod.app cat shared_prefs/player_queue.xml"
& $adb -s $d shell "run-as com.bilipod.app cat shared_prefs/app_settings.xml"
& $adb -s $d shell "run-as com.bilipod.app sqlite3 databases/bilipod.db 'select episodeKey, status, totalBytes, downloadedBytes from audio_cache;'"
& $adb -s $d shell "run-as com.bilipod.app sqlite3 databases/bilipod.db 'select episodeKey, bvid, cid from favorite_episode;'"
& $adb -s $d logcat -d | Select-String -Pattern '已恢复播放队列|续传'
```

### 第 13 条 首页结构对齐小宇宙：底部三档 + 发现流（2026-11-16）

设计判断：小宇宙底部是"发现 / 订阅 / 个人"。B 站没有等价的"订阅"数据源（关注列表要 cookie，更新流要逐个 UP 拉投稿、412 风险高），所以第二档改成"我的播客"，承接本地历史、B 站历史、收藏与缓存入口 —— 这是本项目唯一能真正填满内容的一档。

改动：

1. `Components.kt` 新增 `BottomTabBar`（Explore / LibraryMusic / Person，选中只换主色，不做指示条、不做阴影，`navigationBarsPadding()` 避让手势条）、`ProfileRow`（图标 + 标题 + 可选副文案 + AutoMirrored 右箭头）。删掉 `DateHeading` 与 `Formatters.monthDay / weekday`，发现流不再按日期分组，留着就是孤儿。
2. `HomeScreen.kt` 签名加 `homeTab: Int`，`var tab` 换成 `var myTab`；顶栏标题 / 副标题 / 图标按档切换（发现 = 设置 + 账号，我的播客 = 账号 + 缓存，个人 = 不放大标题）；搜索框只在发现档；搜索结果就地覆盖推荐流，靠 `inSearch`（`searched || searchedUsers`）分派，档位行右侧给了"取消"（`keyword=""` + `vm.clearSearch()`）；发现流改成"为你推荐"一个小标题 + 连续卡片流；新增 `ProfilePane`（头像 / 昵称 / 缓存管理 / 设置 / 扫码登录或退出登录 / 一行"非官方 B 站播客客户端"说明）。
3. `HomeViewModel.kt` 新增 `clearSearch()`：清 `searched / searchedUsers / searchResults / userResults` 并自增两个请求序号，让还在飞的旧请求回来时被丢弃。
4. `MainActivity.kt` 加 `var homeTab by remember { mutableIntStateOf(0) }`；首页结构改为"内容 → 迷你播放条 → 底栏"，缓存 / 设置这类二级页仍是"内容 → 迷你条"，底栏隐藏。

实测（AVD phase05-api35，1080×2340）：

- 发现页："为你推荐" + 首屏 4 张卡，点右侧圆键起播，迷你条出现并显示曲目（logcat `PlaybackState {state=BUFFERING(6)}` 之后进入播放）；
- 我的播客：本地历史落在本机历史（含已缓存对勾的条目），B 站历史未登录时提示"登录后同步 B 站历史"，收藏显示本地收藏空态；
- 个人页：头像 + 未登录 + 三条菜单，点"扫码登录"走原登录路由；
- 搜索：输入 `java` 点放大镜，结果就地替换推荐流，点"取消"回推荐流且搜索框清空；
- 底栏：三档点击切换正常。首版底栏被系统手势条压住、文字和导航条重叠，已用 `navigationBarsPadding()` 修掉。

截图：`screenshots/nav_discover2.png`、`nav_library.png`、`nav_remote_history.png`、`nav_fav.png`、`nav_profile2.png`、`nav_search2.png`、`nav_search_cancel.png`、`nav_play.png`。

遗留：发现流卡片间距随摘要行数变化，视觉上不够齐；`EpisodeCard` 的"已缓存"态仍是对勾，和小宇宙那种实心底图标还不一样；个人页没有做"关于 / 问题反馈"这类没实际功能的入口（故意不给假按钮）。

### 第 14 条 首页视觉对齐第二轮（2026-11-16）

起因：用户发来新版小宇宙首页截图说"不太像"。问题不在数据源，在版式和信息密度：

- 顶栏右侧放了设置和账号两个功能图标，把标题区切碎；小宇宙那里只有一个品牌标记；
- 没有分类 chips 和顶部大卡，页面从搜索框直接跳到列表，看起来发空；
- 卡片封面 56dp、标题 15sp，比小宇宙轻一档；
- 播放键是"浅底 + 主色三角"，小宇宙是实心圆；
- 迷你条是通栏，小宇宙是浮起的圆角卡片 + 环形进度按钮；
- 底栏图标是实心，小宇宙是线性。

顺手验证了一个新接口：`x/web-interface/ranking/v2?rid=3&type=all` 未登录直接返回 `code=0`、97 条，字段和热门榜完全一致（bvid / cid / pic / title / desc / duration / pubdate / tname / owner / stat）。所以分类 chips 做成了真筛选，不是摆着好看的假按钮。

改动：

1. `BiliApiService` 加 `ranking(rid, type)`，复用 `PopularData`；
2. `ContentRepository` 抽出 `toDiscovery(PopularItem)`，`popular()` 和新加的 `ranking(rid)` 共用这份映射；`ranking` 一次整榜、不分页，所以调用方按 `hasMore = false` 处理；
3. `DiscoveryState` 加 `rid` / `label`；`refreshDiscovery` 抽出 `fetchDiscovery`（推荐档走热门、分类档走分区榜），`applyDiscovery` 只认最新请求序号；`selectCategory(rid, label)` 换档时整个状态重置，避免旧榜条目闪一下；`loadMoreDiscovery` 加 `rid != 0` 守卫；
4. `Components`：`DiscoveryCard` 改 76dp 封面 / `titleMedium` 粗体 / 摘要与元信息并成一行灰字 / 44dp 实心主色圆键；新增 `FeaturedBanner`（榜一改成顶部大卡）、`CategoryChip`、`PlanetMark`（Canvas 画的带环行星，用来替代原来那两个功能图标）；`BottomTabBar` 图标换 `Icons.Outlined.*`；
5. `HomeScreen`：标题 32sp 负字距；发现档顶栏只留 `PlanetMark`（设置和账号都收进个人页）；搜索框放大镜移到左侧、右侧在有输入时显示"清空"；新增横滑分类 chips（"分类"展开全部 12 个分区，其余 chip 直接换榜）；发现流改成"榜一大卡 → 为你推荐 → 从第二条开始的连续列表"，播放队列含榜一所以位置是 `index + 1`；
6. `MiniPlayerBar` 重做成圆角卡片：方封面、两行文案、右侧环形进度圆键。`duration` 为 0（队列刚恢复、播放器还没准备完）时不显示 `00:00 / 00:00`，退回"UP 名 · 已暂停"。

实测：默认"推荐"档上热门榜；点"音乐"chip 后 banner 变"音乐榜 1 田一名 sir"、小标题变"音乐热门"、列表换成音乐分区榜（接口和状态切换都通）；点"分类"可展开全部 12 个分区；点卡、点圆键、底栏切换均正常。截图 `screenshots/ui_v3_home.png`、`ui_v5_music.png`、`ui_v6_home.png`。

未做（留给用户拍板）：品牌色。小宇宙是青绿，本项目沿用暖橙 `#C17B52`。结构已经对齐，但"像不像"的最后一公里就是这个色值；要换只改 `Theme.kt` 的 `primary` / `primaryContainer`，全站跟随。

### 第 15 条 配色切到小宇宙的青绿（2026-11-16）

起因：上一轮结构对齐之后，用户追问"色调呢"。先用 PIL 从小宇宙截图里采样：`为你推荐` 标题和底栏选中图标的像素中位落在 `#2AB6E1` / `#74C2D3`（受截图压缩影响偏亮），页面底色是 `#F2F2F7` 的近白冷灰，不是暖白。这里有个必须一起做的判断：青绿配原来的米白 `#FAF7F2` 会发脏，所以不能只换 `primary`，底色必须同步去掉暖调。

改动全部集中在色板，组件层零改动（说明之前所有组件都只引用语义色，没有硬编码暖橙）：

- 主色 `#C17B52` → `#16B4AA`；容器 `#F4E5D9` → `#D6F2EF`；容器文字 `#6A3E23` → `#05403C`
- 页面底色 `#FAF7F2` → `#F6F8F8`；卡片 `#FCFAF6` → 纯白；surfaceVariant `#F1ECE4` → `#EFF3F2`；主文字 `#2B2A27` → `#1B1F1F`；次要文字 `#7B756C` → `#74807E`；描边与分割线一起冷化
- secondary / tertiary 从暖灰、暖灰绿换成冷灰绿，避免和青绿打架
- 深色模式同步：主色 `#5FD3C7`、背景 `#131616`、卡片 `#1B1F1F`
- `values/themes.xml` 与 `values-night/themes.xml` 的 `windowBackground` / `statusBarColor` / `navigationBarColor` 跟着改，否则冷启动会闪一下旧的米白
- 变量名 `OrangeLight` / `OrangeDark` → `BrandLight` / `BrandDark`，不留名不副实的标识

顺手修的：缓存 / 设置这类二级页的迷你条原来没有 bottom inset，改成圆角卡片之后会和手势条重叠，补了 `navigationBarsPadding()`。

实测：首页、我的播客、个人、缓存四页都跟着变，缓存页的容量选中块和播放器进度都是新主色。截图 `screenshots/ui_teal_home.png`、`ui_teal_library.png`、`ui_teal_profile.png`、`ui_teal_cache2.png`。

回退方式：把 `Theme.kt` 的 `BrandLight` 改回 `#C17B52`、`BrandContainerLight` 改回 `#F4E5D9`、`BackgroundLight` 改回 `#FAF7F2`、`SurfaceLight` 改回 `#FCFAF6` 即可，其余原值见本轮记录。

### 第 16 条 个人页菜单补齐（2026-11-16）

起因：用户发来小宇宙个人页完整菜单截图（个人主页 / 创作中心 / 我的通知 / 收听历史 / 我的收藏 / 我的赞赏 / 付费账户 + 分割线 + 设置 / 问题反馈），说"这些功能补齐"。这一条的重点不是照着截图逐行抄，而是先判断每一行背后有没有真实数据源。

分两类处理。第一类是能落到真实页面的，直接接上：个人主页走已有的 `spaceVideos` 接口（`openMySpace()`，未登录返回 false 交给 UI 跳登录页，不在这里弹红字）；收听历史、我的收藏不新建页面，改成把"我的播客"的一级 tab 和二级分栏一起切过去；缓存管理和设置是原有二级页；问题反馈改成弹层，把版本号、包名、机型、Android 版本摆出来并支持一键复制。第二类是本项目没有数据源的——创作中心（B 站创作中心是投稿后台，我们只读不写）、我的通知（要 msgfeed 系接口，且登录态才能拉）、我的赞赏、付费账户——按用户一贯排斥假按钮的偏好，宁可不做：点了没反应的菜单行比少一行更伤。

为了让个人页能"一次改两档"，`myTab` 从 `HomeScreen` 内部状态上提到 `MainActivity` 的 `libraryTab`，`HomeScreen` 相应新增 `libraryTab` / `onLibraryTabChange` / `onHomeTabChange` 三个参数。这是本条唯一跨文件的签名改动，收益是入口逻辑只有一处，不会出现"一级切了二级没切，停在本地历史"的错位。

顺手修的和顺带改的：

- 头像本身变成登录入口（未登录跳扫码，已登录弹退出确认），和第 8 行的账号行共用同一个回调
- 菜单行从 3 行变 8 行，小屏或大字号会顶出屏幕，给 `ProfilePane` 加了 `verticalScroll`
- 原来 `ProfilePane` 外层 20dp + `ProfileRow` 内层 20dp 是双重缩进，去掉外层，菜单图标回到 20dp 对齐

实测：`screenshots/profile_menu.png`（8 行菜单 + 分组分割线）、`profile_to_history.png`（点收听历史 → 一级切我的播客、二级停在 B站历史、给出登录提示）、`profile_feedback.png`（反馈弹层读到真实的 0.1.0 / com.bilipod.app / sdk_gphone64_x86_64 / Android 15 API 35）、`profile_myspace_guest.png`（未登录点个人主页 → 扫码页）。

仍然缺的：顶部会员横幅没做。本项目不含任何付费逻辑（README 开头就写着），做一条"开通会员"只会是点进死路的假入口，所以这一条只保留菜单，不画横幅。

### 第 17 条 登录方式补齐：扫码 / 账号密码 / 短信验证码（2026-11-16）

起因：用户要求补齐密码登录和短信验证码登录，登录接口可参考 BiliPai。中途两次改口，最后拍板：**不要内嵌官方登录页，要原生表单 + 自建极验**。

先把接口那关算清楚。密码登录 `POST passport.bilibili.com/x/passport-login/web/login` 的 `token`、`challenge`、`validate`、`seccode` 四个参数全是必填，后两个只能由极验产出；短信登录 `x/passport-login/web/sms/send` 返回的 `captcha_key` 同样要先过极验。所以想用自绘表单，就必须把极验 SDK 塞进 App。

第一版（已废弃，代码留着当兜底）：把官方登录页嵌进 WebView（`login/WebLoginPane.kt`），页面自己拉极验，App 只搬 Cookie。实测可用，但用户嫌"挤一起了"，排版调过一轮后改口要原生表单。

最终实现分五块：

- `biliapi/AuthApiService.kt`：7 个接口——`qrGenerate` / `qrPoll` / `captcha` / `webKey` / `passwordLogin` / `smsSend` / `smsLogin`，全部 `@FormUrlEncoded`。
- `common/BiliRsa.kt`：密码加密。`web/key` 给的 `key` 是 PKCS8 的 1024 位公钥，剥 PEM 头尾 → `X509EncodedKeySpec` → `RSA/ECB/PKCS1Padding` 加密 `hash + 明文密码` → Base64 NO_WRAP。
- `login/NativeLoginViewModel.kt`：`LoginMode{SMS,PASSWORD}` + `CaptchaPurpose{SEND_SMS,PASSWORD_LOGIN}` 状态机、60 秒倒计时、`confirmLogin` 用 nav 接口复核登录态。
- `login/NativeLoginPane.kt`：自绘 `LoginField`（标签固定宽 52dp + BasicTextField + 圆角 14dp）、`ModeTabs`、主色实心 `PrimaryButton`、`imePadding`。
- `login/GeetestDialog.kt` + `assets/geetest.html`：WebView 承载极验 V3，结果走 JS Bridge 回传。

极验为什么能自建：`GET x/passport-login/captcha?source=main_web` 返回 V3 的 `gt` + `challenge`（gt 固定 `ac597a4506fee079629df5d8b66dd4fe`）；拿这对参数去 `api.geetest.com/gettype.php` 和 `get.php` 请求，带不带 `Referer` 都返回 success，即不校验来源域名，官方 `gt.js` 可以在本地页面里跑通。

自建极验在 WebView 里踩了四个坑，全部有 logcat 证据：

1. `product:'popup'` + 只调 `show()` → 页面全白，body 子节点恒为 2，极验一个节点都没插。改 `product:'float'` + 先 `appendTo(document.body)` + 自动点一次 `.geetest_radar_tip`，验证框才渲染出来。
2. `body` 上写 `display:flex; align-items:center` 会让极验的绝对定位层高度变 0，表现是"ready 了但空白"。改成 `#tip` 单独绝对定位居中，body 不做 flex 容器。
3. `html,body{position:fixed}` 会让极验的回调不再触发（页面卡在"正在加载人机验证…"），已回退。
4. 页面能滚动时触摸坐标和渲染位置错开。锁 `overflow:hidden` + 关 `setSupportZoom / builtInZoomControls / useWideViewPort / loadWithOverviewMode` + `overScrollMode=NEVER` + 外层 `Box(weight(1f))` 包 `AndroidView(fillMaxSize())` 之后，页面 viewport 稳定为 393×682，实测物理 (540,1050) 精确对应页面 (196,272)，坐标关系是线性的。

极验自己的"确认"按钮落点会被 WebView 的坐标换算吃掉一截，物理点 (750,1388) 实际落在图片内。这里走过两条解法：第一版在面板底部加**原生"提交验证"按钮**，点它走 `evaluateJavascript("window.commitCaptcha()")` 去触发 `.geetest_commit.click()`（该方案已被第二轮去掉，见下方"三次修改"）。页面侧 `onSuccess` 加了 `reported` 标志只回传一次——之前因为 `AndroidView` 的 factory 里用 `mutableStateOf` 持有 WebView，赋值触发重组 → WebView 重建 → 页面重载 → 极验再次回调，`onResult` 一次验证刷了 20+ 条日志。

错误码翻译写在 `AuthRepository.loginErrorText()`，覆盖 -105 / -629 / -653 / -662 / -504 / -2100 / 2400 / 2406 / 86000 / 86203。

实测（模拟器 emulator-5556，1080×2340 / density 2.75）：原生表单渲染正常、切 tab 正常、手机号可输入、"获取验证码"能拉起极验、验证框居中、点选可点、点错了会自动换题、**成功一次**时 `onResult` 拿到 `challenge / validate / seccode`（logcat 有 `result: challenge=a53b875d validate=0a60a7d3`）。

尚未验证：真正的登录成功路径。手上没有可用账号，手机号是假的（13800138000），所以"验证码通过 → 短信发出 → 填码登录 → Cookie 落地 → nav 认账"这一整段只做过接口参数核对，需要真机 + 真实账号试一次。

`WebLoginPane` 保留为兜底入口，文案"密码或极验一直不过？改用官方页面登录"。

**2026-11-16 二次修改：极验从二级页改成悬浮卡片。** 用户实测时卡在验证页出不来（验证通过后 WebView 白屏，对话框不关）。根因有两个：`NativeLoginPane` 原来用 `state.captcha?.let { ...; return }` 整页替换掉表单，一旦极验停在白屏就没有"返回"这条退路；`GeetestBridge.onResult` 也没在 Kotlin 侧去重，极验把同一份 validate 连打二十几次，`AndroidView` 反复重建又让页面重载，形成回调风暴。

改法：`GeetestDialog` 改名 `GeetestOverlay`，变成"半透明遮罩 + 居中卡片（94% 宽、560dp 高、18dp 圆角）"，叠在登录表单之上，表单始终存在；`state.captcha` 变 null 卡片就消失，不需要返回。`GeetestBridge` 加 `AtomicBoolean` 去重；`NativeLoginViewModel.onCaptchaResult` 补了 `Log.d("NativeLogin", ...)`，方便以后看回调有没有到。另外拉起验证时用 `LocalSoftwareKeyboardController` 收起键盘，否则输入法会压住卡片下半截。

实测：卡片居中、背后能看到登录页（灰蒙层下的"登录"和副标题）、点"取消"（真实 bounds 877–1009 × 423–555）干净退回表单且手机号还在、点错自动换题、原生"提交验证"能触发 `native commit`、`commit result=true`。

截图：`screenshots/native_geetest_overlay2.png`（悬浮卡片）、`native_geetest_overlay5.png`（取消回表单）。

**2026-11-16 三次修改：去掉原生"提交验证"，改由极验自己的"确认"自动提交。** 用户不想要一个额外按钮，"验证后点确认自动跳转"。做法是页面侧给 `.geetest_commit` 叠一层 `position:fixed` 的透明热区（`ensureCommitPad`，每 400ms 重算 rect，点击时 `stopPropagation` 并代点真按钮），Kotlin 侧把"提交验证"按钮、`window.commitCaptcha`、以及只为按钮服务的 `webViewHolder` 全部删掉，底部只剩一行提示"点完图上的字，再点页面里的"确认"就会自动提交"。

热区尺寸是量出来的，不是猜的。临时加 `document.addEventListener('click', ..., true)` 打日志对照，拿到这组数：卡片内 WebView 的 viewport 是 **341×464 CSS**（WebView 物理高 1231 → 换算比例 2.653，而横向是 2.748，纵向被压了约 3.5%，这就是"点偏"的物理来源）；物理点 (800,1450) 落到页面 **(264,321)**，而 `.geetest_commit` 的 rect.top 是 383.8，差 2.8~22.8 CSS px。最终取 `PAD_TOP=80 / PAD_BOTTOM=40`，热区 y 范围 303.8~460.5，第一次完整命中，logcat 出现 `commit pad hit -> click btn`，随后是按钮自己的 click（client 报 0,0）。热区上沿 303.8 仍落在图片和按钮之间的空白，不会挡住图上的字。

卡片高度也试过 680dp：极验面板宽度由卡片宽度决定，加高不会让图变大，只会多出一片灰，已回退 560dp。

截图：`native_geetest_pad1.png`（已无原生按钮）、`native_geetest_pad4.png`（热区命中后换题）、`native_geetest_pad6.png`（680dp 灰边过多）。

**2026-11-16 四次修改：拆掉自绘卡片，只留极验面板本身；顺带挖出一个致命桥接 bug。** 用户看截图提要求："验证界面能不能仅保留图片里的内容，外面的那个框不要"。于是 `GeetestOverlay` 不再画卡片，只保留一层 32% 黑色遮罩，WebView 改成**全屏 + 透明背景**（`setBackgroundColor(0x00000000)`，页面 `html,body{background:transparent}`），极验面板直接浮在遮罩上。同时删掉标题行、"取消"、进度条和底部提示——退出改由"点面板以外任意处"和系统返回键承担（`BackHandler(enabled = true) { onCancel() }`）。页面侧 `initGeetest` 的宽度从 `'100%'` 改成 `Math.round(clientWidth * 0.92)`，否则面板会贴满整屏。实测面板体现在 `.geetest_popup_box`（66,259 261x334），中心 426 CSS ≈ 屏高中心 425.5，极验自己就是居中的，不用我们挪。

点空白关闭这条一开始不成立，暴露了真正的问题：**`GeetestBridge` 的回调属性名和 `@JavascriptInterface` 方法同名**（都是 `onResult / onError / onClose`）。Kotlin 在成员方法体内解析 `onClose()` 时优先匹配成员函数，于是 `mainHandler.post { onClose() }` 是在调用它自己，post 无限递归、上层一个回调也收不到。现象很有欺骗性：点空白后极验面板自己 hide 了，我们的遮罩却留着不动，截图看是"整屏发灰退不出去"；logcat 里同一毫秒刷几十条 `bridge onClose` 全是主线程。解法是把三个属性改名 `onResultCb / onErrorCb / onCloseCb`。

这条大概率也是历史上"验证通过了却卡在验证页"和"一次验证刷 20+ 条 result 日志"的一部分原因——之前归因于 `AndroidView` 重建，其实重建只是放大器。修完日志就很干净：`bridge onClose`（JS 线程）→ `cancelCaptcha -> captcha=null`（主线程）→ `overlay disposed`，遮罩与 WebView 一起消失。

另外页面里的"点面板外取消"必须只认 `e.isTrusted` 的真手指事件：极验初始化时自己会派发合成 click，我们的自动点 `.geetest_radar_tip` 也是合成事件，不拦的话面板一打开就被自己关掉（实测一次初始化触发 12 次误关闭）。

截图：`geetest_bare4.png`（只剩极验面板，外无任何自绘框）、`geetest_bare_close6.png`（点空白后干净回表单、手机号保留）。

**2026-11-16 五次修改：验证通过了却收不到短信——onClose 抢跑，把 validate 丢掉了。** 用户反馈"验证通过后没有验证码短信"。抓 logcat 一看，时序很说明问题：`commit pad hit -> click btn`（点确认提交）→ 0.55 秒后 `bridge onClose` → `cancelCaptcha -> captcha=null` → `overlay disposed` → **再过 2 秒**才 `result: challenge=a18178ad validate=0203435b`，此时 `onCaptchaResult` 打出 `dropped: 面板已关闭`。也就是说极验在提交成功时会先把自己的弹层收起来、触发 onClose，然后才异步回调 onSuccess；我们一收到 onClose 就清 captcha，等真结果到了面板已经没了、`CaptchaChallenge`（含 token/challenge）也被丢掉，`doSendSms` 根本没机会执行。

修法是把"极验自己收摊"和"用户主动关闭"分开：页面里用 `lastCloseTapAt` 记录用户是否刚点过面板上的 ×，命中就算主动关闭走 `AndroidBridge.onClose()` 立即收；其余走 `AndroidBridge.onSoftClose()`，Kotlin 侧 `pendingSoftClose` 挂一个 4 秒延时，`onResult` 一到就把令牌打掉（`softClose skipped (result arrived)`）。

修完的完整日志：`captcha onClose userClosed=false` → `bridge softClose` → 0.86 秒后 `result: challenge=05c9f8a0 validate=aae74cf6` → `onCaptchaResult purpose=SEND_SMS hasCaptcha=true` → `sendSms ok, captcha_key=3a5aead7`，界面显示"验证码已发送，注意查收"并开始 60 秒倒计时。短信接口 `x/passport-login/web/sms/send` 返回成功、`data.captcha_key` 拿到了，说明参数拼装（cid=86 / tel / source / token / challenge / validate / seccode）全部正确。

短信没进手机是号码的问题：测试用的 `13800138000` 不是真实在用号码，B 站接受了请求并下发 captcha_key，但真实短信当然发不到那个号。换真实手机号即可。

截图：`sms_dbg4.png`（验证通过后回表单，"验证码已发送" + 52s 倒计时）。

### 第 18 条 收听历史 / 我的收藏 的 B 站同步开关（2026-11-16）

起因是个人页那两行的副标题写死了"本机 + B 站"：关掉同步没法表达，登录了也默认去拉 B 站数据。做成两档选择，位置埋在设置页而不是列表页头——这两项的数据来源是账号级的，放页头会变成两条各自独立的开关，用户还得记两遍。

`settings/SettingsStore.kt` 里加了 `sync_history` / `sync_favorites` 两个布尔（默认 `true`，保持老行为），除了 SharedPreferences 还挂了一份 `MutableStateFlow`：设置页改完首页要立刻跟着变，只落盘的话得等下次重建 ViewModel 才生效。

`home/HomeViewModel.kt` 的 `refresh()` 里改成按开关决定是否 `loadFavFolders()` / `loadRemoteHistory()`；两个 flow 在构造时 collect，关掉就把 `remoteHistory` / `favFolders` / `selectedFolderId` / `folderEpisodes` 一起清掉。只做这一半会留个坑：开关从 false 切回 true 时没有任何东西触发重新拉取，用户得杀进程重进才看得到 B 站历史，所以在 collect 里比对上一次的值、只在"false → true"这一跳补一次拉取（收藏夹还要 mid，没拿到就走完整 `refresh()`）。

再一个是飞行中请求的回落：关掉开关那一刻可能有请求还在路上，回来照样会往 state 里写。四个远端入口（`loadRemoteHistory` / `loadFavFolders` / `selectFolder` / `loadMoreFolder`）的成功分支都加了"开关已关就丢弃"的守卫，`loadMoreFolder` 靠 `selectedFolderId` 被清空天然挡住。

UI 三处：设置页"与 B 站同步"下面两行 chip（本机 + B 站 / 仅本机，复用 `QuietChip`）；个人页副标题跟着变"仅本机"；B站历史空态改成"已关闭与 B 站的同步"，收藏栏关掉时不渲染收藏夹 chip 只留"仅显示本机收藏"——留个点不动的夹子按钮比不显示更糟。

实测（真机 A1）：默认两档都是"本机 + B 站"；切到"仅本机"后个人页两行立刻变"仅本机"，B站历史栏显示关闭提示、收藏栏只剩本地收藏；重启 App 后开关仍是"仅本机"，说明落盘生效；再切回"本机 + B 站"，不用重启就直接把 B 站历史和收藏夹（默认收藏夹 / 设计 / 摩擦音 / 鹅毛棒）拉了回来。

截图：`sync_3_settings.png`（设置页两行开关）、`sync_5_profile_local.png`（副标题变"仅本机"）、`sync_6_bili_history_off.png`、`sync_7_fav_local.png`、`sync_9_bili_history_on.png`。

### 第 20 条 登录态"太快失效"的两个根因（2026-11-16）

现象是登录完没多久又要重新登录。查下来不是 B 站那边踢人，是本地的 Cookie 容器自己把登录凭证删了，有两条链路。

第一条：`SecureCookieStore.put()` 手动塞进去的 Cookie 没有设过期时间，在 OkHttp 里就是"会话 Cookie"，`expiresAt` 等于 `Long.MIN_VALUE`。而 `loadForRequest` 里写的是 `filter { it.expiresAt < now }`——对会话 Cookie 这个条件恒为真，于是每发一次请求就把它们当过期删掉，还顺手 persist 把磁盘上的密文也清了。网页登录回填的 `SESSDATA / bili_jct / DedeUserID` 走的正是这条路，登录成功后第一发 nav 请求就把自己删干净了，表现为"扫码成功但立刻又是未登录"。

第二条：`buvid4` 也是 put 进来的，同样被删。下次启动 `ensureBuvid()` 发现 buvid4 缺失，就去请求一次首页，首页的 Set-Cookie 把 `buvid3` 覆盖成新值——设备指纹一变，服务端会把原来登录用的 `SESSDATA` 判成异常设备。这条链路让扫码登录、短信登录也一起掉。

修了四处：过期判定排除会话 Cookie（`it.expiresAt != Long.MIN_VALUE && it.expiresAt < now`）；`put()` 显式写一年有效期，让回填的凭证能落盘；`ensureBuvid()` 在已有 buvid3 时不再走首页，缺 buvid4 只单独用 spi 接口补，绝不重刷 buvid3；`isLoggedIn()` 除了判断 SESSDATA 存在，还看真实过期时间。

还加了诊断日志（只在可调试包输出）：每次 Set-Cookie、put、restore 都打一行 cookie 名单和剩余有效天数，`adb logcat -s SecureCookieStore:D` 能看到。冷启动验证：`restore → buvid3(998天), b_nut(363天), buvid4(2912123天)`，且没有再出现 Set-Cookie 记录，说明指纹没被重刷。

剩下的硬限制：B 站 `SESSDATA` 服务端约 30 天，想突破得做 cookie refresh（`x/passport-login/web/cookie/info` + `refresh`，refresh_csrf 要用 RSA 加密 timestamp），目前没做。

### 第 19 条 播放页按小宇宙重排 + 我的播客清空历史（2026-11-16）

播放页原来是一张"圆形封面 + 滑块 + 一排 chip"的设置面板气质，这次按参考图重排：顶栏（收起 / 队列位置胶囊 / 定时 / 收藏）→ 方形大封面 → 标题 → 播客名 + 收藏胶囊 → 时间 + 波形进度 → 控制行（倍速 / -15s / 播放 / +30s / 缓存）。倍速和定时的 chip 从页面上收走，改成点圆形按钮弹浮层，否则页面上永远铺着两排小胶囊，参考图那种"大封面 + 留白"的节奏出不来。

整页背景不是一块深色，而是当前封面放大后模糊铺满（`Modifier.blur(42.dp)` + `BlurredEdgeTreatment.Unbounded`），再压一层自上而下加深的黑色渐变（0.42 / 0.54 / 0.68），内容浮在上面。那层遮罩不是装饰：遇到浅色封面，白字和未播波形不压暗就彻底看不见。遮罩浓度试了三版——0.30/0.56/0.74 把封面色压没了，只剩一片黑；0.20/0.40/0.62 在亮封面上白字发飘、未播波形近乎消失；最后取中间值，能看见封面的色块（天空的蓝、墙的红都能透出来）但字压得住。顺带把 `Modifier.blur` 的局限写进注释：Android 12 以下不生效，靠遮罩兜底仍然可读，不会出现一块刺眼的清晰大图。播放页展开时状态栏图标也跟着反白（`WindowCompat.getInsetsController`），收起时在 `onDispose` 里恢复，否则黑图标压在深色背景上根本看不见。

几个决定：中间那颗胶囊（参考图是"28 人在听"）没有对应的真实数据，换成队列位置"第 3 / 12 集"，单集时写"单集"，不编人数；参考图里"+ 订阅"换成"+ 收藏"，评论和播放列表没做——队列面板还没写，摆上去就是点了不响的假按钮；点赞当时也一起省了，到第 21 条补成了真实接口。

波形条是自绘的：58 根圆角竖条，高度由 `barFraction(i)`（正弦 + 小数位提取）算出来，同一集每次重绘都一样，不能每帧随机否则整条在抖。外层是一个 54dp 高的圆角矩形容器（白色 7% 底），里面已播柱用品牌青、未播柱用白 22%，进度位置竖一根 2dp 的白线当光标——容器 + 光标的形制是照参考图做的。点一下定位、按住横向拖也能拖，两个手势各挂一个 `pointerInput` 且挂在 padding 之后，这样拿到的坐标直接就是波形区的坐标，不用自己减边距。

播放键按参考图二的形制：圆形浅底 + 实心三角/竖杠，底色用 `primary 12%`，没有跟随参考图的蓝色，整套配色仍是青绿。倍速按钮同款圆形，显示 `1.5x` 这种带一位小数的倍率。

三段弹性留白按 0.28 / 0.34 / 0.38 分（顶部 / 封面与进度之间 / 底部）。第一版把空白全给了中间，结果进度条贴到屏幕最下沿、控制区快压到手势条上。

"清空历史"挂在"我的播客"的二级分栏行右侧，只在"本地历史"这档出现（B站历史在服务端，清不了）。点了走二次确认，文案里写明"B 站账号里的观看记录在服务端，这里动不到"。没有记录时按 alpha 0.3 灰掉并禁用点击。

实测：点封面进详情 → 播一集 → 迷你条展开；点波形 x=780（54.8% 位置）落到 03:48（时长 06:51，误差在 1% 内）；横拖 300→900 落到 04:24；+30s 后从 04:24 走到 05:14（含 1.5 倍速播放的时间）；倍速浮层选 1.5x 后按钮文案跟着变；定时浮层四个档位正常；清空历史确认后列表变"还没有播放记录"、按钮灰掉。

截图：`player_final.png`（播放页终版）、`speed_sheet.png`（倍速浮层）、`sleep_sheet.png`（定时浮层）、`clear_history_dialog.png`、`clear_history_done.png`。

### 第 21 条 控制行两端换功能 + 播放设置面板（2026-11-16）

用户拿三张图提了两个要求：底部控制行最左改成设置（齿轮）、最右改成点赞（大拇指），齿轮点开的面板照小宇宙的播放设置做。

控制行现在是 `齿轮 / ⟲15 / 播放 / 30⟳ / 大拇指`。左端原来的倍速圆钮（`SpeedButton`）和右端缓存下载图标（`CacheIconButton`）一起删掉——缓存状态胶囊 `CacheStateChip` 还在标题下方那行，缓存入口没丢。`⟲15` / `30⟳` 补了参考图里那对端点箭头：`SeekButton` 在弧的起点按切线方向画两条 5dp 短线，+30s 起角 -50°（箭头落在右上）、-15s 起角 230°（箭头落在左上），缺口正好对上参考图。中间播放键没跟着参考图改成"纯白无底大三角"，保留上一轮已经定下的"半透明白圆底 + 青绿实心图标"。

设置面板是新写的 `PlayerSettingsSheet`，替代并删除了旧的 `SpeedSheet` / `SleepSheet`（顺带清掉变成孤儿的 `SpeedButton`、`CacheIconButton` 和 PlayerScreen 里那份没人用的 `formatSpeed`）。深色 Dialog，`#1B1F1F` 底、24dp 圆角，里面两张 `#262B2B` 的卡片：

倍速卡：标题左 + 当前值右（青色 `1.5x`），中间一条 8 档刻度尺（0.5 / 0.75 / 1.0 / 1.25 / 1.5 / 2.0 / 2.5 / 3.0），下面是"只应用到当前节目"开关。睡眠卡：同样形态，5 档（关 / 15 / 30 / 45 / 60），下面是"定时到本集结束"开关。刻度尺 `ScaleSelector` 每格一根竖线，未选 1.5×16dp 白 35%、选中 2.5×26dp 青，标签跟着变青——就是参考图那种"尺子"的意思。用户点名要删的"跳过空白""人声加强 2.0"没有做，本机没有对应音频处理能力。

两个开关都有真语义，不是摆设。"只应用到当前节目"开着只改本次播放，关掉同时把倍速写进 `SettingsStore.setDefaultSpeed`（`setSpeedFromPanel(speed, onlyCurrent)`）；"定时到本集结束"和分钟档互斥（`setSleepAtEnd` 会清 `sleepRemaining`，反向亦然），靠 `init` 里 collect `uiState`，`durationMs > 0 && positionMs >= durationMs - 1500` 时暂停并提示"已到本集结尾，自动暂停"。

点赞是真实链路。`BiliApiService` 加了 `POST x/web-interface/archive/like`（`bvid` / `like` 1 赞 2 取消 / `csrf`），`VideoViewData` 补了 `stat` 与 `req_user`，`PlayerViewModel` 加了 `liked` / `toggleLike()` / `refreshLiked(bvid)`（带 `likedCache`，切集才查一次 view 接口）。

这轮踩了三个坑，都写下来：

**面板里睡眠刻度一选就错位。** 刚点完 30 分，下一次重组时原来的判定 `abs(it * 60_000L - sleepRemaining) < 2_000L` 匹配不上——倒计时每秒在减，29:58 与 30:00 差 2000ms，不满足严格小于，刻度错亮到"关"。改成把剩余毫秒向上取整成分钟数再对档（`((remaining + 59_999) / 60_000)`），29:58 照样落在 30 那一格。这类"拿当前值去匹配档位"的地方，容差要按刻度的最小单位算，不能按秒算。

**点赞接口写成了 `x/v2/view/like`，返回 404。** 现象是点下去提示"点赞失败：网络异常"，很容易误判成没登录或者被风控。正确路径是 `x/web-interface/archive/like`，而且参数叫 `like`（1/2）不是 `action`，也没有 `type`。定位方法就是在 `runCatching` 的失败分支里 `Log.w` 打出异常，`adb logcat -s PlayerViewModel:W` 一眼看到 `retrofit2.HttpException: HTTP 404`。

**只判 `bili_jct` 非空会误报。** 退出登录后 csrf 不一定被清，但 SESSDATA 已过期，请求出去只会拿 -101，被归类成"网络异常"。`toggleLike()` 和 `refreshLiked()` 都改成 `csrf 为空 || !cookieStore.isLoggedIn()` 才算没登录，提示"登录 B 站账号后才能点赞"。

**"只应用到当前节目"原来只是半个功能。** 第一版把开关做成了面板里的局部 `remember` 状态，两个毛病：每次重开面板都重置成"关"，看着像设置没保存；更关键的是 Media3 的倍速是 player 级的、不随 media item 变，切集后倍速还挂着上一集的 2.0x，所谓"只应用到当前节目"实际变成了"只应用到本次播放会话"。修法是把开关状态搬到 ViewModel（`speedOnlyCurrent: StateFlow<Boolean>` + `setSpeedOnlyCurrent`），面板直接绑这一份、不再自己存副本；`setSpeedFromPanel(speed)` 只读这个标记决定要不要写 `setDefaultSpeed`；同时记一个 `pinnedSpeedKey`（当时那一集的 key），切集监听里发现 key 变了就把倍速退回 `SettingsStore.defaultSpeed` 并把开关置回"关"。

实测（真机 A1）：控制行渲染为 齿轮 / 带箭头⟲15 / 播放 / 带箭头30⟳ / 大拇指；点齿轮弹面板，倍速点 1.5 后右上值变 `1.5x`、刻度选中 1.5；睡眠点 30 分后右上倒计时 `29:58`、刻度停在 30（修复前会错亮到"关"）、底部提示"还剩 29:58 自动暂停"、"定时到本集结束"打开后刻度回"关"、右上变"本集结束"、底部变"本集播完就暂停"、顶栏定时图标亮青；点大拇指后图标变青色实心、提示"已点赞"，再点一次恢复空心、提示"已取消点赞"，说明 `archive/like` 的 1/2 两条分支都通了。

**顶栏右侧换成投币 + 收藏（同日追加）。** 原来那两颗是定时和收藏，定时本来也只是"打开设置面板"的另一个入口，睡眠设置还留在齿轮里，所以直接换掉不丢功能。投币走 `POST x/web-interface/coin/add`（`bvid` / `multiply` 1~2 / `csrf`），面板给 1 枚、2 枚两档；"这一集投过没有"不靠本地猜，`req_user.coin` 和 `req_user.like` 是同一次 view 接口返回的，所以 `refreshLiked` 里一并读出来缓存，投过的集图标直接是青的、再点进去文案变"这一集已经投过币了"且两个档位禁用。

硬币图标没直接用 material 的 `Paid`——那个画出来是个美元符号，摆在播客播放页里像在标价。当时改成自绘：一个圆环加中间一竖，投过之后整体变青（后来统一 icon 库时又换回了 `Paid`，见下）。

实测（模拟器 emulator-5556，登录态）：点顶栏硬币 → 面板弹出 → 投 1 枚 → 请求成功、图标转青、提示"已投 1 枚硬币"；再开面板显示"这一集已经投过币了"、两档灰掉不可点。错误分支按 B 站常见码分开提示：-101/-111 登录失效、-104 硬币不足、-103 已经投过。

**自绘图标统一改成 icon 库（同日追加）。** 之前为了贴合参考图自己 `Canvas` 画了四个东西，全部换成 material-icons-extended：

- 播放页的 `⟲15 / 30⟳`：库里有 `Replay5 / Replay10 / Replay30` 和对应的 Forward，但没有 15 秒那支。所以底图统一用 `Icons.Outlined.Replay`，中间叠一个数字；+30 那边整个图标 `rotate(180f)` 就变成顺时针的箭头，缺口位置也对。数字和圆环的比例反复调过：34dp 图标配 9sp 数字时数字直接压到弧线上糊成一团，最后落在 42dp 图标 + 11sp 数字 + 上移 1.5dp。中央播放键的三角顺带放大到 44dp，因为参考图里三角明显比两侧的圆环大一号。
- 硬币：`Icons.Outlined.Paid`（圆环 + 一竖一撇，最接近硬币的一支；`MonetizationOn` 是圆里一个 $）。
- 首页顶栏那颗土星（`PlanetMark`）：换成 `Icons.Outlined.Public`，一个青色地球，语义还贴"发现"。
- 迷你播放条上的环形进度：换成 M3 的 `CircularProgressIndicator`（带 `trackColor` / `strokeCap`），不再自己 `drawCircle` + `drawArc`。

唯一没换的是播放页那条波形——它是进度可视化，不是图标，58 根柱子表示音频振幅、位置还能拖动定位，icon 库里没有对应物，硬换会把这个功能换没。

截图：`home_iconlib.png`（顶栏地球）、`player_iconlib4.png`（icon 库版的 ⟲15 / 30⟳）。

**首页刷新（同日追加）。** 发现流原来只在 ViewModel 初始化时拉一次，挂后台放半天回来还是启动那一刻的榜。现在补两条入口：

- 下拉刷新：M3 1.3 的 `PullToRefreshBox` 包住发现流的 LazyColumn，`isRefreshing` 直接吃 `discovery.loading`。为此把 `refreshDiscovery()` 里的 `loading = it.items.isEmpty()` 改成恒 `true`——骨架屏那侧的判断条件本来就是 `loading && items.isEmpty()`，所以已有内容时不会整屏闪成骨架，只是顶部转个圈。
- 点底部"发现"就刷新：`MainActivity` 里加 `discoverTick`，但**只在本来就停在发现页**时才 +1（`tab == 0 && homeTab == 0`）——从"我的播客 / 个人"切回发现属于普通返回，不该重拉内容。`HomeScreen` 的 `LaunchedEffect(discoverTick)` 收到就 `refreshDiscovery()`；处在搜索态时先清搜索再刷新，否则刷新结果被搜索结果盖着看不见。

刷新的是**当前这档**：推荐档重拉热门榜，停在音乐档就重拉音乐分区榜。验证用的日志在 `HomeViewModel.refreshDiscovery()` 里（`adb logcat -s HomeViewModel:I`），实测三条：首页下拉 → `refreshDiscovery rid=0`；切到音乐档再下拉 → `rid=3`；点底栏发现 → `rid=0`。

一个坑：下拉手势的起点别落在搜索框上。`OutlinedTextField` 会把竖直拖动手势吃掉，从搜索框往下拉不会触发刷新（我第一次验证就是这么失败的，换成从 banner 区域往下拉才通）。

另一个：M3 的刷新指示器是浮在列表顶部的青色小圈，刷新过程中会盖住 banner 上半部分（官方实现里内容本身不下移，只有那个 indicator 动）。要完全像小宇宙那种"整页内容跟着下推"的效果，得自己接 `nestedScroll` 手写，暂时没做。

顺手修了 banner：高度 142dp 时内容比卡片高 8dp，"点击收听"那颗胶囊被圆角裁掉半截。按实际内容（标签 16 + 间距 8 + 标题两行 48 + 间距 6 + UP 名 16 + 胶囊 28）撑到 158dp，胶囊完整落在卡片里；同时给右侧封面补了 `clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))`，之前那张图是直角戳出卡片的圆角。

截图：`home_refresh1.png`（banner 修好后的首页）、`home_refresh_music.png`（音乐档下拉刷新后的榜）。

**推荐流换成 rcmd（同日追加）。** 起因是我自己验证刷新时发现：连续两次刷新的日志是 `oldHead=BV1qbWC61ENR newHead=BV1qbWC61ENR`，内容一模一样。热门榜是这个榜按"近三天热门"算的服务端固定结果，几秒内重复请求返回同一份，所以刷新看着像没反应。于是把"推荐"档的数据源换掉：

- 接口 `GET x/web-interface/wbi/index/top/feed/rcmd`，参数 `web_location=1430650 / y_num=4 / fresh_type=3 / feed_version=V8 / ps=12 / fresh_idx / fresh_idx_1h / brush=0`。路径带 `/wbi/`，签名由 WbiSignInterceptor 自动补 `wts` + `w_rid`，业务层不用管。
- 返回 `data.item` 是视频 / 番剧 / 直播 / 广告混排，每条用 `goto` 区分；只收 `goto=av` 的稿件，其余过滤掉（所以一批 12 条里通常只剩 10 条）。`goto` 是 Kotlin 关键字，模型里的属性只能叫 `gotoType`，`@SerializedName("goto")` 保住序列化名。
- 它是无限流：没有 `no_more`，换批次靠 `fresh_idx` 递增。每成功一批 `discoveryFreshIdx` +1，刷新换新内容、翻页也走它，不能拿 `page` 当批次号。
- 降级链：rcmd 失败（风控 / 密钥过期 / 断网）或者整批被过滤成空，就退回 `popular(1)`。`DiscoveryState.usePopularFallback` 记住这一档当前来自哪儿，翻页时切换成 `popular` 的 `pn` 递增，不然会拿 fresh_idx 去查热门榜、永远返回第一页。
- 去重：不同 `fresh_idx` 之间会撞同一条稿件，而 LazyColumn 的 `key` 重复会直接抛 `IllegalArgumentException` 崩掉，所以追加时按 `episode.key` 过滤掉已存在的。
- 卡片元信息：rcmd 不返回评论数，只有 `stat.view`，所以卡片从"评论 x · 时长"改成"有评论显示评论、没有就显示播放 x · 时长"。

实测（真机 A1，连续三次刷新）：

```
01:30:26 discovery apply source=rcmd fresh_idx=2 oldHead=BV1BMp46wEWA newHead=BV164W36oETR count=10
01:30:31 discovery apply source=rcmd fresh_idx=3 oldHead=BV164W36oETR newHead=BV11MsP6SEFk count=10
01:30:36 discovery apply source=rcmd fresh_idx=4 oldHead=BV11MsP6SEFk newHead=BV147sH6qEsv count=10
```

每次首条都不一样，单次 300~400ms，这才是刷新该有的效果。另外单独验过"加载更多"追加一批不崩（去重生效）。

截图：`home_rcmd.png`（推荐流落地）、`home_rcmd_more.png`（加载更多后的列表，meta 显示"播放 60.2万 · 25:24"）。

数据源换了之后 banner 左上角那颗标签也得跟着改：原来写"今日热门"，可底下已经不是热榜了，改成"猜你喜欢"（`home_guess.png`）。列表小标题仍是"为你推荐"。

顺手修了个交互漏洞：分类 chips 原来只有 12 个分区，`rid=0` 的默认档没有入口，点过一次"音乐"之后就再也回不到推荐流。现在在"分类"按钮后面补了一个"推荐"chip（`selectCategory(0, "推荐")`），默认档选中时高亮，随时能回来（`back_music.png` → `back_recommend.png` 验证来回切换）。

三页顶部留白统一成 60dp：发现 / 我的播客的大标题行 `top = 60.dp`，个人页昵称行 `top = 60.dp / bottom = 14.dp`，标题不再贴状态栏，整体重心下移且三页视觉齐平（`pad60_discover.png`、`pad60_library.png`、`pad60_profile.png`）。个人页的昵称再放大到 27sp + SemiBold，头像 56dp → 72dp（`profile_bigger.png`）。

播放页顶栏的收藏图标从爱心（`Favorite` / `FavoriteBorder`）换成星形：未收藏 `Icons.Outlined.StarBorder`、已收藏 `Icons.Filled.Star` + 青色。爱心是"喜欢 / 点赞"的符号，和底栏那颗点赞并排放会读重；星形是 B 站客户端的收藏语义（`player_star.png`、`player_star_on.png`）。

**播客化筛选（同日追加）。** B 站的内容池对"听"这件事太杂，加了两层过滤：

- 时长下限 `PODCAST_MIN_SECONDS = 600`（十分钟），推荐流和分区榜都过这条线。低于十分钟的视频结构是"钩子 + 高潮 + 求三连"，语音化后信息密度太低，也撑不起一次通勤收听。
- 纯视觉分区的标题词粗筛 `VISUAL_ONLY_WORDS`（鬼畜 / 歌单 / MV / 翻唱 / 舞蹈 / 宅舞 / 穿搭 / 美妆 / 试色 / 萌宠 / 撸猫 / 吃播 / 试吃 / 探店 / 游戏实况 / 电竞 / 手游 / 抽卡 / 开箱 …）。
- 分类 chips 从 12 个砍到 5 个：知识 / 科技 / 生活 / 影视 / 纪录片。音乐、游戏、鬼畜、美食、时尚、运动、动物全部撤掉入口，因为那些分区抽掉画面就听不出东西，留着等于给用户一个必然失望的入口。
- 因为一批 12 条过滤后常常只剩两三条，`HomeViewModel` 一次刷新会连拉最多 3 批 rcmd（`RCMD_BATCHES_PER_PAGE`）凑到 10 条（`RCMD_PAGE_TARGET`），跨批按 `episode.key` 去重；"加载更多"同样走多批凑页。

实测（真机 A1，登录态）：

```
rcmd fresh_idx=1 原始=10 时长不足=5 视觉词=0 保留=5 留下=我好像出了个假国！中国人(41min) | 第171期 历史上每一次(43min) | 一次讲清楚中国10种茶叶(12min)
rcmd fresh_idx=2 原始=10 时长不足=2 视觉词=0 保留=8 留下=去中国最北，探访大兴安岭(48min) | 扳倒黄仁勋的幕后推手都有(11min) | AI对就业市场的冲击20(24min)
```

连续三次刷新：`batches=3 count=14` / `batches=2 count=13` / `batches=2 count=13`，耗时 0.3~1.3 秒，每次首条 bvid 都不同。留下的内容变成历史考据、旅行纪实、科技解读、罪案、长访谈这类真正能听的。

**这里有个没解决的问题（待定）：** `rcmd` 的 `item` 实测没有 `tid` / `tname` 字段（只有 `bvid/cid/goto/pic/title/duration/pubdate/owner/stat`），所以"按分区砍"只能靠标题词。9 批 90 条实测下来视觉词命中 0 次——不是 rcmd 里没有游戏/音乐，而是这类标题往往不带可识别词（比如"【S16决赛】风起今朝，BLG凭什么能成为LCK杀手"是电竞赛事，光看标题完全认不出来）。真要按分区筛只有两条路：对每批 10 个 bvid 挨个查 `x/web-interface/view` 拿 `tid`（30 次请求/页，慢且吃风控），或者换用分区榜/搜索接口（干净但丢个性化）。

截图：`podcast_filter.png`（推荐流过滤后）、`podcast_filter_kn.png`（知识榜，同样是长内容）。

**首页点一条就播（同日追加）。** 原先列表卡片整卡点击走的是 `vm.openDetail(bvid)`，跳进单稿详情（简介 + 选集），只有右侧那颗圆形键才起播——对播客来说"点一条音频却弹出一个作品页"是纯多余的一跳。现在整卡点击和圆形键是同一个动作：`play(feedQueue, position)` 起播，**页面留在原地**，底部迷你条接管进度和播放/暂停，想进全屏播放器点迷你条即可。Banner 整卡也一起改成起播，"点卡片看详情、点胶囊才播"的两套逻辑合成一套。单稿详情页仍保留在搜索结果的入口里（那边按"看简介"理解更合理）。

截图：`click_play_only.png`（点列表卡片后仍在首页、迷你条接管）、`banner_play.png`（点 Banner 同样，这一版还会展开播放器，后续按用户要求改回原地起播）。

**付费内容屏蔽（同日追加）。** 两层判定，都放在 `ContentRepository`：

- 强信号词直丢：`HARD_PAYWALL_WORDS`（充电专属 / 付费课程 / 需购买 / 购买后可见 / 会员专享）。原因是实测发现充电专属稿件在 `x/web-interface/view` 的 `rights` 里毫无标记——`pay`、`arc_pay`、`ugc_pay` 全为 0，`payment` 为 null，连 `is_upower_exclusive` 都是 false，接口层根本认不出来，只能靠作者写在标题里的字来挡。宁可错杀几条也不让它进列表。
- 弱信号词核实：标题命中"付费 / 课程 / 会员 / 解锁 / 网课 / 训练营 / 专栏 / 完整版"时，才花一次 `view` 请求看 `rights.pay / arc_pay / ugc_pay` 是否为 1、`payment` 是否有值，确认付费才丢；不命中的不查，整页只多 0~3 次请求（`PAYWALL_CHECK_LIMIT`）。请求失败按"不是付费"处理，网络抖动不误杀。

提示：真正的"课堂"付费课（bilibili 课堂）不在主站推荐流里出现，所以主推流里能遇到的付费形态主要是充电专属，也就是靠第一层挡。日志里 `付费=N` 就是这一层丢掉的条数。

截图：`click_play_only2.png`（点卡后起播、停在发现页）。

**应用图标与顶栏品牌标识换成 BiliPod（同日追加）。** 素材来自 `<本地素材目录>`（同一套 logo 的 png / ico / svg / cdr）。应用图标用 `BiliPod.png` 那张蓝底白螺旋，切成两套：

- `mipmap-anydpi-v26/ic_launcher_bilipod.xml` + `ic_launcher_bilipod_round.xml`：自适应图标，背景纯色 `#0FB2F2`，前景是抠出来的白螺旋（`drawable-nodpi/ic_launcher_foreground.png`，占画布 52%，和原图里螺旋的占比一致），外加一层 `<monochrome>`（`ic_launcher_monochrome.png`）。
- `mipmap-{mdpi..xxxhdpi}/ic_launcher_bilipod.png` 与 `_round.png`：圆角方形 / 圆形位图（48~192px，圆角半径 22%），给不认自适应图标的环境兜底。
- `AndroidManifest` 的 `icon` / `roundIcon` 指向这一套，旧的粉色 B 站风格矢量 `drawable/ic_launcher.xml` 已删除。

顶栏那颗"地球"（`PlanetMark`）换成 `BiliPod.ico` 里的螺旋：ico 转了透明底（见下），切成 `drawable-xxhdpi` 与 `xxxhdpi` 两档，Compose 里用 `Image` + `ContentScale.Fit` 按 38×41dp 显示，保持螺旋不变形。

过程里踩的两个坑记下来：

**自适应图标被启动器"提亮"了。** 第一版做完，桌面上显示的底色明显比素材淡（实际像素 `(105,194,249)`，素材是 `(0,177,255)`），一开始怀疑是启动器缓存，换资源名重装（`ic_launcher` → `ic_launcher_bilipod`）没变化，删掉 `anydpi-v26` 只留位图也没变化。真正起作用的补上 `<monochrome>` 那一层加把前景从 74% 收到 52% 之后——现在是蓝底白螺旋的正常比例。结论：这类"图标变浅/变大"的现象，先怀疑系统对自适应图标的二次加工（单色层缺失时会自己生成一版着色图），别在资源本身反复改色。

**`.bak` 文件不能留在 `res` 目录。** 来回试图标时把 xml 改名成 `.xml.bak` 存在 `res/mipmap-anydpi-v26/` 里，`mergeDebugResources` 直接失败：`The file name must end with .xml or .png`。备份要放到工程目录之外。

**顶栏那颗 ico 是白底，得自己抠。** `BiliPod.ico` 转出来的 PNG 四角是纯白、`alpha` 通道全 255——它并没有带透明蒙版，放在 53dp 的顶栏上就是一块小白方块。抠的时候第一版用"min(r,g,b) 当白度"算 alpha，结果螺旋的浅蓝段被一起判成白，整片发虚；改成"每通道到 255 的偏差取最大值，偏差 < 18 才当白"，浅蓝段保持不透明、只有真的白底和抗锯齿边缘变透明，交叉处那层深蓝叠色也留住了。

截图：`logo_desktop7.png`（位图版，螺旋偏大、底色偏淡）、`logo_desktop9.png`（最终版，圆角 + 蓝底 + 白螺旋），顶栏见 `logo_home3_mark.png`（透明底螺旋坐在页面底色上）。

**主色从青绿换成 BiliPod 蓝（同日追加）。** 图标定了之后，主题色还是原来那套青绿，色调上和 logo 打架，于是整体换蓝：

- `Theme.kt` 整张色板换掉：`BrandLight #00A8EC`（取图标渐变 `#00B1FF → #69D2FF` 的中间调，白字还看得清）、`BrandContainerLight #DAF2FE`、`OnBrandContainerLight #003A56`；深色档 `BrandDark #5FC8FF` / `BrandContainerDark #0A3B52` / `OnBrandContainerDark #C5E9FA`。secondary / tertiary 本来是一套灰绿（`#5E7C79` / `#7A8B84`），一并挪到冷蓝灰（`#5C7A86` / `#748C96`），不然主色换了它们还透着绿。`InversePrimary` 两档跟着换。
- 播放页那套颜色不在主题里，是 `PlayerScreen` 自己的私有常量：`PlayerAccent #5FD3C7 → #5FC8FF`，收藏激活态 / 开关选中拇指的深青 `#032925 → #002B3F`。
- 页面底色没动（`#F6F8F8` 仍然是极浅冷灰），蓝白配比青绿配白更干净，不需要再压暗背景。

实测（真机 A1）：首页 chips 选中态、"猜你喜欢"、播放圆键、底栏选中项全部转为蓝；播放页"＋收藏"胶囊、整流三角、点赞激活态是蓝；我的播客页 tab 下划线也是蓝。截图 `theme_blue_home.png`、`theme_blue_player.png`、`theme_blue_lib.png`。

**全局改名 BiliPod + 换包名（同日追加）。** 品牌前后试过两个名字（`BiBo` → 最终 `BiliPod`），最后落到 `BiliPod` 时做了一次覆盖整仓的改名，不留旧品牌残留：

- 包名 `com.example.bilipodcast` → `com.bilipod.app`（`build.gradle.kts` 的 `namespace` 与 `applicationId` 同步），源码目录整体搬到 `app/src/main/java/com/bilipod/app/`，62 个文件的 `package` / `import` 全部跟进，`com/example` 空目录删掉。
- Application 类 `BiliPodcastApp` → `BiliPodApp`（文件同步改名，`AndroidManifest` 的 `android:name=".BiliPodApp"`）；主题 style `Theme.BiliPodcast` → `Theme.BiliPod`（`values` 与 `values-night` 两份 + manifest 的 `android:theme`）。
- 应用显示名 `哔哩播客` → `BiliPod`；`settings.gradle.kts` 的 `rootProject.name` → `BiliPod`；数据库文件名 `bilipodcast.db` → `bilipod.db`；图标资源 `ic_launcher_bibo*` → `ic_launcher_bilipod*`（含自适应 xml 与 5 档位图，manifest 的 icon/roundIcon 同步）。
- 换包名等于换应用：`applicationId` 变了，系统会当成新装应用，Room 里的收听历史、收藏、加密的登录 Cookie 都跟着旧包走，新包装上是从零开始。这次是用户明确要求连包名一起改，接受这个代价。

实测（模拟器 emulator-5556）：`gradlew assembleDebug` → `BUILD SUCCESSFUL`，装包后 `pm list packages` 同时存在 `com.bilipod.app` 和旧的 `com.example.bilipodcast`，新包冷启动落在登录页、标题显示 `BiliPod`。真机 A1 中途掉线（`adb devices` 里消失），没能同步这版。

**首启不逼登录 + 登录页 tab 化（同日追加）。** 之前冷启动按 `isLoggedIn()` 分派：没登录直接铺登录页，"先不登录随便逛逛"是唯一出口。现在改成启动即进首页，未登录也能逛推荐、分类、搜索；登录入口只剩个人页和"我的播客"里的未登录提示。

- 播放这一环才拦：`HomeScreen` 的 `play` lambda 先看 `state.loggedIn`，未登录不调 `playerVm.play`，弹 `ConfirmDialog`（标题"播放需要登录"，按钮"取消 / 去登录"）。所有走 `play` 的入口（榜单卡、推荐流、"猜你喜欢"大卡的"点击收听"、详情页圆键）一并生效。
- 登录页从"扫码页 + 两个文字入口跳二级页"改成同页三 tab，顺序固定为 `短信验证码 → 账号密码 → 扫码`。切 tab 只换中间一块内容，极验仍是悬浮卡片叠在表单上，验证完卡片消失、表单原地不动（以前是整页替换，极验白屏就回不来）。原来的 `NativeLoginPane.kt` 整页外壳删掉，`LoginField` / `PrimaryButton` 搬进 `LoginScreen.kt`。
- 两个登录按钮共用 `form.loading`，靠 `state.mode` 区分谁在转圈，所以 `submitPasswordLogin` / `submitSmsLogin` 开头补了 `mode` 赋值。
- 登录成功后 `AppRoot` 回调里加 `homeVm.refresh()`（`HomeViewModel` 已提到根作用域），账号昵称、收藏、B 站历史才会立刻刷新；`HomeViewModel` 不再每次进页面自己 `isLoggedIn()` 初始化。

实测（真机 A1）：冷启动落首页不弹登录；点推荐卡弹"播放需要登录"（`uiautomator` 抓到标题、说明、取消/去登录四个节点）；点"去登录"进新登录页，三个 tab 逐个切换均正常，内容区分别是验证码表单、密码表单、二维码。

**发现页去昵称、品牌标记与标题等高（同日追加）。** 大标题下面那行 `<账号昵称>`（未登录时是"未登录 · 部分内容受限"）是上一版为了"顶栏别太空"加的，实际是重复信息——账号名在个人页已经够显眼，首页挂着只会把标题和搜索框之间的距离撑开。现在发现档的 `subtitle` 直接置空，只保留我的播客页的"历史 · 收藏 · 下载"。

同时把 `PlanetMark` 从 38×41dp 收到 30×32dp，并把标题的 `lineHeight` 显式写成 `32.sp`（字号也是 32sp），这样行盒高度和图标高度是同一个值，`Row` 里 `CenterVertically` 一居中两者上下沿就齐平（`theme_blue_header.png`）。

截图：`player_top_coin2.png`（真机顶栏硬币 + 心形）、`player_coin_sheet.png`（真机投币面板）、`emulator_coin_done.png`（投完后图标变青）、`emulator_coin_reopen.png`（已投过的禁用态）。

截图：`player_ctrl1.png`（新控制行）、`player_settings.png`（面板初版）、`player_settings2.png`（1.5x + 29:58，睡眠刻度还错在"关"）、`player_settings3.png`（刻度修正后停在 30）、`player_settings4.png`（定时到本集结束）、`player_settings5.png`（重开面板后开关仍在"开"、2.5x 选中）、`player_like.png`（点赞后青色实心）、`player_like_off.png`（取消点赞）、`emulator_settings.png` / `emulator_settings2.png`（模拟器 1080×2340 上面板排版没挤）、`emulator_settings6.png`（切到第 3 集后倍速自动回 1.5x、开关回"关"）。

### 环境与工具坑（换机器可复用）

- **模拟器崩溃**：`emulator-5554` 两次报 `device not found`，用 `adb kill-server` + 隐藏窗口重启 `emulator -avd phase05-api35 -no-snapshot-load -no-boot-anim -gpu swiftshader_indirect` 恢复，AVD 用户数据与已装应用保留。用户自己的 `emulator-5556` 全程未触碰。
- **模拟器端口漂移**：`emulator -avd phase05-api35` 启动时若 5554 被占，会自己落到 5556。动手前先 `adb -s emulator-5556 emu avd name` —— 返回 `phase05-api35` 才是测试机，返回 `apkstart` 就是用户那台，别碰。
- **中文输入**：`adb shell input text` 打不了中文，`cmd clipboard set text` 在 API 35 返回 "No shell command implementation"，`input keyevent 279` 粘贴无效。验证中文关键词只能临时硬编码 `keyword` 初值构建一版，验完还原。
- **页面滚动**：`input swipe` 在 Compose 列表里经常无效，必须 `input touchscreen swipe`。
- **UI 抓取**：`uiautomator dump /sdcard/ui.xml` + `cat`，按 `text=` / `content-desc=` 的 bounds 算中心点再 `input tap`；PowerShell 里先把 `[int]` 赋值再相加，Compose `Dialog` 会独立成窗。
- **sqlite 查询**：`run-as com.bilipod.app sqlite3 databases/bilipod.db 'select ...;'`，**SQL 结尾必须带分号**，否则报 incomplete input。
- **真机熄屏**：熄屏后 `mCurrentFocus` 会变成 `NotificationShade`，所有 tap 都落空；先 `input keyevent 224` 唤醒，再 `input keyevent 82` + 上滑解锁。
- **大截图**：真机锁屏截图约 5.7MB，超过模型读图上限，用 `System.Drawing`（`Add-Type -AssemblyName System.Drawing`）缩到一半再读。
- **重装 APK**：会清"跳过登录"的内存态，需要重新点"先不登录，随便逛逛"；`POST_NOTIFICATIONS` 要重新 grant（用 `install -r -g` 可一次给全）。
- **真机 sqlite3 不可用**：Android 16 真机上 `run-as com.bilipod.app sqlite3` 报 `exec failed for sqlite3: Permission denied`（模拟器 API 35 正常）。真机取证改用 UI 状态或 `dumpsys media_session`；要读库先 `run-as ... cat databases/bilipod.db` 导出，二进制别过 PowerShell 管道。
- **强制重建 Activity**：`settings put global always_finish_activities 1` + Home 在 MIUI 上不生效（`am start` 只把任务拉前台，logcat 无新 `wm_on_create_called`）。改用 `am start -n <pkg>/.MainActivity -f 0x10008000`（NEW_TASK|CLEAR_TASK），同进程的播放 Service 存活，正好复现"ViewModel 重建、queue 空"的场景。
- **限速命令**：`adb -s emulator-5554 emu network speed edge|full`，`emu` 是 adb 子命令，写成 `shell emu` 会报 `emu: inaccessible or not found`。edge 档下 `videoDetail` 这类请求会拖到 20~30 秒，`normalize` 窗口足够长，适合验防重复点击。



### 收尾：清理、审查与开源（2026-11-17）

工程从 176.2 MB 压到 0.9 MB —— 删掉 `app/build`、`.gradle`、`.kotlin`，`.gitignore` 补上 `.kotlin`。源码与资源本身只有 0.9 MB，其余全是构建产物。另外把上一轮的 339 个测试截图（240.6 MB）整体清掉了，工程里不再留调试产物。

资源侧用脚本把 `R.type.name` 和 `@type/name` 双向核对了一遍，没有找不到引用的资源。代码侧按顶层声明与函数调用做统计，报出来的"无引用"绝大多数是文件内私有的 Compose 组件、`override` 的框架回调、以及带尾随 lambda 的调用，属于假阳性。真孤儿只有一处：`NativeLoginViewModel.selectMode()`——登录 tab 改成 UI 本地状态后它再没有调用者，删掉。

四处真问题：

1. 登录回调原来调 `homeVm.refresh()`，而 `refresh()` 会置 `loading = true`，登录成功回首页会闪一帧骨架屏。新增 `HomeViewModel.onLoginStateChanged()`，只拉 profile 更新昵称和头像，按设置决定要不要重拉收藏和 B 站历史，不碰 `loading`。
2. 登录页的 `form.notice/error` 三个 tab 全在显示，扫码失败的表单报错会压在二维码底下。改成 `tab <= 1` 才显示表单提示，扫码档只看 `qr.error`。
3. 二维码 bitmap 原来在 `QrPane` 内部 `remember`，每切一次 tab 就丢一次、重建一次。提到 `LoginScreen` 顶层 `remember(qr.qrContent)`，`QrPane` 收一个 `android.graphics.Bitmap?` 参数。
4. 防重复进登录页的 `armed` 标志：`loggedIn` 登录后会长期为 true，二次进登录页会被旧标志直接弹回首页。改成只有"先为 false、再变 true"才算登录成功。

`lintDebug` 从 2 errors / 15 warnings 收到 0 errors / 7 warnings：`windowLightNavigationBar` 标 `tools:targetApi="27"`（API 26 的系统会直接忽略这个属性，行为与不加一致）；`Formatters` 里所有 `String.format` 固定 `Locale.ROOT`（设备切到阿拉伯语区时默认 Locale 会把数字换成当地字形，进度条就废了）；`ic_launcher_monochrome.png` 与 `ic_launcher_foreground.png` 内容完全相同（MD5 一致），删掉前者、adaptive-icon 复用 foreground，每次省 20.3 KB。剩下 7 条都是设计选择（targetSdk 跟着 compileSdk 走、core-ktx 故意停在 1.15.0、`MediaSessionService` 本来就该导出给系统媒体控制、`allowBackup=false` 已表达意图、`mipmap-anydpi-v26` 留着给低版本当 PNG fallback、`brand_mark` 只出两档密度）。

仓库：<https://github.com/ChenL-eon/BiliPod>，已转为公开。这份 README 里的真机序列号、机型、账号昵称和本地素材路径在公开之前做过脱敏，分别替换成 `A1`、`Redmi 旗舰机`、`<账号昵称>`、`<本地素材目录>`。

本地提交与推送：

```powershell
git init -b main
git config user.name "ChenL-eon"
git config user.email "ChenL-eon@users.noreply.github.com"
git add -A
git commit -m "初始化 BiliPod：B 站播客客户端"
gh repo create BiliPod --public --source=. --remote=origin --push
```