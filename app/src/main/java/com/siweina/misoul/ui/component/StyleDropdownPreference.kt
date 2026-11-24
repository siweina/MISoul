package com.siweina.misoul.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import com.siweina.misoul.ui.theme.LocalOs4Ui
import com.siweina.misoul.ui.theme.rememberSurfaceBackdrop

/**
 * 主题页用的下拉行。
 *
 * Miuix 自带两种。OS4 用页面内的玻璃弹层（[GlassDropdownPreference]），经典界面用系统窗口
 * （[WindowDropdownPreference]）。我们这里只放纯文本选项，正好是玻璃弹层能承载的情形，所以
 * 选择只取决于界面风格，调用点不用知道两者存在。
 *
 * 玻璃弹层没有传入背景采样源（backdrop = 默认 null），弹层内部会走兜底填充；
 * 主题页本身没有整页的 layer 记录，传错来源反而会把弹层自己糊进去。
 */
@Composable
fun StyleDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
) {
    if (LocalOs4Ui.current) {
        GlassDropdownPreference(
            onSelectedIndexChange = onSelectedIndexChange,
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            backdrop = rememberSurfaceBackdrop(),
            modifier = modifier,
            enabled = enabled,
            showValue = true,
            summary = summary,
            insideMargin = insideMargin,
        )
    } else {
        WindowDropdownPreference(
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            modifier = modifier,
            summary = summary,
            enabled = enabled,
            showValue = true,
            insideMargin = insideMargin,
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}
