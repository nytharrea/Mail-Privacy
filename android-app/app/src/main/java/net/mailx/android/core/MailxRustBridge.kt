package net.mailx.android.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object MailxRustBridge {
    init {
        System.loadLibrary("mailx_core")
    }

    @JvmStatic
    external fun nativeValidateAccessKey(accessKey: String): String

    @JvmStatic
    external fun nativeGenerateReverseAlias(aliasEmail: String, targetEmail: String): String

    @JvmStatic
    external fun nativeGenerateWildcard(wildcardAlias: String, prefix: String): String

    @JvmStatic
    external fun nativeDecodeTrackersReport(rawInput: String): String

    @JvmStatic
    external fun nativeEncodeQrMatrix(payload: String): String

    @JvmStatic
    external fun nativeEncodeNatoPhonetic(email: String): String

    @JvmStatic
    external fun nativeGenerateRandomSuffix(length: Int): String

    @JvmStatic
    external fun nativeComputePrivacyPosture(
        forwards: Int,
        blocks: Int,
        activeAliases: Int,
        totalAliases: Int,
        pgpActive: Boolean,
        trackersBlocked: Boolean,
        headerStripped: Boolean,
        rulesActive: Int
    ): String

    @JvmStatic
    external fun nativeResolveLiquidMaterial(
        preset: String,
        tintArgb: Int,
        cornerRadiusPt: Float,
        widthPt: Float,
        heightPt: Float,
        transparency: Float,
        darkMode: Boolean,
        reduceTransparency: Boolean,
        increaseContrast: Boolean,
        backdropLuma: Float
    ): String

    @JvmStatic
    external fun nativeEvaluateNavBarLiquid(
        tabCount: Int,
        currentIndex: Int,
        targetIndex: Int,
        progress: Float,
        pointerXNorm: Float,
        isPressed: Boolean,
        spacingPt: Float,
        transparency: Float,
        darkMode: Boolean
    ): String

    @JvmStatic
    external fun nativeGetLiquidGlassAgslShader(): String

    @JvmStatic
    external fun nativeGetLiquidGlassWgslShader(): String

    @JvmStatic
    external fun nativeSanitizeTrackingUrls(rawText: String): String

    @JvmStatic
    external fun nativeAnalyzeEmailHeaders(rawHeaders: String): String

    @JvmStatic
    external fun nativeAuditAliasHygiene(aliasesJson: String): String

    @JvmStatic
    external fun nativeGenerateContextualAliases(serviceName: String, domain: String): String

    @JvmStatic
    external fun nativeAuthenticate(baseUrl: String, accessKey: String): String

    @JvmStatic
    external fun nativeLogin(
        baseUrl: String,
        email: String,
        password: String,
        otp: String
    ): String

    @JvmStatic
    external fun nativeApiRequest(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        method: String,
        path: String,
        bodyJson: String
    ): String

    @JvmStatic
    external fun nativeFetchDefaults(
        baseUrl: String,
        accessKey: String,
        bearerToken: String
    ): String

    @JvmStatic
    external fun nativeFetchAliases(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        search: String,
        status: String,
        wildcard: String,
        page: Int
    ): String

    @JvmStatic
    external fun nativeCreateAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        requestJson: String
    ): String

    @JvmStatic
    external fun nativeUpdateAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String,
        requestJson: String
    ): String

    @JvmStatic
    external fun nativePinAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String,
        pinned: Boolean
    ): String

    @JvmStatic
    external fun nativeDeleteAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String
    ): String

    @JvmStatic
    external fun nativeLogout(
        baseUrl: String,
        bearerToken: String
    ): String

    suspend fun authenticate(baseUrl: String, accessKey: String): JSONObject =
        withContext(Dispatchers.IO) {
            JSONObject(nativeAuthenticate(baseUrl, accessKey))
        }

    suspend fun login(
        baseUrl: String,
        email: String,
        password: String,
        otp: String = ""
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(nativeLogin(baseUrl, email, password, otp))
    }

    suspend fun apiRequest(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        method: String,
        path: String,
        bodyJson: String = ""
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(nativeApiRequest(baseUrl, accessKey, bearerToken, method, path, bodyJson))
    }

    suspend fun fetchDefaults(
        baseUrl: String,
        accessKey: String,
        bearerToken: String
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(nativeFetchDefaults(baseUrl, accessKey, bearerToken))
    }

    suspend fun fetchAliases(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        search: String = "",
        status: String = "active_inactive",
        wildcard: String = "",
        page: Int = 1
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(
            nativeFetchAliases(baseUrl, accessKey, bearerToken, search, status, wildcard, page)
        )
    }

    suspend fun createAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        requestJson: String
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(nativeCreateAlias(baseUrl, accessKey, bearerToken, requestJson))
    }

    suspend fun updateAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String,
        requestJson: String
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(nativeUpdateAlias(baseUrl, accessKey, bearerToken, aliasId, requestJson))
    }

    suspend fun pinAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String,
        pinned: Boolean
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(nativePinAlias(baseUrl, accessKey, bearerToken, aliasId, pinned))
    }

    suspend fun deleteAlias(
        baseUrl: String,
        accessKey: String,
        bearerToken: String,
        aliasId: String
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(nativeDeleteAlias(baseUrl, accessKey, bearerToken, aliasId))
    }

    suspend fun logout(
        baseUrl: String,
        bearerToken: String
    ): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(nativeLogout(baseUrl, bearerToken))
    }
}
