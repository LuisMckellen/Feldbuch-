package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

sealed class DecryptResult {
  data class Success(val plaintext: String) : DecryptResult()

  data object Empty : DecryptResult()

  data class KeyInvalidated(val reason: String) : DecryptResult()
}

interface SecretCipher {
  fun encrypt(plaintext: String): String

  fun decrypt(encoded: String): DecryptResult
}

class KeystoreSecretCipher(
  private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SecretCipher {

  override fun encrypt(plaintext: String): String {
    if (plaintext.isEmpty()) return ""
    val key = getOrCreateKey()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
    val combined = ByteArray(iv.size + encrypted.size)
    System.arraycopy(iv, 0, combined, 0, iv.size)
    System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
    return PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
  }

  override fun decrypt(encoded: String): DecryptResult {
    if (encoded.isBlank()) return DecryptResult.Empty
    if (!encoded.startsWith(PREFIX)) {
      return DecryptResult.KeyInvalidated("Unrecognized ciphertext format")
    }
    val payload =
      try {
        Base64.decode(encoded.removePrefix(PREFIX), Base64.NO_WRAP)
      } catch (_: IllegalArgumentException) {
        return DecryptResult.KeyInvalidated("Corrupted ciphertext")
      }
    if (payload.size <= GCM_IV_LENGTH_BYTES) {
      return DecryptResult.KeyInvalidated("Truncated ciphertext")
    }

    val key =
      try {
        getExistingKey()
      } catch (_: KeyPermanentlyInvalidatedException) {
        deleteKeySilently()
        return DecryptResult.KeyInvalidated("Key permanently invalidated")
      } catch (_: GeneralSecurityException) {
        return DecryptResult.KeyInvalidated("Keystore key unavailable")
      } ?: return DecryptResult.KeyInvalidated("Keystore key missing")

    return try {
      val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
      val cipherBytes = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
      val plainBytes = cipher.doFinal(cipherBytes)
      DecryptResult.Success(String(plainBytes, StandardCharsets.UTF_8))
    } catch (_: KeyPermanentlyInvalidatedException) {
      deleteKeySilently()
      DecryptResult.KeyInvalidated("Key permanently invalidated")
    } catch (_: AEADBadTagException) {
      DecryptResult.KeyInvalidated("Secret authentication tag mismatch")
    } catch (_: GeneralSecurityException) {
      DecryptResult.KeyInvalidated("Unable to decrypt secret")
    }
  }

  private fun getExistingKey(): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
    return entry?.secretKey
  }

  private fun getOrCreateKey(): SecretKey {
    getExistingKey()?.let { return it }
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          keyAlias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setRandomizedEncryptionRequired(true)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  private fun deleteKeySilently() {
    try {
      val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
      if (keyStore.containsAlias(keyAlias)) {
        keyStore.deleteEntry(keyAlias)
      }
    } catch (_: Exception) {
      // Best-effort cleanup only; caller clears stored secret.
    }
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "v1:"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    const val DEFAULT_KEY_ALIAS = "feldbuch_secrets_aes_gcm"
  }
}
