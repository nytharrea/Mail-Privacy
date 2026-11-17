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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.MailxAddyTheme
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
            MailxAddyTheme {
                var aliasEmail by remember { mutableStateOf(defaultAlias) }
                var targetEmail by remember { mutableStateOf(initialTarget) }
                var generatedSendAddress by remember { mutableStateOf("") }
                var errorText by remember { mutableStateOf<String?>(null) }

                fun recompute(aliasStr: String, targetStr: String) {
                    if (targetStr.isBlank()) {
                        generatedSendAddress = ""
                        errorText = null
                        return
                    }
                    val res = JSONObject(
                        MailxRustBridge.nativeGenerateReverseAlias(aliasStr, targetStr)
                    )
                    if (res.optBoolean("ok", false)) {
                        generatedSendAddress = res.optString("data", "")
                        errorText = null
                    } else {
                        generatedSendAddress = ""
                        errorText = res.optString("error", "Geçersiz adres")
                    }
                }

                LaunchedEffect(Unit) {
                    if (initialTarget.isNotBlank()) {
                        recompute(aliasEmail, initialTarget)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Mailx Ters-Alias ile Gönder",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Gerçek e-posta adresinizi gizleyerek takma adınız üzerinden ileti göndermek için hedef adresi girin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (cachedAliases.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                cachedAliases.take(10).forEach { candidate ->
                                    FilterChip(
                                        selected = aliasEmail == candidate,
                                        onClick = {
                                            aliasEmail = candidate
                                            recompute(candidate, targetEmail)
                                        },
                                        label = { Text(candidate) }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = aliasEmail,
                            onValueChange = {
                                aliasEmail = it
                                recompute(it, targetEmail)
                            },
                            label = { Text("Gönderici Mailx Alias'ınız") },
                            leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = targetEmail,
                            onValueChange = {
                                targetEmail = it
                                recompute(aliasEmail, it)
                            },
                            label = { Text("Alıcı E-posta Adresi (To)") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        errorText?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        if (generatedSendAddress.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Üretilen Yönlendirme Adresi:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = generatedSendAddress,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text("Kapat")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            FilledTonalButton(
                                enabled = generatedSendAddress.isNotEmpty(),
                                onClick = {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Mailx Send Address", generatedSendAddress)
                                    )
                                    Toast.makeText(
                                        this@ReverseAliasSendActivity,
                                        "Kopyalandı: $generatedSendAddress",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kopyala")
                            }
                            Button(
                                enabled = generatedSendAddress.isNotEmpty(),
                                onClick = {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("Mailx Send Address", generatedSendAddress)
                                    )
                                    val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$generatedSendAddress"))
                                    startActivity(Intent.createChooser(mailIntent, "E-posta İstemcisi Seçin"))
                                    finish()
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("E-posta Aç")
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
