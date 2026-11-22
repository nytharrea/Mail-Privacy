package net.mailx.android.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.core.SecurePrefsManager
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class QuickAliasTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val isCreating = AtomicBoolean(false)

    private data class TileSessionSnapshot(
        val baseUrl: String,
        val accessKey: String,
        val bearerToken: String,
        val defaultDomain: String,
        val defaultRecipient: String,
        val defaultFormat: String
    ) {
        val isConfigured: Boolean
            get() = bearerToken.isNotEmpty()
    }

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            val snapshot = try {
                loadDurableSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (!isCreating.get()) {
                applyIdleTileState(snapshot)
            }
        }
    }

    override fun onClick() {
        super.onClick()
        if (!isCreating.compareAndSet(false, true)) return

        serviceScope.launch {
            var latestSnapshot: TileSessionSnapshot? = null
            try {
                val (prefs, snapshot) = withContext(Dispatchers.IO) {
                    val manager = SecurePrefsManager(applicationContext)
                    manager to readSessionSnapshot(manager)
                }
                latestSnapshot = snapshot

                if (!snapshot.isConfigured) {
                    applyIdleTileState(snapshot)
                    Toast.makeText(
                        applicationContext,
                        "Please sign in to the Mailx app first",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                updateTileState(Tile.STATE_ACTIVE, "Creating...")

                val reqJson = JSONObject().apply {
                    put("description", "Quick Tile (${System.currentTimeMillis() / 1000})")
                    put("enabled", true)
                    put("recipients", snapshot.defaultRecipient)
                    put("from_name", "")
                    put("format", snapshot.defaultFormat)
                    put("domain", snapshot.defaultDomain)
                }.toString()

                val response = MailxRustBridge.createAlias(
                    baseUrl = snapshot.baseUrl,
                    accessKey = snapshot.accessKey,
                    bearerToken = snapshot.bearerToken,
                    requestJson = reqJson
                )

                val refreshedToken = response.optString("refreshed_token", "").trim()
                if (refreshedToken.isNotEmpty() && refreshedToken != snapshot.bearerToken) {
                    withContext(Dispatchers.IO) {
                        prefs.writeValuesAndPersist(mapOf("bearer_token" to refreshedToken))
                    }
                    latestSnapshot = snapshot.copy(bearerToken = refreshedToken)
                }

                if (response.optBoolean("ok", false)) {
                    val createdAlias = response.optJSONObject("data")
                        ?.optJSONObject("alias")
                        ?.optString("name", "")
                        .orEmpty()

                    if (createdAlias.isNotEmpty()) {
                        val clipboard =
                            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Mailx Alias", createdAlias))
                        Toast.makeText(
                            applicationContext,
                            "Copied: $createdAlias",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    val err = response.optString("error", "Failed to create alias")
                    Toast.makeText(applicationContext, err, Toast.LENGTH_LONG).show()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                Toast.makeText(
                    applicationContext,
                    "Could not create alias. Check Mailx storage and connection.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                isCreating.set(false)
                applyIdleTileState(latestSnapshot)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun loadDurableSnapshot(): TileSessionSnapshot = withContext(Dispatchers.IO) {
        val manager = SecurePrefsManager(applicationContext)
        readSessionSnapshot(manager)
    }

    private suspend fun readSessionSnapshot(manager: SecurePrefsManager): TileSessionSnapshot {
        val stored = manager.readDurableSnapshot()
        return TileSessionSnapshot(
            baseUrl = normalizeApiBaseUrl(stored.optString("base_url", "")),
            accessKey = stored.optString("access_key", "").trim(),
            bearerToken = stored.optString("bearer_token", "").trim(),
            defaultDomain = stored.optString("default_domain", "").trim().ifEmpty { "mailx.net" },
            defaultRecipient = stored.optString("default_recipient", "").trim(),
            defaultFormat = stored.optString("default_format", "").trim().ifEmpty { "words" }
        )
    }

    private fun normalizeApiBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when (trimmed.lowercase()) {
            "", "https://mailx.net", "https://www.mailx.net", "https://app.mailx.net" ->
                SecurePrefsManager.DEFAULT_API_BASE_URL
            else -> trimmed
        }
    }

    private fun applyIdleTileState(snapshot: TileSessionSnapshot?) {
        if (snapshot != null && snapshot.isConfigured) {
            updateTileState(Tile.STATE_INACTIVE, snapshot.defaultDomain)
        } else {
            updateTileState(Tile.STATE_UNAVAILABLE, "Not signed in")
        }
    }

    private fun updateTileState(tileState: Int, tileSubtitle: String) {
        qsTile?.apply {
            state = tileState
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = tileSubtitle
            }
            updateTile()
        }
    }
}
