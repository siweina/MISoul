package com.siweina.misoul

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.io.File
import com.siweina.misoul.ui.component.AppCard

/**
 * 选工具规则：官方那份有 24,067 字，塞进 TextField 既不现实也不好用。
 * 这里只做「导出给别的应用看 / 从文件导入 / 编辑内置精简版 / 恢复官方」四件事。
 */
class RulesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                RulesScreen(onDone = { finish() })
            }
        }
    }
}

private const val RULES_FILE = "tool_selection_rules.md"

@Composable
private fun RulesScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences(PersonaHook.PREFS, Context.MODE_PRIVATE) }
    var custom by remember {
        mutableStateOf(prefs.getString(PersonaHook.KEY_RULES_TEXT, null).orEmpty())
    }
    val scrollBehavior = MiuixScrollBehavior()

    // SAF 导出：文件落在用户自己选的目录里，是真实路径，MT 管理器能定位、能直接编辑
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = custom.ifBlank {
                        readAsset(context, "tool_selection_rules.official.md")
                    }
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    }
                    true
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                if (ok) "已导出，可以在文件管理器里定位编辑" else "导出失败",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val importRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "读不出这个文件", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putString(PersonaHook.KEY_RULES_TEXT, text).apply()
                custom = text
                Toast.makeText(context, "已导入 ${text.length} 字", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "选工具规则",
                subtitle = if (custom.isBlank()) "当前：官方 24,067 字" else "当前：自定义 ${custom.length} 字",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(MiuixIcons.Back, "返回", tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 680.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
            ) {
                SmallTitle("查看")
                AppCard(Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "用其他应用查看",
                        summary = "临时文件，只适合预览；要定位/编辑请用下面的导出",
                        onClick = { exportAndOpen(context, custom) },
                    )
                    ArrowPreference(
                        title = "导出到文件",
                        summary = "系统选保存位置，拿到真实路径，MT 管理器可直接定位编辑",
                        onClick = { createDoc.launch(RULES_FILE) },
                    )
                }
                SmallTitle("修改")
                AppCard(Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "从文件导入",
                        summary = "改好之后选回来，整份替换",
                        onClick = { importRules.launch(arrayOf("text/*", "application/octet-stream")) },
                    )
                    ArrowPreference(
                        title = "编辑内置精简版",
                        summary = "约 900 字，适合在手机上直接改",
                        onClick = {
                            // 精简版走内部编辑器；内容先落盘，保存后回来重读
                            if (prefs.getString(PersonaHook.KEY_RULES_TEXT, null).isNullOrBlank()) {
                                prefs.edit()
                                    .putString(PersonaHook.KEY_RULES_TEXT, PersonaHook.COMPACT_RULES)
                                    .apply()
                            }
                            context.startActivity(DetailActivity.intent(context, Field.Rules))
                        },
                    )
                }
                SmallTitle("还原")
                AppCard(Modifier.fillMaxWidth()) {
                    ArrowPreference(
                        title = "恢复官方规则",
                        summary = "清掉自定义内容，回到 24,067 字那份",
                        onClick = {
                            prefs.edit().remove(PersonaHook.KEY_RULES_TEXT).apply()
                            custom = ""
                            Toast.makeText(context, "已恢复官方规则", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                Hint(
                    "官方规则在小爱的私有目录里，这里导出的是「当前生效的那份」：" +
                        (if (custom.isBlank()) "内置精简版" else "你自己导入的内容") + "。",
                )
                Hint("导入的内容只在本模块内存里替换，不会写进小爱的数据目录。")
            }
        }
    }
}

private fun exportAndOpen(context: Context, current: String) {
    val text = current.ifBlank { readAsset(context, "tool_selection_rules.official.md") }
    val dir = File(context.cacheDir, "rules").apply { mkdirs() }
    val file = File(dir, RULES_FILE)
    runCatching { file.writeText(text) }.onFailure {
        Toast.makeText(context, "写文件失败：${it.message}", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        .setDataAndType(uri, "text/markdown")
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }.onFailure {
        // 没有应用认 text/markdown 时退到 text/plain 再试一次
        val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW)
            .setDataAndType(uri, "text/plain")
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(fallback) }.onFailure {
            Toast.makeText(context, "没有应用能打开这个文件", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun readAsset(context: Context, name: String): String =
    runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrDefault("")

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        style = MiuixTheme.textStyles.footnote1,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}
