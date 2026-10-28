# MISoul

<div align="center">

![release](https://img.shields.io/github/v/release/siweina/MISoul?style=flat-square)
![downloads](https://img.shields.io/github/downloads/siweina/MISoul/total?style=flat-square)
![license](https://img.shields.io/github/license/siweina/MISoul?style=flat-square)
![platform](https://img.shields.io/badge/Android-13%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![api](https://img.shields.io/badge/libxposed-API%20102-blue?style=flat-square)

<img src="docs/icon.png" width="120">

<img src="docs/overview.png" width="23%"> <img src="docs/features.png" width="23%"> <img src="docs/settings.png" width="23%"> <img src="docs/about.png" width="23%">

</div>

把**超级小爱**的人格换成你自己的 —— 一个 LSPosed 模块。

不改小爱的任何文件、不重新签名 APK、不进 Recovery：模块只在 `com.miui.voiceassist` 的内存里替换文本。关掉开关立刻回到官方人格，卸载即彻底消失。

## 功能

**人格与形象**

- **人格全文**：用你自己的 `soul.md` 整份替换官方人设
- **自称**：定点替换官方 `prompt.md` 里「并表明自己是超级小爱」那一句，其余硬规则不动
- **界面形象**：显示名、签名、欢迎语，以及对话页顶部标题和开场语里的品牌文案
- **隐藏桌面图标**：隐藏后拨号 `*#*#7685#*#*`（S=7 O=6 U=8 L=5）会收到一条通知，点通知进设置页

**上下文瘦身**

- **工具描述压缩**：只改 `ToolDefinition.description`，参数 schema 不动。按块打分，保留格式字面量与适用边界，丢弃示例和大段描写。实测 257 个工具：**125,393 → 68,801 字**
- **选工具规则替换**：官方 `tool_selection_rules.md` 有 24,067 字，可整份换成自己的精简版（内置一份约 900 字，清空则回到官方）
- **语气契约**：追加到系统提示末尾的一小段约束，规定短句、禁书面语、不滥用列表
- **工具筛选**：按类别决定给模型看哪些工具，默认不启用

**其它**

- **定时任务放行**（可选，默认关闭）：非会员创建「定时 AI 任务」时，官方会以会员校验为由拒绝。打开后模块在本地放行这道判断，任务可正常创建。只影响客户端检查，云端校验不受影响
- **使用统计**：记录每个工具被调用的次数与最近时间
- **概览页**：自动读取机型、系统版本、内核、架构与页大小、进程位数、小爱版本
- **应用内更新**：从 Releases 检查新版本，下载后交给系统安装器

## 环境要求

| 项 | 要求 |
|---|---|
| 设备 | Android 13 及以上（minSdk 33），arm64 |
| 框架 | [LSPosed](https://github.com/LSPosed/LSPosed)（libxposed API 102） |
| 目标应用 | 超级小爱 `com.miui.voiceassist`（在 8.3.30.4420 / versionCode 508003030 上验证） |
| 作用域 | 只需勾选「超级小爱」 |

## 安装

1. 安装 Releases 里的 APK
2. 在 LSPosed 里启用 **MISoul**，作用域勾选「超级小爱」
3. **强行停止超级小爱**再打开（LSPosed 提示「模块已更新」时也必须这么做）
4. 打开 MISoul → 功能 → 人格全文，粘贴你自己的人格，保存

> 小爱进程只在启动时加载人格，改完不重启小爱看不到变化。

## 工作原理

模块通过 libxposed 挂在 `com.miui.voiceassist` 进程里，替换这几个位置：

| 目标 | 位置 | 用途 |
|---|---|---|
| `soul.md` / `prompt.md` 读取 | `rs.a$a.readAsset` / `readFile` | 人格文本 |
| `AgentProfile` 解析结果 | `ck.o0.l` / `ck.o0.m` | 显示名、签名、欢迎语 |
| `AgentDefinition.name` | `com.aios.osbot.agent.unified.AgentDefinition` | 声明式名字 |
| `config.json` 资源流 | `AssetManager.open` | 配置里的 `name` |
| 品牌文案 | `TextView.setText` / `Resources.getText` | 标题、开场语里的「超级小爱」 |
| 工具定义 | `ToolDefinition.getDescription` | 描述压缩 |
| 定时任务校验 | `MembershipRepository.checkTimerAccess` | 可选的本地放行 |

全部按**精确匹配**替换，命中不了就安静放过——小爱升级后最多是某处不生效，不会崩。日志标签 `MISoul`：

```bash
adb logcat | grep MISoul
```

小爱自己的文件不会被改，`soul.md` 仍是官方内容，替换只发生在内存里。

## 已知限制

- 混淆名（`rs.a`、`ck.o0`）随小爱版本变化，升级后需要适配；模块会记录 `hook skipped`
- 品牌文案是文本层替换，若官方改成 Compose 渲染或拼进长句，这一处会失效
- 拨号密令**不能直接弹出设置页**：后台广播里 `startActivity` 会被系统的 BAL 拦掉，所以用通知兜底
- 工具描述压缩**不保证没有副作用**。想彻底稳妥就只开「语气契约」和「选工具规则替换」；工具调用变笨时关掉开关即可恢复
- 定时任务放行只处理客户端的会员判断。视频生成共用同一判断，但云端仍会查额度，免费账号即使本地放行也出不了片
- 只支持 `com.miui.voiceassist`，不支持其他小爱版本线

## 构建

需要 JDK 21 与 Android SDK 37：

```bash
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/app-release.apk`。release 构建开启 R8 与资源压缩，体积约 3 MB。

## 许可

[MIT](LICENSE)

界面组件来自 [Miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0），液态玻璃底栏改编自 [HyperNavBar](https://github.com/HyperNavBar/HyperNavBar) 与 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（均为 Apache-2.0），工程结构参考了 [HyperMusicCover](https://github.com/zyl6932/HyperMusicCover) 与 [InstallerX](https://github.com/iamr0s/InstallerX) 的做法（未复制其源码，后者为 AGPL-3.0）。

本项目与小米、小爱、DeepSeek 均无隶属关系。