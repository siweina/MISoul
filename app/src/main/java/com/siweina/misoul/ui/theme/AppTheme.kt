package com.siweina.misoul.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 应用主题：配色模式 + 界面风格 + 卡片柔光玻璃。
 *
 * 界面风格是「用户选择」与「设备能力」的与：玻璃要 runtime shader，设备没有时整个 App 停在
 * 经典界面，而不是玻璃底栏配普通卡片这种每处各错一半的状态。
 */
@Composable
fun AppTheme(
    themeMode: ColorSchemeMode,
    useOs4Ui: Boolean,
    cardGlass: Boolean,
    content: @Composable () -> Unit,
) {
    val controller = remember(themeMode) { ThemeController(themeMode) }
    MiuixTheme(controller = controller) {
        // enableEdgeToEdge 的 SystemBarStyle.auto 只跟随系统深浅，手动指定浅色/深色时
        // 状态栏图标不会跟着变，这里按实际解析出的主题补一次。
        val dark = isInDarkTheme()
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                val insets = WindowCompat.getInsetsController(window, view)
                insets.isAppearanceLightStatusBars = !dark
                insets.isAppearanceLightNavigationBars = !dark
            }
        }
        CompositionLocalProvider(
            LocalOs4Ui provides (useOs4Ui && isRuntimeShaderSupported()),
            LocalCardGlass provides cardGlass,
            content = content,
        )
    }
}
