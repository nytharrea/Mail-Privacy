package net.mailx.android.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedBackupCodecTest {
    @Test fun roundTripPreservesSettings() {
        val secret = "correct horse battery staple".toCharArray()
        val payload = JSONObject().put("format", "mailx-android-settings")
            .put("settings", JSONObject().put("theme", "system"))
        val bytes = EncryptedBackupCodec.encrypt(payload, secret)
        val decoded = EncryptedBackupCodec.decrypt(bytes, secret)
        assertEquals("system", decoded.getJSONObject("settings").getString("theme"))
    }

    @Test fun incorrectPasswordFailsAuthentication() {
        val bytes = EncryptedBackupCodec.encrypt(JSONObject().put("ok", true), "very-long-password".toCharArray())
        try {
            EncryptedBackupCodec.decrypt(bytes, "not-the-password".toCharArray())
            fail("Expected GCM authentication failure")
        } catch (_: javax.crypto.AEADBadTagException) { }
    }

    @Test fun tamperedHeaderIsRejected() {
        val bytes = EncryptedBackupCodec.encrypt(JSONObject().put("ok", true), "very-long-password".toCharArray())
        bytes[0] = 0
        try {
            EncryptedBackupCodec.decrypt(bytes, "very-long-password".toCharArray())
            fail("Expected invalid backup")
        } catch (_: IllegalArgumentException) { }
    }
}
