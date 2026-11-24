package com.siweina.misoul.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 当前绘制的是不是 OS4 玻璃界面。
 *
 * 由 [com.siweina.misoul.MainActivity] 在主题外层写一次，值是「用户选择」和「设备能不能画」
 * 的与：设备没有 runtime shader 时整个 App 停在经典界面，而不是玻璃底栏配普通卡片。
 * 页面只问这一个值，不用再自己去判断设备能力。
 */
val LocalOs4Ui = staticCompositionLocalOf { false }

/**
 * OS4 的卡片要不要描边和阴影（主题页里的「卡片柔光玻璃」）。
 *
 * 只决定卡片的边框与阴影，卡片的填充和其它玻璃界面不受影响；关掉就是纯 Miuix Card。
 */
val LocalCardGlass = staticCompositionLocalOf { true }

/**
 * 当前主题是不是深色。
 *
 * 不能再用 `isSystemInDarkTheme()`：主题页允许手动指定深色/浅色，那样应用内会与系统不一致，
 * 玻璃的明暗、状态栏图标、高光强度都会跟着错。这里按实际解析出的 surface 亮度判断。
 */
@androidx.compose.runtime.Composable
fun isInDarkTheme(): Boolean {
    val surface = MiuixTheme.colorScheme.surface
    val luminance = 0.2126f * surface.red + 0.7152f * surface.green + 0.0722f * surface.blue
    return luminance < 0.5f
}
