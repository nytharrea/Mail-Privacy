package net.mailx.android.ui.components
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.ui.theme.MailxPalette
@Composable
fun MailxActivityRing(forwards: Int, replies: Int, sends: Int, blocks: Int, enabled: Boolean, size: Dp = 34.dp, strokeWidth: Dp = 4.dp, showCenterText: Boolean = true, centerSubtitle: String? = null) {
val cForward = if (enabled) MailxPalette.ForwardEmerald else MailxPalette.Slate500
val cReply = if (enabled) MailxPalette.ReplySky else MailxPalette.Slate600
val cSend = if (enabled) MailxPalette.SentIndigo else MailxPalette.Slate600
val cBlock = if (enabled) MailxPalette.BlockedRose else MailxPalette.Slate700
val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
val totalReal = forwards + replies + sends + blocks
val sections = buildList {
if (forwards > 0) add(forwards.toFloat() to cForward)
if (replies > 0) add(replies.toFloat() to cReply)
if (sends > 0) add(sends.toFloat() to cSend)
if (blocks > 0) add(blocks.toFloat() to cBlock)
}
val cap = sections.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(1f)
Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
val strokePx = strokeWidth.toPx()
val arcSize = Size(this.size.width, this.size.height)
drawArc(color = if (totalReal == 0) cForward.copy(alpha = 0.35f) else trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset.Zero, size = arcSize, style = Stroke(width = strokePx, cap = StrokeCap.Round))
if (sections.size == 1) {
drawArc(color = sections.first().second, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = Offset.Zero, size = arcSize, style = Stroke(width = strokePx, cap = StrokeCap.Round))
} else if (sections.size > 1) {
var currentStartAngle = -90f
sections.forEach { (amount, color) ->
val sweep = (amount / cap) * 360f
drawArc(color = color, startAngle = currentStartAngle, sweepAngle = sweep.coerceAtLeast(10f), useCenter = false, topLeft = Offset.Zero, size = arcSize, style = Stroke(width = strokePx, cap = StrokeCap.Butt))
currentStartAngle += sweep
}
}
}
if (showCenterText) {
Column(horizontalAlignment = Alignment.CenterHorizontally) {
Text(text = if (totalReal > 999) "999+" else totalReal.toString(), fontSize = if (size >= 64.dp) 18.sp else 10.sp, fontWeight = FontWeight.Bold, color = if (enabled) {
MaterialTheme.colorScheme.onSurface
} else {
MaterialTheme.colorScheme.onSurfaceVariant
})
if (centerSubtitle != null && size >= 64.dp) {
Text(text = centerSubtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
}
}
}
}
@Composable
fun MailxTelemetryBar(forwards: Int, replies: Int, sends: Int, blocks: Int, modifier: Modifier = Modifier) {
val total = (forwards + replies + sends + blocks).coerceAtLeast(1)
Row(modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
if (forwards > 0) {
Box(modifier = Modifier.weight(forwards.toFloat() / total.toFloat()).fillMaxHeight().background(MailxPalette.ForwardEmerald))
}
if (replies > 0) {
Box(modifier = Modifier.weight(replies.toFloat() / total.toFloat()).fillMaxHeight().background(MailxPalette.ReplySky))
}
if (sends > 0) {
Box(modifier = Modifier.weight(sends.toFloat() / total.toFloat()).fillMaxHeight().background(MailxPalette.SentIndigo))
}
if (blocks > 0) {
Box(modifier = Modifier.weight(blocks.toFloat() / total.toFloat()).fillMaxHeight().background(MailxPalette.BlockedRose))
}
}
}
