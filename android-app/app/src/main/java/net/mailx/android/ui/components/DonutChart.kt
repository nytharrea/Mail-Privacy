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

@Composable
fun AddyDonutChart(
    forwards: Int,
    replies: Int,
    sends: Int,
    blocks: Int,
    enabled: Boolean,
    size: Dp = 40.dp,
    strokeWidth: Dp = 4.5.dp,
    centerSubtitle: String? = null
) {
    val total = forwards + replies + sends + blocks
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    val cForward = if (enabled) AddyPalette.ForwardedOrange else Color(0xFF6E717A)
    val cReply = if (enabled) AddyPalette.RepliedBlue else Color(0xFF5A5D66)
    val cSend = if (enabled) AddyPalette.SentCyan else Color(0xFF4B4E57)
    val cBlock = if (enabled) AddyPalette.BlockedRed else Color(0xFF3E414A)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width, this.size.height)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (total == 0) {
                drawArc(
                    color = cForward.copy(alpha = if (enabled) 0.65f else 0.35f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                val segments = listOf(
                    forwards to cForward,
                    replies to cReply,
                    sends to cSend,
                    blocks to cBlock
                ).filter { it.first > 0 }

                var startAngle = -90f
                val gapDegrees = if (segments.size > 1) 7f else 0f
                val availableDegrees = 360f - (gapDegrees * segments.size)

                segments.forEach { (count, segColor) ->
                    val sweep = (count.toFloat() / total.toFloat()) * availableDegrees
                    drawArc(
                        color = segColor,
                        startAngle = startAngle,
                        sweepAngle = sweep.coerceAtLeast(4f),
                        useCenter = false,
                        topLeft = Offset.Zero,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    startAngle += sweep + gapDegrees
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = total.toString(),
                style = if (size >= 76.dp) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.labelMedium
                },
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (centerSubtitle != null && size >= 76.dp) {
                Text(
                    text = centerSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
