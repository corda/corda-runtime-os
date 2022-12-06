package net.corda.crypto.core.aes

import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.cipher.suite.schemes.KeyScheme
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Provider
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertNotSame

class WrappingKeyTests {
    companion object {
        lateinit var provider: Provider
        lateinit var schemeMetadata: CipherSchemeMetadata

        @BeforeAll
        @JvmStatic
        fun setup() {
            provider = BouncyCastleProvider()
            schemeMetadata = mock {
                on { findKeyFactory(any()) } doAnswer {
                    val scheme = it.getArgument<KeyScheme>(0)
                    KeyFactory.getInstance(scheme.algorithmName, provider)
                }
                on { findKeyScheme(any<AlgorithmIdentifier>()) } doAnswer {
                    val id = it.getArgument<AlgorithmIdentifier>(0)
                    if(ECDSA_SECP256R1_TEMPLATE.algorithmOIDs.contains(id)) {
                        ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
                    } else {
                        @Suppress("TooGenericExceptionThrown")
                        throw Exception()
                    }
                }
            }
        }

        private fun generateEcKeyPair(): KeyPair {
            val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
            keyPairGenerator.initialize(
                ECNamedCurveTable.getParameterSpec("secp256r1"),
                schemeMetadata.secureRandom
            )
            return keyPairGenerator.generateKeyPair()
        }
    }

    @Test
    fun `Should fail to derive for blank passphrase`() {
        assertThrows<IllegalArgumentException> {
            WrappingKey.derive (schemeMetadata,"", UUID.randomUUID().toString())
        }
    }

    @Test
    fun `Should fail to derive for blank salt`() {
        assertThrows<IllegalArgumentException> {
            WrappingKey.derive(schemeMetadata, UUID.randomUUID().toString(), "")
        }
    }

    @Test
    fun `Should be equal for the same secret key`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val key1 = WrappingKey(AesKey(key = secretKey), schemeMetadata)
        val key2 = WrappingKey(AesKey(key = secretKey), schemeMetadata)
        assertEquals(key1, key2)
    }

    @Test
    fun `Should be equal to itself`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val key = WrappingKey(AesKey(key = secretKey), schemeMetadata)
        assertEquals(key, key)
    }

    @Test
    fun `Should not be equal for the different secret keys`() {
        val encoded1 = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey1 = SecretKeySpec(encoded1, AES_KEY_ALGORITHM)
        val encoded2 = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey2 = SecretKeySpec(encoded2, AES_KEY_ALGORITHM)
        val key1 = WrappingKey(AesKey(key = secretKey1), schemeMetadata)
        val key2 = WrappingKey(AesKey(key = secretKey2), schemeMetadata)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `Should not be equal to the object of different type`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val key = WrappingKey(AesKey(key = secretKey), schemeMetadata)
        assertFalse(key.equals("Hello World!"))
    }

    @Test
    fun `Should return hash code of the secret key`() {
        val encoded = AesKey.encodePassPhrase(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val secretKey = SecretKeySpec(encoded, AES_KEY_ALGORITHM)
        val aesKey = AesKey(key = secretKey)
        val key = WrappingKey(aesKey, schemeMetadata)
        assertEquals(aesKey.hashCode(), key.hashCode())
    }

    @Test
    fun `Should generate different keys`() {
        val key1 = WrappingKey.generateWrappingKey(schemeMetadata)
        val key2 = WrappingKey.generateWrappingKey(schemeMetadata)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `Should derive same keys for the same passphrases and salts`() {
        val passphrase = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key1 = WrappingKey.derive(schemeMetadata, KeyCredentials(passphrase, salt))
        val key2 = WrappingKey.derive(schemeMetadata, passphrase, salt)
        assertEquals(key1, key2)
    }

    @Test
    fun `Should derive different keys for the different passphrases`() {
        val passphrase1 = UUID.randomUUID().toString()
        val passphrase2 = UUID.randomUUID().toString()
        val salt = UUID.randomUUID().toString()
        val key1 = WrappingKey.derive(schemeMetadata, passphrase1, salt)
        val key2 = WrappingKey.derive(schemeMetadata, passphrase2, salt)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `Should derive different keys for the different salts`() {
        val passphrase = UUID.randomUUID().toString()
        val salt1 = UUID.randomUUID().toString()
        val salt2 = UUID.randomUUID().toString()
        val key1 = WrappingKey.derive(schemeMetadata, passphrase, salt1)
        val key2 = WrappingKey.derive(schemeMetadata, passphrase, salt2)
        assertNotEquals(key1, key2)
    }

    @Test
    fun `Should wrap and unwrap other wrapping key`() {
        val master = WrappingKey.derive(schemeMetadata, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val other = WrappingKey.derive(schemeMetadata, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        assertNotEquals(master, other)
        val wrapped = master.wrap(other)
        val unwrapped = master.unwrapWrappingKey(wrapped)
        assertNotSame(other, unwrapped)
        assertEquals(other, unwrapped)
    }

    @Test
    fun `Should wrap and unwrap other private key`() {
        val master = WrappingKey.derive(schemeMetadata, UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val other = generateEcKeyPair().private
        assertNotEquals(master, other)
        val wrapped = master.wrap(other)
        val unwrapped = master.unwrap(wrapped)
        assertNotSame(other, unwrapped)
        assertEquals(other, unwrapped)
    }
}
