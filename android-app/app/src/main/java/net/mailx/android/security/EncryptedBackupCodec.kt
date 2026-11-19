package net.mailx.android.security

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected Mailx backup container.
 *
 * Format: magic(8) | version(1) | iterations(4) | salt(16) | nonce(12) | AES-GCM ciphertext.
 * The authenticated header prevents silent changes to format/version/KDF parameters.
 */
object EncryptedBackupCodec {
    private val MAGIC = byteArrayOf(0x4d, 0x41, 0x49, 0x4c, 0x58, 0x42, 0x4b, 0x50) // MAILXBKP
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 310_000
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024

    fun encrypt(payload: JSONObject, password: CharArray): ByteArray {
        require(password.size >= 10) { "Backup password must contain at least 10 characters" }
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val header = ByteBuffer.allocate(MAGIC.size + 1 + 4 + SALT_SIZE + NONCE_SIZE)
            .put(MAGIC).put(VERSION).putInt(ITERATIONS).put(salt).put(nonce).array()
        val key = deriveKey(password, salt, ITERATIONS)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        } finally {
            key.encoded.fill(0)
        }
    }

    fun decrypt(container: ByteArray, password: CharArray): JSONObject {
        require(container.size in (MAGIC.size + 1 + 4 + SALT_SIZE + NONCE_SIZE + 16)..MAX_BACKUP_BYTES) {
            "Invalid or oversized backup"
        }
        val headerSize = MAGIC.size + 1 + 4 + SALT_SIZE + NONCE_SIZE
        val header = container.copyOfRange(0, headerSize)
        val buffer = ByteBuffer.wrap(header)
        val magic = ByteArray(MAGIC.size).also(buffer::get)
        require(magic.contentEquals(MAGIC)) { "Not a Mailx backup" }
        val version = buffer.get()
        require(version == VERSION) { "Unsupported backup version" }
        val iterations = buffer.int
        require(iterations in 100_000..1_000_000) { "Invalid backup KDF parameters" }
        val salt = ByteArray(SALT_SIZE).also(buffer::get)
        val nonce = ByteArray(NONCE_SIZE).also(buffer::get)
        val key = deriveKey(password, salt, iterations)
        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            cipher.doFinal(container.copyOfRange(headerSize, container.size))
        } finally {
            key.encoded.fill(0)
        }
        return JSONObject(plaintext.toString(Charsets.UTF_8))
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }
}
