package net.mailx.android.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ForwardToInbox
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.model.MainNavTab
import net.mailx.android.ui.theme.MailxPalette
import org.json.JSONObject

@Immutable
data class LiquidGlassConfig(
    val transparency: Float = 0.50f,
    val refractionStrength: Float = 1.0f,
    val dispersionStrength: Float = 1.0f,
    val liquidMergingEnabled: Boolean = true,
    val reduceTransparency: Boolean = false,
    val increaseContrast: Boolean = false,
    val darkMode: Boolean = true,
    val amoledDark: Boolean = false
)

val LocalLiquidGlassConfig = compositionLocalOf { LiquidGlassConfig() }

enum class LiquidMaterialPreset(val key: String) {
    Regular("regular"),
    Clear("clear"),
    ClearBar("clear_bar"),
    Tinted("tinted"),
    Identity("identity")
}

@Immutable
data class ResolvedLiquidStyle(
    val faceColor: Color,
    val tintColor: Color,
    val rimLightColor: Color,
    val rimShadowColor: Color,
    val hairlineColor: Color,
    val refractionPx: Float,
    val dispersionPx: Float,
    val splayPx: Float,
    val specularIntensity: Float,
    val superellipseN: Float,
    val wcagContrastRatio: Float
)

private object LiquidShaderCache {
    val agslSource: String by lazy {
        runCatching {
            val env = JSONObject(MailxRustBridge.nativeGetLiquidGlassAgslShader())
            if (env.optBoolean("ok", false)) env.optString("data", "") else ""
        }.getOrDefault("")
    }
}

fun resolveLiquidStyleFromRust(
    preset: LiquidMaterialPreset,
    tintColor: Color,
    cornerRadiusDp: Float,
    widthDp: Float,
    heightDp: Float,
    config: LiquidGlassConfig
): ResolvedLiquidStyle {
    val backdropLuma = when {
        config.darkMode && config.amoledDark -> 0.02f
        config.darkMode -> 0.08f
        else -> 0.88f
    }
    val nativeStyle = runCatching {
        val raw = MailxRustBridge.nativeResolveLiquidMaterial(
            preset.key,
            tintColor.toArgb(),
            cornerRadiusDp,
            widthDp,
            heightDp,
            config.transparency,
            config.darkMode,
            config.reduceTransparency,
            config.increaseContrast,
            backdropLuma
        )
        val env = JSONObject(raw)
        if (!env.optBoolean("ok", false)) return@runCatching null
        val d = env.optJSONObject("data") ?: return@runCatching null
        ResolvedLiquidStyle(
            faceColor = Color(d.optLong("face_color_argb", 0x661C1C1EL).toInt()),
            tintColor = Color(d.optLong("tint_color_argb", tintColor.toArgb().toLong()).toInt()),
            rimLightColor = Color(d.optLong("rim_light_argb", 0x99FFFFFFL).toInt()),
            rimShadowColor = Color(d.optLong("rim_shadow_argb", 0x55282828L).toInt()),
            hairlineColor = Color(d.optLong("hairline_argb", 0x33FFFFFFL).toInt()),
            refractionPx = d.optDouble("refraction_px", 56.0).toFloat() * config.refractionStrength,
            dispersionPx = d.optDouble("dispersion_px", 14.0).toFloat() * config.dispersionStrength,
            splayPx = d.optDouble("splay_px", 16.0).toFloat(),
            specularIntensity = d.optDouble("specular_intensity", 0.45).toFloat(),
            superellipseN = d.optDouble("superellipse_n", 3.34).toFloat(),
            wcagContrastRatio = d.optDouble("wcag_contrast_ratio", 11.5).toFloat()
        )
    }.getOrNull()

    if (nativeStyle != null) return nativeStyle

    val t = if (config.reduceTransparency) 1f else config.transparency.coerceIn(0f, 1f)
    val baseAlpha = if (config.darkMode) {
        (0.28f + 0.50f * t).coerceIn(0.18f, 0.86f)
    } else {
        (0.42f + 0.44f * t).coerceIn(0.24f, 0.90f)
    }
    val face = if (config.darkMode) {
        Color(0xFF1C1C1E).copy(alpha = baseAlpha)
    } else {
        Color(0xFFFBFDFF).copy(alpha = baseAlpha)
    }
    return ResolvedLiquidStyle(
        faceColor = face,
        tintColor = tintColor,
        rimLightColor = Color.White.copy(alpha = if (config.darkMode) 0.48f else 0.72f),
        rimShadowColor = Color(0xFF282828).copy(alpha = 0.36f),
        hairlineColor = if (config.darkMode) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.12f),
        refractionPx = 56f * config.refractionStrength,
        dispersionPx = 14f * config.dispersionStrength,
        splayPx = 16f,
        specularIntensity = if (config.increaseContrast) 0.62f else 0.45f,
        superellipseN = 3.344f,
        wcagContrastRatio = 11.9f
    )
}

@Composable
fun LiquidGlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val config = LocalLiquidGlassConfig.current
    val bgColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = bgColor)
            val w = size.width
            val h = size.height
            val orbAlpha = when {
                config.reduceTransparency -> 0.04f
                config.amoledDark && config.darkMode -> 0.13f
                config.darkMode -> 0.22f
                else -> 0.18f
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MailxPalette.BrandAccentMint.copy(alpha = orbAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.18f, h * 0.12f),
                    radius = w * 0.68f
                ),
                center = Offset(w * 0.18f, h * 0.12f),
                radius = w * 0.68f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MailxPalette.ReplySky.copy(alpha = orbAlpha * 0.85f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.86f, h * 0.34f),
                    radius = w * 0.62f
                ),
                center = Offset(w * 0.86f, h * 0.34f),
                radius = w * 0.62f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MailxPalette.SentIndigo.copy(alpha = orbAlpha * 0.80f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.24f, h * 0.78f),
                    radius = w * 0.72f
                ),
                center = Offset(w * 0.24f, h * 0.78f),
                radius = w * 0.72f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MailxPalette.ForwardEmerald.copy(alpha = orbAlpha * 0.75f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.78f, h * 0.88f),
                    radius = w * 0.58f
                ),
                center = Offset(w * 0.78f, h * 0.88f),
                radius = w * 0.58f
            )
        }
        content()
    }
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    preset: LiquidMaterialPreset = LiquidMaterialPreset.Regular,
    tintColor: Color = Color.Transparent,
    estimatedWidthDp: Float = 340f,
    estimatedHeightDp: Float = 84f,
    interactiveScale: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val config = LocalLiquidGlassConfig.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "liquid_press_glow"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed && (onClick != null || interactiveScale)) 0.982f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquid_press_scale"
    )

    val style = remember(
        preset,
        tintColor,
        cornerRadius.value,
        estimatedWidthDp,
        estimatedHeightDp,
        config
    ) {
        resolveLiquidStyleFromRust(
            preset = preset,
            tintColor = tintColor,
            cornerRadiusDp = cornerRadius.value,
            widthDp = estimatedWidthDp,
            heightDp = estimatedHeightDp,
            config = config
        )
    }

    val shape = RoundedCornerShape(cornerRadius)
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(clickMod)
            .drawWithCache {
                val radiusPx = cornerRadius.toPx().coerceAtMost(size.minDimension * 0.5f)
                val corner = CornerRadius(radiusPx, radiusPx)
                val agslSrc = LiquidShaderCache.agslSource
                val runtimeShaderBrush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslSrc.isNotEmpty()) {
                    runCatching {
                        val rs = RuntimeShader(agslSrc)
                        rs.setFloatUniform("uResolution", size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
                        rs.setFloatUniform(
                            "uFaceColor",
                            style.faceColor.red,
                            style.faceColor.green,
                            style.faceColor.blue,
                            style.faceColor.alpha
                        )
                        rs.setFloatUniform(
                            "uTintColor",
                            style.tintColor.red,
                            style.tintColor.green,
                            style.tintColor.blue,
                            style.tintColor.alpha
                        )
                        rs.setFloatUniform(
                            "uRimLightColor",
                            style.rimLightColor.red,
                            style.rimLightColor.green,
                            style.rimLightColor.blue,
                            style.rimLightColor.alpha
                        )
                        rs.setFloatUniform("uCornerRadius", radiusPx)
                        rs.setFloatUniform("uSuperellipseN", style.superellipseN)
                        rs.setFloatUniform("uRefraction", style.refractionPx)
                        rs.setFloatUniform("uDispersion", style.dispersionPx)
                        rs.setFloatUniform("uSplay", style.splayPx)
                        rs.setFloatUniform("uSpecular", style.specularIntensity)
                        rs.setFloatUniform("uPressGlow", pressProgress)
                        rs.setFloatUniform("uLightDir", -0.7071f, -0.7071f)
                        rs.setFloatUniform("uPointerPos", size.width * 0.5f, size.height * 0.5f)
                        ShaderBrush(rs)
                    }.getOrNull()
                } else {
                    null
                }

                val refractionSheenBrush = Brush.linearGradient(
                    colors = listOf(
                        style.rimLightColor.copy(alpha = (0.22f + 0.14f * pressProgress).coerceAtMost(0.65f)),
                        style.faceColor,
                        if (style.tintColor.alpha > 0.01f) {
                            style.tintColor.copy(alpha = (style.tintColor.alpha * 0.35f).coerceAtMost(0.55f))
                        } else {
                            style.faceColor
                        },
                        style.rimShadowColor.copy(alpha = 0.24f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height)
                )

                val dispersionRimBrush = Brush.sweepGradient(
                    colors = listOf(
                        style.rimLightColor.copy(alpha = 0.62f * style.specularIntensity),
                        MailxPalette.ReplySky.copy(alpha = 0.28f * config.dispersionStrength),
                        style.hairlineColor,
                        MailxPalette.BrandAccentMint.copy(alpha = 0.30f * config.dispersionStrength),
                        style.rimLightColor.copy(alpha = 0.62f * style.specularIntensity)
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.5f)
                )

                onDrawBehind {
                    if (runtimeShaderBrush != null) {
                        drawRoundRect(
                            brush = runtimeShaderBrush,
                            cornerRadius = corner
                        )
                    } else {
                        drawRoundRect(
                            color = style.faceColor,
                            cornerRadius = corner
                        )
                    }

                    drawRoundRect(
                        brush = refractionSheenBrush,
                        cornerRadius = corner
                    )

                    if (pressProgress > 0.01f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.10f * pressProgress),
                            cornerRadius = corner
                        )
                    }

                    drawRoundRect(
                        brush = dispersionRimBrush,
                        cornerRadius = corner,
                        style = Stroke(width = 1.25.dp.toPx())
                    )
                }
            },
        content = content
    )
}

@Composable
fun LiquidGlassBottomBar(
    currentTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val config = LocalLiquidGlassConfig.current
    val tabs = MainNavTab.entries
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 380f
        ),
        label = "liquid_nav_lens"
    )

    var barWidthPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val pressScale by animateFloatAsState(
        targetValue = if (isDragging) 1.035f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquid_bar_scale"
    )

    val fractionalProgress = (animatedIndex - animatedIndex.toInt()).let {
        if (it < 0f) it + 1f else it
    }
    val curIdx = animatedIndex.toInt().coerceIn(0, tabs.lastIndex)
    val tgtIdx = (curIdx + 1).coerceAtMost(tabs.lastIndex)

    val navLiquid = remember(
        tabs.size,
        curIdx,
        tgtIdx,
        fractionalProgress,
        dragFraction,
        isDragging,
        config.transparency,
        config.darkMode
    ) {
        runCatching {
            val raw = MailxRustBridge.nativeEvaluateNavBarLiquid(
                tabs.size,
                curIdx,
                tgtIdx,
                fractionalProgress,
                dragFraction,
                isDragging,
                if (config.liquidMergingEnabled) 18f else 0f,
                config.transparency,
                config.darkMode
            )
            val env = JSONObject(raw)
            val data = env.optJSONObject("data")
            Triple(
                data?.optDouble("lens_width_fraction", 0.20)?.toFloat() ?: (1f / tabs.size),
                data?.optDouble("liquid_neck_strength", 0.0)?.toFloat() ?: 0f,
                data?.optDouble("press_glow", 0.0)?.toFloat() ?: 0f
            )
        }.getOrDefault(Triple(1f / tabs.size, 0f, 0f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        LiquidGlassSurface(
            cornerRadius = 32.dp,
            preset = LiquidMaterialPreset.ClearBar,
            estimatedWidthDp = 368f,
            estimatedHeightDp = 64f,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(tabs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            val idx = (dragFraction * tabs.size).toInt().coerceIn(0, tabs.lastIndex)
                            onSelectTab(tabs[idx])
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                            val idx = (dragFraction * tabs.size).toInt().coerceIn(0, tabs.lastIndex)
                            if (tabs[idx] != currentTab) {
                                onSelectTab(tabs[idx])
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                }
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val skyColor = MailxPalette.ReplySky

            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = tabs.size.toFloat()
                val slotW = size.width / count
                val lensCenterX = if (isDragging) {
                    (dragFraction * size.width).coerceIn(slotW * 0.5f, size.width - slotW * 0.5f)
                } else {
                    (animatedIndex + 0.5f) * slotW
                }
                val lensW = (size.width * navLiquid.first).coerceIn(slotW * 0.84f, slotW * 1.45f)
                val lensH = size.height - 10.dp.toPx()
                val lensLeft = (lensCenterX - lensW * 0.5f).coerceIn(4.dp.toPx(), size.width - lensW - 4.dp.toPx())
                val lensTop = 5.dp.toPx()
                val lensCorner = CornerRadius(lensH * 0.5f, lensH * 0.5f)

                if (config.liquidMergingEnabled && navLiquid.second > 0.02f) {
                    val nearestSlotX = (selectedIndex + 0.5f) * slotW
                    val midX = (lensCenterX + nearestSlotX) * 0.5f
                    val neckH = lensH * (0.28f + 0.42f * navLiquid.second)
                    val neckW = kotlin.math.abs(lensCenterX - nearestSlotX).coerceAtLeast(12f)
                    val neckPath = Path().apply {
                        moveTo(lensCenterX, size.height * 0.5f - neckH * 0.5f)
                        quadraticTo(
                            midX,
                            size.height * 0.5f - neckH * 0.18f,
                            nearestSlotX,
                            size.height * 0.5f - neckH * 0.45f
                        )
                        lineTo(nearestSlotX, size.height * 0.5f + neckH * 0.45f)
                        quadraticTo(
                            midX,
                            size.height * 0.5f + neckH * 0.18f,
                            lensCenterX,
                            size.height * 0.5f + neckH * 0.5f
                        )
                        close()
                    }
                    drawPath(
                        path = neckPath,
                        color = primaryColor.copy(alpha = 0.22f * navLiquid.second)
                    )
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.14f * navLiquid.second),
                        topLeft = Offset(midX - neckW * 0.5f, (size.height - neckH) * 0.5f),
                        size = Size(neckW, neckH),
                        cornerRadius = CornerRadius(neckH * 0.5f, neckH * 0.5f)
                    )
                }

                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.30f + 0.14f * navLiquid.third),
                            skyColor.copy(alpha = 0.18f),
                            primaryColor.copy(alpha = 0.24f)
                        ),
                        start = Offset(lensLeft, lensTop),
                        end = Offset(lensLeft + lensW, lensTop + lensH)
                    ),
                    topLeft = Offset(lensLeft, lensTop),
                    size = Size(lensW, lensH),
                    cornerRadius = lensCorner
                )

                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.62f),
                            primaryColor.copy(alpha = 0.42f),
                            Color.White.copy(alpha = 0.20f)
                        ),
                        start = Offset(lensLeft, lensTop),
                        end = Offset(lensLeft + lensW, lensTop + lensH)
                    ),
                    topLeft = Offset(lensLeft, lensTop),
                    size = Size(lensW, lensH),
                    cornerRadius = lensCorner,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEach { tab ->
                    val selected = currentTab == tab
                    val itemScale by animateFloatAsState(
                        targetValue = if (selected) 1.06f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "tab_item_scale"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(26.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectTab(tab) }
                            )
                            .graphicsLayer {
                                scaleX = itemScale
                                scaleY = itemScale
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidGlassTopBar(
    title: String,
    subtitleBadge: String,
    userInitials: String,
    onPrivacyLabClick: () -> Unit,
    onSendMailClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val config = LocalLiquidGlassConfig.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                LiquidGlassSurface(
                    cornerRadius = 12.dp,
                    preset = LiquidMaterialPreset.Tinted,
                    tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    estimatedWidthDp = 96f,
                    estimatedHeightDp = 22f
                ) {
                    Text(
                        text = subtitleBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        maxLines = 1
                    )
                }
            }
        }

        LiquidGlassSurface(
            cornerRadius = 22.dp,
            preset = LiquidMaterialPreset.ClearBar,
            estimatedWidthDp = 166f,
            estimatedHeightDp = 42f
        ) {
            Box( contentAlignment = Alignment.Center) {
                if (config.liquidMergingEnabled) {
                    val neckColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val cy = size.height * 0.5f
                        drawLine(
                            color = neckColor,
                            start = Offset(24.dp.toPx(), cy),
                            end = Offset(size.width - 24.dp.toPx(), cy),
                            strokeWidth = 8.dp.toPx(),
                            blendMode = BlendMode.SrcOver
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidTopBarIconButton(
                        icon = Icons.Default.AutoAwesome,
                        contentDescription = "Privacy Lab",
                        tint = MailxPalette.BrandAccentMint,
                        onClick = onPrivacyLabClick
                    )
                    LiquidTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ForwardToInbox,
                        contentDescription = "Send from Alias",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onSendMailClick
                    )
                    LiquidTopBarIconButton(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onRefreshClick
                    )
                    LiquidGlassSurface(
                        cornerRadius = 17.dp,
                        preset = LiquidMaterialPreset.Tinted,
                        tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        estimatedWidthDp = 34f,
                        estimatedHeightDp = 34f,
                        onClick = onProfileClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userInitials,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidTopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(17.dp)
        )
    }
}
