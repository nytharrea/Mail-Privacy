package net.mailx.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.theme.AddyPalette

/**
 * `anonaddy/addy-android` içindeki `DonutProgressView` (`AliasAdapter.kt` ve `ManageAliasActivity.kt`)
 * bileşeninin Compose karşılığı.
 * - Aktifken: portalOrange (#FF9800), portalBlue (#4289FD), easternBlue (#16A8B8), softRed (#FD5842)
 * - Pasifken: md_grey_500..800
 * - Hiç istatistik yoksa `emptyDonut = 1` ile tam turuncu/gri halka çizer.
 */
@Composable
fun AddyDonutChart(
    forwards: Int,
    replies: Int,
    sends: Int,
    blocks: Int,
    enabled: Boolean,
    size: Dp = 42.dp,
    strokeWidth: Dp = 6.5.dp,
    showCenterText: Boolean = false,
    centerSubtitle: String? = null
) {
    val cForward = if (enabled) AddyPalette.PortalOrange else AddyPalette.Grey500
    val cReply = if (enabled) AddyPalette.PortalBlue else AddyPalette.Grey600
    val cSend = if (enabled) AddyPalette.EasternBlue else AddyPalette.Grey700
    val cBlock = if (enabled) AddyPalette.SoftRed else AddyPalette.Grey800

    val totalReal = forwards + replies + sends + blocks
    val emptyDonut = if (totalReal == 0) 1 else 0

    val sections = buildList {
        add((forwards + emptyDonut).toFloat() to cForward)
        if (replies > 0) add(replies.toFloat() to cReply)
        if (sends > 0) add(sends.toFloat() to cSend)
        if (blocks > 0) add(blocks.toFloat() to cBlock)
    }.sortedBy { it.first }

    val cap = sections.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(1f)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(strokeWidth / 2)
        ) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width, this.size.height)

            if (sections.size == 1) {
                drawArc(
                    color = sections.first().second,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                var currentStartAngle = 90f
                sections.forEach { (amount, color) ->
                    val sweep = (amount / cap) * 360f
                    drawArc(
                        color = color,
                        startAngle = currentStartAngle,
                        sweepAngle = sweep.coerceAtLeast(8f),
                        useCenter = false,
                        topLeft = Offset.Zero,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt)
                    )
                    currentStartAngle += sweep
                }
            }
        }

        if (showCenterText) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalReal.toString(),
                    style = if (size >= 72.dp) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (centerSubtitle != null && size >= 72.dp) {
                    Text(
                        text = centerSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
