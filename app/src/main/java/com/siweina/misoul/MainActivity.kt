package com.siweina.misoul

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siweina.misoul.ui.component.liquid.IosLiquidGlassNavigationBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.IconButtonDefaults
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    // 从二级页（独立 Activity）返回时递增，触发首页重新读一次配置
    private val resumeTick = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 拨号密令的兜底通知需要通知权限（33+）
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                App(resumeTick.intValue)
            }
        }
    }
}

private class UiState(
    val enabled: Boolean,
    val setEnabled: (Boolean) -> Unit,
    val vipUnlock: Boolean,
    val setVipUnlock: (Boolean) -> Unit,
    val flowPlan: Boolean,
    val setFlowPlan: (Boolean) -> Unit,
    val fixPrompt: Boolean,
    val setFixPrompt: (Boolean) -> Unit,
    val profileEnabled: Boolean,
    val setProfileEnabled: (Boolean) -> Unit,
    val name: String,
    val soul: String,
    val profileName: String,
    val signature: String,
    val welcome: String,
    val textMap: String,
    val styleContract: String,
    val rulesText: String,
    val deepTrim: Boolean,
    val setDeepTrim: (Boolean) -> Unit,
    val toolTrim: Boolean,
    val setToolTrim: (Boolean) -> Unit,
    val open: (Field) -> Unit,
    val openRules: () -> Unit,
    val openTools: () -> Unit,
    val reset: () -> Unit,
    val iconHidden: Boolean,
    val setIconHidden: (Boolean) -> Unit,
    val pickUpdate: () -> Unit,
    val checkUpdate: () -> Unit,
    val openLink: (String) -> Unit,
    val info: List<InfoRow>,
    val xiaoaiVersion: String,
    val moduleVersion: String,
)

@Composable
private fun App(refreshTick: Int) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val prefs = remember { context.getSharedPreferences(PersonaHook.PREFS, Context.MODE_PRIVATE) }
    val defaultSoul = remember { readAsset(context, "soul.default.md") }
    val pageBg = if (dark) Color.Black else Color(0xFFF2F2F7)
    val backdrop = rememberLayerBackdrop {
        drawRect(pageBg)
        drawContent()
    }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { 4 }

    var enabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_ENABLED, false))
    }
    var vipUnlock by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_VIP_UNLOCK, false))
    }
    var flowPlan by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_FLOW_PLAN, false))
    }
    var fixPrompt by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_FIX_PROMPT, true))
    }
    var profileEnabled by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_PROFILE_ENABLED, true))
    }
    var confirmReset by remember { mutableStateOf(false) }
    // 底栏高亮与滚动都以「目标页」为准：点击立刻改，手势滑完落定后同步。
    var targetTab by remember { mutableIntStateOf(0) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var pageWidthPx by remember { mutableStateOf(0f) }
    var checkingUpdate by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var soul by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var signature by remember { mutableStateOf("") }
    var welcome by remember { mutableStateOf("") }
    var textMap by remember { mutableStateOf("") }
    var styleContract by remember { mutableStateOf("") }
    var rulesText by remember { mutableStateOf("") }
    var toolTrim by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_TOOL_TRIM, true))
    }
    var deepTrim by rememberSaveable {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_TOOL_DEEP, false))
    }

    val sync: () -> Unit = {
        name = prefs.getString(PersonaHook.KEY_NAME, null) ?: PersonaHook.DEFAULT_NAME
        soul = prefs.getString(PersonaHook.KEY_SOUL, null) ?: defaultSoul
        profileName = prefs.getString(PersonaHook.KEY_PROFILE_NAME, null)
            ?: PersonaHook.DEFAULT_PROFILE_NAME
        signature = prefs.getString(PersonaHook.KEY_PROFILE_SIGNATURE, null)
            ?: PersonaHook.DEFAULT_PROFILE_SIGNATURE
        welcome = prefs.getString(PersonaHook.KEY_PROFILE_WELCOME, null)
            ?: PersonaHook.DEFAULT_PROFILE_WELCOME
        textMap = prefs.getString(PersonaHook.KEY_TEXT_MAP, null).orEmpty()
        rulesText = prefs.getString(PersonaHook.KEY_RULES_TEXT, null).orEmpty()
        styleContract = prefs.getString(PersonaHook.KEY_STYLE_CONTRACT, null)
            ?: PersonaHook.DEFAULT_STYLE_CONTRACT
    }
    remember(refreshTick) { sync() }

    val xiaoaiVersion = remember { xiaoaiVersion(context) }
    val moduleVersion = remember { BuildConfig.VERSION_NAME }
    val info = remember(refreshTick) {
        DeviceInfo.rows(context, moduleVersion, xiaoaiVersion)
    }

    var iconHidden by rememberSaveable { mutableStateOf(LauncherIcon.isHidden(context)) }
    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { UpdateInstaller.stage(context, uri) }
            when {
                file == null -> Toast.makeText(context, "读不出这个 APK", Toast.LENGTH_SHORT).show()
                !UpdateInstaller.handOff(context, file) ->
                    Toast.makeText(context, "没有应用能打开 APK", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val state = UiState(
        enabled = enabled,
        setEnabled = {
            enabled = it
            prefs.edit().putBoolean(PersonaHook.KEY_ENABLED, it).apply()
        },
        vipUnlock = vipUnlock,
        setVipUnlock = {
            vipUnlock = it
            prefs.edit().putBoolean(PersonaHook.KEY_VIP_UNLOCK, it).apply()
        },
        flowPlan = flowPlan,
        setFlowPlan = {
            flowPlan = it
            prefs.edit().putBoolean(PersonaHook.KEY_FLOW_PLAN, it).apply()
        },
        fixPrompt = fixPrompt,
        setFixPrompt = {
            fixPrompt = it
            prefs.edit().putBoolean(PersonaHook.KEY_FIX_PROMPT, it).apply()
        },
        profileEnabled = profileEnabled,
        setProfileEnabled = {
            profileEnabled = it
            prefs.edit().putBoolean(PersonaHook.KEY_PROFILE_ENABLED, it).apply()
        },
        name = name,
        soul = soul,
        profileName = profileName,
        signature = signature,
        welcome = welcome,
        textMap = textMap,
        styleContract = styleContract,
        rulesText = rulesText,
        deepTrim = deepTrim,
        setDeepTrim = {
            deepTrim = it
            prefs.edit().putBoolean(PersonaHook.KEY_TOOL_DEEP, it).apply()
        },
        toolTrim = toolTrim,
        setToolTrim = {
            toolTrim = it
            prefs.edit().putBoolean(PersonaHook.KEY_TOOL_TRIM, it).apply()
        },
        open = { field -> context.startActivity(DetailActivity.intent(context, field)) },
        openTools = {
            context.startActivity(android.content.Intent(context, ToolsActivity::class.java))
        },
        openRules = {
            context.startActivity(android.content.Intent(context, RulesActivity::class.java))
        },
        reset = { confirmReset = true },
        iconHidden = iconHidden,
        setIconHidden = { hidden ->
            LauncherIcon.setHidden(context, hidden)
            iconHidden = LauncherIcon.isHidden(context)
            Toast.makeText(
                context,
                if (hidden) "已隐藏，拨号 *#*#${LauncherIcon.SECRET_CODE}#*#* 打开" else "图标已恢复",
                Toast.LENGTH_SHORT,
            ).show()
        },
        pickUpdate = { pickApk.launch(arrayOf("application/vnd.android.package-archive")) },
        checkUpdate = {
            if (!checkingUpdate) {
                checkingUpdate = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        UpdateApi.check(BuildConfig.VERSION_NAME)
                    }
                    when (result) {
                        is UpdateApi.Result.UpToDate -> Toast.makeText(
                            context,
                            "已是最新版本 ${BuildConfig.VERSION_NAME}",
                            Toast.LENGTH_SHORT,
                        ).show()

                        is UpdateApi.Result.Failed -> Toast.makeText(
                            context,
                            "检查失败：${result.message}",
                            Toast.LENGTH_LONG,
                        ).show()

                        is UpdateApi.Result.Found -> {
                            Toast.makeText(
                                context,
                                "发现 ${result.release.tag}，开始下载",
                                Toast.LENGTH_SHORT,
                            ).show()
                            val file = withContext(Dispatchers.IO) {
                                UpdateApi.download(context, result.release)
                            }
                            if (file == null) {
                                Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                            } else if (!UpdateInstaller.handOff(context, file)) {
                                Toast.makeText(context, "没有应用能打开 APK", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                    checkingUpdate = false
                }
            }
        },
        openLink = { url ->
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(url),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                Toast.makeText(context, "没有浏览器能打开链接", Toast.LENGTH_SHORT).show()
            }
        },
        info = info,
        xiaoaiVersion = xiaoaiVersion,
        moduleVersion = moduleVersion,
    )

    val items = remember {
        listOf(
            NavigationItem("概览", MiuixIcons.Home),
            NavigationItem("功能", MiuixIcons.Tune),
            NavigationItem("设置", MiuixIcons.Settings),
            NavigationItem("关于", MiuixIcons.Info),
        )
    }
    val canBlur = Build.VERSION.SDK_INT >= 33 && isRuntimeShaderSupported()


    // 底栏高亮 = 目标页。点击立刻改，手势滑动落定后同步；不再跟 currentPage/targetPage 走，
    // 否则跨页滚动经过每一页都会让它动一次，玻璃每动一次就要重画一遍。
    androidx.compose.runtime.LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
            .collect { page ->
                // 程序化滚动被打断时 pager 会在中间页短暂落定，这时不能把目标页拽回去
                if (!programmaticScroll) targetTab = page
            }
    }

    // 由「目标页」驱动滚动，而不是在点击时算一次距离：
    // 上一次动画被取消时 pager 可能停在两页中间，若基于 currentPage 重算就会从错误起点出发，
    // 表现为「胶囊动了页面没动」。这里每帧都按当前实际偏移算剩余距离，天然能续上。
    androidx.compose.runtime.LaunchedEffect(targetTab, pageWidthPx) {
        val width = pageWidthPx
        if (width <= 0f) return@LaunchedEffect
        val target = targetTab
        val current = pagerState.currentPage + pagerState.currentPageOffsetFraction
        val distance = target - current
        if (kotlin.math.abs(distance) < 0.01f) return@LaunchedEffect
        programmaticScroll = true
        try {
            pagerState.scroll {
                var consumed = 0f
                androidx.compose.animation.core.animate(
                    initialValue = 0f,
                    targetValue = distance * width,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = (240 + 90 * kotlin.math.abs(distance)).toInt(),
                        easing = androidx.compose.animation.core.FastOutSlowInEasing,
                    ),
                ) { value, _ ->
                    scrollBy(value - consumed)
                    consumed = value
                }
            }
        } finally {
            programmaticScroll = false
        }
    }

    Scaffold(
        bottomBar = {
            IosLiquidGlassNavigationBar(
                modifier = Modifier.padding(horizontal = 12.dp),
                items = items,
                selectedIndex = targetTab,
                onItemClick = { index -> targetTab = index },
                backdrop = backdrop,
                isBlurActive = canBlur,
            )
        },
    ) { padding ->
        val barHeight = padding.calculateBottomPadding()
        Box(Modifier.fillMaxSize().background(pageBg).layerBackdrop(backdrop)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
                    .onSizeChanged {
                        pageWidthPx = it.width.toFloat()
                    },
                // 四页全部预组：切换要滑过中间页，不预组就会在那页掉一帧
                beyondViewportPageCount = 3,
            ) { index ->
                when (index) {
                    0 -> OverviewPage(state, barHeight)
                    1 -> FeaturesPage(state, barHeight)
                    2 -> SettingsPage(state, barHeight)
                    else -> AboutPage(state, barHeight)
                }
            }

            OverlayDialog(
                show = confirmReset,
                title = "恢复默认人格？",
                summary = "人格全文换回内置的大肥鱼人设，其他设置不变。",
                onDismissRequest = { confirmReset = false },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton("取消", { confirmReset = false }, Modifier.weight(1f))
                    PrimaryButton(
                        "恢复",
                        {
                            prefs.edit()
                                .putString(PersonaHook.KEY_SOUL, defaultSoul)
                                .putString(PersonaHook.KEY_NAME, PersonaHook.DEFAULT_NAME)
                                .apply()
                            sync()
                            confirmReset = false
                            Toast.makeText(context, "已恢复默认", Toast.LENGTH_SHORT).show()
                        },
                        Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageColumn(bottomPadding: Dp, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottomPadding + 24.dp),
            content = content,
        )
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String? = null) {
    Text(
        title,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = if (subtitle == null) 12.dp else 4.dp),
    )
    if (subtitle != null) {
        Text(
            subtitle,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp),
        )
    }
}

// ---------------- 概览 ----------------

@Composable
private fun OverviewPage(state: UiState, bottomPadding: Dp) {
    PageColumn(bottomPadding) {
        val context = LocalContext.current
        val prefs = remember {
            context.getSharedPreferences(PersonaHook.PREFS, Context.MODE_PRIVATE)
        }
        var lastSeen by remember { mutableStateOf(prefs.getLong(PersonaProvider.KEY_HOOK_SEEN, 0L)) }
        // 每 2 秒重读一次心跳，小爱重启后状态会自动变绿，不用退出重进
        LaunchedEffect(Unit) {
            while (true) {
                lastSeen = prefs.getLong(PersonaProvider.KEY_HOOK_SEEN, 0L)
                delay(2000)
            }
        }
        val seenAgo = if (lastSeen > 0) System.currentTimeMillis() - lastSeen else Long.MAX_VALUE
        // 10 分钟内收到过 hook 的配置请求，才算真的注入成功
        val hooked = seenAgo < 10 * 60 * 1000L
        Row(
            Modifier.fillMaxWidth().padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { PageTitle("概览") }
            IconButton(onClick = { restartXiaoAi(context) }) {
                Icon(
                    MiuixIcons.Refresh,
                    "重启小爱",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        }
        StatusCard(state.enabled, hooked, state.moduleVersion, state.xiaoaiVersion)
        Section("设备") {
            state.info.forEach { row -> StackedRow(row.label, row.value) }
        }
        Section("注入") {
            StackedRow("方式", "LSPosed · libxposed API 102")
            StackedRow("作用域", "com.miui.voiceassist")
            StackedRow("人格", if (state.enabled) "替换中" else "官方人格")
            StackedRow(
                "最近通信",
                when {
                    lastSeen <= 0L -> "尚未收到"
                    seenAgo < 60_000L -> "刚刚"
                    seenAgo < 3_600_000L -> "${seenAgo / 60_000L} 分钟前"
                    else -> "${seenAgo / 3_600_000L} 小时前"
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Hint("改完直接生效；小爱里新建一次对话即可看到新人设。")
    }
}

@Composable
private fun StatusCard(enabled: Boolean, hooked: Boolean, moduleVersion: String, xiaoai: String) {
    val active = enabled && hooked
    val accent = when {
        active -> Color(0xFF17A34A)
        enabled -> Color(0xFFB45309)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val container = when {
        active -> Color(0xFFE3F7E9)
        enabled -> Color(0xFFFDF3E3)
        else -> MiuixTheme.colorScheme.surfaceContainer
    }
    Card(
        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = container, contentColor = accent),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when {
                        active -> "已激活"
                        enabled -> "等待注入"
                        else -> "未启用"
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(moduleVersion, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "小爱 $xiaoai",
                    style = MiuixTheme.textStyles.footnote1,
                    color = accent.copy(alpha = 0.75f),
                )
            }
            ApprovalArc(active = enabled, color = accent)
        }
    }
}

/** LSPosed 那个「圆环 + 对勾」的状态图标，用 Canvas 画的，不带资源。 */
@Composable
private fun ApprovalArc(active: Boolean, color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(84.dp)) {
        val stroke = 8.dp.toPx()
        drawArc(
            color = color.copy(alpha = if (active) 0.95f else 0.45f),
            startAngle = -80f,
            sweepAngle = 290f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
        if (active) {
            val w = size.width
            val h = size.height
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.28f, h * 0.52f)
                lineTo(w * 0.44f, h * 0.68f)
                lineTo(w * 0.74f, h * 0.34f)
            }
            drawPath(
                path = path,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}

/** 标题加粗、数值灰色放下一行——LSPosed 状态卡的排法。 */
@Composable
private fun StackedRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

// ---------------- 功能（人格 + 形象） ----------------

@Composable
private fun FeaturesPage(state: UiState, bottomPadding: Dp) {
    PageColumn(bottomPadding) {
        val context = LocalContext.current
        val prefs = remember {
            context.getSharedPreferences(PersonaHook.PREFS, Context.MODE_PRIVATE)
        }
        PageTitle("功能", "人格与界面形象")
        Section("人格") {
            ArrowPreference(
                title = "自称",
                summary = state.name,
                onClick = { state.open(Field.Name) },
            )
            ArrowPreference(
                title = "人格全文",
                summary = "${state.soul.length} 字 · 点按编辑",
                onClick = { state.open(Field.Soul) },
            )
            ArrowPreference(
                title = "语气契约",
                summary = "${state.styleContract.length} 字 · 追加在系统提示末尾",
                onClick = { state.open(Field.StyleContract) },
            )
            ArrowPreference(
                title = "选工具规则",
                summary = if (state.rulesText.isBlank()) {
                    "官方 24,067 字 · 点按换成你自己的"
                } else {
                    "已自定义 ${state.rulesText.length} 字"
                },
                onClick = state.openRules,
            )
            ArrowPreference(
                title = "文案替换",
                summary = if (state.textMap.isBlank()) {
                    "改开场白、「小爱陪伴」这类固定文案"
                } else {
                    "${state.textMap.lines().count { it.isNotBlank() }} 条"
                },
                onClick = { state.open(Field.TextMap) },
            )
        }
        Section("形象") {
            ArrowPreference(
                title = "显示名",
                summary = state.profileName.ifBlank { "未设置" },
                onClick = { state.open(Field.ProfileName) },
            )
        }
        Section("上下文") {
            ArrowPreference(
                title = "工具筛选",
                summary = "按类别决定给模型看哪些工具，默认全部",
                onClick = state.openTools,
            )
        }
        Section("会员解锁") {
            SwitchPreference(
                title = "会员解锁（定时任务 + 视频生成）",
                summary = "绕过本地会员检查，定时任务与视频生成共用同一判断，与人格替换无关，默认关闭",
                checked = state.vipUnlock,
                onCheckedChange = state.setVipUnlock,
            )
        }
        Hint("只放行本地检查。定时任务建好后到点能否执行仍需实测；视频生成在云端渲染，服务端仍可能在生成时再拦，不一定出片。场景如果走米家云端建规则，本地解锁帮不上。")
        Hint("视频生成的客户端关口与定时任务共用同一会员判断，所以本开关打开后两者同时放行，不能只开其中一个。")
        Section("自动化流程") {
            SwitchPreference(
                title = "先规划后执行，成功流程优先复用",
                summary = "自动化先检索流程再执行：没记录时先规划步骤，有成功记录时优先用记录，与会员解锁无关，默认关闭",
                checked = state.flowPlan,
                onCheckedChange = state.setFlowPlan,
            )
            ArrowPreference(
                title = "清除已记录的成功流程",
                summary = "应用改版导致旧流程失效时点这里清掉",
                onClick = {
                    prefs.edit().remove(PersonaHook.KEY_FLOW_STORE).apply()
                    android.widget.Toast.makeText(context, "已清除", android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        }
        Hint("只对界面自动化生效：第一次先让云端规划步骤，某条分步骤指令成功后记下来，下次同样任务优先用它。含支付、转账、付款、下单、购买、充值、提现的指令一律不补全。")
        Section("开关") {
            SwitchPreference(
                title = "人格替换",
                summary = "关掉即回官方人格，无需卸载",
                checked = state.enabled,
                onCheckedChange = state.setEnabled,
            )
            SwitchPreference(
                title = "修正身份句",
                summary = "官方 prompt.md 要求自称「超级小爱」",
                checked = state.fixPrompt,
                onCheckedChange = state.setFixPrompt,
            )
            SwitchPreference(
                title = "界面显示名与欢迎语",
                summary = "改 config.json / profile / 品牌文案",
                checked = state.profileEnabled,
                onCheckedChange = state.setProfileEnabled,
            )
            SwitchPreference(
                title = "工具描述瘦身",
                summary = "只删示例与提醒，参数格式和适用边界保留",
                checked = state.toolTrim,
                onCheckedChange = state.setToolTrim,
            )
            SwitchPreference(
                title = "深度压缩（实验）",
                summary = "按块砍到每个工具 ≤1500 字，省得最多，但可能影响工具调用",
                checked = state.deepTrim,
                onCheckedChange = state.setDeepTrim,
            )
        }
        Hint("深度压缩会砍掉示例和「意图→命令」决策表，只保留参数格式与适用边界。")
        Hint("不保证没有副作用：如果工具调用变笨（用错工具、参数瞎填），把这个开关关掉即可。")
        Hint("整份替换 soul.md；身份句只改 prompt.md 里那一句，其余硬规则不动。")
    }
}

// ---------------- 设置 ----------------

@Composable
private fun SettingsPage(state: UiState, bottomPadding: Dp) {
    PageColumn(bottomPadding) {
        PageTitle("设置", "入口、更新与维护")
        Section("入口") {
            SwitchPreference(
                title = "隐藏桌面图标",
                summary = "隐藏后拨号 *#*#${LauncherIcon.SECRET_CODE}#*#* 打开；LSPosed 里也能进",
                checked = state.iconHidden,
                onCheckedChange = state.setIconHidden,
            )
        }
        Section("更新") {
            ArrowPreference(
                title = "从本地 APK 更新",
                summary = "选一个 APK，交给系统安装器",
                onClick = state.pickUpdate,
            )
        }
        Section("维护") {
            ArrowPreference(
                title = "恢复默认人格",
                summary = "换回内置的大肥鱼人设",
                onClick = state.reset,
            )
        }
        Hint("日志：adb logcat | grep MISoul")
    }
}

// ---------------- 关于 ----------------

@Composable
private fun AboutPage(state: UiState, bottomPadding: Dp) {
    PageColumn(bottomPadding) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2E6CFF), Color(0xFF56C4FF), Color(0xFF78E8FF)),
                    ),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "MISoul",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            state.moduleVersion,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))

        Section("更新") {
            ArrowPreference(
                title = "获取更新",
                summary = "检查 GitHub Releases 并交给系统安装器",
                onClick = state.checkUpdate,
            )
            ArrowPreference(
                title = "从本地 APK 安装",
                summary = "离线时用，选一个 APK 交给系统安装器",
                onClick = state.pickUpdate,
            )
        }
        Section("链接") {
            ArrowPreference(
                title = "项目地址",
                summary = "github.com/${UpdateApi.REPO}",
                onClick = { state.openLink("https://github.com/${UpdateApi.REPO}") },
            )
            ArrowPreference(
                title = "问题反馈",
                summary = "Issues · 附上日志与版本号",
                onClick = { state.openLink("https://github.com/${UpdateApi.REPO}/issues") },
            )
            ArrowPreference(
                title = "更新日志",
                summary = "Releases",
                onClick = { state.openLink("https://github.com/${UpdateApi.REPO}/releases") },
            )
        }
        Section("关于") {
            StackedRow("作用域", "com.miui.voiceassist")
            StackedRow("小爱", state.xiaoaiVersion)
            StackedRow("模块", state.moduleVersion)
        }
        Section("致谢") {
            StackedRow("界面组件", "Miuix")
            StackedRow("液态玻璃", "AndroidLiquidGlass")
            StackedRow("架构参考", "HyperMusicCover")
        }
        Hint("MISoul 只在本机内存里替换人格文本，不改小爱的任何文件。")
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(14.dp))
    SmallTitle(title)
    Card(Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun PrimaryButton(text: String, click: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        text,
        click,
        modifier,
        colors = ButtonDefaults.textButtonColors(
            color = MiuixTheme.colorScheme.primary,
            textColor = MiuixTheme.colorScheme.onPrimary,
        ),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        textAlign = TextAlign.Start,
    )
}

private fun readAsset(context: Context, name: String): String =
    runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")

private fun xiaoaiVersion(context: Context): String =
    runCatching {
        val info = context.packageManager.getPackageInfo("com.miui.voiceassist", 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("未安装")

/** 申请 root 后强杀小爱进程（系统会自己拉起）：am force-stop 对主进程无效，必须 kill -9。 */
private fun restartXiaoAi(context: android.content.Context) {
    Thread {
        // 用 echo 标记判断「shell 是否真的跑起来了」：
        // kill 在进程不存在时会返回非 0，不能拿它当成功标准。
        val result = runCatching {
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "su", "-c",
                    "kill -9 \$(pidof com.miui.voiceassist) >/dev/null 2>&1; echo __misoul_ok__",
                ),
            )
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)
            finished && output.contains("__misoul_ok__")
        }.getOrDefault(false)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                if (result) "已结束小爱进程，系统会自动拉起" else "失败：请确认已授予 root 权限",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }.start()
}
