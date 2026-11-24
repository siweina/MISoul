package com.siweina.misoul

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import com.siweina.misoul.ui.component.AppCard

/** 工具使用记录：每次工具调用由小爱进程回传，这里按类别汇总。 */
class UsageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                UsageScreen(onDone = { finish() })
            }
        }
    }
}

private data class UsageRow(val tool: String, val count: Long, val last: Long)

@Composable
private fun UsageScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val rows = remember { loadUsage(context) }
    val grouped = remember(rows) { rows.groupBy { prefixOf(it.tool) }.toList().sortedByDescending { it.second.sumOf { row -> row.count } } }
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "使用记录",
                subtitle = if (rows.isEmpty()) "还没有记录，用一次工具就会出现" else "${rows.size} 个工具被调用过",
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
                if (rows.isEmpty()) {
                    Hint("记录来自每次工具调用（ToolExecutor）。现在还是空的——随便让小爱查个天气或定个闹钟，回来就能看到。")
                    return@Column
                }
                grouped.forEach { (prefix, list) ->
                    SmallTitle("${prefix} · 共 ${list.sumOf { it.count }} 次")
                    AppCard(Modifier.fillMaxWidth()) {
                        list.sortedByDescending { it.count }.forEach { row ->
                            ArrowPreference(
                                title = row.tool,
                                summary = "${row.count} 次 · ${relativeTime(row.last)}",
                                onClick = {},
                            )
                        }
                    }
                }
                Hint("这些数字可以用来决定「工具筛选」里保留哪些类别。")
            }
        }
    }
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

private fun loadUsage(context: Context): List<UsageRow> {
    val prefs = context.getSharedPreferences(PersonaHook.PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(PersonaProvider.KEY_USAGE, "").orEmpty()
    return raw.lineSequence().mapNotNull { line ->
        val parts = line.trim().split("|")
        if (parts.size < 3) return@mapNotNull null
        val count = parts[1].toLongOrNull() ?: return@mapNotNull null
        val last = parts[2].toLongOrNull() ?: 0L
        UsageRow(parts[0], count, last)
    }.toList()
}

private fun prefixOf(name: String): String {
    val two = name.indexOf("__")
    if (two > 0) return name.substring(0, two).lowercase()
    val one = name.indexOf('_')
    if (one > 0) return name.substring(0, one).lowercase()
    return name.lowercase()
}

private fun relativeTime(ts: Long): String {
    if (ts <= 0) return "未知"
    val diff = System.currentTimeMillis() - ts
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        else -> "${minutes / (60 * 24)} 天前"
    }
}
