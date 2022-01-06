package net.corda.crypto

import net.corda.crypto.PasswordEncodeUtils.encodePassPhrase
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

class EncryptorTests {
    @Test
    fun `Should successfully encrypt and then decrypt zero length data`() {
        val data = ByteArray(0)
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val encryptor = Encryptor.derive(passphrase, salt)
        (0 until 100).forEach { _ ->
            val encrypted = encryptor.encrypt(data)
            val decrypted = encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }
    @Test
    fun `Should successfully encrypt and then decrypt 1 byte length data`() {
        val data = byteArrayOf(33)
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val encryptor = Encryptor.derive(passphrase, salt)
        (0 until 100).forEach { _ ->
            val encrypted = encryptor.encrypt(data)
            val decrypted = encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should successfully encrypt and then decrypt same data multiple times`() {
        val data = "Hello World!".toByteArray()
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val encryptor = Encryptor.derive(passphrase, salt)
        (0 until 100).forEach { _ ->
            val encrypted = encryptor.encrypt(data)
            val decrypted = encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should successfully encrypt and then decrypt different data multiple times`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val encryptor = Encryptor.derive(passphrase, salt)
        val random = Random(Instant.now().toEpochMilli())
        (0 until 100).forEach { _ ->
            val data = random.nextBytes(random.nextInt(1, 193))
            val encrypted = encryptor.encrypt(data)
            val decrypted = encryptor.decrypt(encrypted)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should always generate same key for the same passphrase and salt and use successfully multiple times`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val encryptor1 = Encryptor.derive(passphrase, salt)
        val encryptor2 = Encryptor.derive(passphrase, salt)
        val random = Random(Instant.now().toEpochMilli())
        (0 until 300).forEach { _ ->
            val data = random.nextBytes(random.nextInt(1, 193))
            val encrypted = encryptor1.encrypt(data)
            val decrypted = encryptor2.decrypt(encrypted)
            assertEquals(encryptor1, encryptor2)
            assertFalse(data.contentEquals(encrypted))
            assertArrayEquals(data, decrypted)
        }
    }

    @Test
    fun `Should generate different key for the different passphrase`() {
        val passphrase1 = UUID.randomUUID().toString()
        val passphrase2 = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val encryptor1 = Encryptor.derive(passphrase1, salt)
        val encryptor2 = Encryptor.derive(passphrase2, salt)
        assertNotEquals(encryptor1, encryptor2)
    }

    @Test
    fun `Should generate different key for the different salt`() {
        val passphrase = UUID.randomUUID().toString()
        val salt1 = UUID.randomUUID().toString()
        val salt2 = UUID.randomUUID().toString()
        val encryptor1 = Encryptor.derive(passphrase, salt1)
        val encryptor2 = Encryptor.derive(passphrase, salt2)
        assertNotEquals(encryptor1, encryptor2)
    }

    @Test
    fun `Should return hash code of the secret key`() {
        val encoded = encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, Encryptor.WRAPPING_KEY_ALGORITHM)
        val encryptor = Encryptor(
            key = secretKey
        )
        assertEquals(secretKey.hashCode(), encryptor.hashCode())
    }

    @Test
    fun `Should fail to derive for blank passphrase`() {
        assertThrows<IllegalArgumentException> {
            Encryptor.derive("", UUID.randomUUID().toString())
        }
    }

    @Test
    fun `Should fail to derive for blank salt`() {
        assertThrows<IllegalArgumentException> {
            Encryptor.derive(UUID.randomUUID().toString(), "")
        }
    }
}