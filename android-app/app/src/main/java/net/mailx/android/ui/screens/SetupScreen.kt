package net.mailx.android.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.mailx.android.ui.model.AuthUiState

@Composable
fun SetupScreen(
    authState: AuthUiState,
    onBaseUrlChange: (String) -> Unit,
    onAccessKeyChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    val context = LocalContext.current
    var showAccessKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Mailx Privacy",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Rust Çekirdek • 16 KB Uyumlu • Gizlilik Odaklı Alias Yönetimi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = authState.baseUrl.contains("api.mailx.net"),
                        onClick = { onBaseUrlChange("https://api.mailx.net") },
                        label = { Text("Resmi Sunucu") }
                    )
                    FilterChip(
                        selected = !authState.baseUrl.contains("api.mailx.net"),
                        onClick = {
                            if (authState.baseUrl.contains("api.mailx.net")) {
                                onBaseUrlChange("https://")
                            }
                        },
                        label = { Text("Özel (Self-Host)") }
                    )
                }

                OutlinedTextField(
                    value = authState.baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("API Sunucu Adresi") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = authState.accessKeyInput,
                    onValueChange = onAccessKeyChange,
                    label = { Text("Access Key (85 karakter)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                if (clipText.isNotEmpty()) onAccessKeyChange(clipText)
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Yapıştır")
                            }
                            IconButton(onClick = { showAccessKey = !showAccessKey }) {
                                Icon(
                                    imageVector = if (showAccessKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Göster/Gizle"
                                )
                            }
                        }
                    },
                    supportingText = {
                        Text("${authState.accessKeyInput.trim().length} / 85 karakter")
                    },
                    visualTransformation = if (showAccessKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                authState.errorMessage?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = onConnect,
                    enabled = !authState.isLoading && authState.accessKeyInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (authState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Doğrulanıyor...")
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bağlan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
