# 定时任务放行：问题、代码与效果

> 适用版本：超级小爱 `com.miui.voiceassist` **8.3.30.4420 / versionCode 508003030**
> （分析用的就是这个版本的完整 APK，244.34 MB，
> SHA256 `4e6eccc162a99f53be7f6ddabe4ecaed768ac57d63a35408f6526ac4d5c60869`）
>
> 本文只讲这一个可选功能。「先规划后执行」见 [flow-plan.md](flow-plan.md)，
> 人格替换、上下文瘦身等原有功能见 README 正文。

## 1. 用户遇到的问题

- 非会员在超级小爱里让它建一个**定时 AI 任务**（例如“每天早上 8 点提醒我 / 到点跑个任务”）
  或**场景自动化**时，小爱在对话里拒绝，话术大意是“需要会员才能执行自动化”。
- 这个拒绝**不是一个 UI 弹窗**，而是模型在对话里说出来的。
- 关键误区：如果只去改界面文案、改按钮、改提示语，是改不到它的——
  因为包里根本没有“需要会员才能执行自动化”这句原话。

## 2. 真正的关卡在哪里（静态分析结果）

对 8.3.30.4420 的 31 个 dex 做了字符串与类/方法核对，结论如下：

### 2.1 拒绝是模型现场组织出来的

- 全 dex 搜索“需要会员”“会员才能”，命中 **0**。
- 真正存在的、与拒绝对应的内置文案在 `classes28.dex` 里，是工具返回给模型后
  模型按指令组织出来的，例如：
  - 免费版被挡时，模型被要求“直接如实告知用户免费版做不到”，并以
    “请到「我的」→「用量与订阅」中，升级会员。”结尾；
  - 查不到会员状态时，模型被要求说“暂时无法确认会员状态，本次定时任务未创建”，
    并且不要断言用户身份、不要引导开通、不要承诺稍后自动重试。
- 所以关卡不在文案层，而在**工具层返回的拒绝结果**。

### 2.2 定时任务的会员关（本地工具层，未混淆）

| 位置 | 作用 |
|---|---|
| `com.aios.osbot.membership.MembershipRepository`（`classes27.dex`） | 会员仓库：`StatusData.level`（Integer）、`refresh()` / `requestLevel()` 从账号/服务端刷新等级，本地只缓存 `membership_state`（SharedPreferences） |
| `MembershipRepository.checkTimerAccess()` | 只返回三种状态的枚举 `MembershipRepository$b`：`ALLOWED` / `BLOCKED_FREE` / `BLOCKED_UNVERIFIED` |
| `com.aios.osbot.tools.system.TimerTool`（`classes28.dex`） | 定时任务工具本体 |
| `TimerTool.membershipBlockOrNull()` | 创建前检查：返回非空即挡住，返回 null 即放行 |
| `TimerTool.enabledUpdateMembershipBlockOrNull(boolean)` | 修改/启用定时任务时的同类检查 |
| `TimerTool.validateTimerCreationGuardrails()`、`getBlockTimerCreationMessage()` | 创建守卫与拒绝文案来源 |

调用关系（简化）：

```
用户说“建定时任务”
  -> 模型调 timer set
  -> TimerTool.membershipBlockOrNull()
       -> MembershipRepository.checkTimerAccess()  // 查服务端刷新过的等级
            -> BLOCKED_FREE：返回拒绝，模型说“免费版做不到……去升级会员”
            -> BLOCKED_UNVERIFIED：返回拒绝，模型说“暂时查不到会员状态”
            -> ALLOWED：继续建任务，写入本地 timers 表并排闹钟
```

### 2.3 场景自动化：没有找到独立的会员关

- 包里确实有智能家居场景类（`xiaomihome MIoTManualScene`、`SceneNluMatcher`），
  也有“本地执行米家自动化规则”的文案，但**没有在定时任务之外找到第二个会员检查**。
- 推论（不是保证）：
  - 如果场景是“走定时任务去执行场景”，那它走的就是上面同一条 TimerTool 路径，
    本功能就覆盖它；
  - 如果场景是“走米家云端建联动规则”，那道关在云端，本地 hook 帮不上，
    只能拿一条具体场景命令实测后才能定论。

### 2.4 视频生成：不是独立的判断，共用同一个 checkTimerAccess

- 视频生成工具 `video_builder`（文案：视频生成模型 → 生成视频文件，文生视频 /
  图生视频均支持）确实有自己的拒绝文案（`video_builder_membership_required` /
  `video_builder_membership_unverified`，即“当前账号为免费版，视频生成是会员功能”），
  但它的关口**不是另一个独立的会员判断**：
  - 付费功能门 `PaidFeatureGate`（`Ldl/z3$a$b`）的 `verdictOf(VIDEO_BUILDER)`：
    先调 `MembershipRepository.checkTimerAccess()`，结果是 ALLOWED 就返回 null（=放行），
    否则返回拒绝（免费版代号 `50201`，未验证为 `MEMBERSHIP_UNVERIFIED`）；
  - 聊天界面的视频模式关口（`Lhu/o5$d0`）同样先调 `checkTimerAccess()`，
    ALLOWED 才设置 activeTaskMode，否则弹上述两条提示并清除任务模式。
- 结论：**本开关打开后，定时任务和视频生成的客户端关口同时放行，无法只开其中一个。**
  但视频是云端沙箱渲染，服务端仍可能在真正生成时再查一次，客户端放行
  不等于一定出片，必须真机实测（见第 5 节）。

## 3. 这个功能改了哪些代码

| 文件 | 改动 |
|---|---|
| `PersonaHook.java` | `KEY_VIP_UNLOCK`；`install()` 里的 `hookVipUnlock()`（失败时只记日志，不影响人格等原有功能）；`vipUnlock()` 只读开关，**与人格替换开关、先规划后执行开关相互独立** |
| `PersonaProvider.java` | `call()` 里多返回一个布尔：`KEY_VIP_UNLOCK`（默认 false） |
| `MainActivity.kt` | 功能页「定时任务」区一个开关 `放行定时任务的会员校验` + 两条边界提示；状态存在同一个 `persona` SharedPreferences 里 |
| `docs/timer-access.md`（本文件） | 问题分析与效果说明 |

### hook 的具体做法

```java
// 只在开关打开时生效，关掉即原逻辑
MembershipRepository.checkTimerAccess()  ->  ALLOWED
    // 通过反射取枚举常量 MembershipRepository$b.ALLOWED；
    // 拿不到就走原逻辑，绝不硬编一个假值

TimerTool.membershipBlockOrNull()                  ->  null  // = 不挡
TimerTool.enabledUpdateMembershipBlockOrNull(...)  ->  null  // = 不挡
```

补充说明（给看代码的人）：

- 这三个方法都是 Kotlin suspend 方法，Java 签名是 `(Continuation) -> Object`。
  未挂起时直接返回结果对象就是正常返回值：返回 `null` / 枚举常量不是“绕过协程”，
  而是这个方法本身的合法返回值。
- 类名没有混淆（不像人格 hook 要对付的 `ck.*` / `rs.*` / `sj.*`），
  所以 hook 直接用全限定类名，不用猜。
- 开关默认关闭：不打开它，模块在这三处完全不介入。

## 4. 效果：开关关 vs 开关开

| 场景 | 开关关 | 开关开 |
|---|---|---|
| 非会员说“明天 8 点提醒我 / 到点跑个 AI 任务” | 模型拒绝，话术是免费版做不到、去“用量与订阅”升级会员 | 创建检查放行，任务能建进本地 timers 表并排闹钟（见第 5 节的实测要求） |
| 修改 / 重新启用一个已建的定时任务 | 同样可能被会员检查挡住 | 修改检查同样放行 |
| 查看已建任务列表（timer list） | 本来就不判断会员状态（包内文案明确写了） | 不变 |
| 场景自动化 | 若走定时任务路径，随定时任务一起放行；若走米家云端，仍可能被云端挡 | 同左，需实测具体命令 |
| 视频生成 | 免费版被挡（客户端关口 + 云端生成） | 客户端关口随本开关一起放行；云端是否再拦需实测，不保证出片 |
| 人格、形象、工具筛选、描述瘦身、先规划后执行 | 各自开关决定 | **不受本开关影响** |

## 5. 安装与实测（必须做，别只看创建成功）

1. 安装 APK，在 LSPosed 里启用 MISoul，作用域只勾选「超级小爱」。
2. **杀掉超级小爱进程再打开**：`am force-stop` 杀不掉小爱主进程，要用
   `su -c 'kill -9 $(pidof com.miui.voiceassist)'`（模块设置页的重启按钮就是这么做的）。
3. 打开本模块 → 功能 → 定时任务 → 打开「放行定时任务的会员校验」。
4. 先测创建：让小爱建一个 **几分钟后** 的定时任务（不要先测明天/下周），看它是否不再说会员拒绝、
   且任务出现在任务列表里。
5. **再测执行（最关键的一步）**：等到那个几分钟后的时间点，看任务到底有没有执行。
   - 能执行，才算真的可用；
   - 如果到点被跳过 / 日志出现 `timer_skipped_membership_required` 或
     “One-time timer, pausing until membership can be verified”，
     说明执行侧又查了一次——这是第 6 节写明的已知边界，不是创建侧 hook 写错了。
6. 再测一条你自己的场景自动化命令，确认它是走定时任务还是走米家云端。
7. 再测视频生成：让小爱生成一段短视频，看它是否不再说会员拒绝、且最终是否
   真的返回视频文件。只到“不再拒绝”不算成功——云端生成侧可能另有校验，
   若生成失败 / 长时间不出片，说明服务端那一关本地 hook 绕不过去。

日志：`adb logcat | grep MISoul`，放行生效时有一行
`定时任务放行：checkTimerAccess -> ALLOWED`。

**已确认的真机结果：** 未开开关时建定时任务被拒（免费版不支持创建由 AI 到点自动执行的
定时任务）；开开关后“支付宝签到”定时任务成功建进列表并启用，模型不再说会员拒绝。
创建可用不等于到点执行可用——到点执行、视频云端出片、米家云端场景仍按第 6 节实测。
视频生成在真机上被服务端以“额度不足”挡下，本地放行确实不等于出片。

## 6. 诚实边界与风险（读完再决定用不用）

- **创建放行 ≠ 执行保证**：执行侧（`TimerAlarmReceiver` / `TaskForegroundService`）
  手里也有会员仓库，包内日志明确有“一次性定时任务，等会员状态能确认时再暂停”
  和 `timer_skipped_membership_required / unverified`。本功能只做了创建/修改侧的放行，
  执行侧是否同样放行必须按第 5 节实测，不能提前承诺（创建侧已在真机上确认可用）。
- **服务端仍可能挡**：会员等级本身来自账号/服务端刷新，本 hook 没有改账号状态、
  没有伪造购买、没有改服务端数据。任何需要服务端真正校验的任务（例如米家云端规则、
  需要服务端配额的能力）仍可能失败——视频生成的“额度不足”就是实例。
- **合规与账号风险**：这是在本地放行一款应用的会员检查，是否违反该应用的服务条款、
  是否影响账号，由使用者自己判断。本项目仅作技术分析与学习交流，不提供任何
  “保证可用 / 保证不封号”的承诺。
- **版本漂移**：分析只针对 8.3.30.4420。超级小爱升级后类名/方法名可能变化，
  hook 找不到目标时会只记日志、不影响小爱正常运行（`install()` 里每个 hook 都有
  try/catch 降级），但放行也随之失效，需要重新分析新版。

## 7. 构建

需要 JDK 21 与 Android SDK 37（`compileSdk 37` / `buildToolsVersion 37.0.0`）：

```bash
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

没有 `keystore.properties` 时退回 debug 签名；正式发布必须用同一个 keystore，
否则每次构建签名都不同、无法覆盖升级（做法见 [signing.md](signing.md)）。
