/*
 * 与 Miuix 的 PagerNavigationSpringSpec 同一条曲线（Apache-2.0，
 * Copyright 2025, compose-miuix-ui contributors）。
 */
package com.siweina.misoul.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import kotlin.math.sqrt

/**
 * 点击底栏切页用的弹性曲线。
 *
 * 用 tween 就得自己定时长，而「越远越久」的朴素规则会让跨两页显得拖沓、跨三页更慢；
 * 弹簧没有时长，距离本身就决定了速度。
 */
internal val PagerNavigationSpringSpec: SpringSpec<Float> = spring(
    stiffness = 322.2f,
    dampingRatio = 32.31f / (2f * sqrt(322.2f)),
    visibilityThreshold = 0.5f,
)
