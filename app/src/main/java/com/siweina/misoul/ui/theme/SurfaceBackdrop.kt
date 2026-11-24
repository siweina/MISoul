package com.siweina.misoul.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 只画页面底色的 [Backdrop]。
 *
 * 给那些采不到真实记录、又必须有个「身体」才能上玻璃的调用点用：比如设置页里的玻璃下拉弹层，
 * 它渲染在页面记录之上，采页面就会把弹层自己糊进去，不采又会退化成不透明面板。
 * 用它至少能拿到玻璃的填充、描边和高光。
 */
private class SurfaceBackdrop(private val color: Color) : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        drawRect(color)
    }
}

@Composable
fun rememberSurfaceBackdrop(): Backdrop {
    val surface = MiuixTheme.colorScheme.surface
    return remember(surface) { SurfaceBackdrop(surface) }
}
