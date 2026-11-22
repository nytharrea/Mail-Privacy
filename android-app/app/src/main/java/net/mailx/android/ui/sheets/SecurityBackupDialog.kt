package net.mailx.android.ui.sheets

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mailx.android.core.SecurePrefsManager
import net.mailx.android.security.EncryptedBackupCodec
import net.mailx.android.security.MailxBackupRepository
import net.mailx.android.ui.MainActivity

/** SAF-based encrypted backup/restore; does not ask for broad storage permission. */
@Composable
fun SecurityBackupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var includeSession by remember { mutableStateOf(false) }
    var restoreSession by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var confirmRestore by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    fun runBackup(uri: Uri, restore: Boolean) {
        if (busy) return
        val secret = password.toCharArray()
        password = ""
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val prefs = SecurePrefsManager(context.applicationContext)
                    if (restore) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use {
                            // Reject excessive input before decoding, even from untrusted providers.
                            val out = java.io.ByteArrayOutputStream()
                            val buf = ByteArray(8192)
                            while (true) {
                                val count = it.read(buf)
                                if (count < 0) break
                                require(out.size() + count <= 16 * 1024 * 1024) { "Backup exceeds 16 MiB" }
                                out.write(buf, 0, count)
                            }
                            out.toByteArray()
                        } ?: error("Cannot read selected file")
                        try {
                            val payload = EncryptedBackupCodec.decrypt(bytes, secret)
                            MailxBackupRepository.restore(prefs, payload, restoreSession)
                        } finally {
                            bytes.fill(0)
                        }
                    } else {
                        val payload = MailxBackupRepository.export(prefs, includeSession)
                        val bytes = EncryptedBackupCodec.encrypt(payload, secret)
                        try {
                            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                                ?: error("Cannot write selected file")
                        } finally {
                            bytes.fill(0)
                        }
                    }
                }
                message = if (restore) "Backup restored. Reloading Mailx…" else "Encrypted backup saved"
                if (restore) {
                    (context as? Activity)?.recreate()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Backup operation failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                secret.fill('\u0000')
                busy = false
            }
        }
    }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        (context as? MainActivity)?.setExternalDocumentFlow(false)
        if (uri != null) runBackup(uri, restore = false)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        (context as? MainActivity)?.setExternalDocumentFlow(false)
        if (uri != null) {
            pendingRestoreUri = uri
            confirmRestore = true
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Encrypted backup & restore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Settings and watched aliases are backed up locally. Credentials are excluded unless explicitly selected.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Backup password (10+ characters)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row {
                    Checkbox(checked = includeSession, onCheckedChange = { includeSession = it })
                    Text("Include sign-in credentials in new backup")
                }
                Row {
                    Checkbox(checked = restoreSession, onCheckedChange = { restoreSession = it })
                    Text("Restore sign-in credentials if present")
                }
                if (message.isNotBlank()) Text(message)
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    enabled = !busy && password.length >= 10,
                    onClick = {
                        (context as? MainActivity)?.setExternalDocumentFlow(true)
                        create.launch("mailx-backup.mailxbkp")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export encrypted backup") }
                Button(
                    enabled = !busy && password.isNotBlank(),
                    onClick = {
                        (context as? MainActivity)?.setExternalDocumentFlow(true)
                        open.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Select backup to restore") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Close") } }
    )
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore settings?") },
            text = { Text("This will overwrite local settings and watched aliases. Credentials will only be replaced if selected. App lock and search indexing stay off until re-enabled.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    pendingRestoreUri?.let { runBackup(it, restore = true) }
                    pendingRestoreUri = null
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } }
        )
    }
}
