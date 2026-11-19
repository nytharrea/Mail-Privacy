package net.mailx.android.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

object MailxIcons {

    fun strokeVector(
        name: String,
        strokeColor: Color = Color.Unspecified,
        strokeWidth: Float = 1.75f,
        fillType: PathFillType = PathFillType.NonZero,
        block: PathBuilder.() -> Unit
    ): ImageVector {
        val resolvedStroke = if (strokeColor == Color.Unspecified) Color.Black else strokeColor
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = PathBuilder().apply(block).nodes,
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(resolvedStroke),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathFillType = fillType
        ).build()
    }

    val MailxMark: ImageVector by lazy {
        strokeVector(
            name = "MailxMark",
            strokeColor = Color.Unspecified,
            strokeWidth = 1.7f
        ) {
            // Envelope body.
            moveTo(3.5f, 7.5f)
            lineTo(20.5f, 7.5f)
            lineTo(20.5f, 18.5f)
            lineTo(3.5f, 18.5f)
            close()
            // Envelope fold.
            moveTo(3.5f, 8f)
            lineTo(12f, 14f)
            lineTo(20.5f, 8f)
            // Minimal web strands around the envelope.
            moveTo(12f, 2.5f)
            lineTo(12f, 7.5f)
            moveTo(6.5f, 4.5f)
            lineTo(9.2f, 8.2f)
            moveTo(17.5f, 4.5f)
            lineTo(14.8f, 8.2f)
        }
    }

    val LayoutDashboard: ImageVector get() = Icons.Outlined.Dashboard
    val LayoutDashboardSelected: ImageVector get() = Icons.Filled.Dashboard

    val AtSign: ImageVector get() = Icons.Outlined.AlternateEmail
    val AtSignSelected: ImageVector get() = Icons.Filled.AlternateEmail

    val Inbox: ImageVector get() = Icons.Outlined.Inbox
    val InboxSelected: ImageVector get() = Icons.Filled.Inbox

    val SlidersHorizontal: ImageVector get() = Icons.Outlined.Tune
    val SlidersHorizontalSelected: ImageVector get() = Icons.Filled.Tune

    val Settings: ImageVector get() = Icons.Outlined.Settings
    val SettingsSelected: ImageVector get() = Icons.Filled.Settings

    val Passkey: ImageVector get() = Icons.Outlined.Key
    val ShieldCheck: ImageVector get() = Icons.Outlined.Shield
    val Send: ImageVector get() = Icons.AutoMirrored.Outlined.Send
    val RefreshCw: ImageVector get() = Icons.Outlined.Refresh
    val Plus: ImageVector get() = Icons.Outlined.Add
    val Copy: ImageVector get() = Icons.Outlined.ContentCopy
    val Check: ImageVector get() = Icons.Outlined.Check
    val CheckCircle: ImageVector get() = Icons.Outlined.CheckCircle
    val Pin: ImageVector get() = Icons.Outlined.PushPin
    val Sparkles: ImageVector get() = Icons.Outlined.AutoAwesome
    val PauseCircle: ImageVector get() = Icons.Outlined.PauseCircle
    val PlayCircle: ImageVector get() = Icons.Outlined.PlayCircle
    val Trash2: ImageVector get() = Icons.Outlined.DeleteOutline
    val Search: ImageVector get() = Icons.Outlined.Search
    val X: ImageVector get() = Icons.Outlined.Close
    val KeyRound: ImageVector get() = Icons.Outlined.Key
    val Lock: ImageVector get() = Icons.Outlined.Lock
    val Globe: ImageVector get() = Icons.Outlined.Language
    val Server: ImageVector get() = Icons.Outlined.Dns
    val Bug: ImageVector get() = Icons.Outlined.BugReport
    val UserCog: ImageVector get() = Icons.Outlined.ManageAccounts
    val Megaphone: ImageVector get() = Icons.Outlined.Campaign
    val LogOut: ImageVector get() = Icons.AutoMirrored.Outlined.Logout
    val ChevronRight: ImageVector get() = Icons.Outlined.ChevronRight
    val Edit3: ImageVector get() = Icons.Outlined.Edit
    val Palette: ImageVector get() = Icons.Outlined.Palette
    val MoonStar: ImageVector get() = Icons.Outlined.DarkMode
    val Cpu: ImageVector get() = Icons.Outlined.Memory
    val Ban: ImageVector get() = Icons.Outlined.Block
    val RotateCcw: ImageVector get() = Icons.Outlined.Restore
    val ExternalLink: ImageVector get() = Icons.AutoMirrored.Outlined.OpenInNew
    val ClipboardPaste: ImageVector get() = Icons.Outlined.ContentPaste
    val Mail: ImageVector get() = Icons.Outlined.Mail
    val MailCheck: ImageVector get() = Icons.Outlined.CheckCircle
    val Shield: ImageVector get() = Icons.Outlined.Shield
    val Add: ImageVector get() = Icons.Outlined.Add
    val Eye: ImageVector get() = Icons.Outlined.Visibility
    val EyeOff: ImageVector get() = Icons.Outlined.VisibilityOff
    val ArrowUpDown: ImageVector get() = Icons.Outlined.SwapVert
}
