package com.siweina.misoul.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.material.GlassDefaults
import com.siweina.misoul.ui.component.liquid.IosLiquidGlassNavigationBar
import com.siweina.misoul.ui.theme.LocalOs4Ui
import com.siweina.misoul.ui.theme.isInDarkTheme

/**
 * 底栏。两种界面风格各一套实现，调用点只负责给数据。
 *
 * - OS4：Miuix 的 [GlassNavigationBar]，浮起的玻璃胶囊，采页面记录；
 * - 经典：我们自己移植的 iOS 液态玻璃底栏（折射 + 边缘高光 + 按压回弹）。
 */
@Composable
fun AppBottomBar(
    items: List<NavigationItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    backdrop: LayerBackdrop?,
    isBlurActive: Boolean,
) {
    if (!LocalOs4Ui.current) {
        IosLiquidGlassNavigationBar(
            modifier = Modifier.padding(horizontal = 12.dp),
            items = items,
            selectedIndex = selectedIndex,
            onItemClick = onItemClick,
            backdrop = backdrop,
            isBlurActive = isBlurActive,
        )
        return
    }
    val isDark = isInDarkTheme()
    val glassItems = remember(items) {
        items.map { GlassNavigationItem(icon = it.icon, label = it.label) }
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        GlassNavigationBar(
            items = glassItems,
            selectedIndex = selectedIndex,
            onSelect = onItemClick,
            backdrop = backdrop,
            // 演示里的胶囊宽度：两侧各 8dp 内容留白，每个标签 84dp。
            modifier = Modifier.width((items.size * 84).dp + 16.dp),
            style = GlassDefaults.style(isDark),
        )
    }
}
