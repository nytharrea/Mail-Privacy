package net.mailx.android.ui.model

import net.mailx.android.core.MailxRustBridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class UiAlias(
    val id: String,
    val name: String,
    val description: String,
    val recipients: String,
    val fromName: String,
    val enabled: Boolean,
    val pinned: Boolean,
    val wildcard: Boolean,
    val pausedUntil: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String,
    val forwards: Int,
    val blocks: Int,
    val replies: Int,
    val sends: Int,
    val isCustomDomain: Boolean,
    val isDomainVerified: Boolean,
    val isDomainEnabled: Boolean,
    val rawJson: JSONObject
) {
    val localPart: String
        get() = name.substringBefore("@")

    val domainPart: String
        get() = name.substringAfter("@", "mailx.net")

    val totalActivity: Int
        get() = forwards + blocks + replies + sends

    val isPaused: Boolean
        get() = pausedUntil.isNotBlank() && pausedUntil != "null"

    companion object {
        fun fromJson(obj: JSONObject): UiAlias {
            val stats = obj.optJSONObject("stats")
            val deletedRaw = obj.opt("deleted_at")
            val deletedStr = if (deletedRaw == null || deletedRaw == JSONObject.NULL) "" else deletedRaw.toString()
            val pausedRaw = obj.opt("paused_until")
            val pausedStr = if (pausedRaw == null || pausedRaw == JSONObject.NULL) "" else pausedRaw.toString()
            val domVerRaw = obj.opt("is_domain_verified")
            val domVer = if (domVerRaw == null || domVerRaw == JSONObject.NULL) true else obj.optBoolean("is_domain_verified", true)
            return UiAlias(
                id = obj.optString("id"),
                name = obj.optString("name"),
                description = obj.optString("description"),
                recipients = obj.optString("recipients"),
                fromName = obj.optString("from_name"),
                enabled = obj.optBoolean("enabled", true),
                pinned = obj.optBoolean("pinned", false),
                wildcard = obj.optBoolean("wildcard", false),
                pausedUntil = pausedStr,
                createdAt = obj.optString("created_at", ""),
                updatedAt = obj.optString("updated_at", ""),
                deletedAt = deletedStr,
                forwards = stats?.optInt("forwards", 0) ?: 0,
                blocks = stats?.optInt("blocks", 0) ?: 0,
                replies = stats?.optInt("replies", 0) ?: 0,
                sends = stats?.optInt("sends", 0) ?: 0,
                isCustomDomain = obj.optBoolean("is_custom_domain", false),
                isDomainVerified = domVer,
                isDomainEnabled = obj.optBoolean("is_domain_enabled", true),
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

@Immutable
data class UiRecipient(
    val id: String,
    val createdAt: String,
    val email: String,
    val isActive: Boolean,
    val pgpKey: String,
    val pgpEnabled: Boolean,
    val pgpInline: Boolean
) {
    val hasPgpKey: Boolean
        get() = pgpKey.isNotBlank() && pgpKey != "null"

    companion object {
        fun fromJson(obj: JSONObject): UiRecipient {
            val rawKey = obj.opt("pgp_key")
            val keyStr = if (rawKey == null || rawKey == JSONObject.NULL) "" else rawKey.toString()
            return UiRecipient(
                id = obj.optString("id"),
                createdAt = obj.optString("created_at", ""),
                email = obj.optString("email"),
                isActive = obj.optBoolean("is_active", true),
                pgpKey = keyStr,
                pgpEnabled = obj.optBoolean("pgp_enabled", false),
                pgpInline = obj.optBoolean("pgp_inline", false)
            )
        }

        fun listFromJsonArray(arr: JSONArray?): List<UiRecipient> {
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

@Immutable
data class UiCustomDomain(
    val id: String,
    val createdAt: String,
    val name: String,
    val description: String,
    val recipient: String,
    val fromName: String,
    val enabled: Boolean,
    val catchAll: Boolean,
    val createAlias: Boolean,
    val ownerVerifiedAt: String,
    val mxVerifiedAt: String,
    val sendVerifiedAt: String
) {
    val isOwnerVerified: Boolean
        get() = ownerVerifiedAt.isNotBlank() && ownerVerifiedAt != "null"

    val isMxVerified: Boolean
        get() = mxVerifiedAt.isNotBlank() && mxVerifiedAt != "null"

    val isSendVerified: Boolean
        get() = sendVerifiedAt.isNotBlank() && sendVerifiedAt != "null"

    val isFullyVerified: Boolean
        get() = isOwnerVerified && isMxVerified && isSendVerified

    companion object {
        private fun cleanNull(obj: JSONObject, key: String): String {
            val v = obj.opt(key)
            return if (v == null || v == JSONObject.NULL) "" else v.toString()
        }

        fun fromJson(obj: JSONObject): UiCustomDomain = UiCustomDomain(
            id = obj.optString("id"),
            createdAt = cleanNull(obj, "created_at"),
            name = obj.optString("name"),
            description = cleanNull(obj, "description"),
            recipient = cleanNull(obj, "recipient"),
            fromName = cleanNull(obj, "from_name"),
            enabled = obj.optBoolean("enabled", true),
            catchAll = obj.optBoolean("catch_all", false),
            createAlias = obj.optBoolean("create_alias", false),
            ownerVerifiedAt = cleanNull(obj, "owner_verified_at"),
            mxVerifiedAt = cleanNull(obj, "mx_verified_at"),
            sendVerifiedAt = cleanNull(obj, "send_verified_at")
        )

        fun listFromJsonArray(arr: JSONArray?): List<UiCustomDomain> {
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

@Immutable
data class UiDnsConfig(
    val verify: String = "",
    val domain: String = "mailx.net",
    val dkimSelectors: List<String> = listOf("mx1", "mx2", "mx3"),
    val mxHosts: List<String> = listOf("mx1.mailx.net", "mx2.mailx.net")
) {
    companion object {
        fun fromJson(obj: JSONObject?): UiDnsConfig {
            if (obj == null) return UiDnsConfig()
            val dkim = mutableListOf<String>()
            obj.optJSONArray("dkim_selectors")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotEmpty()) dkim.add(s)
                }
            }
            val mx = mutableListOf<String>()
            obj.optJSONArray("mx_hosts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotEmpty()) mx.add(s)
                }
            }
            return UiDnsConfig(
                verify = obj.optString("verify", ""),
                domain = obj.optString("domain", "mailx.net").ifEmpty { "mailx.net" },
                dkimSelectors = if (dkim.isEmpty()) listOf("mx1", "mx2", "mx3") else dkim,
                mxHosts = if (mx.isEmpty()) listOf("mx1.mailx.net", "mx2.mailx.net") else mx
            )
        }
    }
}

@Immutable
data class UiDnsCheckResult(
    val name: String,
    val passed: Boolean,
    val error: String
)

@Immutable
data class UiWildcardDomainInfo(
    val count: Int = 0,
    val limit: Int = 2,
    val delimitersUsed: List<String> = emptyList()
) {
    val isAtLimit: Boolean
        get() = count >= limit

    companion object {
        fun fromJson(obj: JSONObject?): UiWildcardDomainInfo {
            if (obj == null) return UiWildcardDomainInfo()
            val used = mutableListOf<String>()
            obj.optJSONArray("delimiters_used")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val d = arr.optString(i).trim()
                    if (d.isNotEmpty()) used.add(d)
                }
            }
            return UiWildcardDomainInfo(
                count = obj.optInt("count", 0),
                limit = obj.optInt("limit", 2).coerceAtLeast(1),
                delimitersUsed = used
            )
        }
    }
}

@Immutable
data class UiBlocklistItem(
    val id: String,
    val createdAt: String,
    val type: String,
    val value: String,
    val blocked: Int
) {
    companion object {
        fun fromJson(obj: JSONObject): UiBlocklistItem = UiBlocklistItem(
            id = obj.optString("id"),
            createdAt = obj.optString("created_at", ""),
            type = obj.optString("type", "email"),
            value = obj.optString("value", ""),
            blocked = obj.optInt("blocked", 0)
        )

        fun listFromJsonArray(arr: JSONArray?): List<UiBlocklistItem> {
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

@Immutable
data class UiRuleCondition(
    val type: String = "sender",
    val match: String = "contains",
    val values: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        val isBool = MailxRuleCatalog.booleanConditionTypes.contains(type)
        put("type", type)
        put("match", if (isBool) "" else match)
        val arr = JSONArray()
        if (!isBool) {
            values.forEach { arr.put(it) }
        }
        put("values", arr)
    }

    companion object {
        fun fromJson(obj: JSONObject): UiRuleCondition {
            val vals = mutableListOf<String>()
            obj.optJSONArray("values")?.let { arr ->
                for (i in 0 until arr.length()) {
                    vals.add(arr.optString(i, ""))
                }
            }
            return UiRuleCondition(
                type = obj.optString("type", "sender"),
                match = obj.optString("match", "contains"),
                values = vals
            )
        }
    }
}

@Immutable
data class UiRuleAction(
    val type: String = "block",
    val value: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        val needsVal = MailxRuleCatalog.textValueActions.contains(type) || type == "forward_to"
        if (needsVal && value.isNotBlank()) {
            put("value", value.trim())
        } else {
            put("value", "")
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): UiRuleAction = UiRuleAction(
            type = obj.optString("type", "block"),
            value = obj.optString("value", "")
        )
    }
}

@Immutable
data class UiRule(
    val id: String,
    val createdAt: String,
    val name: String,
    val order: Int,
    val operator: String,
    val conditions: List<UiRuleCondition>,
    val actions: List<UiRuleAction>,
    val forwards: Boolean,
    val replies: Boolean,
    val sends: Boolean,
    val active: Boolean,
    val applied: Int,
    val lastAppliedAt: String,
    val invalidRecipients: Boolean = false
) {
    companion object {
        fun fromJson(obj: JSONObject): UiRule {
            val conds = mutableListOf<UiRuleCondition>()
            obj.optJSONArray("conditions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { conds.add(UiRuleCondition.fromJson(it)) }
                }
            }
            val acts = mutableListOf<UiRuleAction>()
            obj.optJSONArray("actions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { acts.add(UiRuleAction.fromJson(it)) }
                }
            }
            val lastRaw = obj.opt("last_applied_at")
            val lastStr = if (lastRaw == null || lastRaw == JSONObject.NULL) "" else lastRaw.toString()
            return UiRule(
                id = obj.optString("id"),
                createdAt = obj.optString("created_at", ""),
                name = obj.optString("name", ""),
                order = obj.optInt("order", 1),
                operator = obj.optString("operator", "AND"),
                conditions = conds,
                actions = acts,
                forwards = obj.optBoolean("forwards", true),
                replies = obj.optBoolean("replies", false),
                sends = obj.optBoolean("sends", false),
                active = obj.optBoolean("active", true),
                applied = obj.optInt("applied", 0),
                lastAppliedAt = lastStr,
                invalidRecipients = obj.optBoolean("invalid_recipients", false)
            )
        }

        fun listFromJsonArray(arr: JSONArray?): List<UiRule> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(fromJson(obj))
                }
            }.sortedBy { it.order }
        }
    }
}

@Immutable
data class UiDailyStatPoint(
    val dateIso: String,
    val label: String,
    val forwards: Int,
    val replies: Int,
    val sends: Int,
    val blocks: Int
) {
    val total: Int
        get() = forwards + replies + sends + blocks
}

@Immutable
data class UiUserStats(
    val forwards: Int = 0,
    val blocks: Int = 0,
    val replies: Int = 0,
    val sends: Int = 0,
    val aliases: Int = 0,
    val dailyPoints: List<UiDailyStatPoint> = emptyList()
)

@Immutable
data class UiDiagnosticLog(
    val id: String,
    val createdAt: String,
    val attemptedAt: String,
    val logType: String,
    val from: String,
    val destination: String,
    val message: String,
    val status: String,
    val remoteMta: String
) {
    companion object {
        fun fromJson(obj: JSONObject): UiDiagnosticLog = UiDiagnosticLog(
            id = obj.optString("id"),
            createdAt = obj.optString("created_at", ""),
            attemptedAt = obj.optString("attempted_at", ""),
            logType = obj.optString("log_type", ""),
            from = obj.optString("from", ""),
            destination = obj.optString("destination", ""),
            message = obj.optString("message", ""),
            status = obj.optString("status", ""),
            remoteMta = obj.optString("remote_mta", "")
        )

        fun listFromJsonArray(arr: JSONArray?): List<UiDiagnosticLog> {
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

@Immutable
data class UiAccessKey(
    val id: String,
    val createdAt: String,
    val expiresAt: String,
    val name: String
) {
    companion object {
        fun fromJson(obj: JSONObject): UiAccessKey {
            val expRaw = obj.opt("expires_at")
            val expStr = if (expRaw == null || expRaw == JSONObject.NULL) "" else expRaw.toString()
            return UiAccessKey(
                id = obj.optString("id"),
                createdAt = obj.optString("created_at", ""),
                expiresAt = expStr,
                name = obj.optString("name", "")
            )
        }

        fun listFromJsonArray(arr: JSONArray?): List<UiAccessKey> {
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

@Immutable
data class UiPasskeyCredential(
    val id: String,
    val createdAt: String
) {
    companion object {
        fun listFromJsonArray(arr: JSONArray?): List<UiPasskeyCredential> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        UiPasskeyCredential(
                            id = obj.optString("id", ""),
                            createdAt = obj.optString("created_at", "")
                        )
                    )
                }
            }
        }
    }
}

@Immutable
data class UiRemovedTracker(
    val domain: String,
    val element: String,
    val reason: String
) {
    val elementLabel: String
        get() = when (element) {
            "img" -> "Image"
            "srcset" -> "Image srcset"
            "background" -> "Background image"
            "link" -> "Prefetch link"
            else -> element.ifBlank { "Element" }
        }

    val reasonLabel: String
        get() = when (reason) {
            "domain" -> "Known tracker domain"
            "tiny" -> "Tiny image (1x1 pixel)"
            "hidden" -> "Hidden image"
            "pattern" -> "Tracking URL pattern"
            else -> reason.ifBlank { "Tracker" }
        }
}

@Immutable
data class UiTrackersReport(
    val total: Int,
    val trackers: List<UiRemovedTracker>
) {
    companion object {
        fun fromJson(obj: JSONObject?): UiTrackersReport? {
            if (obj == null) return null
            val arr = obj.optJSONArray("trackers")
            val list = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        add(
                            UiRemovedTracker(
                                domain = item.optString("domain", "Unknown").ifBlank { "Unknown" },
                                element = item.optString("element", ""),
                                reason = item.optString("reason", "")
                            )
                        )
                    }
                }
            }
            val total = maxOf(obj.optInt("total", list.size), list.size)
            return UiTrackersReport(total = total, trackers = list)
        }
    }
}

@Immutable
data class UiUserAccount(
    val id: String = "",
    val createdAt: String = "",
    val email: String = "",
    val isActive: Boolean = true,
    val totpEnabled: Boolean = false,
    val hasPassword: Boolean = true,
    val subStatus: String = "Active",
    val subActiveUntil: String = "",
    val subTier: String = "Pro",
    val totpSetupSecret: String = "",
    val totpSetupUri: String = "",
    val totpBackupCodes: String = "",
    val newlyCreatedAccessKeyToken: String = "",
    val deleteAccountOtp: String = ""
)

@Immutable
data class UiAnnouncement(
    val title: String,
    val body: String,
    val link: String
) {
    companion object {
        fun listFromJsonArray(arr: JSONArray?): List<UiAnnouncement> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        UiAnnouncement(
                            title = obj.optString("title", ""),
                            body = obj.optString("body", ""),
                            link = obj.optString("link", "")
                        )
                    )
                }
            }
        }
    }
}

enum class MainNavTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Overview", Icons.Outlined.Home, Icons.Default.Home),
    ALIASES("Aliases", Icons.Outlined.AlternateEmail, Icons.Default.AlternateEmail),
    RECIPIENTS("Recipients", Icons.Outlined.Inbox, Icons.Default.Inbox),
    RULES("Rules", Icons.Outlined.FilterAlt, Icons.Default.FilterAlt),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Default.Settings)
}

enum class AliasSortField(val key: String, val label: String) {
    CREATED_AT("created_at", "Date Created"),
    UPDATED_AT("updated_at", "Date Updated"),
    NAME("name", "Email Address (A-Z)"),
    FORWARDS("forwards", "Forwarded Count"),
    BLOCKS("blocks", "Blocked Count"),
    REPLIES("replies", "Replied Count"),
    SENDS("sends", "Sent Count")
}

@Immutable
data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val isWebSession: Boolean = false,
    val baseUrl: String = "https://api.mailx.net",
    val loginMode: String = "credentials",
    val emailInput: String = "",
    val passwordInput: String = "",
    val otpInput: String = "",
    val totpRequired: Boolean = false,
    val accessKeyInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@Immutable
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

@Immutable
data class DefaultsUiState(
    val defaultDomain: String = "mailx.net",
    val defaultRecipient: String = "",
    val defaultFormat: String = "words",
    val defaultFromName: String = "",
    val availableDomains: List<String> = listOf(
        "mailx.net",
        "zeropost.net",
        "mailshift.net",
        "layermail.net",
        "ambox.net",
        "spotsend.com"
    ),
    val availableRecipients: List<String> = emptyList(),
    val blockTrackers: Boolean = false,
    val removeHeader: Boolean = false,
    val logIssues: Boolean = false,
    val unsubscribeBehaviour: Int = 0
)

@Immutable
data class ThemeUiState(
    val darkModeMode: Int = 1,
    val dynamicColorEnabled: Boolean = false,
    val amoledDarkEnabled: Boolean = false
)

sealed interface ActiveSheet {
    data object None : ActiveSheet
    data object CreateAlias : ActiveSheet
    data object FilterOptions : ActiveSheet
    data object ProfileMenu : ActiveSheet
    data object AppearanceSheet : ActiveSheet
    data object DiagnosticsSheet : ActiveSheet
    data object AccountSheet : ActiveSheet
    data object AnnouncementsSheet : ActiveSheet
    data object TrackersReportSheet : ActiveSheet
    data class ManageAlias(val aliasId: String) : ActiveSheet
    data class WildcardGen(val alias: UiAlias) : ActiveSheet
    data class ReverseSend(val initialAlias: String) : ActiveSheet
    data class ConfirmDelete(val alias: UiAlias) : ActiveSheet
    data object ConfirmLogout : ActiveSheet
}

object MailxRuleCatalog {
    val conditionTypes = listOf(
        "sender" to "Sender email",
        "sender_domain" to "Sender domain",
        "sender_name" to "Sender name",
        "subject" to "Subject",
        "alias" to "Alias",
        "alias_description" to "Alias description",
        "destination" to "Destination email",
        "is_mailing_list" to "Email is from a mailing list / newsletter",
        "has_attachments" to "Email has attachments",
        "alias_is_wildcard" to "Alias is a wildcard alias"
    )

    val booleanConditionTypes = setOf("is_mailing_list", "has_attachments", "alias_is_wildcard")

    val matchTypes = listOf(
        "is" to "is exactly",
        "is_not" to "is not",
        "contains" to "contains",
        "not_contains" to "does not contain",
        "starts_with" to "starts with",
        "not_starts_with" to "does not start with",
        "ends_with" to "ends with",
        "not_ends_with" to "does not end with",
        "regex" to "matches regex",
        "not_regex" to "does not match regex"
    )

    val actionTypes = listOf(
        "block" to "Block the email",
        "blocklist_sender" to "Add sender email to the blocklist",
        "blocklist_domain" to "Add sender domain to the blocklist",
        "replace_subject" to "Replace the subject with",
        "set_display_name" to "Set the sender name to",
        "forward_to" to "Forward to recipient(s)",
        "disable_encryption" to "Turn off PGP encryption",
        "hide_banner" to "Hide the Mailx banner",
        "show_banner" to "Show the Mailx banner",
        "deactivate_alias" to "Deactivate the alias",
        "set_alias_description" to "Set the alias description to",
        "remove_attachments" to "Remove attachments",
        "stop" to "Stop processing more rules"
    )

    val textValueActions = setOf("replace_subject", "set_display_name", "set_alias_description")

    val unsubscribeBehaviours = listOf(
        0 to "Unsubscribe from sender; if that fails, deactivate alias",
        1 to "Deactivate alias",
        2 to "Delete alias",
        3 to "Block sender email address",
        4 to "Block sender domain"
    )

    fun conditionLabel(key: String): String =
        conditionTypes.find { it.first == key }?.second ?: key

    fun matchLabel(key: String): String =
        matchTypes.find { it.first == key }?.second ?: key

    fun actionLabel(key: String): String =
        actionTypes.find { it.first == key }?.second ?: key

    fun unsubscribeLabel(code: Int): String =
        unsubscribeBehaviours.find { it.first == code }?.second ?: unsubscribeBehaviours.first().second
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
        '.' to "Period", '@' to "At (@)", '-' to "Hyphen", '_' to "Underscore", '+' to "Plus", '*' to "Asterisk"
    )

    fun toPhoneticPairs(email: String): List<Pair<String, String>> {
        val nativePairs: List<Pair<String, String>>? = runCatching {
            val res = JSONObject(MailxRustBridge.nativeEncodeNatoPhonetic(email))
            val arr = res.optJSONArray("data")
            if (res.optBoolean("ok", false) && arr != null && arr.length() > 0) {
                val list = ArrayList<Pair<String, String>>(arr.length())
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)
                    if (item != null) {
                        val sym: String = item.optString("symbol", "")
                        val wrd: String = item.optString("word", "")
                        list.add(Pair(sym, wrd))
                    }
                }
                list
            } else {
                null
            }
        }.getOrNull()
        return nativePairs ?: email.map { ch -> ch.toString() to (map[ch.uppercaseChar()] ?: ch.toString()) }
    }
}

fun formatIsoDateShort(raw: String): String {
    val clean = raw.trim()
    if (clean.isEmpty() || clean == "null") return "Unknown"
    val datePart = clean.substringBefore("T")
    val timePart = clean.substringAfter("T", "").take(5)
    val parts = datePart.split("-")
    return if (parts.size == 3) {
        val d = "${parts[2]}.${parts[1]}.${parts[0]}"
        if (timePart.isNotEmpty()) "$d $timePart" else d
    } else {
        clean.take(16)
    }
}

fun formatAliasTypeLabel(formatKey: String): String = when (formatKey) {
    "words" -> "Words (e.g. quiet.haze16)"
    "random" -> "Random (e.g. uf1h0xi)"
    "uuid" -> "UUID"
    "wildcard" -> "Wildcard (*)"
    "custom" -> "Custom"
    else -> formatKey
}

fun copyToClipboard(context: Context, label: String, text: String, toastMessage: String? = null) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toastMessage ?: "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
