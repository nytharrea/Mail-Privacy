package net.mailx.android.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MailxIconsUnitTest {

    @Test
    fun mailxLineCoreSetHasAllFiftyOneIconsOnTwentyFourDpGrid() {
        val core = MailxIcons.coreFiftyOne
        assertEquals(51, core.size)
        for (icon in core) {
            assertEquals(24.dp, icon.defaultWidth)
            assertEquals(24.dp, icon.defaultHeight)
            assertEquals(24f, icon.viewportWidth, 0.01f)
            assertEquals(24f, icon.viewportHeight, 0.01f)
            assertTrue("${icon.name} should not be empty", icon.root.size > 0)
        }
    }

    @Test
    fun directionalIconsEnableAutoMirror() {
        val directional = listOf(
            MailxIcons.ArrowLeft,
            MailxIcons.ChevronRight,
            MailxIcons.Forward,
            MailxIcons.Reply,
            MailxIcons.Send,
            MailxIcons.LogOut
        )
        for (icon in directional) {
            assertTrue("${icon.name} must enable autoMirror", icon.autoMirror)
        }
    }
}
