package com.siweina.misoul

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import com.siweina.misoul.ui.component.AppCard

/** 工具筛选：按类别决定这次会话里给模型看哪些工具。 */
class ToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                ToolsScreen(onDone = { finish() })
            }
        }
    }
}

private data class ToolGroup(
    val prefix: String,
    val label: String,
    val count: Int,
    val chars: Int,
)

// 场景组：按「这一趟大概需要什么能力」划分，而不是按使用频率猜。
private val GO_OUT = setOf(
    "voiceassist", "timer", "deskclock", "control",
    "amap_car", "amap-navigation", "variflight", "ctrip_travel", "alipay",
)
private val WORK = setOf(
    "voiceassist", "deskclock", "calendar", "timer",
    "notes", "read", "send", "search", "file", "memory", "get", "set", "app",
)
// 自动档的安全集：这些永远保留，不管有没有用过。
private val SAFETY = setOf("voiceassist", "deskclock", "calendar", "timer")

private val HOME = setOf(
    "voiceassist", "deskclock", "calendar", "timer",
    "notes", "music", "gallery", "phone", "telecom",
)

@Composable
private fun ToolsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PersonaHook.PREFS, Context.MODE_PRIVATE) }
    val groups = remember { loadGroups(context) }

    var filterEnabled by remember {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_TOOL_FILTER, false))
    }
    var autoExpand by remember {
        mutableStateOf(prefs.getBoolean(PersonaHook.KEY_AUTO_EXPAND, true))
    }
    var keep by remember {
        mutableStateOf(
            prefs.getString(PersonaHook.KEY_TOOL_KEEP, null)
                .orEmpty()
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet(),
        )
    }
    val scrollBehavior = MiuixScrollBehavior()

    fun persist(enabled: Boolean, selection: Set<String>) {
        prefs.edit()
            .putBoolean(PersonaHook.KEY_TOOL_FILTER, enabled)
            .putString(PersonaHook.KEY_TOOL_KEEP, selection.joinToString(","))
            .apply()
    }

    val keptChars = groups.filter { it.prefix in keep }.sumOf { it.chars }
    val totalChars = groups.sumOf { it.chars }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "工具筛选",
                subtitle = if (!filterEnabled) {
                    "当前：全部 ${groups.size} 类"
                } else {
                    "当前：保留 ${keep.size} 类 · 约 ${keptChars / 1000}k / ${totalChars / 1000}k 字"
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(MiuixIcons.Back, "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
            ) {
                SmallTitle("使用情况")
                AppCard(Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "查看使用记录",
                        summary = "各个工具被调用了多少次、最近一次是什么时候",
                        onClick = {
                            context.startActivity(
                                android.content.Intent(context, UsageActivity::class.java),
                            )
                        },
                    )
                }

                SmallTitle("预设")
                AppCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PresetButton("全部", Modifier.weight(1f)) {
                            filterEnabled = false
                            keep = emptySet()
                            persist(false, emptySet())
                        }
                        PresetButton("出门", Modifier.weight(1f)) {
                            filterEnabled = true
                            keep = GO_OUT
                            persist(true, GO_OUT)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PresetButton("工作", Modifier.weight(1f)) {
                            filterEnabled = true
                            keep = WORK
                            persist(true, WORK)
                        }
                        PresetButton("居家", Modifier.weight(1f)) {
                            filterEnabled = true
                            keep = HOME
                            persist(true, HOME)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PresetButton("自动（用过 + 必备 + 关键词）", Modifier.weight(1f)) {
                            val used = usedPrefixes(context)
                            // 还没有任何使用记录时，「自动」退化成「工作」档，
                            // 否则只剩安全集 4 类，太激进
                            val auto = if (used.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "还没有使用记录，先按「工作」档保留",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                WORK
                            } else {
                                SAFETY + used
                            }
                            filterEnabled = true
                            keep = auto
                            persist(true, auto)
                        }
                    }
                }

                Hint("「全部」= 不过滤；出门 / 工作 / 居家 = 只保留对应能力的类别。")
                Hint("筛选按请求生效，改完直接发消息即可，不用重启小爱。")

                SmallTitle("类别")
                AppCard(Modifier.fillMaxWidth()) {
                    groups.forEach { group ->
                        SwitchPreference(
                            title = group.label.ifBlank { group.prefix },
                            summary = "${group.prefix} · ${group.count} 个工具 · 约 ${group.chars} 字",
                            // 不过滤时一律显示为「开」，避免看着像全被排除
                            checked = !filterEnabled || group.prefix in keep,
                            onCheckedChange = { on ->
                                val next = if (on) keep + group.prefix else keep - group.prefix
                                keep = next
                                filterEnabled = next.isNotEmpty()
                                persist(filterEnabled, next)
                            },
                        )
                    }
                }
                Hint("未列出的新类别（小爱升级后新增）默认保留，不会被静默砍掉。")
                if (filterEnabled && keep.isEmpty()) {
                    Hint("注意：没勾选任何类别时不会过滤，等同于「全部」。")
                }
            }
        }
    }
}

@Composable
private fun PresetButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    TextButton(
        text,
        onClick,
        modifier,
        colors = ButtonDefaults.textButtonColors(
            color = MiuixTheme.colorScheme.secondaryContainer,
            textColor = MiuixTheme.colorScheme.onSecondaryContainer,
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
    )
}

/** assets/tool_groups.txt：前缀|用途|工具数|描述字数|示例 */
private fun loadGroups(context: Context): List<ToolGroup> {
    val text = runCatching {
        context.assets.open("tool_groups.txt").bufferedReader().use { it.readText() }
    }.getOrDefault("")
    return text.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 4) return@mapNotNull null
            val count = parts[2].trim().toIntOrNull() ?: return@mapNotNull null
            val chars = parts[3].trim().toIntOrNull() ?: 0
            ToolGroup(
                prefix = parts[0].trim().lowercase(),
                label = parts[1].trim(),
                count = count,
                chars = chars,
            )
        }
        .sortedByDescending { it.chars }
        .toList()
}

/** 从使用记录里取出现过的类别前缀。 */
private fun usedPrefixes(context: Context): Set<String> {
    val prefs = context.getSharedPreferences(PersonaHook.PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(PersonaProvider.KEY_USAGE, "").orEmpty()
    return raw.lineSequence().mapNotNull { line ->
        val name = line.substringBefore('|')
        if (name.isBlank()) null else prefixOf(name)
    }.toSet()
}

private fun prefixOf(name: String): String {
    val two = name.indexOf("__")
    if (two > 0) return name.substring(0, two).lowercase()
    val one = name.indexOf('_')
    if (one > 0) return name.substring(0, one).lowercase()
    return name.lowercase()
}
