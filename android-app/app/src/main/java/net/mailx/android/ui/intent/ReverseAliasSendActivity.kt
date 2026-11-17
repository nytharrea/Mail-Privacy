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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import net.mailx.android.ui.theme.MailxAddyTheme
import org.json.JSONArray
import org.json.JSONObject

/**
 * `anonaddy/addy-android` içindeki `IntentSendMailRecipientBottomDialogFragment.kt` ve
 * `IntentContextMenuAliasActivity.kt` sınıflarından uyarlanmıştır.
 */
class ReverseAliasSendActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialTarget = extractTargetEmail(intent)
        val prefs = EncryptedPrefsManager(this)
        val cachedAliases = loadCachedAliasNames(prefs.cachedAliasesJson)
        val defaultAlias = cachedAliases.firstOrNull() ?: "ornek.alias@${prefs.defaultDomain}"

        setContent {
            val systemDark = isSystemInDarkTheme()
            val isDark = when (prefs.darkModeMode) {
                1 -> true
                0 -> false
                else -> systemDark
            }

            MailxAddyTheme(
                darkTheme = isDark,
                dynamicColor = prefs.dynamicColorEnabled,
                amoledDark = prefs.amoledDarkEnabled
            ) {
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

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Text(
                            text = "Takma Addan İleti Gönder",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp, bottom = 14.dp)
                        )

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Gerçek e-posta adresinizi gizleyerek takma adınız üzerinden ileti göndermek için alıcı adresini girin.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
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
                                label = { Text("Gönderici Takma Adınız") },
                                leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = targetEmail,
                                onValueChange = {
                                    targetEmail = it
                                    recompute(aliasEmail, it)
                                },
                                label = { Text("Alıcı E-posta Adresi") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
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
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    ),
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
                                    shape = CircleShape,
                                    onClick = {
                                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText("Mailx Send Address", generatedSendAddress)
                                        )
                                        Toast.makeText(
                                            this@ReverseAliasSendActivity,
                                            "Yönlendirme adresi kopyalandı",
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
                                    shape = CircleShape,
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
                                    Text("İleti Gönder")
                                }
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
