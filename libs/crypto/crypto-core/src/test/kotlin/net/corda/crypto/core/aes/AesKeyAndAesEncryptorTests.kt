package net.corda.crypto.core.aes

import net.corda.crypto.core.ManagedSecret
import net.corda.test.util.createTestCase
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

@Suppress("ForEachOnRange")
class AesKeyAndAesEncryptorTests {
    @Test
    fun `Should successfully encrypt and then decrypt zero length data`() {
        val data = ByteArray(0)
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key = AesKey.derive(passphrase, salt)
        (0 until 10).forEach { _ ->
            val encrypted = key.encryptor.encrypt(data)
            val decrypted = key.encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should successfully encrypt and then decrypt 1 byte length data`() {
        val data = byteArrayOf(33)
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key = AesKey.derive(passphrase, salt)
        (0 until 10).forEach { _ ->
            val encrypted = key.encryptor.encrypt(data)
            val decrypted = key.encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should successfully encrypt and then decrypt same data multiple times`() {
        val data = "Hello World!".toByteArray()
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key = AesKey.derive(passphrase, salt)
        (0 until 10).forEach { _ ->
            val encrypted = key.encryptor.encrypt(data)
            val decrypted = key.encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should successfully generate new AesKey and then use provided encryptor`() {
        val key = AesKey.generate()
        val random = Random(Instant.now().toEpochMilli())
        (0 until 10).forEach { _ ->
            val data = random.nextBytes(random.nextInt(1, 193))
            val encrypted = key.encryptor.encrypt(data)
            val decrypted = key.encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should successfully encrypt and then decrypt concurrently`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key = AesKey.derive(passphrase, salt)
        (1..100).createTestCase {
            val random = Random(Instant.now().toEpochMilli())
            val data = random.nextBytes(random.nextInt(1, 193))
            val encrypted = key.encryptor.encrypt(data)
            val decrypted = key.encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }.runAndValidate()
    }

    @Test
    fun `Should successfully encrypt and then decrypt different data multiple times`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key = AesKey.derive(passphrase, salt)
        val random = Random(Instant.now().toEpochMilli())
        (0 until 10).forEach { _ ->
            val data = random.nextBytes(random.nextInt(1, 193))
            val encrypted = key.encryptor.encrypt(data)
            val decrypted = key.encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should always derive same key for the same passphrase and salt and use successfully multiple times`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key1 = AesKey.derive(passphrase, salt)
        val key2 = AesKey.derive(passphrase, salt)
        val random = Random(Instant.now().toEpochMilli())
        (0 until 10).forEach { _ ->
            val data = random.nextBytes(random.nextInt(1, 193))
            val encrypted = key1.encryptor.encrypt(data)
            val decrypted = key2.encryptor.decrypt(encrypted)
            assertEquals(key1, key2)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should wrap and then unwrap AesKey and be able use it`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val master = AesKey.derive(passphrase, salt)
        val random = Random(Instant.now().toEpochMilli())
        (0 until 10).forEach { _ ->
            val data = random.nextBytes(random.nextInt(1, 193))
            val key = AesKey.derive(passphrase, salt)
            val encrypted = key.encryptor.encrypt(data)
            val wrapped = master.wrapKey(key)
            val unwrapped = master.unwrapKey(wrapped)
            val decrypted = unwrapped.encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should wrap and then unwrap ManagedSecret`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val master = AesKey.derive(passphrase, salt)
        (0 until 10).forEach { _ ->
            val secret = ManagedSecret.generate()
            val wrapped = master.wrapSecret(secret)
            val unwrapped = master.unwrapSecret(wrapped)
            assertArrayEquals(secret.secret, unwrapped.secret)
        }
    }

    @Test
    fun `Should derive different keys for the different passphrases`() {
        val passphrase1 = UUID.randomUUID().toString()
        val passphrase2 = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key1 = AesKey.derive(passphrase1, salt)
        val key2 = AesKey.derive(passphrase2, salt)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `Should derive different keys for the different salts`() {
        val passphrase = UUID.randomUUID().toString()
        val salt1 = UUID.randomUUID().toString()
        val salt2 = UUID.randomUUID().toString()
        val key1 = AesKey.derive(passphrase, salt1)
        val key2 = AesKey.derive(passphrase, salt2)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `AesKey should return hash code of the secret key`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val key = AesKey(key = secretKey)
        assertEquals(secretKey.hashCode(), key.hashCode())
    }

    @Test
    fun `AesKey should be equal for the same secret key`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val key1 = AesKey(key = secretKey)
        val key2 = AesKey(key = secretKey)
        assertEquals(key1, key2)
    }

    @Test
    fun `AesKey should be equal to itself`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val key = AesKey(key = secretKey)
        assertEquals(key, key)
    }

    @Test
    fun `AesKey should not be equal for the different secret keys`() {
        val encoded1 = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey1 = SecretKeySpec(encoded1, AES_KEY_ALGORITHM)
        val encoded2 = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey2 = SecretKeySpec(encoded2, AES_KEY_ALGORITHM)
        val key1 = AesKey(key = secretKey1)
        val key2 = AesKey(key = secretKey2)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `AesKey should not be equal to the object of different type`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val key = AesKey(key = secretKey)
        assertFalse(key.equals("Hello World!"))
    }

    @Test
    fun `AesEncryptor should return hash code of the secret key`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val key = AesKey(key = secretKey)
        val encryptor = AesEncryptor(key)
        assertEquals(key.hashCode(), encryptor.hashCode())
    }

    @Test
    fun `AesEncryptor should be equal for the same secret key`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val encryptor1 = AesEncryptor(AesKey(key = secretKey))
        val encryptor2 = AesEncryptor(AesKey(key = secretKey))
        assertEquals(encryptor1, encryptor2)
    }

    @Test
    fun `AesEncryptor should be equal to itself`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val encryptor = AesEncryptor(AesKey(key = secretKey))
        assertEquals(encryptor, encryptor)
    }

    @Test
    fun `AesEncryptor should not be equal for the different secret keys`() {
        val encoded1 = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey1 = SecretKeySpec(encoded1, AES_KEY_ALGORITHM)
        val encoded2 = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey2 = SecretKeySpec(encoded2, AES_KEY_ALGORITHM)
        val encryptor1 = AesEncryptor(AesKey(key = secretKey1))
        val encryptor2 = AesEncryptor(AesKey(key = secretKey2))
        assertNotEquals(encryptor1, encryptor2)
    }

    @Test
    fun `AesEncryptor should not be equal to the object of different type`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val encryptor = AesEncryptor(AesKey(key = secretKey))
        assertFalse(encryptor.equals("Hello World!"))
    }

    @Test
    fun `Should fail to derive for blank passphrase`() {
        assertThrows<IllegalArgumentException> {
            AesKey.derive("", UUID.randomUUID().toString())
        }
    }

    @Test
    fun `Should fail to derive for blank salt`() {
        assertThrows<IllegalArgumentException> {
            AesKey.derive(UUID.randomUUID().toString(), "")
        }
    }
}