package net.mailx.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * Unified Lucide Icons (v1.70 24x24 rounded stroke & expressive duotone geometry)
 * icon system for Mailx Privacy Material 3 Expressive UI.
 */
object MailxIcons {

    private fun lucideStrokeVector(
        name: String,
        strokeWidth: Float = 2f,
        fillAlpha: Float = 0f,
        fillType: PathFillType = PathFillType.NonZero,
        block: PathBuilder.() -> Unit
    ): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = PathBuilder().apply(block).nodes,
            fill = if (fillAlpha > 0f) SolidColor(Color.White.copy(alpha = fillAlpha)) else SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = fillType
        ).build()
    }

    private fun PathBuilder.roundedBox(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        r: Float = 1.8f
    ) {
        moveTo(left + r, top)
        horizontalLineTo(right - r)
        arcTo(r, r, 0f, false, true, right, top + r)
        verticalLineTo(bottom - r)
        arcTo(r, r, 0f, false, true, right - r, bottom)
        horizontalLineTo(left + r)
        arcTo(r, r, 0f, false, true, left, bottom - r)
        verticalLineTo(top + r)
        arcTo(r, r, 0f, false, true, left + r, top)
        close()
    }

    private fun PathBuilder.gearContourAndHub() {
        // Outer 8-tooth rounded gear contour
        moveTo(21.38f, 10.51f)
        lineTo(21.38f, 13.49f)
        lineTo(18.95f, 13.86f)
        lineTo(18.24f, 15.60f)
        lineTo(19.69f, 17.58f)
        lineTo(17.58f, 19.69f)
        lineTo(15.60f, 18.24f)
        lineTo(13.86f, 18.95f)
        lineTo(13.49f, 21.38f)
        lineTo(10.51f, 21.38f)
        lineTo(10.14f, 18.95f)
        lineTo(8.40f, 18.24f)
        lineTo(6.42f, 19.69f)
        lineTo(4.31f, 17.58f)
        lineTo(5.76f, 15.60f)
        lineTo(5.05f, 13.86f)
        lineTo(2.62f, 13.49f)
        lineTo(2.62f, 10.51f)
        lineTo(5.05f, 10.14f)
        lineTo(5.76f, 8.40f)
        lineTo(4.31f, 6.42f)
        lineTo(6.42f, 4.31f)
        lineTo(8.40f, 5.76f)
        lineTo(10.14f, 5.05f)
        lineTo(10.51f, 2.62f)
        lineTo(13.49f, 2.62f)
        lineTo(13.86f, 5.05f)
        lineTo(15.60f, 5.76f)
        lineTo(17.58f, 4.31f)
        lineTo(19.69f, 6.42f)
        lineTo(18.24f, 8.40f)
        lineTo(18.95f, 10.14f)
        close()
        // Center gear hub hole
        moveTo(15.2f, 12f)
        arcTo(3.2f, 3.2f, 0f, true, true, 8.8f, 12f)
        arcTo(3.2f, 3.2f, 0f, true, true, 15.2f, 12f)
        close()
    }

    // Navigation & Core Layout Icons (Rounded Bento Dashboard, Alias @, Inbox, Knob Sliders, Gear Cog)
    val LayoutDashboard: ImageVector by lazy {
        lucideStrokeVector("LucideLayoutDashboard", strokeWidth = 1.95f) {
            roundedBox(3f, 3f, 10.2f, 12.2f, 2f)
            roundedBox(13.8f, 3f, 21f, 8.2f, 2f)
            roundedBox(13.8f, 11.8f, 21f, 21f, 2f)
            roundedBox(3f, 15.8f, 10.2f, 21f, 2f)
        }
    }

    val LayoutDashboardSelected: ImageVector by lazy {
        lucideStrokeVector("LucideLayoutDashboardSelected", strokeWidth = 2.25f, fillAlpha = 0.24f) {
            roundedBox(3f, 3f, 10.2f, 12.2f, 2f)
            roundedBox(13.8f, 3f, 21f, 8.2f, 2f)
            roundedBox(13.8f, 11.8f, 21f, 21f, 2f)
            roundedBox(3f, 15.8f, 10.2f, 21f, 2f)
        }
    }

    val AtSign: ImageVector by lazy {
        lucideStrokeVector("LucideAtSign", strokeWidth = 1.95f) {
            moveTo(16f, 12f)
            arcTo(4f, 4f, 0f, true, true, 8f, 12f)
            arcTo(4f, 4f, 0f, true, true, 16f, 12f)
            moveTo(16f, 8f)
            verticalLineTo(13f)
            arcTo(3f, 3f, 0f, false, false, 22f, 13f)
            verticalLineTo(12f)
            arcTo(10f, 10f, 0f, true, false, 16.5f, 20.9f)
        }
    }

    val AtSignSelected: ImageVector by lazy {
        lucideStrokeVector("LucideAtSignSelected", strokeWidth = 2.3f, fillAlpha = 0.18f) {
            moveTo(16f, 12f)
            arcTo(4f, 4f, 0f, true, true, 8f, 12f)
            arcTo(4f, 4f, 0f, true, true, 16f, 12f)
            close()
            moveTo(16f, 8f)
            verticalLineTo(13f)
            arcTo(3f, 3f, 0f, false, false, 22f, 13f)
            verticalLineTo(12f)
            arcTo(10f, 10f, 0f, true, false, 16.5f, 20.9f)
        }
    }

    val Inbox: ImageVector by lazy {
        lucideStrokeVector("LucideInbox", strokeWidth = 1.95f) {
            moveTo(22f, 12.5f)
            horizontalLineTo(16.2f)
            lineTo(14.3f, 15.2f)
            horizontalLineTo(9.7f)
            lineTo(7.8f, 12.5f)
            horizontalLineTo(2f)
            moveTo(5.45f, 5.11f)
            lineTo(2f, 12.5f)
            verticalLineTo(18f)
            arcTo(2.2f, 2.2f, 0f, false, false, 4.2f, 20.2f)
            horizontalLineTo(19.8f)
            arcTo(2.2f, 2.2f, 0f, false, false, 22f, 18f)
            verticalLineTo(12.5f)
            lineTo(18.55f, 5.11f)
            arcTo(2.2f, 2.2f, 0f, false, false, 16.56f, 4f)
            horizontalLineTo(7.44f)
            arcTo(2.2f, 2.2f, 0f, false, false, 5.45f, 5.11f)
            close()
        }
    }

    val InboxSelected: ImageVector by lazy {
        lucideStrokeVector("LucideInboxSelected", strokeWidth = 2.25f, fillAlpha = 0.22f) {
            moveTo(22f, 12.5f)
            horizontalLineTo(16.2f)
            lineTo(14.3f, 15.2f)
            horizontalLineTo(9.7f)
            lineTo(7.8f, 12.5f)
            horizontalLineTo(2f)
            moveTo(5.45f, 5.11f)
            lineTo(2f, 12.5f)
            verticalLineTo(18f)
            arcTo(2.2f, 2.2f, 0f, false, false, 4.2f, 20.2f)
            horizontalLineTo(19.8f)
            arcTo(2.2f, 2.2f, 0f, false, false, 22f, 18f)
            verticalLineTo(12.5f)
            lineTo(18.55f, 5.11f)
            arcTo(2.2f, 2.2f, 0f, false, false, 16.56f, 4f)
            horizontalLineTo(7.44f)
            arcTo(2.2f, 2.2f, 0f, false, false, 5.45f, 5.11f)
            close()
        }
    }

    val SlidersHorizontal: ImageVector by lazy {
        lucideStrokeVector("LucideSlidersHorizontal", strokeWidth = 1.95f) {
            // Top slider track & circular knob at x=15, y=5
            moveTo(3f, 5f); horizontalLineTo(12.6f)
            moveTo(17.4f, 5f); horizontalLineTo(21f)
            moveTo(17.2f, 5f)
            arcTo(2.2f, 2.2f, 0f, true, true, 12.8f, 5f)
            arcTo(2.2f, 2.2f, 0f, true, true, 17.2f, 5f)
            // Middle slider track & circular knob at x=9, y=12
            moveTo(3f, 12f); horizontalLineTo(6.6f)
            moveTo(11.4f, 12f); horizontalLineTo(21f)
            moveTo(11.2f, 12f)
            arcTo(2.2f, 2.2f, 0f, true, true, 6.8f, 12f)
            arcTo(2.2f, 2.2f, 0f, true, true, 11.2f, 12f)
            // Bottom slider track & circular knob at x=15, y=19
            moveTo(3f, 19f); horizontalLineTo(12.6f)
            moveTo(17.4f, 19f); horizontalLineTo(21f)
            moveTo(17.2f, 19f)
            arcTo(2.2f, 2.2f, 0f, true, true, 12.8f, 19f)
            arcTo(2.2f, 2.2f, 0f, true, true, 17.2f, 19f)
        }
    }

    val SlidersHorizontalSelected: ImageVector by lazy {
        lucideStrokeVector("LucideSlidersHorizontalSelected", strokeWidth = 2.25f, fillAlpha = 0.28f) {
            moveTo(3f, 5f); horizontalLineTo(12.6f)
            moveTo(17.4f, 5f); horizontalLineTo(21f)
            moveTo(17.2f, 5f)
            arcTo(2.2f, 2.2f, 0f, true, true, 12.8f, 5f)
            arcTo(2.2f, 2.2f, 0f, true, true, 17.2f, 5f)
            close()
            moveTo(3f, 12f); horizontalLineTo(6.6f)
            moveTo(11.4f, 12f); horizontalLineTo(21f)
            moveTo(11.2f, 12f)
            arcTo(2.2f, 2.2f, 0f, true, true, 6.8f, 12f)
            arcTo(2.2f, 2.2f, 0f, true, true, 11.2f, 12f)
            close()
            moveTo(3f, 19f); horizontalLineTo(12.6f)
            moveTo(17.4f, 19f); horizontalLineTo(21f)
            moveTo(17.2f, 19f)
            arcTo(2.2f, 2.2f, 0f, true, true, 12.8f, 19f)
            arcTo(2.2f, 2.2f, 0f, true, true, 17.2f, 19f)
            close()
        }
    }

    val Settings: ImageVector by lazy {
        lucideStrokeVector(
            name = "LucideSettings",
            strokeWidth = 1.95f,
            fillType = PathFillType.EvenOdd
        ) {
            gearContourAndHub()
        }
    }

    val SettingsSelected: ImageVector by lazy {
        lucideStrokeVector(
            name = "LucideSettingsSelected",
            strokeWidth = 2.2f,
            fillAlpha = 0.25f,
            fillType = PathFillType.EvenOdd
        ) {
            gearContourAndHub()
        }
    }

    val Passkey: ImageVector by lazy {
        lucideStrokeVector("LucidePasskey", strokeWidth = 2f) {
            // User head + shoulder + key emblem
            moveTo(13f, 7f)
            arcTo(3.5f, 3.5f, 0f, true, true, 6f, 7f)
            arcTo(3.5f, 3.5f, 0f, true, true, 13f, 7f)
            moveTo(3f, 20f)
            verticalLineTo(18.5f)
            arcTo(4.5f, 4.5f, 0f, false, true, 7.5f, 14f)
            horizontalLineTo(11f)
            // Key circle + teeth on right
            moveTo(19f, 14.5f)
            arcTo(2.5f, 2.5f, 0f, true, true, 14f, 14.5f)
            arcTo(2.5f, 2.5f, 0f, true, true, 19f, 14.5f)
            moveTo(18.3f, 16.3f)
            lineTo(21.5f, 19.5f)
            moveTo(20f, 18f)
            lineTo(21.5f, 16.5f)
        }
    }

    val ShieldCheck: ImageVector by lazy {
        lucideStrokeVector("LucideShieldCheck") {
            moveTo(12f, 22f)
            curveTo(12f, 22f, 20f, 18f, 20f, 12f)
            verticalLineTo(5f)
            lineTo(12f, 2f)
            lineTo(4f, 5f)
            verticalLineTo(12f)
            curveTo(4f, 18f, 12f, 22f, 12f, 22f)
            close()
            moveTo(9f, 12f)
            lineTo(11f, 14f)
            lineTo(15f, 10f)
        }
    }

    val Send: ImageVector by lazy {
        lucideStrokeVector("LucideSend") {
            moveTo(22f, 2f)
            lineTo(11f, 13f)
            moveTo(22f, 2f)
            lineTo(15f, 22f)
            lineTo(11f, 13f)
            lineTo(2f, 9f)
            lineTo(22f, 2f)
            close()
        }
    }

    val RefreshCw: ImageVector by lazy {
        lucideStrokeVector("LucideRefreshCw") {
            moveTo(21f, 12f)
            arcTo(9f, 9f, 0f, false, false, 6f, 5.3f)
            lineTo(3f, 8f)
            moveTo(3f, 3f); verticalLineTo(8f); horizontalLineTo(8f)
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, false, false, 18f, 18.7f)
            lineTo(21f, 16f)
            moveTo(16f, 16f); horizontalLineTo(21f); verticalLineTo(21f)
        }
    }

    val Plus: ImageVector by lazy {
        lucideStrokeVector("LucidePlus") {
            moveTo(12f, 5f); verticalLineTo(19f)
            moveTo(5f, 12f); horizontalLineTo(19f)
        }
    }

    val Copy: ImageVector by lazy {
        lucideStrokeVector("LucideCopy") {
            moveTo(10f, 8f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 21f, 10f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 19f, 21f)
            horizontalLineTo(10f)
            arcTo(2f, 2f, 0f, false, true, 8f, 19f)
            verticalLineTo(10f)
            arcTo(2f, 2f, 0f, false, true, 10f, 8f)
            close()
            moveTo(4f, 16f)
            arcTo(2f, 2f, 0f, false, true, 2f, 14f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 4f, 3f)
            horizontalLineTo(13f)
            arcTo(2f, 2f, 0f, false, true, 15f, 5f)
        }
    }

    val Check: ImageVector by lazy {
        lucideStrokeVector("LucideCheck") {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineTo(4f, 12f)
        }
    }

    val CheckCircle: ImageVector by lazy {
        lucideStrokeVector("LucideCheckCircle") {
            moveTo(22f, 11.08f)
            verticalLineTo(12f)
            arcTo(10f, 10f, 0f, true, true, 16.07f, 2.86f)
            moveTo(22f, 4f)
            lineTo(12f, 14.01f)
            lineTo(9f, 11.01f)
        }
    }

    val Pin: ImageVector by lazy {
        lucideStrokeVector("LucidePin") {
            moveTo(12f, 17f); verticalLineTo(22f)
            moveTo(5f, 17f)
            horizontalLineTo(19f)
            verticalLineTo(15.2f)
            lineTo(17f, 13f)
            verticalLineTo(7f)
            lineTo(18f, 5f)
            horizontalLineTo(6f)
            lineTo(7f, 7f)
            verticalLineTo(13f)
            lineTo(5f, 15.2f)
            close()
        }
    }

    val Sparkles: ImageVector by lazy {
        lucideStrokeVector("LucideSparkles") {
            moveTo(12f, 3f)
            lineTo(13.9f, 8.8f)
            lineTo(19.7f, 10.7f)
            lineTo(13.9f, 12.6f)
            lineTo(12f, 18.4f)
            lineTo(10.1f, 12.6f)
            lineTo(4.3f, 10.7f)
            lineTo(10.1f, 8.8f)
            close()
            moveTo(19f, 3f); verticalLineTo(7f)
            moveTo(17f, 5f); horizontalLineTo(21f)
        }
    }

    val PauseCircle: ImageVector by lazy {
        lucideStrokeVector("LucidePauseCircle") {
            moveTo(22f, 12f)
            arcTo(10f, 10f, 0f, true, true, 2f, 12f)
            arcTo(10f, 10f, 0f, true, true, 22f, 12f)
            moveTo(10f, 15f); verticalLineTo(9f)
            moveTo(14f, 15f); verticalLineTo(9f)
        }
    }

    val PlayCircle: ImageVector by lazy {
        lucideStrokeVector("LucidePlayCircle") {
            moveTo(22f, 12f)
            arcTo(10f, 10f, 0f, true, true, 2f, 12f)
            arcTo(10f, 10f, 0f, true, true, 22f, 12f)
            moveTo(10f, 8f)
            lineTo(16f, 12f)
            lineTo(10f, 16f)
            close()
        }
    }

    val Trash2: ImageVector by lazy {
        lucideStrokeVector("LucideTrash2") {
            moveTo(3f, 6f); horizontalLineTo(21f)
            moveTo(19f, 6f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 17f, 22f)
            horizontalLineTo(7f)
            arcTo(2f, 2f, 0f, false, true, 5f, 20f)
            verticalLineTo(6f)
            moveTo(8f, 6f)
            verticalLineTo(4f)
            arcTo(2f, 2f, 0f, false, true, 10f, 2f)
            horizontalLineTo(14f)
            arcTo(2f, 2f, 0f, false, true, 16f, 4f)
            verticalLineTo(6f)
            moveTo(10f, 11f); verticalLineTo(17f)
            moveTo(14f, 11f); verticalLineTo(17f)
        }
    }

    val Search: ImageVector by lazy {
        lucideStrokeVector("LucideSearch") {
            moveTo(19f, 11f)
            arcTo(8f, 8f, 0f, true, true, 3f, 11f)
            arcTo(8f, 8f, 0f, true, true, 19f, 11f)
            moveTo(21f, 21f)
            lineTo(16.65f, 16.65f)
        }
    }

    val X: ImageVector by lazy {
        lucideStrokeVector("LucideX") {
            moveTo(18f, 6f); lineTo(6f, 18f)
            moveTo(6f, 6f); lineTo(18f, 18f)
        }
    }

    val KeyRound: ImageVector by lazy {
        lucideStrokeVector("LucideKeyRound") {
            moveTo(11.5f, 15.5f)
            arcTo(5.5f, 5.5f, 0f, true, true, 15.5f, 11.5f)
            lineTo(22f, 18f)
            verticalLineTo(21f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineTo(17f)
            verticalLineTo(17f)
            horizontalLineTo(13f)
            close()
        }
    }

    val Lock: ImageVector by lazy {
        lucideStrokeVector("LucideLock") {
            moveTo(5f, 11f)
            horizontalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 21f, 13f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 19f, 22f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 20f)
            verticalLineTo(13f)
            arcTo(2f, 2f, 0f, false, true, 5f, 11f)
            close()
            moveTo(7f, 11f)
            verticalLineTo(7f)
            arcTo(5f, 5f, 0f, false, true, 17f, 7f)
            verticalLineTo(11f)
        }
    }

    val Globe: ImageVector by lazy {
        lucideStrokeVector("LucideGlobe") {
            moveTo(22f, 12f)
            arcTo(10f, 10f, 0f, true, true, 2f, 12f)
            arcTo(10f, 10f, 0f, true, true, 22f, 12f)
            moveTo(2f, 12f); horizontalLineTo(22f)
            moveTo(12f, 2f)
            curveTo(14.5f, 4.7f, 16f, 8.2f, 16f, 12f)
            curveTo(16f, 15.8f, 14.5f, 19.3f, 12f, 22f)
            curveTo(9.5f, 19.3f, 8f, 15.8f, 8f, 12f)
            curveTo(8f, 8.2f, 9.5f, 4.7f, 12f, 2f)
        }
    }

    val Server: ImageVector by lazy {
        lucideStrokeVector("LucideServer") {
            moveTo(4f, 2f)
            horizontalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 22f, 4f)
            verticalLineTo(8f)
            arcTo(2f, 2f, 0f, false, true, 20f, 10f)
            horizontalLineTo(4f)
            arcTo(2f, 2f, 0f, false, true, 2f, 8f)
            verticalLineTo(4f)
            arcTo(2f, 2f, 0f, false, true, 4f, 2f)
            close()
            moveTo(4f, 14f)
            horizontalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 22f, 16f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 20f, 22f)
            horizontalLineTo(4f)
            arcTo(2f, 2f, 0f, false, true, 2f, 20f)
            verticalLineTo(16f)
            arcTo(2f, 2f, 0f, false, true, 4f, 14f)
            close()
            moveTo(6f, 6f); horizontalLineTo(6.01f)
            moveTo(6f, 18f); horizontalLineTo(6.01f)
        }
    }

    val Bug: ImageVector by lazy {
        lucideStrokeVector("LucideBug") {
            moveTo(8f, 2f); lineTo(9.88f, 3.88f)
            moveTo(14.12f, 3.88f); lineTo(16f, 2f)
            moveTo(9f, 7.13f)
            verticalLineTo(6f)
            arcTo(3f, 3f, 0f, false, true, 15f, 6f)
            verticalLineTo(7.13f)
            moveTo(6f, 13f)
            verticalLineTo(10f)
            arcTo(6f, 6f, 0f, false, true, 18f, 10f)
            verticalLineTo(13f)
            arcTo(6f, 6f, 0f, false, true, 6f, 13f)
            close()
            moveTo(12f, 20f); verticalLineTo(11f)
            moveTo(2f, 13f); horizontalLineTo(6f)
            moveTo(18f, 13f); horizontalLineTo(22f)
        }
    }

    val UserCog: ImageVector by lazy {
        lucideStrokeVector("LucideUserCog") {
            moveTo(16f, 21f)
            verticalLineTo(19f)
            arcTo(4f, 4f, 0f, false, false, 12f, 15f)
            horizontalLineTo(6f)
            arcTo(4f, 4f, 0f, false, false, 2f, 19f)
            verticalLineTo(21f)
            moveTo(13f, 7f)
            arcTo(4f, 4f, 0f, true, true, 5f, 7f)
            arcTo(4f, 4f, 0f, true, true, 13f, 7f)
            moveTo(21f, 15f)
            arcTo(2f, 2f, 0f, true, true, 17f, 15f)
            arcTo(2f, 2f, 0f, true, true, 21f, 15f)
        }
    }

    val Megaphone: ImageVector by lazy {
        lucideStrokeVector("LucideMegaphone") {
            moveTo(3f, 11f)
            lineTo(21f, 5f)
            verticalLineTo(17f)
            lineTo(3f, 13f)
            close()
            moveTo(11.6f, 16.8f)
            arcTo(3f, 3f, 0f, true, true, 5.8f, 15.2f)
        }
    }

    val LogOut: ImageVector by lazy {
        lucideStrokeVector("LucideLogOut") {
            moveTo(9f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            verticalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 5f, 3f)
            horizontalLineTo(9f)
            moveTo(16f, 17f)
            lineTo(21f, 12f)
            lineTo(16f, 7f)
            moveTo(21f, 12f)
            horizontalLineTo(9f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        lucideStrokeVector("LucideChevronRight") {
            moveTo(9f, 18f)
            lineTo(15f, 12f)
            lineTo(9f, 6f)
        }
    }

    val Edit3: ImageVector by lazy {
        lucideStrokeVector("LucideEdit3") {
            moveTo(12f, 20f); horizontalLineTo(21f)
            moveTo(16.5f, 3.5f)
            arcTo(2.12f, 2.12f, 0f, false, true, 19.5f, 6.5f)
            lineTo(7f, 19f)
            lineTo(3f, 20f)
            lineTo(4f, 16f)
            close()
        }
    }

    val Palette: ImageVector by lazy {
        lucideStrokeVector("LucidePalette") {
            moveTo(12f, 2f)
            arcTo(10f, 10f, 0f, false, false, 2f, 12f)
            arcTo(10f, 10f, 0f, false, false, 12f, 22f)
            arcTo(2.5f, 2.5f, 0f, false, false, 14.5f, 19.5f)
            curveTo(14.5f, 18.9f, 14.3f, 18.4f, 14f, 18f)
            curveTo(13.7f, 17.6f, 13.5f, 17.1f, 13.5f, 16.5f)
            arcTo(2.5f, 2.5f, 0f, false, true, 16f, 14f)
            horizontalLineTo(18f)
            arcTo(4f, 4f, 0f, false, false, 22f, 10f)
            curveTo(22f, 5.6f, 17.5f, 2f, 12f, 2f)
            close()
            moveTo(7.5f, 10.5f); horizontalLineTo(7.51f)
            moveTo(12f, 7.5f); horizontalLineTo(12.01f)
            moveTo(16.5f, 10.5f); horizontalLineTo(16.51f)
        }
    }

    val MoonStar: ImageVector by lazy {
        lucideStrokeVector("LucideMoonStar") {
            moveTo(12f, 3f)
            arcTo(6f, 6f, 0f, false, false, 21f, 12f)
            arcTo(9f, 9f, 0f, true, true, 12f, 3f)
            close()
            moveTo(19f, 3f); verticalLineTo(7f)
            moveTo(21f, 5f); horizontalLineTo(17f)
        }
    }

    val Cpu: ImageVector by lazy {
        lucideStrokeVector("LucideCpu") {
            moveTo(6f, 4f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, false, true, 20f, 6f)
            verticalLineTo(18f)
            arcTo(2f, 2f, 0f, false, true, 18f, 20f)
            horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 4f, 18f)
            verticalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 6f, 4f)
            close()
            moveTo(9f, 9f); horizontalLineTo(15f); verticalLineTo(15f); horizontalLineTo(9f); close()
            moveTo(9f, 1f); verticalLineTo(4f)
            moveTo(15f, 1f); verticalLineTo(4f)
            moveTo(9f, 20f); verticalLineTo(23f)
            moveTo(15f, 20f); verticalLineTo(23f)
            moveTo(20f, 9f); horizontalLineTo(23f)
            moveTo(20f, 14f); horizontalLineTo(23f)
            moveTo(1f, 9f); horizontalLineTo(4f)
            moveTo(1f, 14f); horizontalLineTo(4f)
        }
    }

    val Ban: ImageVector by lazy {
        lucideStrokeVector("LucideBan") {
            moveTo(22f, 12f)
            arcTo(10f, 10f, 0f, true, true, 2f, 12f)
            arcTo(10f, 10f, 0f, true, true, 22f, 12f)
            moveTo(4.9f, 4.9f)
            lineTo(19.1f, 19.1f)
        }
    }

    val RotateCcw: ImageVector by lazy {
        lucideStrokeVector("LucideRotateCcw") {
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, true, false, 6f, 5.3f)
            lineTo(3f, 8f)
            moveTo(3f, 3f); verticalLineTo(8f); horizontalLineTo(8f)
        }
    }

    val ExternalLink: ImageVector by lazy {
        lucideStrokeVector("LucideExternalLink") {
            moveTo(18f, 13f)
            verticalLineTo(19f)
            arcTo(2f, 2f, 0f, false, true, 16f, 21f)
            horizontalLineTo(5f)
            arcTo(2f, 2f, 0f, false, true, 3f, 19f)
            verticalLineTo(8f)
            arcTo(2f, 2f, 0f, false, true, 5f, 6f)
            horizontalLineTo(11f)
            moveTo(15f, 3f); horizontalLineTo(21f); verticalLineTo(9f)
            moveTo(10f, 14f); lineTo(21f, 3f)
        }
    }

    val ClipboardPaste: ImageVector by lazy {
        lucideStrokeVector("LucideClipboardPaste") {
            moveTo(15f, 2f)
            horizontalLineTo(9f)
            arcTo(1f, 1f, 0f, false, false, 8f, 3f)
            verticalLineTo(5f)
            arcTo(1f, 1f, 0f, false, false, 9f, 6f)
            horizontalLineTo(15f)
            arcTo(1f, 1f, 0f, false, false, 16f, 5f)
            verticalLineTo(3f)
            arcTo(1f, 1f, 0f, false, false, 15f, 2f)
            close()
            moveTo(8f, 4f)
            horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, false, false, 4f, 6f)
            verticalLineTo(20f)
            arcTo(2f, 2f, 0f, false, false, 6f, 22f)
            horizontalLineTo(18f)
            arcTo(2f, 2f, 0f, false, false, 20f, 20f)
            verticalLineTo(6f)
            arcTo(2f, 2f, 0f, false, false, 18f, 4f)
            horizontalLineTo(16f)
        }
    }

    val Mail: ImageVector by lazy {
        lucideStrokeVector("LucideMail") {
            moveTo(4f, 4f)
            horizontalLineTo(20f)
            arcTo(2f, 2f, 0f, false, true, 22f, 6f)
            verticalLineTo(18f)
            arcTo(2f, 2f, 0f, false, true, 20f, 20f)
            horizontalLineTo(4f)
            arcTo(2f, 2f, 0f, false, true, 2f, 18f)
            verticalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 4f, 4f)
            close()
            moveTo(22f, 6f)
            lineTo(12f, 13f)
            lineTo(2f, 6f)
        }
    }

    val Eye: ImageVector by lazy {
        lucideStrokeVector("LucideEye") {
            moveTo(2f, 12f)
            curveTo(2f, 12f, 5f, 5f, 12f, 5f)
            curveTo(19f, 5f, 22f, 12f, 22f, 12f)
            curveTo(22f, 12f, 19f, 19f, 12f, 19f)
            curveTo(5f, 19f, 2f, 12f, 2f, 12f)
            close()
            moveTo(15f, 12f)
            arcTo(3f, 3f, 0f, true, true, 9f, 12f)
            arcTo(3f, 3f, 0f, true, true, 15f, 12f)
        }
    }

    val EyeOff: ImageVector by lazy {
        lucideStrokeVector("LucideEyeOff") {
            moveTo(2f, 2f); lineTo(22f, 22f)
            moveTo(6.7f, 6.7f)
            curveTo(4.3f, 8.3f, 2.7f, 10.7f, 2f, 12f)
            curveTo(2f, 12f, 5f, 19f, 12f, 19f)
            curveTo(14.1f, 19f, 15.9f, 18.3f, 17.3f, 17.3f)
            moveTo(10.7f, 5.1f)
            curveTo(11.1f, 5f, 11.6f, 5f, 12f, 5f)
            curveTo(19f, 5f, 22f, 12f, 22f, 12f)
            curveTo(21.4f, 13.1f, 20.5f, 14.4f, 19.3f, 15.5f)
        }
    }

    val ArrowUpDown: ImageVector by lazy {
        lucideStrokeVector("LucideArrowUpDown") {
            moveTo(21f, 16f); lineTo(17f, 20f); lineTo(13f, 16f)
            moveTo(17f, 20f); verticalLineTo(4f)
            moveTo(3f, 8f); lineTo(7f, 4f); lineTo(11f, 8f)
            moveTo(7f, 4f); verticalLineTo(20f)
        }
    }
}
