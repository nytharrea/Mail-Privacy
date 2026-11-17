package net.mailx.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.mailx.android.core.EncryptedPrefsManager
import net.mailx.android.core.MailxRustBridge
import org.json.JSONArray
import org.json.JSONObject

// ============================================================================
// ADDY.IO İLHAMLI RENK PALETİ VE TEMA SİSTEMİ (`anonaddy_shared/colors.xml`)
// ============================================================================

object AddyPalette {
    // Addy.io imza istatistik ve durum renkleri
    val PortalOrange = Color(0xFFFF9800) // İletilen (Forwarded)
    val PortalBlue = Color(0xFF4289FD)   // Yanıtlanan (Replied)
    val EasternBlue = Color(0xFF16A8B8)  // Gönderilen (Sent)
    val SoftRed = Color(0xFFFD5842)      // Engellenen (Blocked) & Tehlikeli İşlem
    val AddyGreen = Color(0xFF8BC34A)    // Aktif & Doğrulanmış
    val PrimaryNavy = Color(0xFF19216C)  // Addy Statik Ana Renk
    val PrimaryIndigo = Color(0xFF555A92)
    val AccentIndigoLight = Color(0xFFBEC2FF)

    // Koyu tema yüzey hiyerarşisi
    val DarkBackground = Color(0xFF0D0F17)
    val DarkSurface = Color(0xFF151926)
    val DarkSurfaceContainer = Color(0xFF1B2030)
    val DarkSurfaceHigh = Color(0xFF23293D)
    val DarkOutline = Color(0xFF343C57)

    // Açık tema yüzey hiyerarşisi
    val LightBackground = Color(0xFFFBF8FF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceContainer = Color(0xFFF1EEF9)
    val LightSurfaceHigh = Color(0xFFE6E2F2)
    val LightOutline = Color(0xFFC7C5D0)
}

private val AddyDarkColorScheme = darkColorScheme(
    primary = AddyPalette.AccentIndigoLight,
    onPrimary = Color(0xFF19216C),
    primaryContainer = Color(0xFF2E3468),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = AddyPalette.EasternBlue,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF1B3A44),
    onSecondaryContainer = Color(0xFFB6EBFF),
    tertiary = AddyPalette.PortalOrange,
    onTertiary = Color(0xFF3E2200),
    tertiaryContainer = Color(0xFF4D3208),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error = AddyPalette.SoftRed,
    onError = Color(0xFF5C0A00),
    errorContainer = Color(0xFF4F1711),
    onErrorContainer = Color(0xFFFFDAD6),
    background = AddyPalette.DarkBackground,
    onBackground = Color(0xFFEAE7F2),
    surface = AddyPalette.DarkSurface,
    onSurface = Color(0xFFEAE7F2),
    surfaceVariant = AddyPalette.DarkSurfaceContainer,
    onSurfaceVariant = Color(0xFFB8B6C8),
    surfaceContainerLowest = Color(0xFF090B11),
    surfaceContainerLow = AddyPalette.DarkSurface,
    surfaceContainer = AddyPalette.DarkSurfaceContainer,
    surfaceContainerHigh = AddyPalette.DarkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF2B324A),
    outline = AddyPalette.DarkOutline,
    outlineVariant = Color(0xFF252B3F)
)

private val AddyLightColorScheme = lightColorScheme(
    primary = AddyPalette.PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF19216C),
    secondary = Color(0xFF05677E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB6EBFF),
    onSecondaryContainer = Color(0xFF004E60),
    tertiary = Color(0xFFC77200),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF3E2200),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = AddyPalette.LightBackground,
    onBackground = Color(0xFF1B1B21),
    surface = AddyPalette.LightSurface,
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = AddyPalette.LightSurfaceContainer,
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F3FC),
    surfaceContainer = AddyPalette.LightSurfaceContainer,
    surfaceContainerHigh = AddyPalette.LightSurfaceHigh,
    surfaceContainerHighest = Color(0xFFDDD9EA),
    outline = AddyPalette.LightOutline,
    outlineVariant = Color(0xFFE0DDEA)
)

@Composable
fun MailxAddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AddyDarkColorScheme else AddyLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// ============================================================================
// VERİ MODELLERİ VE YARDIMCI FONKSİYONLAR
// ============================================================================

data class UiAlias(
    val id: String,
    val name: String,
    val description: String,
    val recipients: String,
    val fromName: String,
    val enabled: Boolean,
    val pinned: Boolean,
    val wildcard: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String,
    val forwards: Int,
    val blocks: Int,
    val replies: Int,
    val sends: Int,
    val isCustomDomain: Boolean,
    val rawJson: JSONObject
) {
    val localPart: String
        get() = name.substringBefore("@")

    val domainPart: String
        get() = name.substringAfter("@", "mailx.net")

    val totalActivity: Int
        get() = forwards + blocks + replies + sends

    val recipientList: List<String>
        get() = recipients.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

enum class MainNavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Ana Sayfa", Icons.Outlined.Home, Icons.Default.Home),
    ALIASES("Takma Adlar", Icons.Outlined.AlternateEmail, Icons.Default.AlternateEmail),
    SETTINGS("Ayarlar", Icons.Outlined.Settings, Icons.Default.Settings)
}

enum class AliasSortField(val key: String, val label: String) {
    CREATED_AT("created_at", "Oluşturulma Tarihi"),
    UPDATED_AT("updated_at", "Güncellenme Tarihi"),
    NAME("name", "E-posta Adresi (A-Z)"),
    FORWARDS("forwards", "İletilen Sayısı"),
    BLOCKS("blocks", "Engellenen Sayısı"),
    REPLIES("replies", "Yanıtlanan Sayısı"),
    SENDS("sends", "Gönderilen Sayısı")
}

/**
 * `addy-android` içindeki `NatoAlphabet.kt` sınıfından uyarlanan fonetik alfabe yardımcısı.
 */
object NatoPhoneticHelper {
    private val map = mapOf(
        'A' to "Alpha", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta", 'E' to "Echo",
        'F' to "Foxtrot", 'G' to "Golf", 'H' to "Hotel", 'I' to "India", 'J' to "Juliett",
        'K' to "Kilo", 'L' to "Lima", 'M' to "Mike", 'N' to "November", 'O' to "Oscar",
        'P' to "Papa", 'Q' to "Quebec", 'R' to "Romeo", 'S' to "Sierra", 'T' to "Tango",
        'U' to "Uniform", 'V' to "Victor", 'W' to "Whiskey", 'X' to "X-ray", 'Y' to "Yankee",
        'Z' to "Zulu",
        '0' to "Sıfır (Zero)", '1' to "Bir (One)", '2' to "İki (Two)", '3' to "Üç (Three)",
        '4' to "Dört (Four)", '5' to "Beş (Five)", '6' to "Altı (Six)", '7' to "Yedi (Seven)",
        '8' to "Sekiz (Eight)", '9' to "Dokuz (Nine)",
        '.' to "Nokta (Period)", '@' to "At İşareti (@)", '-' to "Tire (Dash)",
        '_' to "Alt Çizgi", '+' to "Artı (Plus)", '*' to "Yıldız (Wildcard)"
    )

    fun toPhoneticPairs(email: String): List<Pair<String, String>> {
        return email.map { ch ->
            val word = map[ch.uppercaseChar()] ?: ch.toString()
            ch.toString() to word
        }
    }
}

fun formatIsoDateShort(raw: String): String {
    val clean = raw.trim()
    if (clean.isEmpty() || clean == "null") return "—"
    // Örn: "2026-04-15T12:34:56Z" -> "15.04.2026 12:34"
    val datePart = clean.substringBefore("T")
    val timePart = clean.substringAfter("T", "").take(5)
    val parts = datePart.split("-")
    return if (parts.size == 3) {
        val formattedDate = "${parts[2]}.${parts[1]}.${parts[0]}"
        if (timePart.isNotEmpty()) "$formattedDate • $timePart" else formattedDate
    } else {
        clean.take(16)
    }
}

fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String? = null) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage ?: "Panoya kopyalandı: $text", Toast.LENGTH_SHORT).show()
}

// ============================================================================
// ANA ETKİNLİK (MAIN ACTIVITY)
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = EncryptedPrefsManager(this)

        setContent {
            MailxAddyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MailxAppScreen(prefs = prefs)
                }
            }
        }
    }
}

// ============================================================================
// ANA UYGULAMA EKRANI VE DURUM YÖNETİMİ
// ============================================================================

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

    // Navigasyon sekmesi
    var currentTab by remember { mutableStateOf(MainNavTab.ALIASES) }

    // Alias listesi ve filtre/sıralama durumları
    var aliases by remember { mutableStateOf<List<UiAlias>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    // Sunucu filtreleri: "active_inactive", "active", "inactive", "deleted"
    // İstemci hızlı filtreleri: "pinned_only", "wildcard_only"
    var selectedFilterChip by remember { mutableStateOf("active_inactive") }
    var sortField by remember { mutableStateOf(AliasSortField.CREATED_AT) }
    var sortDescending by remember { mutableStateOf(true) }
    var pinnedFirst by remember { mutableStateOf(true) }

    // BottomSheet ve Modal durumları
    var showCreateSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedAliasForManage by remember { mutableStateOf<UiAlias?>(null) }
    var sendModalAlias by remember { mutableStateOf<UiAlias?>(null) }
    var wildcardModalAlias by remember { mutableStateOf<UiAlias?>(null) }
    var showStandaloneReverseAliasSheet by remember { mutableStateOf(false) }
    var aliasPendingDelete by remember { mutableStateOf<UiAlias?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // Sunucu varsayılanları (reaktif & bağımsız)
    var availableDomains by remember {
        mutableStateOf(listOf(prefs.defaultDomain.ifEmpty { "mailx.net" }).distinct())
    }
    var availableRecipients by remember {
        mutableStateOf(
            prefs.defaultRecipient.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        )
    }
    var defaultDomain by remember { mutableStateOf(prefs.defaultDomain.ifEmpty { "mailx.net" }) }
    var defaultRecipient by remember { mutableStateOf(prefs.defaultRecipient) }
    var defaultFormat by remember { mutableStateOf(prefs.defaultFormat.ifEmpty { "words" }) }
    var serverDefaultFromName by remember { mutableStateOf("") }
    var serverBlockTrackers by remember { mutableStateOf(false) }
    var serverRemoveHeader by remember { mutableStateOf(false) }

    fun syncLocalDefaultsFromServerJson(defObj: JSONObject?) {
        defaultDomain = prefs.defaultDomain.ifEmpty { "mailx.net" }
        defaultRecipient = prefs.defaultRecipient
        defaultFormat = prefs.defaultFormat.ifEmpty { "words" }

        val domSet = linkedSetOf<String>()
        if (defaultDomain.isNotEmpty()) domSet.add(defaultDomain)
        defObj?.optJSONArray("domains")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.optString(i, "").trim()
                if (d.isNotEmpty()) domSet.add(d)
            }
        }
        defObj?.optJSONArray("custom_domains")?.let { arr ->
            for (i in 0 until arr.length()) {
                val cd = arr.optJSONObject(i)
                val name = cd?.optString("name", "")?.trim().orEmpty()
                val enabled = cd?.optBoolean("enabled", true) ?: true
                if (name.isNotEmpty() && enabled) domSet.add(name)
            }
        }
        if (domSet.isEmpty()) domSet.add("mailx.net")
        availableDomains = domSet.toList()

        val recSet = linkedSetOf<String>()
        defaultRecipient.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { recSet.add(it) }
        defObj?.optJSONArray("recipients")?.let { arr ->
            for (i in 0 until arr.length()) {
                val r = arr.optString(i, "").trim()
                if (r.isNotEmpty()) recSet.add(r)
            }
        }
        availableRecipients = recSet.toList()

        if (defObj != null) {
            if (defObj.has("from_name")) serverDefaultFromName = defObj.optString("from_name", "")
            if (defObj.has("block_trackers")) serverBlockTrackers = defObj.optBoolean("block_trackers", false)
            if (defObj.has("remove_header")) serverRemoveHeader = defObj.optBoolean("remove_header", false)
        }
    }

    fun parseAliasesArray(arr: JSONArray?): List<UiAlias> {
        if (arr == null) return emptyList()
        val list = mutableListOf<UiAlias>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val stats = obj.optJSONObject("stats")
            val deletedRaw = obj.opt("deleted_at")
            val deletedStr = if (deletedRaw == null || deletedRaw == JSONObject.NULL) "" else deletedRaw.toString()
            list.add(
                UiAlias(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    recipients = obj.optString("recipients"),
                    fromName = obj.optString("from_name"),
                    enabled = obj.optBoolean("enabled", true),
                    pinned = obj.optBoolean("pinned", false),
                    wildcard = obj.optBoolean("wildcard", false),
                    createdAt = obj.optString("created_at", ""),
                    updatedAt = obj.optString("updated_at", ""),
                    deletedAt = deletedStr,
                    forwards = stats?.optInt("forwards", 0) ?: 0,
                    blocks = stats?.optInt("blocks", 0) ?: 0,
                    replies = stats?.optInt("replies", 0) ?: 0,
                    sends = stats?.optInt("sends", 0) ?: 0,
                    isCustomDomain = obj.optBoolean("is_custom_domain", false),
                    rawJson = obj
                )
            )
        }
        return list
    }

    suspend fun loadAliasesFromServer() {
        isLoading = true
        if (aliases.isEmpty() && prefs.cachedAliasesJson.isNotEmpty()) {
            runCatching {
                aliases = parseAliasesArray(JSONArray(prefs.cachedAliasesJson))
            }
        }

        val serverStatus = when (selectedFilterChip) {
            "active" -> "active"
            "inactive" -> "inactive"
            "deleted" -> "deleted"
            else -> "active_inactive"
        }
        val wildcardParam = if (selectedFilterChip == "wildcard_only") "true" else ""

        val res = MailxRustBridge.fetchAliases(
            baseUrl = prefs.baseUrl,
            accessKey = prefs.accessKey,
            bearerToken = prefs.bearerToken,
            search = searchQuery,
            status = serverStatus,
            wildcard = wildcardParam
        )
        prefs.syncRefreshedTokenIfPresent(res)
        if (res.optBoolean("ok", false)) {
            val arr = res.optJSONObject("data")?.optJSONArray("aliases")
            val parsed = parseAliasesArray(arr)
            aliases = parsed
            // Açık olan detay sayfası varsa güncel veriyi yansıt
            selectedAliasForManage?.let { current ->
                selectedAliasForManage = parsed.find { it.id == current.id }
            }
            if (arr != null && searchQuery.isEmpty() && serverStatus == "active_inactive" && wildcardParam.isEmpty()) {
                prefs.cachedAliasesJson = arr.toString()
            }
        } else {
            Toast.makeText(
                context,
                res.optString("error", "Alias listesi güncellenemedi"),
                Toast.LENGTH_SHORT
            ).show()
        }
        isLoading = false
    }

    fun refreshAliases() {
        scope.launch {
            loadAliasesFromServer()
        }
    }

    suspend fun syncDefaultsFromServer(showToast: Boolean = false) {
        val defaultsRes = MailxRustBridge.fetchDefaults(
            baseUrl = prefs.baseUrl,
            accessKey = prefs.accessKey,
            bearerToken = prefs.bearerToken
        )
        prefs.syncRefreshedTokenIfPresent(defaultsRes)
        if (defaultsRes.optBoolean("ok", false)) {
            defaultsRes.optJSONObject("data")?.let { defObj ->
                prefs.applyServerDefaults(defObj)
                syncLocalDefaultsFromServerJson(defObj)
                if (showToast) {
                    Toast.makeText(context, "Sunucu varsayılanları senkronize edildi", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (showToast) {
            Toast.makeText(context, defaultsRes.optString("error", "Senkronizasyon başarısız"), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isLoggedIn, selectedFilterChip, searchQuery) {
        if (isLoggedIn) {
            if (searchQuery.isNotEmpty()) {
                delay(280)
            }
            loadAliasesFromServer()
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            syncDefaultsFromServer(showToast = false)
        }
    }

    // İstemci tarafı filtreleme ve sıralama
    val displayedAliases = remember(aliases, selectedFilterChip, sortField, sortDescending, pinnedFirst) {
        val filtered = aliases.filter { alias ->
            when (selectedFilterChip) {
                "pinned_only" -> alias.pinned
                "wildcard_only" -> alias.wildcard
                "active" -> alias.enabled
                "inactive" -> !alias.enabled
                else -> true
            }
        }

        val comparator = Comparator<UiAlias> { a, b ->
            if (pinnedFirst && a.pinned != b.pinned) {
                if (a.pinned) -1 else 1
            } else {
                val rawCmp = when (sortField) {
                    AliasSortField.CREATED_AT -> a.createdAt.compareTo(b.createdAt)
                    AliasSortField.UPDATED_AT -> a.updatedAt.compareTo(b.updatedAt)
                    AliasSortField.NAME -> a.name.compareTo(b.name, ignoreCase = true)
                    AliasSortField.FORWARDS -> a.forwards.compareTo(b.forwards)
                    AliasSortField.BLOCKS -> a.blocks.compareTo(b.blocks)
                    AliasSortField.REPLIES -> a.replies.compareTo(b.replies)
                    AliasSortField.SENDS -> a.sends.compareTo(b.sends)
                }
                if (sortDescending) -rawCmp else rawCmp
            }
        }
        filtered.sortedWith(comparator)
    }

    // ========================================================================
    // 1. GİRİŞ / KURULUM EKRANI (ADDY SETUP İLHAMLI)
    // ========================================================================
    if (!isLoggedIn) {
        AddySetupScreen(
            baseUrl = baseUrl,
            onBaseUrlChange = { baseUrl = it },
            accessKeyInput = accessKeyInput,
            onAccessKeyChange = {
                accessKeyInput = it
                authError = null
            },
            authError = authError,
            isLoading = isLoading,
            onConnect = {
                val check = JSONObject(MailxRustBridge.nativeValidateAccessKey(accessKeyInput))
                if (!check.optBoolean("ok", false)) {
                    authError = check.optString("error")
                    return@AddySetupScreen
                }
                scope.launch {
                    isLoading = true
                    authError = null
                    val authRes = MailxRustBridge.authenticate(baseUrl, accessKeyInput)
                    if (authRes.optBoolean("ok", false)) {
                        val data = authRes.optJSONObject("data") ?: JSONObject()
                        val token = data.optString("token")
                        prefs.baseUrl = baseUrl
                        baseUrl = prefs.baseUrl
                        prefs.accessKey = accessKeyInput
                        prefs.bearerToken = token
                        prefs.applyServerDefaults(data)
                        syncLocalDefaultsFromServerJson(data)

                        syncDefaultsFromServer(showToast = false)
                        isLoading = false
                        isLoggedIn = true
                    } else {
                        isLoading = false
                        authError = authRes.optString("error", "Kimlik doğrulama başarısız")
                    }
                }
            }
        )
        return
    }

    // ========================================================================
    // 2. ANA UYGULAMA İSKELETİ (TOPBAR + 3 SEKMELİ BOTTOM NAV + FAB)
    // ========================================================================
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = when (currentTab) {
                                    MainNavTab.HOME -> "Mailx Genel Bakış"
                                    MainNavTab.ALIASES -> "Takma Adlar"
                                    MainNavTab.SETTINGS -> "Ayarlar & Alan Adları"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = defaultDomain,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (currentTab != MainNavTab.SETTINGS) {
                        IconButton(onClick = { refreshAliases() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                        }
                    }
                    IconButton(onClick = { showCreateSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Yeni Alias")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == MainNavTab.ALIASES && aliases.isNotEmpty()) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Text(aliases.size.toString())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.icon,
                                        contentDescription = tab.label
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab == MainNavTab.ALIASES || currentTab == MainNavTab.HOME) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateSheet = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Yeni Alias", fontWeight = FontWeight.SemiBold) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            when (currentTab) {
                MainNavTab.HOME -> {
                    AddyHomeDashboardTab(
                        aliases = aliases,
                        defaultDomain = defaultDomain,
                        defaultRecipient = defaultRecipient,
                        onNavigateToFilter = { filterKey ->
                            selectedFilterChip = filterKey
                            currentTab = MainNavTab.ALIASES
                        },
                        onSelectAlias = { selectedAliasForManage = it },
                        onOpenReverseAliasTool = { showStandaloneReverseAliasSheet = true },
                        onOpenCreateAlias = { showCreateSheet = true }
                    )
                }

                MainNavTab.ALIASES -> {
                    AddyAliasesTab(
                        aliases = displayedAliases,
                        totalLoadedCount = aliases.size,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        selectedFilterChip = selectedFilterChip,
                        onSelectFilterChip = { selectedFilterChip = it },
                        sortField = sortField,
                        sortDescending = sortDescending,
                        onOpenFilterSheet = { showFilterSheet = true },
                        onSelectAlias = { selectedAliasForManage = it },
                        onQuickCopyOrWildcard = { alias ->
                            if (alias.wildcard) {
                                wildcardModalAlias = alias
                            } else {
                                copyToClipboard(context, "Mailx Alias", alias.name)
                            }
                        },
                        onOpenCreateSheet = { showCreateSheet = true }
                    )
                }

                MainNavTab.SETTINGS -> {
                    AddySettingsTab(
                        prefs = prefs,
                        blockTrackers = serverBlockTrackers,
                        removeHeader = serverRemoveHeader,
                        availableDomains = availableDomains,
                        availableRecipients = availableRecipients,
                        defaultDomain = defaultDomain,
                        defaultRecipient = defaultRecipient,
                        defaultFormat = defaultFormat,
                        onUpdateDefaultDomain = { newDom ->
                            prefs.defaultDomain = newDom
                            defaultDomain = newDom
                            Toast.makeText(context, "Varsayılan alan adı: $newDom", Toast.LENGTH_SHORT).show()
                        },
                        onUpdateDefaultFormat = { newFmt ->
                            prefs.defaultFormat = newFmt
                            defaultFormat = newFmt
                            Toast.makeText(context, "Varsayılan format güncellendi", Toast.LENGTH_SHORT).show()
                        },
                        onSyncDefaults = {
                            scope.launch {
                                isLoading = true
                                syncDefaultsFromServer(showToast = true)
                                isLoading = false
                            }
                        },
                        onOpenReverseAliasTool = { showStandaloneReverseAliasSheet = true },
                        onRequestLogout = { showLogoutConfirm = true }
                    )
                }
            }
        }
    }

    // ========================================================================
    // 3. ADDY TARZI ALIAS DETAY VE YÖNETİM BOTTOM SHEET (`ManageAliasBottomSheet`)
    // ========================================================================
    selectedAliasForManage?.let { activeAlias ->
        ManageAliasBottomSheet(
            alias = activeAlias,
            availableRecipients = availableRecipients,
            onDismiss = { selectedAliasForManage = null },
            onToggleEnabled = { newEnabled ->
                scope.launch {
                    val req = JSONObject().apply {
                        put("description", activeAlias.description)
                        put("enabled", newEnabled)
                        put("recipients", activeAlias.recipients)
                        put("from_name", activeAlias.fromName)
                        put("format", if (activeAlias.wildcard) "wildcard" else "words")
                        put("domain", activeAlias.domainPart)
                    }.toString()
                    val res = MailxRustBridge.updateAlias(
                        prefs.baseUrl,
                        prefs.accessKey,
                        prefs.bearerToken,
                        activeAlias.id,
                        req
                    )
                    prefs.syncRefreshedTokenIfPresent(res)
                    if (res.optBoolean("ok", false)) {
                        refreshAliases()
                    } else {
                        Toast.makeText(context, res.optString("error", "Durum güncellenemedi"), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onTogglePinned = { newPinned ->
                scope.launch {
                    val res = MailxRustBridge.pinAlias(
                        prefs.baseUrl,
                        prefs.accessKey,
                        prefs.bearerToken,
                        activeAlias.id,
                        newPinned
                    )
                    prefs.syncRefreshedTokenIfPresent(res)
                    if (res.optBoolean("ok", false)) {
                        refreshAliases()
                    } else {
                        Toast.makeText(context, res.optString("error", "Sabitleme güncellenemedi"), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onUpdateMetadata = { newDesc, newFromName, newRecipients ->
                scope.launch {
                    val req = JSONObject().apply {
                        put("description", newDesc)
                        put("enabled", activeAlias.enabled)
                        put("recipients", newRecipients)
                        put("from_name", newFromName)
                        put("format", if (activeAlias.wildcard) "wildcard" else "words")
                        put("domain", activeAlias.domainPart)
                    }.toString()
                    val res = MailxRustBridge.updateAlias(
                        prefs.baseUrl,
                        prefs.accessKey,
                        prefs.bearerToken,
                        activeAlias.id,
                        req
                    )
                    prefs.syncRefreshedTokenIfPresent(res)
                    if (res.optBoolean("ok", false)) {
                        Toast.makeText(context, "Alias bilgileri güncellendi", Toast.LENGTH_SHORT).show()
                        refreshAliases()
                    } else {
                        Toast.makeText(context, res.optString("error", "Güncelleme başarısız"), Toast.LENGTH_LONG).show()
                    }
                }
            },
            onOpenSendFromAlias = {
                sendModalAlias = activeAlias
            },
            onOpenWildcardGenerator = {
                wildcardModalAlias = activeAlias
            },
            onRequestDelete = {
                aliasPendingDelete = activeAlias
            }
        )
    }

    // ========================================================================
    // 4. SİLME ONAY DİYALOĞU (YANLIŞLIKLA SİLMEYİ ÖNLER)
    // ========================================================================
    aliasPendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { aliasPendingDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = AddyPalette.SoftRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Takma Ad Silinsin mi?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = target.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Bu takma ad silinenler (90g) bölümüne taşınacak ve gelen e-postaları reddedecektir.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AddyPalette.SoftRed,
                        contentColor = Color.White
                    ),
                    onClick = {
                        val idToDelete = target.id
                        aliasPendingDelete = null
                        selectedAliasForManage = null
                        scope.launch {
                            val res = MailxRustBridge.deleteAlias(
                                prefs.baseUrl,
                                prefs.accessKey,
                                prefs.bearerToken,
                                idToDelete
                            )
                            prefs.syncRefreshedTokenIfPresent(res)
                            if (res.optBoolean("ok", false)) {
                                Toast.makeText(context, "Alias silindi: ${target.name}", Toast.LENGTH_SHORT).show()
                                refreshAliases()
                            } else {
                                Toast.makeText(context, res.optString("error", "Silinemedi"), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Evet, Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { aliasPendingDelete = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // ========================================================================
    // 5. ADDY TARZI YENİ ALIAS OLUŞTURMA BOTTOM SHEET (`bottomsheet_addalias.xml`)
    // ========================================================================
    if (showCreateSheet) {
        AddAliasBottomSheet(
            availableDomains = availableDomains,
            availableRecipients = availableRecipients,
            defaultDomain = defaultDomain,
            defaultRecipient = defaultRecipient,
            defaultFormat = defaultFormat,
            defaultFromName = serverDefaultFromName,
            onDismiss = { showCreateSheet = false },
            onCreateAlias = { reqJsonStr ->
                scope.launch {
                    val res = MailxRustBridge.createAlias(
                        prefs.baseUrl,
                        prefs.accessKey,
                        prefs.bearerToken,
                        reqJsonStr
                    )
                    prefs.syncRefreshedTokenIfPresent(res)
                    if (res.optBoolean("ok", false)) {
                        val newName = res.optJSONObject("data")
                            ?.optJSONObject("alias")
                            ?.optString("name", "") ?: ""
                        if (newName.isNotEmpty()) {
                            copyToClipboard(
                                context,
                                "Mailx Alias",
                                newName,
                                "Oluşturuldu ve kopyalandı: $newName"
                            )
                        }
                        showCreateSheet = false
                        refreshAliases()
                    } else {
                        Toast.makeText(
                            context,
                            res.optString("error", "Alias oluşturulamadı"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }

    // ========================================================================
    // 6. ADDY TARZI FİLTRELEME VE SIRALAMA BOTTOM SHEET (`bottomsheet_filter_options_alias.xml`)
    // ========================================================================
    if (showFilterSheet) {
        FilterOptionsBottomSheet(
            selectedFilterChip = selectedFilterChip,
            sortField = sortField,
            sortDescending = sortDescending,
            pinnedFirst = pinnedFirst,
            onApply = { newFilter, newSort, newDesc, newPinnedFirst ->
                selectedFilterChip = newFilter
                sortField = newSort
                sortDescending = newDesc
                pinnedFirst = newPinnedFirst
                showFilterSheet = false
            },
            onReset = {
                selectedFilterChip = "active_inactive"
                sortField = AliasSortField.CREATED_AT
                sortDescending = true
                pinnedFirst = true
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    // ========================================================================
    // 7. WILDCARD ANINDA ÜRETİM BOTTOM SHEET
    // ========================================================================
    wildcardModalAlias?.let { wcAlias ->
        WildcardGeneratorBottomSheet(
            wildcardAlias = wcAlias,
            onDismiss = { wildcardModalAlias = null }
        )
    }

    // ========================================================================
    // 8. ALIAS ÜZERİNDEN E-POSTA GÖNDER (TERS-ALIAS) BOTTOM SHEET
    // ========================================================================
    sendModalAlias?.let { targetAlias ->
        ReverseAliasSendBottomSheet(
            initialAliasName = targetAlias.name,
            availableAliases = aliases.filter { it.enabled && !it.wildcard }.map { it.name },
            onDismiss = { sendModalAlias = null }
        )
    }

    if (showStandaloneReverseAliasSheet) {
        val activeNonWildcard = aliases.filter { it.enabled && !it.wildcard }.map { it.name }
        val initial = activeNonWildcard.firstOrNull() ?: "ornek.alias@$defaultDomain"
        ReverseAliasSendBottomSheet(
            initialAliasName = initial,
            availableAliases = activeNonWildcard,
            onDismiss = { showStandaloneReverseAliasSheet = false }
        )
    }

    // ========================================================================
    // 9. ÇIKIŞ ONAY DİYALOĞU
    // ========================================================================
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text("Oturumu Kapat") },
            text = {
                Text("Cihazdaki şifreli oturum anahtarı ve çevrimdışı önbellek temizlenecek. Devam etmek istiyor musunuz?")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AddyPalette.SoftRed,
                        contentColor = Color.White
                    ),
                    onClick = {
                        showLogoutConfirm = false
                        val oldUrl = prefs.baseUrl
                        val oldToken = prefs.bearerToken
                        prefs.clearSession()
                        aliases = emptyList()
                        isLoggedIn = false
                        scope.launch {
                            if (oldToken.isNotEmpty()) {
                                MailxRustBridge.logout(oldUrl, oldToken)
                            }
                        }
                    }
                ) {
                    Text("Çıkış Yap")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

// ============================================================================
// BİLEŞEN 1: ADDY İLHAMLI DONUT GRAFİĞİ (`DonutProgressView` Compose Karşılığı)
// ============================================================================

@Composable
fun AddyDonutChart(
    forwards: Int,
    replies: Int,
    sends: Int,
    blocks: Int,
    enabled: Boolean,
    size: Dp = 46.dp,
    strokeWidth: Dp = 6.dp,
    showCenterLabel: Boolean = true,
    centerSubtitle: String? = null
) {
    val total = forwards + replies + sends + blocks
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    val cForward = if (enabled) AddyPalette.PortalOrange else Color(0xFF757575)
    val cReply = if (enabled) AddyPalette.PortalBlue else Color(0xFF616161)
    val cSend = if (enabled) AddyPalette.EasternBlue else Color(0xFF525252)
    val cBlock = if (enabled) AddyPalette.SoftRed else Color(0xFF424242)

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(strokeWidth / 2)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width, this.size.height)

            // Arka plan halkası
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (total == 0) {
                // Addy'deki `emptyDonut = 1` davranışı: istatistik yoksa tam turuncu/gri halka
                drawArc(
                    color = cForward.copy(alpha = if (enabled) 0.75f else 0.4f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset.Zero,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            } else {
                val segments = listOf(
                    forwards to cForward,
                    replies to cReply,
                    sends to cSend,
                    blocks to cBlock
                ).filter { it.first > 0 }

                var startAngle = -90f
                val gapDegrees = if (segments.size > 1) 6f else 0f
                val availableDegrees = 360f - (gapDegrees * segments.size)

                segments.forEach { (count, segColor) ->
                    val sweep = (count.toFloat() / total.toFloat()) * availableDegrees
                    drawArc(
                        color = segColor,
                        startAngle = startAngle,
                        sweepAngle = sweep.coerceAtLeast(4f),
                        useCenter = false,
                        topLeft = Offset.Zero,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    startAngle += sweep + gapDegrees
                }
            }
        }

        if (showCenterLabel) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = total.toString(),
                    style = if (size >= 88.dp) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (centerSubtitle != null && size >= 88.dp) {
                    Text(
                        text = centerSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// BİLEŞEN 2: ADDY TARZI KOMPAKT ALIAS LİSTE KARTI (`aliases_recyclerview_list_item.xml`)
// (Taşma sorununu %100 ortadan kaldırır; karta tıklayınca detay sayfası açılır)
// ============================================================================

@Composable
fun AddyAliasItemCard(
    alias: UiAlias,
    onClick: () -> Unit,
    onQuickActionClick: () -> Unit
) {
    var justCopied by remember { mutableStateOf(false) }

    LaunchedEffect(justCopied) {
        if (justCopied) {
            delay(1400)
            justCopied = false
        }
    }

    val cardBorderColor = when {
        alias.pinned -> AddyPalette.PortalOrange.copy(alpha = 0.55f)
        !alias.enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = if (alias.enabled) 2.dp else 0.dp,
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .alpha(if (alias.enabled) 1f else 0.68f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SOL: Addy Donut İstatistik Halkası
            AddyDonutChart(
                forwards = alias.forwards,
                replies = alias.replies,
                sends = alias.sends,
                blocks = alias.blocks,
                enabled = alias.enabled,
                size = 46.dp,
                strokeWidth = 5.5.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            // ORTA: E-posta Başlığı, Açıklama/Tarih ve Renkli Mini İstatistik Etiketleri
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Satır 1: E-posta adresi (Tek satır, taşma yok) + Sağ üst durum rozetleri
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                append(alias.localPart)
                            }
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                append("@${alias.domainPart}")
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        if (alias.wildcard) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Wildcard",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (!alias.enabled) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = "Pasif",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (alias.pinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Sabitlendi",
                                tint = AddyPalette.PortalOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Satır 2: Açıklama veya Alıcı + Oluşturulma Tarihi (`AliasAdapter.kt` tarzı)
                val subtitleText = buildString {
                    if (alias.description.isNotBlank()) {
                        append(alias.description)
                    } else if (alias.recipients.isNotBlank()) {
                        append("→ ${alias.recipients}")
                    } else {
                        append("Açıklama eklenmemiş")
                    }
                    if (alias.createdAt.isNotBlank()) {
                        append(" • ")
                        append(formatIsoDateShort(alias.createdAt).substringBefore(" •"))
                    }
                }
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Satır 3: Kompakt Renkli İstatistik Rozetleri (Hiçbir zaman taşmaz)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniStatDot(color = AddyPalette.PortalOrange, label = "${alias.forwards} iletilen")
                    if (alias.blocks > 0) {
                        MiniStatDot(color = AddyPalette.SoftRed, label = "${alias.blocks} engel")
                    }
                    if (alias.replies > 0) {
                        MiniStatDot(color = AddyPalette.PortalBlue, label = "${alias.replies} yanıt")
                    }
                    if (alias.sends > 0) {
                        MiniStatDot(color = AddyPalette.EasternBlue, label = "${alias.sends} gönderim")
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // SAĞ: Tek Dokunuş Kopyala / Wildcard Üret Düğmesi (`aliases_recyclerview_list_copy`)
            val btnContainerColor by animateColorAsState(
                targetValue = if (justCopied) {
                    AddyPalette.AddyGreen.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                label = "copyBtnBg"
            )
            val btnTint by animateColorAsState(
                targetValue = if (justCopied) {
                    AddyPalette.AddyGreen
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "copyBtnTint"
            )

            FilledTonalIconButton(
                onClick = {
                    if (!alias.wildcard) {
                        justCopied = true
                    }
                    onQuickActionClick()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = btnContainerColor,
                    contentColor = btnTint
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = when {
                        justCopied -> Icons.Default.Check
                        alias.wildcard -> Icons.Default.AutoAwesome
                        else -> Icons.Default.ContentCopy
                    },
                    contentDescription = if (alias.wildcard) "Wildcard Üret" else "Kopyala",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniStatDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// ============================================================================
// BİLEŞEN 3: ANA SAYFA SEKMESİ (`fragment_home.xml` & `HomeStatCardView` Karşılığı)
// ============================================================================

@Composable
fun AddyHomeDashboardTab(
    aliases: List<UiAlias>,
    defaultDomain: String,
    defaultRecipient: String,
    onNavigateToFilter: (String) -> Unit,
    onSelectAlias: (UiAlias) -> Unit,
    onOpenReverseAliasTool: () -> Unit,
    onOpenCreateAlias: () -> Unit
) {
    val context = LocalContext.current
    val totalForwards = remember(aliases) { aliases.sumOf { it.forwards } }
    val totalBlocks = remember(aliases) { aliases.sumOf { it.blocks } }
    val totalReplies = remember(aliases) { aliases.sumOf { it.replies } }
    val totalSends = remember(aliases) { aliases.sumOf { it.sends } }

    val activeCount = remember(aliases) { aliases.count { it.enabled } }
    val inactiveCount = remember(aliases) { aliases.count { !it.enabled } }
    val pinnedAliases = remember(aliases) { aliases.filter { it.pinned } }
    val wildcardCount = remember(aliases) { aliases.count { it.wildcard } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero İstatistik Özeti Kartı
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "E-posta Trafik İstatistikleri",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (defaultRecipient.isNotEmpty()) {
                                    "Ana Alıcı: $defaultRecipient"
                                } else {
                                    "Aktif Alan Adı: @$defaultDomain"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AddyDonutChart(
                            forwards = totalForwards,
                            replies = totalReplies,
                            sends = totalSends,
                            blocks = totalBlocks,
                            enabled = true,
                            size = 64.dp,
                            strokeWidth = 7.dp
                        )
                    }

                    // 2x2 İstatistik Izgarası (`home_stats_gridlayout`)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AddyStatGridCard(
                            modifier = Modifier.weight(1f),
                            title = "İletilen",
                            value = totalForwards.toString(),
                            icon = Icons.Default.Inbox,
                            accentColor = AddyPalette.PortalOrange
                        )
                        AddyStatGridCard(
                            modifier = Modifier.weight(1f),
                            title = "Engellenen",
                            value = totalBlocks.toString(),
                            icon = Icons.Default.Block,
                            accentColor = AddyPalette.SoftRed
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AddyStatGridCard(
                            modifier = Modifier.weight(1f),
                            title = "Yanıtlanan",
                            value = totalReplies.toString(),
                            icon = Icons.Default.Reply,
                            accentColor = AddyPalette.PortalBlue
                        )
                        AddyStatGridCard(
                            modifier = Modifier.weight(1f),
                            title = "Gönderilen",
                            value = totalSends.toString(),
                            icon = Icons.AutoMirrored.Filled.Send,
                            accentColor = AddyPalette.EasternBlue
                        )
                    }
                }
            }
        }

        // 2. Takma Ad Durum Özeti (`home_stats_aliases_gridlayout`)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Takma Ad Dağılımı",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyInteractiveCountCard(
                        modifier = Modifier.weight(1f),
                        label = "Toplam",
                        count = aliases.size,
                        icon = Icons.Default.AlternateEmail,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToFilter("active_inactive") }
                    )
                    AddyInteractiveCountCard(
                        modifier = Modifier.weight(1f),
                        label = "Aktif",
                        count = activeCount,
                        icon = Icons.Default.CheckCircle,
                        tint = AddyPalette.AddyGreen,
                        onClick = { onNavigateToFilter("active") }
                    )
                    AddyInteractiveCountCard(
                        modifier = Modifier.weight(1f),
                        label = "Pasif",
                        count = inactiveCount,
                        icon = Icons.Default.PauseCircle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onNavigateToFilter("inactive") }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AddyInteractiveCountCard(
                        modifier = Modifier.weight(1f),
                        label = "Sabitlenen",
                        count = pinnedAliases.size,
                        icon = Icons.Default.PushPin,
                        tint = AddyPalette.PortalOrange,
                        onClick = { onNavigateToFilter("pinned_only") }
                    )
                    AddyInteractiveCountCard(
                        modifier = Modifier.weight(1f),
                        label = "Wildcard (*)",
                        count = wildcardCount,
                        icon = Icons.Default.AutoAwesome,
                        tint = AddyPalette.EasternBlue,
                        onClick = { onNavigateToFilter("wildcard_only") }
                    )
                    AddyInteractiveCountCard(
                        modifier = Modifier.weight(1f),
                        label = "Silinen (90g)",
                        count = 0,
                        icon = Icons.Default.DeleteOutline,
                        tint = AddyPalette.SoftRed,
                        onClick = { onNavigateToFilter("deleted") }
                    )
                }
            }
        }

        // 3. Hızlı Eylem Araçları
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Hızlı Gizlilik Araçları",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenCreateAlias,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hızlı Alias", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onOpenReverseAliasTool,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ters-Alias Üret", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // 4. Sabitlenen Takma Adlar Bölümü
        if (pinnedAliases.isNotEmpty()) {
            item {
                Text(
                    text = "Sabitlenen Takma Adlar (${pinnedAliases.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(pinnedAliases, key = { "home_pinned_${it.id}" }) { alias ->
                AddyAliasItemCard(
                    alias = alias,
                    onClick = { onSelectAlias(alias) },
                    onQuickActionClick = {
                        copyToClipboard(context, "Mailx Alias", alias.name)
                    }
                )
            }
        }
    }
}

@Composable
private fun AddyStatGridCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddyInteractiveCountCard(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// BİLEŞEN 4: TAKMA ADLAR LİSTE SEKMESİ (`fragment_aliases.xml` Karşılığı)
// ============================================================================

@Composable
fun AddyAliasesTab(
    aliases: List<UiAlias>,
    totalLoadedCount: Int,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedFilterChip: String,
    onSelectFilterChip: (String) -> Unit,
    sortField: AliasSortField,
    sortDescending: Boolean,
    onOpenFilterSheet: () -> Unit,
    onSelectAlias: (UiAlias) -> Unit,
    onQuickCopyOrWildcard: (UiAlias) -> Unit,
    onOpenCreateSheet: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Arama Çubuğu + Filtre/Sıralama Düğmesi (`alias_search_bar`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Temizle")
                        }
                    }
                },
                placeholder = {
                    Text(
                        text = "Alias, açıklama veya alıcı ara...",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.weight(1f)
            )

            FilledTonalIconButton(
                onClick = onOpenFilterSheet,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Filtrele ve Sırala"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Yatay Kaydırılabilir Filtre Çipleri (`HorizontalScrollView` + `alias_chipgroup`)
        // Yatay scroll sayesinde ekran ne kadar dar olursa olsun hiçbir çip sıkışmaz!
        val filterItems = listOf(
            "active_inactive" to "Tümü",
            "pinned_only" to "Sabitlenenler",
            "active" to "Aktif",
            "inactive" to "Pasif",
            "wildcard_only" to "Wildcard (*)",
            "deleted" to "Silinen (90g)"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterItems.forEach { (key, label) ->
                val isSelected = selectedFilterChip == key
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectFilterChip(key) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    label = { Text(label) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Başlık + Kayıt Sayısı Kapsülü (`alias_count` / `bg_count_capsule`) + Aktif Sıralama Bilgisi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Takma Adlar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = aliases.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            TextButton(
                onClick = onOpenFilterSheet,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = if (sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = sortField.label,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (aliases.isEmpty()) {
            // Boş Durum (Empty State) Görünümü
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AlternateEmail,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "\"$searchQuery\" için sonuç bulunamadı"
                        } else {
                            "Bu filtrede takma ad bulunmuyor"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Yeni bir Mailx takma adı oluşturmak için aşağıdaki düğmeye dokunun.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FilledTonalButton(onClick = onOpenCreateSheet) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yeni Alias Oluştur")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
            ) {
                items(aliases, key = { it.id }) { alias ->
                    AddyAliasItemCard(
                        alias = alias,
                        onClick = { onSelectAlias(alias) },
                        onQuickActionClick = { onQuickCopyOrWildcard(alias) }
                    )
                }
            }
        }
    }
}

// ============================================================================
// BİLEŞEN 5: ADDY TARZI ALIAS DETAY VE YÖNETİM SAYFASI (`activity_manage_alias.xml`)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAliasBottomSheet(
    alias: UiAlias,
    availableRecipients: List<String>,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePinned: (Boolean) -> Unit,
    onUpdateMetadata: (description: String, fromName: String, recipients: String) -> Unit,
    onOpenSendFromAlias: () -> Unit,
    onOpenWildcardGenerator: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showNatoPhonetic by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<String?>(null) } // "description", "from_name", "recipients"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Üst Başlık: Tam E-posta Adresi + NATO Fonetik Alfabe Geçişi
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = alias.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = { showNatoPhonetic = !showNatoPhonetic },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(if (showNatoPhonetic) "Fonetik Gizle" else "NATO Fonetik Kodlama")
                            }
                        )
                        if (alias.wildcard) {
                            AssistChip(
                                onClick = onOpenWildcardGenerator,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Wildcard Üret") }
                            )
                        }
                    }

                    // NATO Fonetik Alfabe Açılır Paneli (`ManageAliasNATOActivity.kt` ilhamlı)
                    AnimatedVisibility(visible = showNatoPhonetic) {
                        val pairs = remember(alias.name) { NatoPhoneticHelper.toPhoneticPairs(alias.name) }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Harf Harf NATO Fonetik Okunuşu:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    pairs.forEachIndexed { idx, (ch, word) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = ch,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "${idx + 1}. $word",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Büyük Donut Grafik + 4 Renkli İstatistik Göstergesi (`activity_manage_alias_chart` & `stats_LL`)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AddyDonutChart(
                        forwards = alias.forwards,
                        replies = alias.replies,
                        sends = alias.sends,
                        blocks = alias.blocks,
                        enabled = alias.enabled,
                        size = 98.dp,
                        strokeWidth = 10.dp,
                        centerSubtitle = "Toplam"
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendStatRow(
                            icon = Icons.Default.Inbox,
                            tint = AddyPalette.PortalOrange,
                            text = "${alias.forwards} İletilen (Forwarded)"
                        )
                        LegendStatRow(
                            icon = Icons.Default.Reply,
                            tint = AddyPalette.PortalBlue,
                            text = "${alias.replies} Yanıtlanan (Replied)"
                        )
                        LegendStatRow(
                            icon = Icons.AutoMirrored.Filled.Send,
                            tint = AddyPalette.EasternBlue,
                            text = "${alias.sends} Gönderilen (Sent)"
                        )
                        LegendStatRow(
                            icon = Icons.Default.Block,
                            tint = AddyPalette.SoftRed,
                            text = "${alias.blocks} Engellenen (Blocked)"
                        )
                    }
                }
            }

            // 3. Birincil Eylem Düğmeleri (2 Eşit Genişlikte Düğme - Asla Taşmaz!)
            // (`activity_manage_alias_copy` & `activity_manage_alias_send`)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (alias.wildcard) {
                            onOpenWildcardGenerator()
                        } else {
                            copyToClipboard(context, "Mailx Alias", alias.name)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = if (alias.wildcard) Icons.Default.AutoAwesome else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (alias.wildcard) "Wildcard Üret" else "Kopyala",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onOpenSendFromAlias,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "İleti Gönder",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 4. Genel Ayarlar ve Düzenleme Bölümü (`alias_general_actions.xml` / `SectionView`)
            Text(
                text = "Genel Ayarlar",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 4a. Alias Durumu (Aktif / Pasif Switch)
            AddySectionSwitchRow(
                icon = Icons.Default.CheckCircle,
                title = "Alias Aktif",
                subtitle = if (alias.enabled) {
                    "Gelen e-postalar alıcı adresinize yönlendiriliyor"
                } else {
                    "Duraklatıldı — bu adrese gelen e-postalar sessizce bırakılır"
                },
                checked = alias.enabled,
                onCheckedChange = onToggleEnabled
            )

            // 4b. Sabitle (Pinned Switch)
            AddySectionSwitchRow(
                icon = Icons.Default.PushPin,
                title = "Takma Adı Sabitle",
                subtitle = "Ana sayfada ve listenin en üstünde sabit tut",
                checked = alias.pinned,
                onCheckedChange = onTogglePinned
            )

            // 4c. Açıklama (Description) Düzenle
            AddySectionActionRow(
                icon = Icons.Default.Description,
                title = "Açıklama",
                subtitle = alias.description.ifBlank { "Açıklama yok (Eklemek için dokunun)" },
                trailingIcon = Icons.Default.Edit,
                onClick = { editingField = "description" }
            )

            // 4d. Gönderici Adı (From Name) Düzenle
            AddySectionActionRow(
                icon = Icons.Default.Person,
                title = "Gönderici Adı (From Name)",
                subtitle = alias.fromName.ifBlank { "Varsayılan (Ayarlanmamış)" },
                trailingIcon = Icons.Default.Edit,
                onClick = { editingField = "from_name" }
            )

            // 4e. Alıcılar (Recipients) Düzenle
            AddySectionActionRow(
                icon = Icons.Default.Inbox,
                title = "Alıcı E-posta Adresleri",
                subtitle = alias.recipients.ifBlank { "Varsayılan alıcı" },
                trailingIcon = Icons.Default.Edit,
                onClick = { editingField = "recipients" }
            )

            // 4f. Tarih Bilgileri
            AddySectionActionRow(
                icon = Icons.Default.Schedule,
                title = "Oluşturulma ve Güncellenme",
                subtitle = "Oluşturulma: ${formatIsoDateShort(alias.createdAt)}\nGüncellenme: ${formatIsoDateShort(alias.updatedAt)}",
                trailingIcon = null,
                onClick = null
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 5. Tehlikeli Bölge: Alias'ı Sil (`activity_manage_alias_delete`)
            // Tam genişlikte, kırmızı vurgulu, asla ekrandan taşmayan net silme kartı!
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onRequestDelete),
                shape = RoundedCornerShape(16.dp),
                color = AddyPalette.SoftRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AddyPalette.SoftRed.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = AddyPalette.SoftRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Takma Adı Sil",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AddyPalette.SoftRed
                        )
                        Text(
                            text = "Bu takma adı silinenler kutusuna (90 gün) taşır ve gelen iletileri durdurur.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Alan Düzenleme Alt Diyaloğu (Açıklama / Gönderici Adı / Alıcılar)
    editingField?.let { field ->
        var textVal by remember(field, alias) {
            mutableStateOf(
                when (field) {
                    "description" -> alias.description
                    "from_name" -> alias.fromName
                    "recipients" -> alias.recipients
                    else -> ""
                }
            )
        }
        AlertDialog(
            onDismissRequest = { editingField = null },
            title = {
                Text(
                    when (field) {
                        "description" -> "Açıklamayı Düzenle"
                        "from_name" -> "Gönderici Adını Düzenle"
                        else -> "Alıcıları Düzenle"
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (field == "recipients" && availableRecipients.isNotEmpty()) {
                        Text(
                            text = "Kayıtlı Alıcılar (Seçmek için dokunun):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableRecipients.forEach { rec ->
                                val currentList = textVal.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val isSelected = currentList.contains(rec)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val mutable = currentList.toMutableList()
                                        if (isSelected) {
                                            if (mutable.size > 1) mutable.remove(rec)
                                        } else {
                                            mutable.add(rec)
                                        }
                                        textVal = mutable.joinToString(",")
                                    },
                                    label = { Text(rec) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = textVal,
                        onValueChange = { textVal = it },
                        label = {
                            Text(
                                when (field) {
                                    "description" -> "Açıklama (Örn: GitHub, Kargo)"
                                    "from_name" -> "Gönderici Adı (Örn: Ad Soyad)"
                                    else -> "Alıcılar (Virgülle ayrılmış)"
                                }
                            )
                        },
                        singleLine = field != "description",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newDesc = if (field == "description") textVal.trim() else alias.description
                        val newFrom = if (field == "from_name") textVal.trim() else alias.fromName
                        val newRec = if (field == "recipients") textVal.trim() else alias.recipients
                        editingField = null
                        onUpdateMetadata(newDesc, newFrom, newRec)
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingField = null }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
private fun LegendStatRow(icon: ImageVector, tint: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AddySectionSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun AddySectionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingIcon: ImageVector?,
    onClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = "Düzenle",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ============================================================================
// BİLEŞEN 6: ADDY TARZI YENİ ALIAS OLUŞTURMA BOTTOM SHEET (`bottomsheet_addalias.xml`)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAliasBottomSheet(
    availableDomains: List<String>,
    availableRecipients: List<String>,
    defaultDomain: String,
    defaultRecipient: String,
    defaultFormat: String,
    defaultFromName: String,
    onDismiss: () -> Unit,
    onCreateAlias: (requestJson: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedDomain by remember { mutableStateOf(defaultDomain.ifEmpty { "mailx.net" }) }
    var selectedFormat by remember { mutableStateOf(defaultFormat.ifEmpty { "words" }) }
    var description by remember { mutableStateOf("") }
    var fromName by remember { mutableStateOf(defaultFromName) }
    var customLocalPart by remember { mutableStateOf("") }
    var wildcardLocalPart by remember { mutableStateOf("") }
    var wildcardDelimiter by remember { mutableStateOf("+") }
    var recipientsText by remember { mutableStateOf(defaultRecipient) }
    var isSubmitting by remember { mutableStateOf(false) }

    val formats = listOf(
        "words" to "Kelime (Örn: autumn.wind42)",
        "random" to "Rastgele Karakter",
        "uuid" to "UUID",
        "wildcard" to "Wildcard (*+sonek)",
        "custom" to "Özel Yerel Ad (Custom)"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Yeni Takma Ad (Alias) Oluştur",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Oluşturulan takma ad anında panonuza kopyalanır ve seçili alıcılara yönlendirilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 1. Alan Adı (Domain) Seçimi (`bs_addalias_domain_til`)
            Text(
                text = "Alan Adı (Domain)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val domainsToShow = if (availableDomains.isNotEmpty()) availableDomains else listOf(selectedDomain)
                domainsToShow.forEach { dom ->
                    FilterChip(
                        selected = selectedDomain == dom,
                        onClick = { selectedDomain = dom },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text("@$dom") }
                    )
                }
            }

            // 2. Alias Formatı Seçimi (`bs_addalias_alias_format_til`)
            Text(
                text = "Alias Formatı",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                formats.forEach { (fmtKey, fmtLabel) ->
                    FilterChip(
                        selected = selectedFormat == fmtKey,
                        onClick = { selectedFormat = fmtKey },
                        label = { Text(fmtLabel) }
                    )
                }
            }

            // 3. Özel (Custom) veya Wildcard için Yerel Kısım Alanları (`bs_addalias_alias_local_part_til`)
            AnimatedVisibility(visible = selectedFormat == "custom") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = customLocalPart,
                        onValueChange = { customLocalPart = it.trim() },
                        label = { Text("Özel Yerel Ad (Local Part)") },
                        placeholder = { Text("Örn: github.hesabim") },
                        suffix = { Text("@$selectedDomain", fontFamily = FontFamily.Monospace) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AnimatedVisibility(visible = selectedFormat == "wildcard") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Wildcard Ayırıcı:", style = MaterialTheme.typography.bodySmall)
                        listOf("+" to "*+ (Artı)", "." to "*. (Nokta)").forEach { (delim, lbl) ->
                            FilterChip(
                                selected = wildcardDelimiter == delim,
                                onClick = { wildcardDelimiter = delim },
                                label = { Text(lbl) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = wildcardLocalPart,
                        onValueChange = { wildcardLocalPart = it.trim() },
                        label = { Text("Wildcard Soneki (İsteğe bağlı, boşsa rastgele)") },
                        placeholder = { Text("Örn: alisveris") },
                        prefix = { Text("*$wildcardDelimiter", fontFamily = FontFamily.Monospace) },
                        suffix = { Text("@$selectedDomain", fontFamily = FontFamily.Monospace) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 4. Açıklama (`bs_addalias_alias_desc_til`)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Açıklama (Örn: GitHub, Amazon, Kargo)") },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Gönderici Adı (From Name)
            OutlinedTextField(
                value = fromName,
                onValueChange = { fromName = it },
                label = { Text("Gönderici Adı (İsteğe Bağlı)") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 6. Alıcı Seçimi (`bs_addalias_recipients_chipgroup`)
            Text(
                text = "Yönlendirilecek Alıcı (Recipients)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (availableRecipients.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableRecipients.forEach { rec ->
                        val currentRecs = recipientsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val isSelected = currentRecs.contains(rec)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val mutable = currentRecs.toMutableList()
                                if (isSelected) {
                                    if (mutable.size > 1) mutable.remove(rec)
                                } else {
                                    mutable.add(rec)
                                }
                                recipientsText = mutable.joinToString(",")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(rec) }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = recipientsText,
                onValueChange = { recipientsText = it },
                label = { Text("Alıcı E-posta Adresi") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 7. Oluştur ve Kopyala Düğmesi (`bs_addalias_alias_add_alias_button`)
            Button(
                onClick = {
                    isSubmitting = true
                    val reqObj = JSONObject().apply {
                        put("description", description.trim())
                        put("enabled", true)
                        put("recipients", recipientsText.trim())
                        put("from_name", fromName.trim())
                        put("format", selectedFormat)
                        put("domain", selectedDomain)
                        if (selectedFormat == "custom" && customLocalPart.isNotEmpty()) {
                            put("local_part", customLocalPart)
                        }
                        if (selectedFormat == "wildcard") {
                            put("wildcard_delimiter", wildcardDelimiter)
                            if (wildcardLocalPart.isNotEmpty()) {
                                put("wildcard_local_part", wildcardLocalPart)
                            }
                        }
                    }
                    onCreateAlias(reqObj.toString())
                },
                enabled = !isSubmitting && (selectedFormat != "custom" || customLocalPart.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSubmitting) "Oluşturuluyor..." else "Oluştur ve Panoya Kopyala",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ============================================================================
// BİLEŞEN 7: ADDY TARZI FİLTRELEME VE SIRALAMA BOTTOM SHEET (`bottomsheet_filter_options_alias.xml`)
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterOptionsBottomSheet(
    selectedFilterChip: String,
    sortField: AliasSortField,
    sortDescending: Boolean,
    pinnedFirst: Boolean,
    onApply: (String, AliasSortField, Boolean, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var tempFilter by remember { mutableStateOf(selectedFilterChip) }
    var tempSort by remember { mutableStateOf(sortField) }
    var tempDesc by remember { mutableStateOf(sortDescending) }
    var tempPinnedFirst by remember { mutableStateOf(pinnedFirst) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtreleme ve Sıralama",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onReset) {
                    Text("Sıfırla")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "Durum Filtresi",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "active_inactive" to "Tümü",
                    "active" to "Yalnızca Aktif",
                    "inactive" to "Yalnızca Pasif",
                    "pinned_only" to "Sabitlenenler",
                    "wildcard_only" to "Wildcard (*)",
                    "deleted" to "Silinenler (90g)"
                ).forEach { (key, lbl) ->
                    FilterChip(
                        selected = tempFilter == key,
                        onClick = { tempFilter = key },
                        label = { Text(lbl) }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sıralama Ölçütü",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = { tempDesc = !tempDesc },
                    leadingIcon = {
                        Icon(
                            imageVector = if (tempDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (tempDesc) "Azalan (Yeni/Yüksek)" else "Artan (Eski/Düşük)") }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AliasSortField.entries.forEach { field ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { tempSort = field },
                        shape = RoundedCornerShape(12.dp),
                        color = if (tempSort == field) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            Color.Transparent
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = field.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (tempSort == field) FontWeight.Bold else FontWeight.Normal
                            )
                            if (tempSort == field) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            AddySectionSwitchRow(
                icon = Icons.Default.PushPin,
                title = "Sabitlenenleri En Üstte Göster",
                subtitle = "Sıralama ne olursa olsun sabitlenen takma adları listenin başında tut",
                checked = tempPinnedFirst,
                onCheckedChange = { tempPinnedFirst = it }
            )

            Button(
                onClick = { onApply(tempFilter, tempSort, tempDesc, tempPinnedFirst) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Uygula", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// BİLEŞEN 8: WILDCARD ANINDA ÜRETİCİ VE TERS-ALIAS GÖNDERİM BOTTOM SHEET'LERİ
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WildcardGeneratorBottomSheet(
    wildcardAlias: UiAlias,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var prefix by remember { mutableStateOf("") }
    var generated by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Anında Wildcard Alias Üret",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Şablon: ${wildcardAlias.name}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
            OutlinedTextField(
                value = prefix,
                onValueChange = {
                    prefix = it
                    val r = JSONObject(MailxRustBridge.nativeGenerateWildcard(wildcardAlias.name, it))
                    if (r.optBoolean("ok", false)) {
                        generated = r.optString("data")
                        errorMsg = null
                    } else {
                        generated = ""
                        errorMsg = if (it.isNotEmpty()) r.optString("error") else null
                    }
                },
                label = { Text("Servis Öneki (Örn: amazon, github, netflix)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (generated.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Üretilen Wildcard Adresi:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectionContainer {
                            Text(
                                text = generated,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Button(
                enabled = generated.isNotEmpty(),
                onClick = {
                    copyToClipboard(context, "Mailx Wildcard Alias", generated)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adresi Kopyala")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReverseAliasSendBottomSheet(
    initialAliasName: String,
    availableAliases: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var senderAlias by remember { mutableStateOf(initialAliasName) }
    var targetEmail by remember { mutableStateOf("") }
    var generated by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun recompute(aliasStr: String, toStr: String) {
        if (toStr.isBlank()) {
            generated = ""
            errorMsg = null
            return
        }
        val r = JSONObject(MailxRustBridge.nativeGenerateReverseAlias(aliasStr, toStr))
        if (r.optBoolean("ok", false)) {
            generated = r.optString("data")
            errorMsg = null
        } else {
            generated = ""
            errorMsg = r.optString("error")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Alias Üzerinden E-posta Gönder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Gerçek e-posta adresinizi gizleyerek seçtiğiniz takma ad üzerinden e-posta göndermek için alıcı adresini girin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (availableAliases.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableAliases.take(12).forEach { candidate ->
                        FilterChip(
                            selected = senderAlias == candidate,
                            onClick = {
                                senderAlias = candidate
                                recompute(candidate, targetEmail)
                            },
                            label = { Text(candidate, maxLines = 1) }
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
                label = { Text("Gönderici Alias Adresiniz") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = targetEmail,
                onValueChange = {
                    targetEmail = it
                    recompute(senderAlias, it)
                },
                label = { Text("Alıcı E-posta Adresi (To)") },
                placeholder = { Text("Örn: destek@firma.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            errorMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (generated.isNotEmpty()) {
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
                            text = "E-posta İstemcinizde 'Kime (To)' Alanına Yapıştırılacak Adres:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectionContainer {
                            Text(
                                text = generated,
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    enabled = generated.isNotEmpty(),
                    onClick = {
                        copyToClipboard(context, "Mailx Send Address", generated)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kopyala")
                }

                Button(
                    enabled = generated.isNotEmpty(),
                    onClick = {
                        copyToClipboard(context, "Mailx Send Address", generated)
                        runCatching {
                            val mailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$generated"))
                            context.startActivity(Intent.createChooser(mailIntent, "E-posta Uygulaması Seçin"))
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("E-posta Aç")
                }
            }
        }
    }
}

// ============================================================================
// BİLEŞEN 9: AYARLAR, ALAN ADLARI VE ALICILAR SEKMESİ (`SettingsTab`)
// ============================================================================

@Composable
fun AddySettingsTab(
    prefs: EncryptedPrefsManager,
    blockTrackers: Boolean,
    removeHeader: Boolean,
    availableDomains: List<String>,
    availableRecipients: List<String>,
    defaultDomain: String,
    defaultRecipient: String,
    defaultFormat: String,
    onUpdateDefaultDomain: (String) -> Unit,
    onUpdateDefaultFormat: (String) -> Unit,
    onSyncDefaults: () -> Unit,
    onOpenReverseAliasTool: () -> Unit,
    onRequestLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Sunucu ve Varsayılan Alias Ayarları
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Sunucu & Varsayılanlar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = prefs.baseUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalIconButton(onClick = onSyncDefaults) {
                            Icon(Icons.Default.Sync, contentDescription = "Varsayılanları Senkronize Et")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Text(
                        text = "Varsayılan Alan Adı (${availableDomains.size} mevcut)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableDomains.forEach { dom ->
                            FilterChip(
                                selected = defaultDomain == dom,
                                onClick = { onUpdateDefaultDomain(dom) },
                                label = { Text("@$dom") }
                            )
                        }
                    }

                    Text(
                        text = "Varsayılan Alias Formatı (Quick Tile & Yeni Alias)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "words" to "Kelime",
                            "random" to "Rastgele",
                            "uuid" to "UUID",
                            "wildcard" to "Wildcard"
                        ).forEach { (fmt, lbl) ->
                            FilterChip(
                                selected = defaultFormat == fmt,
                                onClick = { onUpdateDefaultFormat(fmt) },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Doğrulanmış Alıcı E-postalar (`RecipientsFragment` ilhamlı)
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Doğrulanmış Alıcı Adresleri",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (availableRecipients.isEmpty()) {
                        Text(
                            text = "Henüz senkronize edilmiş alıcı bulunamadı.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        availableRecipients.forEach { rec ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MarkEmailRead,
                                    contentDescription = null,
                                    tint = AddyPalette.AddyGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = rec,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                if (rec == defaultRecipient) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Varsayılan",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Sunucu Gizlilik Durumu & Entegrasyonlar
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Gizlilik ve Sistem Entegrasyonları",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AddySectionActionRow(
                    icon = Icons.Default.Shield,
                    title = "Takipçi Engelleme (Block Trackers)",
                    subtitle = if (blockTrackers) "Sunucu üzerinde aktif" else "Sunucu varsayılanı: Kapalı",
                    trailingIcon = null,
                    onClick = null
                )
                AddySectionActionRow(
                    icon = Icons.Default.Security,
                    title = "E-posta Başlığı Temizleme (Remove Header)",
                    subtitle = if (removeHeader) "Sunucu üzerinde aktif" else "Sunucu varsayılanı: Kapalı",
                    trailingIcon = null,
                    onClick = null
                )
                AddySectionActionRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "Ters-Alias (Send from Alias) Aracı",
                    subtitle = "Herhangi bir takma adınızdan gerçek adresinizi gizleyerek e-posta gönderin",
                    trailingIcon = Icons.Default.ChevronRight,
                    onClick = onOpenReverseAliasTool
                )
            }
        }

        // 4. Oturumu Kapat
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onRequestLogout),
                shape = RoundedCornerShape(16.dp),
                color = AddyPalette.SoftRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, AddyPalette.SoftRed.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = AddyPalette.SoftRed
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Güvenli Çıkış Yap",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AddyPalette.SoftRed
                        )
                        Text(
                            text = "Android Keystore üzerindeki Access Key ve önbelleği sıfırlar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// BİLEŞEN 10: ADDY İLHAMLI KURULUM / GİRİŞ EKRANI (`SetupNewActivity` Karşılığı)
// ============================================================================

@Composable
fun AddySetupScreen(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    accessKeyInput: String,
    onAccessKeyChange: (String) -> Unit,
    authError: String?,
    isLoading: Boolean,
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
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Mailx Privacy",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Rust Çekirdek • 16 KB Uyumlu • Addy Deneyimi",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Sunucu ve Kimlik Doğrulama",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = baseUrl.contains("api.mailx.net"),
                        onClick = { onBaseUrlChange("https://api.mailx.net") },
                        label = { Text("Resmi (api.mailx.net)") }
                    )
                    FilterChip(
                        selected = !baseUrl.contains("api.mailx.net"),
                        onClick = { if (baseUrl.contains("api.mailx.net")) onBaseUrlChange("https://") },
                        label = { Text("Özel Sunucu (Self-Host)") }
                    )
                }

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = { Text("API Sunucu Adresi") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = accessKeyInput,
                    onValueChange = onAccessKeyChange,
                    label = { Text("Access Key (85 karakter)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                if (clipText.isNotEmpty()) {
                                    onAccessKeyChange(clipText)
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Yapıştır")
                            }
                            IconButton(onClick = { showAccessKey = !showAccessKey }) {
                                Icon(
                                    imageVector = if (showAccessKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showAccessKey) "Gizle" else "Göster"
                                )
                            }
                        }
                    },
                    supportingText = {
                        Text("${accessKeyInput.trim().length} / 85 karakter")
                    },
                    visualTransformation = if (showAccessKey) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                authError?.let {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = onConnect,
                    enabled = !isLoading && accessKeyInput.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Doğrulanıyor...")
                    } else {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Access Key ile Bağlan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
