package net.mailx.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import org.json.JSONArray
import org.json.JSONObject

data class UiAlias(
    val id: String,
    val name: String,
    val description: String,
    val recipients: String,
    val enabled: Boolean,
    val pinned: Boolean,
    val wildcard: Boolean,
    val forwards: Int,
    val blocks: Int,
    val replies: Int,
    val sends: Int,
    val rawJson: JSONObject
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = EncryptedPrefsManager(this)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    val ctx = LocalContext.current
                    if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
                }
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MailxAppScreen(prefs = prefs)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailxAppScreen(prefs: EncryptedPrefsManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoggedIn by remember { mutableStateOf(prefs.isConfigured()) }
    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var accessKeyInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var aliases by remember { mutableStateOf<List<UiAlias>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("active_inactive") }
    var showCreateSheet by remember { mutableStateOf(false) }
    var sendModalAlias by remember { mutableStateOf<UiAlias?>(null) }

    fun parseAliasesArray(arr: JSONArray?): List<UiAlias> {
        if (arr == null) return emptyList()
        val list = mutableListOf<UiAlias>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val stats = obj.optJSONObject("stats")
            list.add(
                UiAlias(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    recipients = obj.optString("recipients"),
                    enabled = obj.optBoolean("enabled", true),
                    pinned = obj.optBoolean("pinned", false),
                    wildcard = obj.optBoolean("wildcard", false),
                    forwards = stats?.optInt("forwards", 0) ?: 0,
                    blocks = stats?.optInt("blocks", 0) ?: 0,
                    replies = stats?.optInt("replies", 0) ?: 0,
                    sends = stats?.optInt("sends", 0) ?: 0,
                    rawJson = obj
                )
            )
        }
        return list
    }

    fun refreshAliases() {
        scope.launch {
            isLoading = true
            // Çevrimdışı önbellekten hızlı yükle
            if (aliases.isEmpty() && prefs.cachedAliasesJson.isNotEmpty()) {
                runCatching {
                    aliases = parseAliasesArray(JSONArray(prefs.cachedAliasesJson))
                }
            }
            val res = MailxRustBridge.fetchAliases(
                baseUrl = prefs.baseUrl,
                accessKey = prefs.accessKey,
                bearerToken = prefs.bearerToken,
                search = searchQuery,
                status = statusFilter
            )
            if (res.optBoolean("ok", false)) {
                val arr = res.optJSONObject("data")?.optJSONArray("aliases")
                aliases = parseAliasesArray(arr)
                if (arr != null && searchQuery.isEmpty()) {
                    prefs.cachedAliasesJson = arr.toString()
                }
            } else {
                Toast.makeText(
                    context,
                    res.optString("error", "Alias listesi alınamadı"),
                    Toast.LENGTH_SHORT
                ).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(isLoggedIn, statusFilter) {
        if (isLoggedIn) {
            refreshAliases()
        }
    }

    if (!isLoggedIn) {
        // GİRİŞ EKRANI (Access Key + Self-Hosted Sunucu URL)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AlternateEmail,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mailx Android",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Rust Core • 16 KB • Gizlilik Odaklı Alias Yönetimi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Sunucu Adresi (Self-Host veya Resmi)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = accessKeyInput,
                onValueChange = { accessKeyInput = it },
                label = { Text("Access Key (85 karakter)") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            authError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    val check = JSONObject(MailxRustBridge.nativeValidateAccessKey(accessKeyInput))
                    if (!check.optBoolean("ok", false)) {
                        authError = check.optString("error")
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        authError = null
                        val authRes = MailxRustBridge.authenticate(baseUrl, accessKeyInput)
                        isLoading = false
                        if (authRes.optBoolean("ok", false)) {
                            val data = authRes.optJSONObject("data")!!
                            prefs.baseUrl = baseUrl
                            prefs.accessKey = accessKeyInput
                            prefs.bearerToken = data.optString("token")
                            prefs.defaultDomain = data.optString("domain", "mailx.net")
                            prefs.defaultRecipient = data.optString("recipient", "")
                            prefs.defaultFormat = data.optString("alias_format", "words")
                            isLoggedIn = true
                        } else {
                            authError = authRes.optString("error", "Kimlik doğrulama başarısız")
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoading) "Doğrulanıyor..." else "Access Key ile Bağlan")
            }
        }
        return
    }

    // ANA ALIAS LİSTESİ EKRANI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mailx Takma Adlar") },
                actions = {
                    IconButton(onClick = { refreshAliases() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                    }
                    IconButton(onClick = {
                        prefs.clearSession()
                        aliases = emptyList()
                        isLoggedIn = false
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Çıkış Yap")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Yeni Alias") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    refreshAliases()
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Alias veya açıklama ara...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "active_inactive" to "Tümü",
                    "active" to "Aktif",
                    "inactive" to "Pasif",
                    "deleted" to "Silinen (90g)"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = statusFilter == key,
                        onClick = { statusFilter = key },
                        label = { Text(label) }
                    )
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(aliases, key = { it.id }) { alias ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = alias.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = alias.enabled,
                                    onCheckedChange = { newState ->
                                        scope.launch {
                                            val req = JSONObject().apply {
                                                put("description", alias.description)
                                                put("enabled", newState)
                                                put("recipients", alias.recipients)
                                                put("from_name", alias.rawJson.optString("from_name", ""))
                                                put("format", if (alias.wildcard) "wildcard" else "words")
                                                put("domain", alias.name.substringAfter("@"))
                                            }.toString()
                                            MailxRustBridge.updateAlias(
                                                prefs.baseUrl,
                                                prefs.accessKey,
                                                prefs.bearerToken,
                                                alias.id,
                                                req
                                            )
                                            refreshAliases()
                                        }
                                    }
                                )
                            }

                            if (alias.description.isNotEmpty()) {
                                Text(
                                    text = alias.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "İletilen: ${alias.forwards} • Engellenen: ${alias.blocks} • Yanıt: ${alias.replies} • Gönderim: ${alias.sends}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssistChip(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Mailx Alias", alias.name))
                                        Toast.makeText(context, "Kopyalandı: ${alias.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    label = { Text("Kopyala") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )

                                AssistChip(
                                    onClick = { sendModalAlias = alias },
                                    label = { Text("Gönder") },
                                    leadingIcon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )

                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            MailxRustBridge.pinAlias(
                                                prefs.baseUrl,
                                                prefs.accessKey,
                                                prefs.bearerToken,
                                                alias.id,
                                                !alias.pinned
                                            )
                                            refreshAliases()
                                        }
                                    },
                                    label = { Text(if (alias.pinned) "Sabitlendi" else "Sabitle") },
                                    leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )

                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            MailxRustBridge.deleteAlias(
                                                prefs.baseUrl,
                                                prefs.accessKey,
                                                prefs.bearerToken,
                                                alias.id
                                            )
                                            refreshAliases()
                                        }
                                    },
                                    label = { Text("Sil") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        var description by remember { mutableStateOf("") }
        var format by remember { mutableStateOf(prefs.defaultFormat) }
        AlertDialog(
            onDismissRequest = { showCreateSheet = false },
            title = { Text("Yeni Mailx Alias Oluştur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Açıklama (Örn: GitHub, Kargo)") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("words" to "Kelime", "random" to "Rastgele", "uuid" to "UUID").forEach { (fmt, lbl) ->
                            FilterChip(
                                selected = format == fmt,
                                onClick = { format = fmt },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val req = JSONObject().apply {
                            put("description", description)
                            put("enabled", true)
                            put("recipients", prefs.defaultRecipient)
                            put("from_name", "")
                            put("format", format)
                            put("domain", prefs.defaultDomain)
                        }.toString()
                        val res = MailxRustBridge.createAlias(
                            prefs.baseUrl,
                            prefs.accessKey,
                            prefs.bearerToken,
                            req
                        )
                        if (res.optBoolean("ok", false)) {
                            val newName = res.optJSONObject("data")?.optJSONObject("alias")?.optString("name", "") ?: ""
                            if (newName.isNotEmpty()) {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("Mailx Alias", newName))
                                Toast.makeText(context, "Oluşturuldu ve kopyalandı: $newName", Toast.LENGTH_LONG).show()
                            }
                            showCreateSheet = false
                            refreshAliases()
                        } else {
                            Toast.makeText(context, res.optString("error"), Toast.LENGTH_LONG).show()
                        }
                    }
                }) {
                    Text("Oluştur ve Kopyala")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSheet = false }) {
                    Text("İptal")
                }
            }
        )
    }

    sendModalAlias?.let { targetAlias ->
        var targetEmail by remember { mutableStateOf("") }
        var generated by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { sendModalAlias = null },
            title = { Text("Alias Üzerinden Gönder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gönderici: ${targetAlias.name}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = targetEmail,
                        onValueChange = {
                            targetEmail = it
                            val r = JSONObject(MailxRustBridge.nativeGenerateReverseAlias(targetAlias.name, it))
                            generated = if (r.optBoolean("ok", false)) r.optString("data") else ""
                        },
                        label = { Text("Alıcı E-posta (To)") },
                        singleLine = true
                    )
                    if (generated.isNotEmpty()) {
                        Text("Bu adrese e-posta gönderin:\n$generated", fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = generated.isNotEmpty(),
                    onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Mailx Send Address", generated))
                        Toast.makeText(context, "Kopyalandı: $generated", Toast.LENGTH_SHORT).show()
                        sendModalAlias = null
                    }
                ) {
                    Text("Adresi Kopyala")
                }
            },
            dismissButton = {
                TextButton(onClick = { sendModalAlias = null }) {
                    Text("Kapat")
                }
            }
        )
    }
}
