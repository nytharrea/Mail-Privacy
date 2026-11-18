@file:Suppress("OPT_IN_IS_NOT_ENABLED", "EXPERIMENTAL_IS_NOT_ENABLED", "UNNECESSARY_OPT_IN")

package net.mailx.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clean Material 3 Tonal Container Wrapper aligned with MailxShapes.medium (14.dp).
 */
@Composable
fun ExpressiveSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f),
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Box(content = content)
    }
}
