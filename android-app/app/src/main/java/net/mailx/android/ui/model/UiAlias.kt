package net.mailx.android.ui.model

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

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

    companion object {
        fun fromJson(obj: JSONObject): UiAlias {
            val stats = obj.optJSONObject("stats")
            val deletedRaw = obj.opt("deleted_at")
            val deletedStr = if (deletedRaw == null || deletedRaw == JSONObject.NULL) "" else deletedRaw.toString()
            return UiAlias(
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
        }

        fun listFromJsonArray(arr: JSONArray?): List<UiAlias> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(fromJson(obj))
                }
            }
        }
    }
}

enum class MainNavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Özet", Icons.Outlined.Home, Icons.Default.Home),
    ALIASES("Takma Adlar", Icons.Outlined.AlternateEmail, Icons.Default.AlternateEmail),
    SETTINGS("Ayarlar", Icons.Outlined.Settings, Icons.Default.Settings)
}

enum class AliasSortField(val key: String, val label: String) {
    CREATED_AT("created_at", "Oluşturulma Tarihi"),
    UPDATED_AT("updated_at", "Güncellenme Tarihi"),
    NAME("name", "Alfabetik (A-Z)"),
    FORWARDS("forwards", "En Çok İletilen"),
    BLOCKS("blocks", "En Çok Engellenen"),
    REPLIES("replies", "En Çok Yanıtlanan"),
    SENDS("sends", "En Çok Gönderilen")
}

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val baseUrl: String = "https://api.mailx.net",
    val accessKeyInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class FilterUiState(
    val searchQuery: String = "",
    val selectedChip: String = "active_inactive",
    val sortField: AliasSortField = AliasSortField.CREATED_AT,
    val sortDescending: Boolean = true,
    val pinnedFirst: Boolean = true
) {
    val isCustomFilterActive: Boolean
        get() = selectedChip != "active_inactive" ||
            sortField != AliasSortField.CREATED_AT ||
            !sortDescending ||
            !pinnedFirst
}

data class DefaultsUiState(
    val defaultDomain: String = "mailx.net",
    val defaultRecipient: String = "",
    val defaultFormat: String = "words",
    val defaultFromName: String = "",
    val availableDomains: List<String> = listOf("mailx.net"),
    val availableRecipients: List<String> = emptyList(),
    val blockTrackers: Boolean = false,
    val removeHeader: Boolean = false
)

sealed interface ActiveSheet {
    data object None : ActiveSheet
    data object CreateAlias : ActiveSheet
    data object FilterOptions : ActiveSheet
    data class ManageAlias(val aliasId: String) : ActiveSheet
    data class WildcardGen(val alias: UiAlias) : ActiveSheet
    data class ReverseSend(val initialAlias: String) : ActiveSheet
    data class ConfirmDelete(val alias: UiAlias) : ActiveSheet
    data object ConfirmLogout : ActiveSheet
}

object NatoPhoneticHelper {
    private val map = mapOf(
        'A' to "Alpha", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta", 'E' to "Echo",
        'F' to "Foxtrot", 'G' to "Golf", 'H' to "Hotel", 'I' to "India", 'J' to "Juliett",
        'K' to "Kilo", 'L' to "Lima", 'M' to "Mike", 'N' to "November", 'O' to "Oscar",
        'P' to "Papa", 'Q' to "Quebec", 'R' to "Romeo", 'S' to "Sierra", 'T' to "Tango",
        'U' to "Uniform", 'V' to "Victor", 'W' to "Whiskey", 'X' to "X-ray", 'Y' to "Yankee",
        'Z' to "Zulu",
        '0' to "Zero", '1' to "One", '2' to "Two", '3' to "Three", '4' to "Four",
        '5' to "Five", '6' to "Six", '7' to "Seven", '8' to "Eight", '9' to "Nine",
        '.' to "Nokta", '@' to "At (@)", '-' to "Tire", '_' to "AltÇizgi", '+' to "Artı", '*' to "Yıldız"
    )

    fun toPhoneticPairs(email: String): List<Pair<String, String>> =
        email.map { ch -> ch.toString() to (map[ch.uppercaseChar()] ?: ch.toString()) }
}

fun formatIsoDateShort(raw: String): String {
    val clean = raw.trim()
    if (clean.isEmpty() || clean == "null") return "—"
    val datePart = clean.substringBefore("T")
    val timePart = clean.substringAfter("T", "").take(5)
    val parts = datePart.split("-")
    return if (parts.size == 3) {
        val d = "${parts[2]}.${parts[1]}.${parts[0]}"
        if (timePart.isNotEmpty()) "$d • $timePart" else d
    } else {
        clean.take(16)
    }
}

fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String? = null) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage ?: "Kopyalandı: $text", Toast.LENGTH_SHORT).show()
}
