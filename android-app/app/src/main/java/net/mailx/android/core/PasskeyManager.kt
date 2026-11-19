package net.mailx.android.core

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import org.json.JSONObject

object PasskeyManager {

    fun extractPublicKeyJson(optionsWrapper: JSONObject): String {
        val pk = optionsWrapper.optJSONObject("publicKey") ?: optionsWrapper
        return pk.toString()
    }

    suspend fun requestPasskeyAssertion(
        activityContext: Context,
        optionsWrapper: JSONObject
    ): Result<String> {
        return runCatching {
            val requestJson = extractPublicKeyJson(optionsWrapper)
            validateRpId(requestJson, activityContext)
            val credentialManager = CredentialManager.create(activityContext)
            val option = GetPublicKeyCredentialOption(requestJson = requestJson)
            val request = GetCredentialRequest(listOf(option))
            val result = credentialManager.getCredential(
                context = activityContext,
                request = request
            )
            val cred = result.credential
            if (cred is PublicKeyCredential) {
                cred.authenticationResponseJson
            } else {
                throw IllegalStateException("Unexpected credential type returned by system")
            }
        }.recoverCatching { err ->
            val rpId = (optionsWrapper.optJSONObject("publicKey") ?: optionsWrapper).let { pk ->
                pk.optString("rpId").ifBlank { pk.optJSONObject("rp")?.optString("id").orEmpty() }
            }
            val hint = if (rpId.isBlank()) "" else " RP ID: $rpId. Verify https://$rpId/.well-known/assetlinks.json."
            throw IllegalStateException(formatPasskeyError(err) + hint)
        }
    }

    suspend fun createPasskeyCredential(
        activityContext: Context,
        optionsWrapper: JSONObject
    ): Result<String> {
        return runCatching {
            val requestJson = extractPublicKeyJson(optionsWrapper)
            validateRpId(requestJson, activityContext)
            val credentialManager = CredentialManager.create(activityContext)
            val request = CreatePublicKeyCredentialRequest(requestJson = requestJson)
            val result = credentialManager.createCredential(
                context = activityContext,
                request = request
            )
            if (result is CreatePublicKeyCredentialResponse) {
                result.registrationResponseJson
            } else {
                throw IllegalStateException("Unexpected registration response from Credential Manager")
            }
        }.recoverCatching { err ->
            val rpId = (optionsWrapper.optJSONObject("publicKey") ?: optionsWrapper).let { pk ->
                pk.optString("rpId").ifBlank { pk.optJSONObject("rp")?.optString("id").orEmpty() }
            }
            val hint = if (rpId.isBlank()) "" else " RP ID: $rpId. Verify https://$rpId/.well-known/assetlinks.json."
            throw IllegalStateException(formatPasskeyError(err) + hint)
        }
    }

    /**
     * RP ID is server-controlled. Never rewrite it to the API hostname or the
     * Android package name: doing so invalidates WebAuthn verification.
     */
    private fun validateRpId(requestJson: String, context: Context) {
        val json = JSONObject(requestJson)
        val rpId = json.optString("rpId").ifBlank {
            json.optJSONObject("rp")?.optString("id").orEmpty()
        }
        if (rpId.isBlank()) return // rpId is optional for assertion requests.
        require(!rpId.contains("://") && !rpId.contains('/') && !rpId.contains(':')) {
            "Invalid passkey RP ID: expected a domain name, not a URL."
        }
        // Association with the APK's signing certificate is verified by the
        // credential provider using Digital Asset Links on the RP's domain.
    }

    private fun formatPasskeyError(err: Throwable): String {
        return when (err) {
            is GetCredentialCancellationException,
            is CreateCredentialCancellationException -> "Passkey prompt was cancelled."
            is NoCredentialException -> "No saved passkey found on this device for this domain."
            is GetCredentialException,
            is CreateCredentialException -> {
                val msg = err.message.orEmpty()
                if (msg.contains("assetlinks", ignoreCase = true) ||
                    msg.contains("RP ID", ignoreCase = true) ||
                    msg.contains("relying party", ignoreCase = true) ||
                    msg.contains("SecurityError", ignoreCase = true) ||
                    msg.contains("associated url", ignoreCase = true) ||
                    msg.contains("selected app", ignoreCase = true) ||
                    msg.contains("security reasons", ignoreCase = true)
                ) {
                    "Passkey app/domain mismatch. Check the server RP ID and https://<RP-ID>/.well-known/assetlinks.json for package net.mailx.android and the installed APK's signing SHA-256. 1Password blocks unverified associations."
                } else {
                    msg.ifBlank { "Passkey operation failed (${err.javaClass.simpleName})." }
                }
            }
            else -> err.message?.takeIf { it.isNotBlank() } ?: "Passkey operation failed."
        }
    }
}
