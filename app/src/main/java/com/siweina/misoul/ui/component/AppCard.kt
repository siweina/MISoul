package com.siweina.misoul.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.glass.glassPanel
import top.yukonga.miuix.kmp.material.GlassDefaults
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.GlassShape
import top.yukonga.miuix.kmp.material.GlassShadows
import com.siweina.misoul.ui.theme.LocalCardGlass
import com.siweina.misoul.ui.theme.LocalOs4Ui
import com.siweina.misoul.ui.theme.isInDarkTheme

/**
 * 所有设置页共用的卡片。
 *
 * OS4 下同一张卡片画成玻璃面板：仍然是 Miuix 的 [Card] 负责裁剪、内容色和内边距，只是把它的
 * 填充改成透明，玻璃面板（描边、阴影、兜底填充）在后面垫一层。经典界面完全不变，同一个 Card、
 * 同样的颜色。
 *
 * [LocalCardGlass] 可以关掉面板 —— 主题页里的「卡片柔光玻璃」。关掉就是普通 Card。
 *
 * 面板故意不采背景：页面记录里包含卡片自己，卡片去采就会把自己的画面糊一遍、和上一帧打架。
 * 这里的「玻璃感」来自描边、阴影和半透填充，透出来的是页面自己的底色。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardDefaults.CornerRadius,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    colors: CardColors = CardDefaults.defaultColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalOs4Ui.current || !LocalCardGlass.current) {
        Card(
            modifier = modifier,
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = colors,
            content = content,
        )
        return
    }
    val shape = remember(cornerRadius) { GlassShape(cornerRadius) }
    val isDark = isInDarkTheme()
    val fallbackShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    Box(
        modifier = modifier.glassPanel(
            backdrop = null,
            shape = shape,
            style = GlassDefaults.style(isDark),
            stroke = GlassEdgeStrokes.forTheme(isDark),
            shadow = GlassShadows.Regular,
            // 用卡片色本身而不是它的半透明副本：经典卡片是不透明的，
            // 换成半透明会在有渐变背景的页面上把内容压暗。
            fallback = Modifier.background(colors.color, fallbackShape),
        ),
    ) {
        Card(
            cornerRadius = cornerRadius,
            insideMargin = insideMargin,
            colors = colors.copy(color = Color.Transparent),
            content = content,
        )
    }
}
