package net.mailx.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import org.json.JSONObject

/**
 * `anonaddy/addy-android` içindeki `QuickAliasTileService.kt` sınıfından Mailx'e uyarlanmıştır.
 * Bildirim panelindeki Hızlı Ayarlar (Quick Settings) kutucuğuna dokunulduğunda Rust çekirdeği
 * üzerinden anında yeni bir Mailx takma adı oluşturur ve panoya kopyalar.
 */
class QuickAliasTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        val prefs = EncryptedPrefsManager(applicationContext)
        qsTile?.apply {
            state = if (prefs.isConfigured()) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            subtitle = if (prefs.isConfigured()) prefs.defaultDomain else "Giriş yapılmadı"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val prefs = EncryptedPrefsManager(applicationContext)
        if (!prefs.isConfigured()) {
            Toast.makeText(this, "Önce Mailx uygulamasına Access Key ile giriş yapın", Toast.LENGTH_LONG).show()
            return
        }

        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = "Oluşturuluyor..."
            updateTile()
        }

        serviceScope.launch {
            val reqJson = JSONObject().apply {
                put("description", "Quick Tile (${System.currentTimeMillis() / 1000})")
                put("enabled", true)
                put("recipients", prefs.defaultRecipient)
                put("from_name", "")
                put("format", prefs.defaultFormat)
                put("domain", prefs.defaultDomain)
            }.toString()

            val response = MailxRustBridge.createAlias(
                baseUrl = prefs.baseUrl,
                accessKey = prefs.accessKey,
                bearerToken = prefs.bearerToken,
                requestJson = reqJson
            )
            prefs.syncRefreshedTokenIfPresent(response)

            if (response.optBoolean("ok", false)) {
                val createdAlias = response.optJSONObject("data")
                    ?.optJSONObject("alias")
                    ?.optString("name", "") ?: ""

                if (createdAlias.isNotEmpty()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Mailx Alias", createdAlias))
                    Toast.makeText(
                        applicationContext,
                        "Kopyalandı: $createdAlias",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                val err = response.optString("error", "Alias oluşturulamadı")
                Toast.makeText(applicationContext, err, Toast.LENGTH_LONG).show()
            }

            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                subtitle = prefs.defaultDomain
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
