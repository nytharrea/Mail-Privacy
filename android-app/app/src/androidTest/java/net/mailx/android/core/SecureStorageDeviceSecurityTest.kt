package net.mailx.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStorageDeviceSecurityTest {

    @Test
    fun masterKeyIsHardwareBacked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SecurePrefsManager(context)
        val level = prefs.getMasterKeySecurityLevel()
        assertTrue(
            "Expected hardware-backed master key level, got: $level",
            SecurePrefsManager.isHardwareSecurityLevel(level)
        )
    }

    @Test
    fun sessionRoundTripAndLogoutAreDurableOnDevice() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SecurePrefsManager(context)
        prefs.setSessionSecretsAndPersist(
            accessKey = "test_access_key",
            bearerToken = "test_bearer_token",
            accountEmail = "device-test@example.com"
        )
        val afterLogin = prefs.readDurableSnapshot()
        assertEquals("test_bearer_token", afterLogin.optString("bearer_token"))
        assertEquals("device-test@example.com", afterLogin.optString("account_email"))

        prefs.clearSessionAndPersist()
        val afterLogout = prefs.readDurableSnapshot()
        assertEquals("", afterLogout.optString("bearer_token", ""))
        assertEquals("", afterLogout.optString("access_key", ""))
        assertEquals("", afterLogout.optString("account_email", ""))
    }
}
