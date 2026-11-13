package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.security.DecryptResult
import com.example.data.security.KeystoreSecretCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreSecretCipherInstrumentedTest {

  @Test
  fun encryptAndDecryptRoundTripWithAndroidKeystore() {
    val cipher = KeystoreSecretCipher(keyAlias = "feldbuch_test_keystore_alias")
    val secret = "ghp_realDeviceKeystoreRoundTrip123"
    val encrypted = cipher.encrypt(secret)
    assertTrue(encrypted.startsWith("v1:"))
    assertNotEquals(secret, encrypted)

    val decrypted = cipher.decrypt(encrypted)
    assertEquals(DecryptResult.Success(secret), decrypted)
  }
}
