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
import net.mailx.android.ui.theme.MailxTheme
import org.json.JSONArray
import org.json.JSONObject

class ReverseAliasSendActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialTarget = extractTargetEmail(intent)
        val prefs = EncryptedPrefsManager(this)
        val cachedAliases = loadCachedAliasNames(prefs.cachedAliasesJson)
        val defaultAlias = cachedAliases.firstOrNull() ?: "example.alias@${prefs.defaultDomain}"

        setContent {
            val systemDark = isSystemInDarkTheme()
            val isDark = when (prefs.darkModeMode) {
                1 -> true
                0 -> false
                else -> systemDark
            }

            MailxTheme(
                darkTheme = isDark,
                dynamicColor = prefs.dynamicColorEnabled,
                amoledDark = prefs.amoledDarkEnabled
            ) {
                ReverseAliasDialogCard(
                    initialAlias = defaultAlias,
                    availableAliases = cachedAliases,
                    initialTargetEmail = initialTarget,
                    onCopyAndClose = { generated ->
                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Mailx Reverse Alias", generated))
                        Toast.makeText(
                            this,
                            "Reverse-alias copied: $generated",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    },
                    onOpenMailClient = { generated ->
                        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Mailx Reverse Alias", generated))
                        runCatching {
                            val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$generated"))
                            startActivity(Intent.createChooser(mailIntent, "Send Email"))
                        }
                        finish()
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun extractTargetEmail(intent: Intent?): String {
        if (intent == null) return ""
        if (intent.action == Intent.ACTION_SENDTO) {
            val schemePart = intent.data?.schemeSpecificPart.orEmpty()
            return schemePart.substringBefore("?").trim()
        }
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty().trim()
            if (selected.contains("@")) return selected
        }
        return ""
    }

    private fun loadCachedAliasNames(rawJson: String): List<String> {
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val enabled = obj.optBoolean("enabled", true)
                    val wildcard = obj.optBoolean("wildcard", false)
                    val name = obj.optString("name")
                    if (enabled && !wildcard && name.isNotBlank()) {
                        add(name)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}

@Composable
private fun ReverseAliasDialogCard(
    initialAlias: String,
    availableAliases: List<String>,
    initialTargetEmail: String,
    onCopyAndClose: (String) -> Unit,
    onOpenMailClient: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var senderAlias by remember { mutableStateOf(initialAlias) }
    var targetEmail by remember { mutableStateOf(initialTargetEmail) }
    var generatedAddress by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun recompute(aliasStr: String, targetStr: String) {
        if (targetStr.isBlank()) {
            generatedAddress = ""
            errorMessage = null
            return
        }
        val res = JSONObject(MailxRustBridge.nativeGenerateReverseAlias(aliasStr, targetStr))
        if (res.optBoolean("ok", false)) {
            generatedAddress = res.optString("data")
            errorMessage = null
        } else {
            generatedAddress = ""
            errorMessage = res.optString("error")
        }
    }

    LaunchedEffect(Unit) {
        if (initialTargetEmail.isNotBlank()) {
            recompute(senderAlias, initialTargetEmail)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Send via Mailx Alias",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Generate a relay address so your recipient only sees your alias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                if (availableAliases.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableAliases.take(10).forEach { candidate ->
                            FilterChip(
                                selected = senderAlias == candidate,
                                onClick = {
                                    senderAlias = candidate
                                    recompute(candidate, targetEmail)
                                },
                                label = { Text(candidate, maxLines = 1, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = senderAlias,
                    onValueChange = {
                        senderAlias = it
                        recompute(it, targetEmail)
                    },
                    label = { Text("Sender Alias") },
                    leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = targetEmail,
                    onValueChange = {
                        targetEmail = it
                        recompute(senderAlias, it)
                    },
                    label = { Text("Recipient Email") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (generatedAddress.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = generatedAddress,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        enabled = generatedAddress.isNotEmpty(),
                        onClick = { onCopyAndClose(generatedAddress) },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }

                    Button(
                        enabled = generatedAddress.isNotEmpty(),
                        onClick = { onOpenMailClient(generatedAddress) },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compose")
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Close")
                }
            }
        }
    }
}
