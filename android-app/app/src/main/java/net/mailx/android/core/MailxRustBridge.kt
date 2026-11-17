package net.mailx.android.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * `libmailx_core.so` (Rust çekirdeği) ile doğrudan JNI üzerinden konuşan tekil köprü.
 */
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
    external fun nativeAuthenticate(baseUrl: String, accessKey: String): String

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

    suspend fun authenticate(baseUrl: String, accessKey: String): JSONObject =
        withContext(Dispatchers.IO) {
            JSONObject(nativeAuthenticate(baseUrl, accessKey))
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
}
