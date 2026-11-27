package net.mailx.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

object MailxIcons {

    fun strokeVector(
        name: String,
        strokeColor: Color = Color.Unspecified,
        strokeWidth: Float = 2f,
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

    private fun lineIcon(
        name: String,
        strokePaths: List<String> = emptyList(),
        fillPaths: List<String> = emptyList(),
        strokeWidth: Float = 2f,
        autoMirror: Boolean = false
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror
        )
        for (d in strokePaths) {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        for (d in fillPaths) {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = SolidColor(Color.Black),
                stroke = null
            )
        }
        return builder.build()
    }

    val MailxMark: ImageVector by lazy {
        lineIcon(
            name = "MailxMark",
            strokePaths = listOf("M12,2.2 L18.93,5.07 L21.8,12 L18.93,18.93 L12,21.8 L5.07,18.93 L2.2,12 L5.07,5.07 Z M8.7,8.7 H15.3 A1.5,1.5 0 0 1 16.8,10.2 V13.8 A1.5,1.5 0 0 1 15.3,15.3 H8.7 A1.5,1.5 0 0 1 7.2,13.8 V10.2 A1.5,1.5 0 0 1 8.7,8.7 Z M7.9,10.1 L12,12.9 L16.1,10.1"),
            strokeWidth = 1.8f
        )
    }

    val Add: ImageVector by lazy {
        lineIcon(name = "Add", strokePaths = listOf("M12,5 V19 M5,12 H19"), fillPaths = emptyList(), autoMirror = false)
    }
    val Alert: ImageVector by lazy {
        lineIcon(name = "Alert", strokePaths = listOf("M12,3.9 L21,19.3 A1.3,1.3 0 0 1 19.9,21.2 H4.1 A1.3,1.3 0 0 1 3,19.3 L12,3.9 Z M12,10 V14.4"), fillPaths = listOf("M10.9,17.5 A1.1,1.1 0 1 0 13.1,17.5 A1.1,1.1 0 1 0 10.9,17.5 Z"), autoMirror = false)
    }
    val ArrowDown: ImageVector by lazy {
        lineIcon(name = "ArrowDown", strokePaths = listOf("M12,5 V19 M5.5,12.5 L12,19 L18.5,12.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val ArrowLeft: ImageVector by lazy {
        lineIcon(name = "ArrowLeft", strokePaths = listOf("M19,12 H5 M11,6 L5,12 L11,18"), fillPaths = emptyList(), autoMirror = true)
    }
    val ArrowUp: ImageVector by lazy {
        lineIcon(name = "ArrowUp", strokePaths = listOf("M12,19 V5 M5.5,11.5 L12,5 L18.5,11.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val AtSign: ImageVector by lazy {
        lineIcon(name = "AtSign", strokePaths = listOf("M8.2,12 A3.8,3.8 0 1 0 15.8,12 A3.8,3.8 0 1 0 8.2,12 Z M15.8,8.2 V13.2 A2.8,2.8 0 0 0 21.4,13.2 V12 A9.4,9.4 0 1 0 17.4,19.6"), fillPaths = emptyList(), autoMirror = false)
    }
    val Ban: ImageVector by lazy {
        lineIcon(name = "Ban", strokePaths = listOf("M3,12 A9,9 0 1 0 21,12 A9,9 0 1 0 3,12 Z M5.6,5.6 L18.4,18.4"), fillPaths = emptyList(), autoMirror = false)
    }
    val Bell: ImageVector by lazy {
        lineIcon(name = "Bell", strokePaths = listOf("M6,16.5 H18 L16.5,14 V10 A4.5,4.5 0 0 0 7.5,10 V14 Z M10,19.5 A2,2 0 0 0 14,19.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val Bug: ImageVector by lazy {
        lineIcon(name = "Bug", strokePaths = listOf("M8,9 H16 V15 A4,4 0 0 1 8,15 Z M9.5,9 V7.5 A2.5,2.5 0 0 1 14.5,7.5 V9 M4,11 H8 M16,11 H20 M5,16 L8,14.5 M19,16 L16,14.5 M5,7 L8,9 M19,7 L16,9"), fillPaths = emptyList(), autoMirror = false)
    }
    val Check: ImageVector by lazy {
        lineIcon(name = "Check", strokePaths = listOf("M5,12.5 L10,17.5 L19,7"), fillPaths = emptyList(), autoMirror = false)
    }
    val CheckBox: ImageVector by lazy {
        lineIcon(name = "CheckBox", strokePaths = listOf("M5,5 H19 A2,2 0 0 1 21,7 V17 A2,2 0 0 1 19,19 H5 A2,2 0 0 1 3,17 V7 A2,2 0 0 1 5,5 Z M8,12 L11,15 L16.5,9"), fillPaths = emptyList(), autoMirror = false)
    }
    val CheckCircle: ImageVector by lazy {
        lineIcon(name = "CheckCircle", strokePaths = listOf("M3,12 A9,9 0 1 0 21,12 A9,9 0 1 0 3,12 Z M8,12.3 L11,15.3 L16.3,9.3"), fillPaths = emptyList(), autoMirror = false)
    }
    val ChevronDown: ImageVector by lazy {
        lineIcon(name = "ChevronDown", strokePaths = listOf("M5,9 L12,16 L19,9"), fillPaths = emptyList(), autoMirror = false)
    }
    val ChevronRight: ImageVector by lazy {
        lineIcon(name = "ChevronRight", strokePaths = listOf("M9,5 L16,12 L9,19"), fillPaths = emptyList(), autoMirror = true)
    }
    val ChevronUp: ImageVector by lazy {
        lineIcon(name = "ChevronUp", strokePaths = listOf("M6,15 L12,9 L18,15"), fillPaths = emptyList(), autoMirror = false)
    }
    val Clock: ImageVector by lazy {
        lineIcon(name = "Clock", strokePaths = listOf("M3,12 A9,9 0 1 0 21,12 A9,9 0 1 0 3,12 Z M12,7 V12 L15.6,14.2"), fillPaths = emptyList(), autoMirror = false)
    }
    val Close: ImageVector by lazy {
        lineIcon(name = "Close", strokePaths = listOf("M6,6 L18,18 M18,6 L6,18"), fillPaths = emptyList(), autoMirror = false)
    }
    val Contrast: ImageVector by lazy {
        lineIcon(name = "Contrast", strokePaths = listOf("M3.5,12 A8.5,8.5 0 1 0 20.5,12 A8.5,8.5 0 1 0 3.5,12 Z M12,3.5 V20.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val Copy: ImageVector by lazy {
        lineIcon(name = "Copy", strokePaths = listOf("M11,8.5 H17 A2.5,2.5 0 0 1 19.5,11 V17 A2.5,2.5 0 0 1 17,19.5 H11 A2.5,2.5 0 0 1 8.5,17 V11 A2.5,2.5 0 0 1 11,8.5 Z M15.5,8.5 V6.5 A2.5,2.5 0 0 0 13,4 H6.5 A2.5,2.5 0 0 0 4,6.5 V13 A2.5,2.5 0 0 0 6.5,15.5 H8.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val Device: ImageVector by lazy {
        lineIcon(name = "Device", strokePaths = listOf("M9.6,2.6 H14.4 A2.8,2.8 0 0 1 17.2,5.4 V18.6 A2.8,2.8 0 0 1 14.4,21.4 H9.6 A2.8,2.8 0 0 1 6.8,18.6 V5.4 A2.8,2.8 0 0 1 9.6,2.6 Z M9.6,11.6 L11.6,13.6 L14.6,10 M10.8,18.2 H13.2"), fillPaths = emptyList(), autoMirror = false)
    }
    val Dice: ImageVector by lazy {
        lineIcon(name = "Dice", strokePaths = listOf("M8.2,4 H15.8 A4.2,4.2 0 0 1 20,8.2 V15.8 A4.2,4.2 0 0 1 15.8,20 H8.2 A4.2,4.2 0 0 1 4,15.8 V8.2 A4.2,4.2 0 0 1 8.2,4 Z"), fillPaths = listOf("M7.25,8.6 A1.35,1.35 0 1 0 9.95,8.6 A1.35,1.35 0 1 0 7.25,8.6 Z M14.05,8.6 A1.35,1.35 0 1 0 16.75,8.6 A1.35,1.35 0 1 0 14.05,8.6 Z M10.65,12 A1.35,1.35 0 1 0 13.35,12 A1.35,1.35 0 1 0 10.65,12 Z M7.25,15.4 A1.35,1.35 0 1 0 9.95,15.4 A1.35,1.35 0 1 0 7.25,15.4 Z M14.05,15.4 A1.35,1.35 0 1 0 16.75,15.4 A1.35,1.35 0 1 0 14.05,15.4 Z"), autoMirror = false)
    }
    val DoneAll: ImageVector by lazy {
        lineIcon(name = "DoneAll", strokePaths = listOf("M3.5,12.5 L7.5,16.5 L15.5,8 M10.5,16.5 L12.5,16.5 L20.5,8"), fillPaths = emptyList(), autoMirror = false)
    }
    val Download: ImageVector by lazy {
        lineIcon(name = "Download", strokePaths = listOf("M12,4 V15 M7,10.5 L12,15.5 L17,10.5 M5,20 H19"), fillPaths = emptyList(), autoMirror = false)
    }
    val Edit: ImageVector by lazy {
        lineIcon(name = "Edit", strokePaths = listOf("M4,20 L5,15.5 L16.3,4.2 A1.5,1.5 0 0 1 18.4,4.2 L19.8,5.6 A1.5,1.5 0 0 1 19.8,7.7 L8.5,19 Z M14.5,6 L18,9.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val External: ImageVector by lazy {
        lineIcon(name = "External", strokePaths = listOf("M14,4 H20 V10 M20,4 L11.5,12.5 M18,14.5 V18 A2,2 0 0 1 16,20 H6 A2,2 0 0 1 4,18 V8 A2,2 0 0 1 6,6 H9.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val Eye: ImageVector by lazy {
        lineIcon(name = "Eye", strokePaths = listOf("M2.5,12 C5,7.6 8.4,5.6 12,5.6 C15.6,5.6 19,7.6 21.5,12 C19,16.4 15.6,18.4 12,18.4 C8.4,18.4 5,16.4 2.5,12 Z M9,12 A3,3 0 1 0 15,12 A3,3 0 1 0 9,12 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val EyeOff: ImageVector by lazy {
        lineIcon(name = "EyeOff", strokePaths = listOf("M2.5,12 C5,7.6 8.4,5.6 12,5.6 C15.6,5.6 19,7.6 21.5,12 C19,16.4 15.6,18.4 12,18.4 C8.4,18.4 5,16.4 2.5,12 Z M9,12 A3,3 0 1 0 15,12 A3,3 0 1 0 9,12 Z M4,4 L20,20"), fillPaths = emptyList(), autoMirror = false)
    }
    val Filter: ImageVector by lazy {
        lineIcon(name = "Filter", strokePaths = listOf("M4,5.5 H20 L14,12.5 V18.5 L10,20 V12.5 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val FilterOff: ImageVector by lazy {
        lineIcon(name = "FilterOff", strokePaths = listOf("M4,5.5 H20 L14,12.5 V18.5 L10,20 V12.5 Z M4,4 L20,20"), fillPaths = emptyList(), autoMirror = false)
    }
    val Forward: ImageVector by lazy {
        lineIcon(name = "Forward", strokePaths = listOf("M3.5,12 H6.4 M11.6,12 H20 M15.4,7.4 L20,12 L15.4,16.6"), fillPaths = listOf("M6.9,12 A2.1,2.1 0 1 0 11.1,12 A2.1,2.1 0 1 0 6.9,12 Z"), autoMirror = true)
    }
    val Globe: ImageVector by lazy {
        lineIcon(name = "Globe", strokePaths = listOf("M3,12 A9,9 0 1 0 21,12 A9,9 0 1 0 3,12 Z M3,12 H21 M12,3 C9.2,6 8,9 8,12 C8,15 9.2,18 12,21 M12,3 C14.8,6 16,9 16,12 C16,15 14.8,18 12,21"), fillPaths = emptyList(), autoMirror = false)
    }
    val Inbox: ImageVector by lazy {
        lineIcon(name = "Inbox", strokePaths = listOf("M3,13 H8 L9.5,16 H14.5 L16,13 H21 M3,13 L5.4,5.8 A2,2 0 0 1 7.3,4.4 H16.7 A2,2 0 0 1 18.6,5.8 L21,13 V18 A2,2 0 0 1 19,20 H5 A2,2 0 0 1 3,18 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Info: ImageVector by lazy {
        lineIcon(name = "Info", strokePaths = listOf("M3,12 A9,9 0 1 0 21,12 A9,9 0 1 0 3,12 Z M12,11 V16.4"), fillPaths = listOf("M10.8,7.7 A1.2,1.2 0 1 0 13.2,7.7 A1.2,1.2 0 1 0 10.8,7.7 Z"), autoMirror = false)
    }
    val Key: ImageVector by lazy {
        lineIcon(name = "Key", strokePaths = listOf("M3.7,15.6 A3.9,3.9 0 1 0 11.5,15.6 A3.9,3.9 0 1 0 3.7,15.6 Z M10.5,12.8 L20,3.5 M16.4,7 L19,9.6"), fillPaths = emptyList(), autoMirror = false)
    }
    val Lock: ImageVector by lazy {
        lineIcon(name = "Lock", strokePaths = listOf("M7.6,10.5 H16.4 A2.6,2.6 0 0 1 19,13.1 V18.4 A2.6,2.6 0 0 1 16.4,21 H7.6 A2.6,2.6 0 0 1 5,18.4 V13.1 A2.6,2.6 0 0 1 7.6,10.5 Z M8,10.5 V8 A4,4 0 0 1 16,8 V10.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val LogOut: ImageVector by lazy {
        lineIcon(name = "LogOut", strokePaths = listOf("M9,4 H6 A2,2 0 0 0 4,6 V18 A2,2 0 0 0 6,20 H9 M16,8 L20,12 L16,16 M20,12 H9"), fillPaths = emptyList(), autoMirror = true)
    }
    val Mail: ImageVector by lazy {
        lineIcon(name = "Mail", strokePaths = listOf("M6.2,5.5 H17.8 A3.2,3.2 0 0 1 21,8.7 V15.3 A3.2,3.2 0 0 1 17.8,18.5 H6.2 A3.2,3.2 0 0 1 3,15.3 V8.7 A3.2,3.2 0 0 1 6.2,5.5 Z M4,7.6 L12,13.4 L20,7.6"), fillPaths = emptyList(), autoMirror = false)
    }
    val Megaphone: ImageVector by lazy {
        lineIcon(name = "Megaphone", strokePaths = listOf("M4,10 V14 H7.5 L16.5,18.5 V5.5 L7.5,10 Z M19,9.5 A4,4 0 0 1 19,14.5 M7.5,14 L9,19"), fillPaths = emptyList(), autoMirror = false)
    }
    val Moon: ImageVector by lazy {
        lineIcon(name = "Moon", strokePaths = listOf("M20,14.6 A8.5,8.5 0 1 1 9.4,4 A6.8,6.8 0 0 0 20,14.6 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val More: ImageVector by lazy {
        lineIcon(name = "More", strokePaths = emptyList(), fillPaths = listOf("M10.3,5.5 A1.7,1.7 0 1 0 13.7,5.5 A1.7,1.7 0 1 0 10.3,5.5 Z M10.3,12 A1.7,1.7 0 1 0 13.7,12 A1.7,1.7 0 1 0 10.3,12 Z M10.3,18.5 A1.7,1.7 0 1 0 13.7,18.5 A1.7,1.7 0 1 0 10.3,18.5 Z"), autoMirror = false)
    }
    val MotionOff: ImageVector by lazy {
        lineIcon(name = "MotionOff", strokePaths = listOf("M12,4 A8,8 0 1 0 20,12 M4,4 L20,20"), fillPaths = emptyList(), autoMirror = false)
    }
    val Notes: ImageVector by lazy {
        lineIcon(name = "Notes", strokePaths = listOf("M5,7 H19 M5,12 H19 M5,17 H13"), fillPaths = emptyList(), autoMirror = false)
    }
    val Overview: ImageVector by lazy {
        lineIcon(name = "Overview", strokePaths = listOf("M5.7,3.5 H8.5 A2.2,2.2 0 0 1 10.7,5.7 V10.6 A2.2,2.2 0 0 1 8.5,12.8 H5.7 A2.2,2.2 0 0 1 3.5,10.6 V5.7 A2.2,2.2 0 0 1 5.7,3.5 Z M15.5,3.5 H18.3 A2.2,2.2 0 0 1 20.5,5.7 V6.6 A2.2,2.2 0 0 1 18.3,8.8 H15.5 A2.2,2.2 0 0 1 13.3,6.6 V5.7 A2.2,2.2 0 0 1 15.5,3.5 Z M15.5,11.7 H18.3 A2.2,2.2 0 0 1 20.5,13.9 V18.3 A2.2,2.2 0 0 1 18.3,20.5 H15.5 A2.2,2.2 0 0 1 13.3,18.3 V13.9 A2.2,2.2 0 0 1 15.5,11.7 Z M5.7,15.7 H8.5 A2.2,2.2 0 0 1 10.7,17.9 V18.3 A2.2,2.2 0 0 1 8.5,20.5 H5.7 A2.2,2.2 0 0 1 3.5,18.3 V17.9 A2.2,2.2 0 0 1 5.7,15.7 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Palette: ImageVector by lazy {
        lineIcon(name = "Palette", strokePaths = listOf("M12,3.5 A8.5,8.5 0 1 0 12,20.5 H13.5 A2,2 0 0 0 15.5,18.5 A2,2 0 0 1 17.5,16.5 H18.5 A2,2 0 0 0 20.5,14.5 A8.5,8.5 0 0 0 12,3.5 Z"), fillPaths = listOf("M7.5,11 A1.1,1.1 0 1 0 9.7,11 A1.1,1.1 0 1 0 7.5,11 Z M10.5,7.5 A1.1,1.1 0 1 0 12.7,7.5 A1.1,1.1 0 1 0 10.5,7.5 Z M14.3,9 A1.1,1.1 0 1 0 16.5,9 A1.1,1.1 0 1 0 14.3,9 Z"), autoMirror = false)
    }
    val Paste: ImageVector by lazy {
        lineIcon(name = "Paste", strokePaths = listOf("M7.6,5.2 H16.4 A2.6,2.6 0 0 1 19,7.8 V18.4 A2.6,2.6 0 0 1 16.4,21 H7.6 A2.6,2.6 0 0 1 5,18.4 V7.8 A2.6,2.6 0 0 1 7.6,5.2 Z M10.4,3 H13.6 A1.6,1.6 0 0 1 15.2,4.6 V5.8 A1.6,1.6 0 0 1 13.6,7.4 H10.4 A1.6,1.6 0 0 1 8.8,5.8 V4.6 A1.6,1.6 0 0 1 10.4,3 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Pause: ImageVector by lazy {
        lineIcon(name = "Pause", strokePaths = listOf("M7.9,5 H8.7 A1.4,1.4 0 0 1 10.1,6.4 V17.6 A1.4,1.4 0 0 1 8.7,19 H7.9 A1.4,1.4 0 0 1 6.5,17.6 V6.4 A1.4,1.4 0 0 1 7.9,5 Z M15.3,5 H16.1 A1.4,1.4 0 0 1 17.5,6.4 V17.6 A1.4,1.4 0 0 1 16.1,19 H15.3 A1.4,1.4 0 0 1 13.9,17.6 V6.4 A1.4,1.4 0 0 1 15.3,5 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Pin: ImageVector by lazy {
        lineIcon(name = "Pin", strokePaths = listOf("M12,16.5 V21 M8.2,3.8 H15.8 L14.8,9.6 L18,13 V15.2 H6 V13 L9.2,9.6 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Play: ImageVector by lazy {
        lineIcon(name = "Play", strokePaths = listOf("M7.5,5 L19,12 L7.5,19 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Pulse: ImageVector by lazy {
        lineIcon(name = "Pulse", strokePaths = listOf("M3,12 H7 L10,5 L14,19 L17,12 H21"), fillPaths = emptyList(), autoMirror = false)
    }
    val Refresh: ImageVector by lazy {
        lineIcon(name = "Refresh", strokePaths = listOf("M19.5,12 A7.5,7.5 0 1 1 16.9,6.3 M19.8,4 V8.6 H15.2"), fillPaths = emptyList(), autoMirror = false)
    }
    val Reply: ImageVector by lazy {
        lineIcon(name = "Reply", strokePaths = listOf("M9.5,6.5 L4,12 L9.5,17.5 M4,12 H13.5 A6.5,6.5 0 0 1 20,18.5 V19.5"), fillPaths = emptyList(), autoMirror = true)
    }
    val Scan: ImageVector by lazy {
        lineIcon(name = "Scan", strokePaths = listOf("M4,8.2 V6.2 A2.2,2.2 0 0 1 6.2,4 H8.2 M15.8,4 H17.8 A2.2,2.2 0 0 1 20,6.2 V8.2 M20,15.8 V17.8 A2.2,2.2 0 0 1 17.8,20 H15.8 M8.2,20 H6.2 A2.2,2.2 0 0 1 4,17.8 V15.8 M8.6,12 A3.4,3.4 0 1 0 15.4,12 A3.4,3.4 0 1 0 8.6,12 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Science: ImageVector by lazy {
        lineIcon(name = "Science", strokePaths = listOf("M9.5,3.5 H14.5 M10.5,3.5 V9 L5.5,18 A2,2 0 0 0 7.2,21 H16.8 A2,2 0 0 0 18.5,18 L13.5,9 V3.5 M7.8,15 H16.2"), fillPaths = emptyList(), autoMirror = false)
    }
    val Search: ImageVector by lazy {
        lineIcon(name = "Search", strokePaths = listOf("M4.5,11 A6.5,6.5 0 1 0 17.5,11 A6.5,6.5 0 1 0 4.5,11 Z M16,16 L20.5,20.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val Send: ImageVector by lazy {
        lineIcon(name = "Send", strokePaths = listOf("M20.5,3.5 L10.2,13.8 M20.5,3.5 L14.2,20.5 L10.2,13.8 L3.5,9.8 Z"), fillPaths = emptyList(), autoMirror = true)
    }
    val Server: ImageVector by lazy {
        lineIcon(name = "Server", strokePaths = listOf("M6.2,4 H17.8 A2.2,2.2 0 0 1 20,6.2 V8 A2.2,2.2 0 0 1 17.8,10.2 H6.2 A2.2,2.2 0 0 1 4,8 V6.2 A2.2,2.2 0 0 1 6.2,4 Z M6.2,13.8 H17.8 A2.2,2.2 0 0 1 20,16 V17.8 A2.2,2.2 0 0 1 17.8,20 H6.2 A2.2,2.2 0 0 1 4,17.8 V16 A2.2,2.2 0 0 1 6.2,13.8 Z"), fillPaths = listOf("M7,7.1 A1,1 0 1 0 9,7.1 A1,1 0 1 0 7,7.1 Z M7,16.9 A1,1 0 1 0 9,16.9 A1,1 0 1 0 7,16.9 Z"), autoMirror = false)
    }
    val Settings: ImageVector by lazy {
        lineIcon(name = "Settings", strokePaths = listOf("M10.08,5.06 L10.77,2.68 L13.23,2.68 L13.92,5.06 L15.55,5.73 L17.72,4.54 L19.46,6.28 L18.27,8.45 L18.94,10.08 L21.32,10.77 L21.32,13.23 L18.94,13.92 L18.27,15.55 L19.46,17.72 L17.72,19.46 L15.55,18.27 L13.92,18.94 L13.23,21.32 L10.77,21.32 L10.08,18.94 L8.45,18.27 L6.28,19.46 L4.54,17.72 L5.73,15.55 L5.06,13.92 L2.68,13.23 L2.68,10.77 L5.06,10.08 L5.73,8.45 L4.54,6.28 L6.28,4.54 L8.45,5.73 Z M9.1,12 A2.9,2.9 0 1 0 14.9,12 A2.9,2.9 0 1 0 9.1,12 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Shield: ImageVector by lazy {
        lineIcon(name = "Shield", strokePaths = listOf("M12,3 L19.5,6 V11.5 C19.5,16 16.5,19.4 12,21 C7.5,19.4 4.5,16 4.5,11.5 V6 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val ShieldCheck: ImageVector by lazy {
        lineIcon(name = "ShieldCheck", strokePaths = listOf("M12,3 L19.5,6 V11.5 C19.5,16 16.5,19.4 12,21 C7.5,19.4 4.5,16 4.5,11.5 V6 Z M8.6,12 L11,14.4 L15.6,9.6"), fillPaths = emptyList(), autoMirror = false)
    }
    val Sliders: ImageVector by lazy {
        lineIcon(name = "Sliders", strokePaths = listOf("M4,7 H9.5 M14.5,7 H20 M4,17 H6.5 M11.5,17 H20 M9.5,7 A2.5,2.5 0 1 0 14.5,7 A2.5,2.5 0 1 0 9.5,7 Z M6.5,17 A2.5,2.5 0 1 0 11.5,17 A2.5,2.5 0 1 0 6.5,17 Z"), fillPaths = emptyList(), autoMirror = false)
    }
    val Sort: ImageVector by lazy {
        lineIcon(name = "Sort", strokePaths = listOf("M8,19 V5 M8,5 L4.5,8.5 M8,5 L11.5,8.5 M16,5 V19 M16,19 L12.5,15.5 M16,19 L19.5,15.5"), fillPaths = emptyList(), autoMirror = false)
    }
    val Trash: ImageVector by lazy {
        lineIcon(name = "Trash", strokePaths = listOf("M4,7 H20 M10,11 V16 M14,11 V16 M6,7 L7,19 A2,2 0 0 0 9,21 H15 A2,2 0 0 0 17,19 L18,7 M9,7 V5 A1,1 0 0 1 10,4 H14 A1,1 0 0 1 15,5 V7"), fillPaths = emptyList(), autoMirror = false)
    }
    val Upload: ImageVector by lazy {
        lineIcon(name = "Upload", strokePaths = listOf("M12,16 V5 M7,9.5 L12,4.5 L17,9.5 M5,20 H19"), fillPaths = emptyList(), autoMirror = false)
    }
    val User: ImageVector by lazy {
        lineIcon(name = "User", strokePaths = listOf("M8,8 A4,4 0 1 0 16,8 A4,4 0 1 0 8,8 Z M4.5,20 C4.5,16.2 7.7,14 12,14 C16.3,14 19.5,16.2 19.5,20"), fillPaths = emptyList(), autoMirror = false)
    }
    val Wildcard: ImageVector by lazy {
        lineIcon(name = "Wildcard", strokePaths = listOf("M12,3.8 V20.2 M4.9,7.9 L19.1,16.1 M19.1,7.9 L4.9,16.1"), fillPaths = emptyList(), autoMirror = false)
    }
    val LayoutDashboardSelected: ImageVector by lazy {
        lineIcon(name = "LayoutDashboardSelected", strokePaths = listOf("M5.7,3.5 H8.5 A2.2,2.2 0 0 1 10.7,5.7 V10.6 A2.2,2.2 0 0 1 8.5,12.8 H5.7 A2.2,2.2 0 0 1 3.5,10.6 V5.7 A2.2,2.2 0 0 1 5.7,3.5 Z M15.5,3.5 H18.3 A2.2,2.2 0 0 1 20.5,5.7 V6.6 A2.2,2.2 0 0 1 18.3,8.8 H15.5 A2.2,2.2 0 0 1 13.3,6.6 V5.7 A2.2,2.2 0 0 1 15.5,3.5 Z M15.5,11.7 H18.3 A2.2,2.2 0 0 1 20.5,13.9 V18.3 A2.2,2.2 0 0 1 18.3,20.5 H15.5 A2.2,2.2 0 0 1 13.3,18.3 V13.9 A2.2,2.2 0 0 1 15.5,11.7 Z M5.7,15.7 H8.5 A2.2,2.2 0 0 1 10.7,17.9 V18.3 A2.2,2.2 0 0 1 8.5,20.5 H5.7 A2.2,2.2 0 0 1 3.5,18.3 V17.9 A2.2,2.2 0 0 1 5.7,15.7 Z"), fillPaths = emptyList(), strokeWidth = 2.5f)
    }
    val AtSignSelected: ImageVector by lazy {
        lineIcon(name = "AtSignSelected", strokePaths = listOf("M8.2,12 A3.8,3.8 0 1 0 15.8,12 A3.8,3.8 0 1 0 8.2,12 Z M15.8,8.2 V13.2 A2.8,2.8 0 0 0 21.4,13.2 V12 A9.4,9.4 0 1 0 17.4,19.6"), fillPaths = emptyList(), strokeWidth = 2.5f)
    }
    val InboxSelected: ImageVector by lazy {
        lineIcon(name = "InboxSelected", strokePaths = listOf("M3,13 H8 L9.5,16 H14.5 L16,13 H21 M3,13 L5.4,5.8 A2,2 0 0 1 7.3,4.4 H16.7 A2,2 0 0 1 18.6,5.8 L21,13 V18 A2,2 0 0 1 19,20 H5 A2,2 0 0 1 3,18 Z"), fillPaths = emptyList(), strokeWidth = 2.5f)
    }
    val SlidersHorizontalSelected: ImageVector by lazy {
        lineIcon(name = "SlidersHorizontalSelected", strokePaths = listOf("M4,7 H9.5 M14.5,7 H20 M4,17 H6.5 M11.5,17 H20 M9.5,7 A2.5,2.5 0 1 0 14.5,7 A2.5,2.5 0 1 0 9.5,7 Z M6.5,17 A2.5,2.5 0 1 0 11.5,17 A2.5,2.5 0 1 0 6.5,17 Z"), fillPaths = emptyList(), strokeWidth = 2.5f)
    }
    val SettingsSelected: ImageVector by lazy {
        lineIcon(name = "SettingsSelected", strokePaths = listOf("M10.08,5.06 L10.77,2.68 L13.23,2.68 L13.92,5.06 L15.55,5.73 L17.72,4.54 L19.46,6.28 L18.27,8.45 L18.94,10.08 L21.32,10.77 L21.32,13.23 L18.94,13.92 L18.27,15.55 L19.46,17.72 L17.72,19.46 L15.55,18.27 L13.92,18.94 L13.23,21.32 L10.77,21.32 L10.08,18.94 L8.45,18.27 L6.28,19.46 L4.54,17.72 L5.73,15.55 L5.06,13.92 L2.68,13.23 L2.68,10.77 L5.06,10.08 L5.73,8.45 L4.54,6.28 L6.28,4.54 L8.45,5.73 Z M9.1,12 A2.9,2.9 0 1 0 14.9,12 A2.9,2.9 0 1 0 9.1,12 Z"), fillPaths = emptyList(), strokeWidth = 2.5f)
    }

    val LayoutDashboard: ImageVector get() = Overview
    val SlidersHorizontal: ImageVector get() = Sliders
    val Passkey: ImageVector get() = Key
    val RefreshCw: ImageVector get() = Refresh
    val Plus: ImageVector get() = Add
    val Sparkles: ImageVector get() = Wildcard
    val PauseCircle: ImageVector get() = Pause
    val PlayCircle: ImageVector get() = Play
    val Trash2: ImageVector get() = Trash
    val X: ImageVector get() = Close
    val KeyRound: ImageVector get() = Key
    val UserCog: ImageVector get() = User
    val Edit3: ImageVector get() = Edit
    val MoonStar: ImageVector get() = Moon
    val Cpu: ImageVector get() = Device
    val RotateCcw: ImageVector get() = Refresh
    val ExternalLink: ImageVector get() = External
    val ClipboardPaste: ImageVector get() = Paste
    val MailCheck: ImageVector get() = CheckCircle
    val ArrowUpDown: ImageVector get() = Sort

    val coreFiftyOne: List<ImageVector> by lazy {
        listOf(
            Add,
            Alert,
            ArrowLeft,
            AtSign,
            Ban,
            Check,
            CheckCircle,
            ChevronDown,
            ChevronRight,
            Clock,
            Close,
            Copy,
            Device,
            Dice,
            Download,
            Edit,
            External,
            Eye,
            EyeOff,
            Filter,
            Forward,
            Globe,
            Inbox,
            Info,
            Key,
            Lock,
            LogOut,
            Mail,
            Moon,
            More,
            Overview,
            Paste,
            Pause,
            Pin,
            Play,
            Pulse,
            Refresh,
            Reply,
            Scan,
            Search,
            Send,
            Server,
            Settings,
            Shield,
            ShieldCheck,
            Sliders,
            Sort,
            Trash,
            Upload,
            User,
            Wildcard,
        )
    }
}
