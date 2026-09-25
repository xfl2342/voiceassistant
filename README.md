# 语音助手（VoiceAssistant）

说一句话，自动变成日程。

一个面向个人的安卓语音日程助手：按住说话 → 语音转文字 → AI 解析成结构化行程 → 写入本地日历 → 到点提醒。

> **当前状态：开发中。** 已经能跑通「说一句话 → AI 解析 → 存入本地日历」这条主线，
> 提醒的触发、行程编辑、重复规则的界面还在继续做。功能清单与技术选型见 [语音助手开发规划.md](./语音助手开发规划.md)。

---

## 为什么做这个

记一条日程本该很简单，但打开日历、点新建、选日期、选时间、填标题、设提醒，一套下来半分钟就没了。这个项目想做的事很朴素：**说一句话就够了**。

「明天下午三点开项目评审会」——说完，日程就建好了。

项目为个人自用而做，开源出来是因为想法不复杂，代码也不复杂，希望能给别人一点参考。

## 计划中的功能

| 功能 | 说明 |
| --- | --- |
| 语音录入 ✅ | 一键录音，走系统语音识别，识别结果可手动修改 |
| AI 解析 ✅ | 调用 DeepSeek 把自然语言转成结构化行程 |
| 结果确认 ✅ | 展示解析结果后再保存 |
| 月视图日历 ✅ | 按月展示行程，全天行程与定时行程在同一格里混排 |
| 全天行程 ✅ | 支持「下周三请一天假」，按日期存储，不受时区影响 |
| 本地存储 ✅ | 行程、重复规则、提醒三类数据存本地数据库 |
| 分类提醒 🚧 | 按急迫性设置提前量（15 分钟 ~ 2 天），数据已存好，通知触发待接 |
| 定期行程 🚧 | 每日、每周（可指定周几）的展开逻辑已完成并测试，编辑界面待做 |
| 行程编辑 ❌ | 目前只能新建，编辑与删除待做 |
| 设置中心 🚧 | 已有 API Key 与模型选择，其余设置项待补 |

✅ 可用 · 🚧 开发中 · ❌ 尚未开始

暂不支持（后续可能做）：账号与云同步、系统日历同步、每月/每年/农历等复杂重复规则、语音播报、行程冲突检测。

## 技术栈

- **语言与界面**：Kotlin + Jetpack Compose（Material 3）
- **架构**：UI / ViewModel / 业务用例 / 数据层 的轻量分层
- **本地存储**：Room（SQLite）
- **网络**：OkHttp + Retrofit + kotlinx.serialization
- **提醒**：AlarmManager + NotificationManager（本地通知）
- **语音识别**：系统 `SpeechRecognizer` 优先，必要时可扩展云 ASR
- **AI 解析**：DeepSeek API（`deepseek-chat` / `deepseek-reasoner`）

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Android Studio | 2026.1 或更高（内置 JDK 即可） |
| JDK | 17 及以上 |
| Android SDK | API 36（Android 16），Build Tools 36 |
| minSdk | 31（Android 12） |
| 目标系统 | Android 12 ~ Android 16，主要在 Android 16 上验证 |

## 构建与运行

1. 用 Android Studio 打开本项目根目录；
2. 首次打开会自动同步 Gradle 依赖，等待完成；
3. 连接安卓手机（需开启「开发者选项 → USB 调试」）或启动模拟器；
4. 点击 Run 安装到设备。

命令行构建需要先在根目录创建 `local.properties`（Android Studio 会自动生成，无需手动创建）：

```properties
sdk.dir=/path/to/Android/Sdk
```

Windows 路径中的反斜杠需要转义，例如：

```properties
sdk.dir=C\:\\Users\\<用户名>\\AppData\\Local\\Android\\Sdk
```

## 配置 AI 解析

**本项目不提供 API Key，需要你自备。**

1. 到 [DeepSeek 开放平台](https://platform.deepseek.com/) 注册并充值，创建一个 API Key；
2. 打开 App → 设置 → DeepSeek，填入 API Key；
3. 点击「测试连接」确认可用。

Key 只保存在你自己的手机上（经系统密钥库加密存储），**不会上传到任何第三方服务器**；App 直接向 DeepSeek 官方接口发起请求。

> 请勿把 API Key 写进代码或提交到仓库。仓库的 `.gitignore` 已覆盖常见的密钥文件名，但仍需你自己留意。

## 权限说明

| 权限 | 用途 | 何时申请 |
| --- | --- | --- |
| 麦克风 | 语音录入 | 首次点击录音时 |
| 通知 | 发送日程提醒 | 首次保存带提醒的行程时 |
| 闹钟和提醒 | 精确到分钟触发提醒（Android 12+，需在系统设置中手动允许） | 注册提醒前引导用户开启 |
| 电池优化豁免 | 避免提醒被系统后台限制 | 在权限引导页说明，由用户决定 |

## 隐私说明

- **语音**：录音用于语音识别，不保存原始音频文件（除非你手动开启该选项）；
- **行程数据**：全部保存在本机数据库，不上传、不同步、不备份到云端；
- **发给 AI 的内容**：仅包含语音识别后的文字，用于解析成行程结构；
- **无账号体系**：App 不需要注册登录，也没有任何埋点与统计。

## 已知限制

- **不能放在中文路径下**（Windows）：安卓构建工具默认拒绝中文路径，本项目已用开关绕过，
  编译打包正常；但 **Gradle 单元测试任务仍会失败**，详见「常见问题」。放到纯英文路径可彻底避免；
- **语音识别**：部分国内安卓系统（如小米、华为国行）未预装 Google 语音服务，系统识别可能不可用，届时需要改用云 ASR；
- **提醒准时性**：国产 ROM 的后台管理较严格，需要手动开启「自启动」「省电策略设为无限制」，并在多任务界面锁定应用，否则提醒可能延迟或丢失；
- **重复规则**：目前只支持每日、每周，不支持每月、每年、农历与节假日跳过；
- **平台**：仅支持安卓，暂无 iOS 计划。

## 路线图

开发阶段与任务清单见 [语音助手开发规划.md](./语音助手开发规划.md) 第 13 章。

## 常见问题

**单元测试跑不起来，报 `ClassNotFoundException`**

如果工程放在含中文的路径下（Windows），就会出现这个现象。原因是：Gradle 以 UTF-8 写出
测试进程的参数文件，而 Windows 版 Java 默认按系统本地编码（简体中文环境是 GBK）去读它，
中文路径被解析成乱码，于是测试类「找不到」。

把工程移动到纯英文路径即可彻底解决。需要注意的是，在中文路径下**编译、打包、安装到手机
都不受影响**，只有单元测试会失败。

**依赖下载很慢或长时间卡住**

直连 Maven Central 在国内经常出现 TLS 握手被中断、长时间无响应。可以给 Gradle 配一份本机
镜像，把依赖源指向国内站点。这个配置放在 `%USERPROFILE%\.gradle\init.d\` 目录下，
只对本机生效，不会写进工程，也不影响别人克隆你的代码。

## 参与贡献

项目目前以自用为主，代码尚未成型。如果你有想法或发现问题，欢迎提 Issue 讨论；等有一定完成度后会更欢迎 Pull Request。

## 许可证

[MIT](./LICENSE) © 2026 xfl2342

---

## English

**VoiceAssistant** — an Android voice-driven calendar for personal use. Speak a sentence like "meeting at 3 pm tomorrow, remind me 15 minutes earlier", and the app transcribes it, uses the DeepSeek API to parse it into a structured event, stores it locally, and fires a local notification on time.

Built with Kotlin + Jetpack Compose, Room, and AlarmManager. All data stays on your device; you need to bring your own DeepSeek API key.

**Status:** planning stage — only the design document exists so far.

Licensed under the [MIT License](./LICENSE).
