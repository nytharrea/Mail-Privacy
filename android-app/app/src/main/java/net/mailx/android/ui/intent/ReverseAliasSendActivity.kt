package net.mailx.android.ui.intent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import org.json.JSONArray
import org.json.JSONObject

/**
 * `anonaddy/addy-android` içindeki `IntentSendMailRecipientBottomDialogFragment.kt` ve
 * `IntentContextMenuAliasActivity.kt` sınıflarından uyarlanmıştır.
 * Bir `mailto:` bağlantısına tıklandığında veya başka bir uygulamada e-posta adresi seçilip
 * "Mailx ile Gönder" dendiğinde Rust çekirdeğindeki `generate_reverse_alias` fonksiyonunu
 * çalıştırarak `alias+hedef=domain.com@mailx.net` adresini üretir.
 */
class ReverseAliasSendActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialTarget = extractTargetEmail(intent)
        val prefs = EncryptedPrefsManager(this)
        val cachedAliases = loadCachedAliasNames(prefs.cachedAliasesJson)
        val defaultAlias = cachedAliases.firstOrNull() ?: "ornek.alias@${prefs.defaultDomain}"

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var aliasEmail by remember { mutableStateOf(defaultAlias) }
                var targetEmail by remember { mutableStateOf(initialTarget) }
                var generatedSendAddress by remember { mutableStateOf("") }
                var errorText by remember { mutableStateOf<String?>(null) }

                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Mailx Ters-Alias ile Gönder", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Gerçek e-posta adresinizi gizleyerek alias üzerinden e-posta göndermek için hedef adresi girin.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (cachedAliases.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                cachedAliases.take(8).forEach { candidate ->
                                    FilterChip(
                                        selected = aliasEmail == candidate,
                                        onClick = { aliasEmail = candidate },
                                        label = { Text(candidate) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = aliasEmail,
                            onValueChange = { aliasEmail = it },
                            label = { Text("Gönderici Mailx Alias'ınız") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = targetEmail,
                            onValueChange = { targetEmail = it },
                            label = { Text("Alıcı E-posta Adresi (To)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        errorText?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        if (generatedSendAddress.isNotEmpty()) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Üretilen Yönlendirme Adresi:", style = MaterialTheme.typography.labelMedium)
                                    Text(generatedSendAddress, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text("İptal")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                val res = JSONObject(
                                    MailxRustBridge.nativeGenerateReverseAlias(aliasEmail, targetEmail)
                                )
                                if (res.optBoolean("ok", false)) {
                                    val addr = res.optString("data", "")
                                    generatedSendAddress = addr
                                    errorText = null
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Mailx Send Address", addr))
                                    Toast.makeText(this@ReverseAliasSendActivity, "Kopyalandı: $addr", Toast.LENGTH_SHORT).show()

                                    val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$addr"))
                                    startActivity(Intent.createChooser(mailIntent, "E-posta İstemcisi Seçin"))
                                } else {
                                    errorText = res.optString("error", "Geçersiz adres")
                                }
                            }) {
                                Text("Üret & E-posta Aç")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadCachedAliasNames(rawJson: String): List<String> {
        if (rawJson.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optBoolean("enabled", true) && !obj.optBoolean("wildcard", false)) {
                        val name = obj.optString("name", "")
                        if (name.isNotEmpty()) add(name)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun extractTargetEmail(intent: Intent?): String {
        if (intent == null) return ""
        if (intent.action == Intent.ACTION_SENDTO) {
            return intent.data?.schemeSpecificPart?.substringBefore("?") ?: ""
        }
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            return intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim() ?: ""
        }
        return ""
    }
}
