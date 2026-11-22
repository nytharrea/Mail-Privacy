package net.mailx.android.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mailx.android.core.uniffi.ApiCallResult
import net.mailx.android.core.uniffi.MailxCore
import net.mailx.android.core.uniffi.SecureStorePort
import net.mailx.android.core.uniffi.uniffiAnalyzeEmailHeaders
import net.mailx.android.core.uniffi.uniffiAnalyzeMtaBounce
import net.mailx.android.core.uniffi.uniffiAuditAliasHygiene
import net.mailx.android.core.uniffi.uniffiComputePrivacyPosture
import net.mailx.android.core.uniffi.uniffiComputeSevenDayTelemetry
import net.mailx.android.core.uniffi.uniffiDecodeTrackersReport
import net.mailx.android.core.uniffi.uniffiDetectAliasWatchEvents
import net.mailx.android.core.uniffi.uniffiEncodeNatoPhonetic
import net.mailx.android.core.uniffi.uniffiEncodeQrMatrix
import net.mailx.android.core.uniffi.uniffiEvaluatePhishingRisk
import net.mailx.android.core.uniffi.uniffiFilterAndSortAliases
import net.mailx.android.core.uniffi.uniffiFormatAliasesCsv
import net.mailx.android.core.uniffi.uniffiGenerateContextualAliases
import net.mailx.android.core.uniffi.uniffiGeneratePassphraseAndCandidate
import net.mailx.android.core.uniffi.uniffiGenerateRandomSuffix
import net.mailx.android.core.uniffi.uniffiGenerateReverseAlias
import net.mailx.android.core.uniffi.uniffiGenerateTotpToken
import net.mailx.android.core.uniffi.uniffiGenerateWildcard
import net.mailx.android.core.uniffi.uniffiGetSkeletonInfo
import net.mailx.android.core.uniffi.uniffiInspectOpenpgpKey
import net.mailx.android.core.uniffi.uniffiParseAliasesCsvImport
import net.mailx.android.core.uniffi.uniffiSanitizeTrackingUrls
import net.mailx.android.core.uniffi.uniffiSimulateRule
import net.mailx.android.core.uniffi.uniffiValidateAccessKey
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

object MailxRustBridge {

    private class EphemeralSessionStore(
        baseUrl: String,
        accessKey: String = "",
        bearerToken: String = ""
    ) : SecureStorePort {
        private val entries = ConcurrentHashMap<String, ByteArray>()

        init {
            if (baseUrl.isNotBlank()) {
                entries["base_url"] = baseUrl.trim().toByteArray(StandardCharsets.UTF_8)
            }
            if (accessKey.isNotBlank()) {
                entries["access_key"] = accessKey.trim().toByteArray(StandardCharsets.UTF_8)
            }
            if (bearerToken.isNotBlank()) {
                entries["bearer_token"] = bearerToken.trim().toByteArray(StandardCharsets.UTF_8)
            }
        }

        override fun get(key: String): ByteArray? = entries[key]?.copyOf()

        override fun put(key: String, value: ByteArray) {
            entries[key] = value.copyOf()
        }

        override fun delete(key: String) {
            entries.remove(key)
        }

        fun currentBearerToken(): String =
            entries["bearer_token"]?.let { String(it, StandardCharsets.UTF_8) }.orEmpty()
    }

    private fun ApiCallResult.toEnvelope(
        originalToken: String = "",
        currentToken: String = ""
    ): JSONObject = JSONObject().apply {
        put("ok", ok)
        if (ok && dataJson.isNotBlank()) {
            val trimmed = dataJson.trim()
            when {
                trimmed.startsWith("{") -> put("data", JSONObject(trimmed))
                trimmed.startsWith("[") -> put("data", JSONArray(trimmed))
                else -> put("data", trimmed)
            }
        }
        error?.let { put("error", it) }
        errorCode?.let { put("error_code", it) }
        if (currentToken.isNotEmpty() && currentToken != originalToken.trim()) {
            put("refreshed_token", currentToken)
        }
    }

    private suspend fun runWithSession(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        block: (MailxCore) -> ApiCallResult
    ): JSONObject = withContext(Dispatchers.IO) {
        val store = EphemeralSessionStore(baseUrl, accessKey, bearerToken)
        MailxCore().use { core ->
            core.attachSecureStore(store)
            val result = block(core)
            result.toEnvelope(
                originalToken = bearerToken,
                currentToken = store.currentBearerToken()
            )
        }
    }

    fun nativeDetectAliasWatchEvents(
        previousAliasesJson: String,
        currentAliasesJson: String,
        watchedIdsJson: String
    ): String = uniffiDetectAliasWatchEvents(
        previousAliasesJson,
        currentAliasesJson,
        watchedIdsJson
    )

    fun nativeValidateAccessKey(accessKey: String): String =
        uniffiValidateAccessKey(accessKey)

    fun nativeGenerateReverseAlias(aliasEmail: String, targetEmail: String): String =
        uniffiGenerateReverseAlias(aliasEmail, targetEmail)

    fun nativeGenerateWildcard(wildcardAlias: String, prefix: String): String =
        uniffiGenerateWildcard(wildcardAlias, prefix)

    fun nativeDecodeTrackersReport(rawInput: String): String =
        uniffiDecodeTrackersReport(rawInput)

    fun nativeEncodeQrMatrix(payload: String): String =
        uniffiEncodeQrMatrix(payload)

    fun nativeEncodeNatoPhonetic(email: String): String =
        uniffiEncodeNatoPhonetic(email)

    fun nativeGenerateRandomSuffix(length: Int): String =
        uniffiGenerateRandomSuffix(length)

    fun nativeComputePrivacyPosture(
        forwards: Int,
        blocks: Int,
        activeAliases: Int,
        totalAliases: Int,
        pgpActive: Boolean,
        trackersBlocked: Boolean,
        headerStripped: Boolean,
        rulesActive: Int
    ): String = uniffiComputePrivacyPosture(
        forwards,
        blocks,
        activeAliases,
        totalAliases,
        pgpActive,
        trackersBlocked,
        headerStripped,
        rulesActive
    )

    fun nativeFilterAndSortAliases(
        aliasesJson: String,
        searchQuery: String,
        selectedChip: String,
        sortField: String,
        sortDescending: Boolean,
        pinnedFirst: Boolean
    ): String = uniffiFilterAndSortAliases(
        aliasesJson,
        searchQuery,
        selectedChip,
        sortField,
        sortDescending,
        pinnedFirst
    )

    fun nativeComputeSevenDayTelemetry(
        rawStatsJson: String,
        aliasesJson: String,
        referenceEpochSecs: Long
    ): String = uniffiComputeSevenDayTelemetry(
        rawStatsJson,
        aliasesJson,
        referenceEpochSecs
    )

    fun nativeFormatAliasesCsv(aliasesJson: String): String =
        uniffiFormatAliasesCsv(aliasesJson)

    fun nativeParseAliasesCsvImport(
        csvText: String,
        customDomainsJson: String,
        defaultRecipient: String
    ): String = uniffiParseAliasesCsvImport(
        csvText,
        customDomainsJson,
        defaultRecipient
    )

    fun nativeGetSkeletonInfo(): String = uniffiGetSkeletonInfo()

    fun nativeSanitizeTrackingUrls(rawText: String): String =
        uniffiSanitizeTrackingUrls(rawText)

    fun nativeAnalyzeEmailHeaders(rawHeaders: String): String =
        uniffiAnalyzeEmailHeaders(rawHeaders)

    fun nativeGenerateContextualAliases(
        serviceInput: String,
        domain: String,
        count: Int
    ): String = uniffiGenerateContextualAliases(serviceInput, domain, count)

    fun nativeAuditAliasHygiene(aliasesJson: String): String =
        uniffiAuditAliasHygiene(aliasesJson)

    fun nativeInspectOpenPgpKey(armoredKey: String): String =
        uniffiInspectOpenpgpKey(armoredKey)

    fun nativeEvaluatePhishingRisk(
        fromHeader: String,
        replyToHeader: String,
        returnPathHeader: String
    ): String = uniffiEvaluatePhishingRisk(fromHeader, replyToHeader, returnPathHeader)

    fun nativeSimulateRule(
        ruleJson: String,
        emailContextJson: String
    ): String = uniffiSimulateRule(ruleJson, emailContextJson)

    fun nativeAnalyzeMtaBounce(
        logType: String,
        statusText: String,
        messageText: String,
        remoteMta: String,
        rawBounce: String
    ): String = uniffiAnalyzeMtaBounce(
        logType,
        statusText,
        messageText,
        remoteMta,
        rawBounce
    )

    fun nativeGenerateTotpToken(
        base32Secret: String,
        unixSeconds: Long
    ): String = uniffiGenerateTotpToken(base32Secret, unixSeconds)

    fun nativeGeneratePassphraseAndCandidate(
        format: String,
        domain: String,
        wordCount: Int
    ): String = uniffiGeneratePassphraseAndCandidate(format, domain, wordCount)

    suspend fun authenticate(baseUrl: String, accessKey: String): JSONObject =
        runWithSession(baseUrl, "", "") { core ->
            core.loginWithAccessKey(baseUrl, accessKey)
        }

    suspend fun login(
        baseUrl: String,
        email: String,
        password: String,
        otp: String = ""
    ): JSONObject = runWithSession(baseUrl, "", "") { core ->
        core.loginWithPassword(baseUrl, email, password, otp)
    }

    suspend fun passkeyLoginBegin(
        baseUrl: String,
        email: String = ""
    ): JSONObject = withContext(Dispatchers.IO) {
        MailxCore().use { core ->
            core.passkeyLoginBegin(baseUrl, email).toEnvelope()
        }
    }

    suspend fun passkeyLoginFinish(
        baseUrl: String,
        ceremonyCookie: String,
        credentialJson: String,
        discoverable: Boolean,
        fallbackEmail: String = ""
    ): JSONObject = runWithSession(baseUrl, "", "") { core ->
        core.passkeyLoginFinish(
            baseUrl,
            ceremonyCookie,
            credentialJson,
            discoverable,
            fallbackEmail
        )
    }

    suspend fun passkeyRegisterBegin(
        baseUrl: String,
        bearerToken: String
    ): JSONObject = runWithSession(baseUrl, "", bearerToken) { core ->
        core.passkeyRegisterBegin()
    }

    suspend fun passkeyRegisterFinish(
        baseUrl: String,
        bearerToken: String,
        ceremonyCookie: String,
        credentialJson: String
    ): JSONObject = runWithSession(baseUrl, "", bearerToken) { core ->
        core.passkeyRegisterFinish(ceremonyCookie, credentialJson)
    }

    suspend fun apiRequest(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        method: String,
        path: String,
        bodyJson: String = ""
    ): JSONObject = runWithSession(baseUrl, accessKey, bearerToken) { core ->
        core.apiRequest(method, path, bodyJson)
    }

    suspend fun importAliasesCsv(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        csvText: String
    ): JSONObject = runWithSession(baseUrl, accessKey, bearerToken) { core ->
        core.importAliasesCsv(csvText)
    }

    suspend fun fetchDefaults(
        baseUrl: String,
        accessKey: String,
        bearerToken: String
    ): JSONObject = runWithSession(baseUrl, accessKey, bearerToken) { core ->
        core.fetchDefaults()
    }

    suspend fun fetchAliases(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        search: String = "",
        status: String = "active_inactive",
        wildcard: String = "",
        page: Int = 1
    ): JSONObject = runWithSession(baseUrl, accessKey, bearerToken) { core ->
        core.fetchAliases(search, status, wildcard, page.coerceAtLeast(1).toUInt())
    }

    suspend fun createAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        requestJson: String
    ): JSONObject = runWithSession(baseUrl, accessKey, bearerToken) { core ->
        core.createAlias(requestJson)
    }

    suspend fun updateAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String,
        requestJson: String
    ): JSONObject = runWithSession(baseUrl, accessKey, bearerToken) { core ->
        core.updateAlias(aliasId, requestJson)
    }

    suspend fun pinAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String,
        pinned: Boolean
    ): JSONObject = runWithSession(baseUrl, accessKey, bearerToken) { core ->
        core.pinAlias(aliasId, pinned)
    }

    suspend fun deleteAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String
    ): JSONObject = runWithSession(baseUrl, accessKey, bearerToken) { core ->
        core.deleteAlias(aliasId)
    }

    suspend fun logout(
        baseUrl: String,
        bearerToken: String
    ): JSONObject = runWithSession(baseUrl, "", bearerToken) { core ->
        core.logoutSession()
    }
}
