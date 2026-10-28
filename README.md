# MISoul

# MISoul（本 fork：超级小爱会员解锁版，非官方分支）

> **这是 siweina/MISoul 的一个 fork，不是上游原项目。**
> 基于上游 1.0.4（commit `28de5a1`，MIT 协议，原作者与原项目地址见下文），
> 只在上游基础上加了下面两个功能，其余人格、形象、工具筛选等功能一行未动。
> 上游 README 正文全部保留在下面，本节只讲 fork 新增的部分。
> 本 fork 的代码与说明已公开在 https://github.com/228983641/MISoul（分支 `vip-unlock`），
> 并以 PR https://github.com/siweina/MISoul/pull/1 提交给上游作者评审；PR 尚未合并，
> 合并与否由上游作者决定，fork 本身独立存在，不依赖 PR 结果。

## 本 fork 新增的两个功能

### 1. 会员解锁（定时任务 + 视频生成）——开关默认关闭

- 解决的问题：非会员在超级小爱里建**定时 AI 任务**或**场景自动化**时，
  模型在对话里拒绝，话术大意是“需要会员才能执行自动化”。
  这个拒绝不是 UI 弹窗，而是模型按工具返回的拒绝结果现场组织出来的——
  包里根本没有“需要会员才能执行自动化”这句原话，改界面文案改不到它。
- 真正的关卡（针对超级小爱 8.3.30.4420 / versionCode 508003030 的静态分析）：
  `com.aios.osbot.membership.MembershipRepository.checkTimerAccess()` 只返回
  `ALLOWED / BLOCKED_FREE / BLOCKED_UNVERIFIED` 三种状态，
  `com.aios.osbot.tools.system.TimerTool` 的 `membershipBlockOrNull()` /
  `enabledUpdateMembershipBlockOrNull()` 据此拦截定时任务的创建与修改。
- 本 fork 的做法：开关打开时，`checkTimerAccess()` 返回 `ALLOWED`，
  两个 `membershipBlockOrNull()` 返回 null（=不挡）；开关关闭时行为与上游完全一致；
  取不到 `ALLOWED` 枚举时原样走原逻辑，绝不硬编假值。
- 视频生成不是独立的关：付费功能门与聊天界面的视频模式关口都先调同一个
  `checkTimerAccess()`，所以一个开关同时放行定时任务和视频生成的**客户端关口**，
  无法只开其中一个；视频是云端沙箱渲染，服务端生成时是否再查会员，必须真机实测，
  客户端放行不等于一定出片。详见 [docs/vip-unlock.md](docs/vip-unlock.md) 第 2.4 节。

### 2. 自动化流程（先规划后执行，成功流程优先复用）——开关默认关闭，与会员解锁相互独立

- 解决的问题：小爱做界面自动化时只把一句话传给云端 UI 代理
  （`com.xiaomi.voiceassistant.UIAgentServiceForOSBot.controlApp(String)`），
  笼统的一句话经常在首页找不到入口。实测：只说“打开支付宝签到”，云端在首页
  找不到签到；补成“打开支付宝，点我的，再点签到”就顺利完成了——不是点不了，
  是模型自己不知道流程。
- 本 fork 的做法（通用机制，**不内置任何应用、任何任务的流程**）：
  先检索流程，再按流程执行——有成功记录时优先复用成功流程，
  没有成功记录时先给云端加一段通用规划要求（先想清楚依次点哪些按钮、
  按钮的准确文字，找不到就停下，不要猜测、不要乱点）；已经是分步骤的指令原样放行。
  成功执行过的分步骤指令会被记下来（应用 + 末步目标为键，上限 50 条），
  含支付、转账、付款、下单、购买、充值、提现字样的指令一律原样放行、绝不补全。
  详见 [docs/flow-inject.md](docs/flow-inject.md)。

## 版本历史

| 版本 | versionCode | 变化 |
|---|---|---|
| 1.0.4-vip1 | 40 | 会员解锁：只放行定时任务的创建与修改 |
| 1.0.4-vip2 | 41 | 开关文案改为“会员解锁（定时任务 + 视频生成）”，说明两者共用同一判断 |
| 1.0.4-vip3 | 42 | 新增自动化流程（先规划后执行，成功流程优先复用） |
| 1.0.4-vip4 | 43 | 固定发布签名（密钥只存仓库 Secrets，不进代码）；往后版本可直接覆盖升级 |

## 安装（本 fork，和上游版不一样）

> 本 fork 已在 Releases 里发包：[v1.0.4-vip4](https://github.com/228983641/MISoul/releases/tag/v1.0.4-vip4)，附件 `MISoul-v1.0.4-vip4.apk`。
> 也可以按 [docs/vip-unlock.md](docs/vip-unlock.md) 第 7 节自己用 `./gradlew assembleRelease` 编译。

1. 如已装上游 MISoul 或本 fork 的 vip1–vip3，先卸载它（签名不同，无法覆盖安装；
   vip4 起签名固定，往后版本可直接覆盖升级，详见 [docs/signing.md](docs/signing.md)）。
2. 安装本版 APK，在 LSPosed 里启用 **MISoul**，作用域**只勾选「超级小爱」**。
3. **强行停止超级小爱**再打开（LSPosed 提示“模块已更新”时也必须这么做）。
4. 打开 MISoul → 功能 → 打开「会员解锁（定时任务 + 视频生成）」，
   如需流程辅助再打开「先规划后执行，成功流程优先复用」。
   两个开关默认都是关闭的，不打开就与上游行为完全一致。

## 真机实测结果（截至 vip4，诚实记录）

- 定时任务创建：已确认可用。非会员建“支付宝签到”定时任务，任务成功建进列表并启用，
  模型不再说会员拒绝（此前未开开关时被拒，文案是免费版不支持创建由 AI 到点自动执行的定时任务）。
- 前台界面点击：已确认小爱能点。第一次“打开支付宝找签到”在首页找不到；
  第二次按“打开支付宝 → 点我的 → 点签到”的准确流程顺利完成——这正是 vip3 流程机制要解决的。
- 仍需实测、不要提前承诺：定时任务到点执行时是否按流程点下去（执行侧另有会员仓库，
  锁屏、云端超时也可能影响）；视频生成云端是否真的出片；场景自动化走米家云端时本地 hook 是否无效。
  日志：`adb logcat | grep MISoul`（解锁生效时有 `会员解锁：checkTimerAccess -> ALLOWED`）。

## 文档索引

- [docs/vip-unlock.md](docs/vip-unlock.md)：问题分析、hook 点、效果表、安装与实测、诚实边界
- [docs/flow-inject.md](docs/flow-inject.md)：自动化流程的机制、优先级、安全红线、日志
- [docs/signing.md](docs/signing.md)：固定发布签名为什么需要、怎么构建、升级与密钥丢失说明

## 与前一个开源项目的区别（不要装错）

- 前一个项目是 **GramSieve Conso 改编版**（`gramsieve-conso-mod`）：给 Telegram 的 Conso 客户端
  （`global.xfinite.conso`）做消息过滤、去广告、本地 Premium，基于 GramSieve 上游，与小爱完全无关。
- 本项目是 **MISoul 会员解锁版**：给超级小爱（`com.miui.voiceassist`）用，基于 siweina/MISoul 上游，
  只解决“非会员建不了定时 AI 任务 / 场景自动化”这一个问题，外加一个通用的自动化流程辅助。
  两者目标应用、上游、作用域、安装包都不一样，APK 不能互换，LSPosed 作用域也不能互勾。


<div align="center">

![release](https://img.shields.io/github/v/release/siweina/MISoul?include_prereleases&style=flat-square)
![downloads](https://img.shields.io/github/downloads/siweina/MISoul/total?style=flat-square)
![license](https://img.shields.io/github/license/siweina/MISoul?style=flat-square)
![platform](https://img.shields.io/badge/Android-13%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![api](https://img.shields.io/badge/libxposed-API%20102-blue?style=flat-square)

</div>

把**超级小爱**的人格换成你自己的 —— 一个 LSPosed 模块。


不改小爱的任何文件、不重新签名 APK、不进 Recovery：模块只在 `com.miui.voiceassist` 的内存里把人格文本换掉。关掉开关就立刻回到官方人格，卸载即彻底消失。

<div align="center"><img src="docs/icon.png" width="120"></div>


<div align="center">
  <img src="docs/overview.png" width="23%">
  <img src="docs/features.png" width="23%">
  <img src="docs/settings.png" width="23%">
  <img src="docs/about.png" width="23%">
</div>
## 功能

- **人格全文**：用你自己的 `soul.md` 整份替换官方人设
- **自称**：定点替换官方 `prompt.md` 里「并表明自己是超级小爱」那一句，其余硬规则不动
- **界面形象**：显示名、签名、欢迎语（替换 `AgentProfile`），以及对话页顶部标题和开场语里的品牌文案
- **隐藏桌面图标**：隐藏后拨号 `*#*#7685#*#*`（S=7 O=6 U=8 L=5）收到一条通知，点通知进设置页；LSPosed 管理器里也能直接进
- **本地 APK 更新**：选一个 APK 交给设备自己的系统安装器，不自己开 `PackageInstaller` 会话
- 概览页自动读取机型、系统版本、内核、架构与页大小、进程位数、小爱版本

## 环境要求

| 项 | 要求 |
|---|---|
| 设备 | Android 13 及以上（minSdk 33），arm64 |
| 框架 | [LSPosed](https://github.com/LSPosed/LSPosed)（libxposed API 102） |
| 目标应用 | 超级小爱 `com.miui.voiceassist`（在 8.3.30.4420 / versionCode 508003030 上验证） |
| 作用域 | 只需勾选「超级小爱」 |

## 安装

1. 安装本 fork 的 APK（见上文“安装（本 fork）”一节，来自本仓库 Releases 的 v1.0.4-vip4；上游 Releases 里的是原版，没有这两个新增功能）
2. 在 LSPosed 里启用 **MISoul**，作用域勾选「超级小爱」
3. **强行停止超级小爱**（LSPosed 提示「模块已更新」时也必须这么做），再打开
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

全部按**精确匹配**替换，命中不了就安静放过（小爱升级后最多是某处不生效，不会崩）。日志标签 `MISoul`：

```bash
adb logcat | grep MISoul
```

数据目录里小爱自己的文件不会被改，`soul.md` 仍是官方内容——替换只发生在内存里。

## 上下文与语气

小爱的系统提示里带着大量工具定义（function-calling 的 	ools 数组），一来挤占上下文，二来这些 schema 全是技术文档语体，会把回复带成官腔。模块为此做两件事：

**语气契约**（功能 → 语气契约）：追加到系统提示最末尾的一段短约束，规定短句、禁书面语、不滥用列表、保留口语。模型对结尾更敏感，这 200 字往往比开头写 3000 字人设更管用。

**工具描述瘦身**（功能 → 开关）：只改 ToolDefinition.description，参数 schema 一个字节都不动。压缩规则刻意保守：

- **保留**含格式字面量（格式、必须、必填、枚举、ISO、经度）和适用边界（不适用、仅用于、不要用于）的行——删了模型就会瞎猜参数、用错工具
- **丢弃**示例、⚠️ 提醒、重复语气和长场景描写

想关掉随时在开关里关，也可以在某条工具上单独覆盖描述（	ool_overrides，一行一条 工具名=新描述）。
### 选工具规则可替换

官方 	ool_selection_rules.md 有 **24,067 字**，是仅次于工具描述的第二大块。在「功能 → 选工具规则」里填自己的精简版即可整份替换；编辑器默认带一份内置精简版（约 900 字，保留闹钟/日程/定时的区分与时间格式），清空保存则回到官方那份。

### 工具描述深度压缩（实验）

上面那个开关只按行过滤，实测只省 4.5%——因为这些描述是 Markdown 表格和散文，不是行列表。深度压缩改成按空行/标题切块打分：

- 加分：含参数格式（格式/必须/枚举/ISO/经度）或适用边界（不适用/别用/禁止）的块
- 减分：示例、⚠️ 提醒、超过 800 字的大段
- 每个工具硬上限 800 字，高价值块超预算时截断保留

实测（257 个真实工具描述）：**125,393 → 68,801 字（省 45.1%）**，cli 从 34,881 压到 800。

<p><b>不保证没有副作用</b>：如果想彻底稳妥，就只开「语气契约」和「选工具规则替换」，两个工具描述开关都关掉。工具调用变笨时关掉开关即可恢复。</p>
## 更新

关于页的「获取更新」会读取 [releases.atom](https://github.com/siweina/MISoul/releases.atom)（普通网页，不限流；不用 api.github.com，因为未登录的 API 按 IP 限流 60 次/小时），发现新版本就下载 APK 并交给系统安装器。

发布时请保持附件命名约定，否则自动更新拼不出下载地址：

```
MISoul-<tag>.apk     例如  MISoul-v0.2.1-beta.1.apk
```

离线时可以用「从本地 APK 安装」手动选择文件。
## 已知限制

- 混淆名（`rs.a`、`ck.o0`）随小爱版本变化，升级后需要适配；模块会记录 `hook skipped`
- 品牌文案是文本层替换，若官方改成 Compose 渲染或拼进长句，这一处会失效（其余不受影响）
- 拨号密令**不能直接弹出设置页**：后台广播里 `startActivity` 会被系统的 BAL 拦掉，所以用通知兜底
- 只支持 `com.miui.voiceassist`，不支持其他小爱版本线

## 构建

需要 JDK 21 与 Android SDK 37：

```bash
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/release/app-release.apk`。release 构建开启了 R8 与资源压缩，体积约 3 MB。

## 许可

[MIT](LICENSE)

界面组件来自 [Miuix](https://github.com/compose-miuix-ui/miuix)（Apache-2.0），液态玻璃底栏改编自 [HyperNavBar](https://github.com/HyperNavBar/HyperNavBar) 与 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（均为 Apache-2.0），工程结构参考了 [HyperMusicCover](https://github.com/zyl6932/HyperMusicCover) 与 [InstallerX](https://github.com/iamr0s/InstallerX) 的做法（未复制其源码，后者为 AGPL-3.0）。

本项目与小米、小爱、DeepSeek 均无隶属关系。
