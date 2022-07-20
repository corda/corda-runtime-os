package net.corda.crypto.service.impl.signing

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.persistence.signing.SigningKeyStatus
import net.corda.crypto.persistence.signing.SigningPublicKeySaveContext
import net.corda.crypto.persistence.signing.SigningWrappedKeySaveContext
import net.corda.crypto.service.CryptoServiceRef
import net.corda.v5.cipher.suite.CRYPTO_CATEGORY
import net.corda.v5.cipher.suite.CRYPTO_TENANT_ID
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.SharedSecretAliasSpec
import net.corda.v5.cipher.suite.SharedSecretWrappedSpec
import net.corda.v5.cipher.suite.SigningAliasSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CryptoServiceRefUtilsTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
        }
    }

    @Test
    fun `Should return supported schemes`() {
        val expectedResult = mapOf(
            schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME) to listOf(SignatureSpec.ECDSA_SHA256),
            schemeMetadata.findKeyScheme(RSA_CODE_NAME) to listOf(SignatureSpec.RSA_SHA256),
        )
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { supportedSchemes } doReturn expectedResult
            }
        )
        val result = ref.getSupportedSchemes()
        assertEquals(2, result.size)
        assertThat(result).containsAll(listOf(ECDSA_SECP256R1_CODE_NAME, RSA_CODE_NAME))
        Mockito.verify(ref.instance, times(1)).supportedSchemes
    }

    @Test
    fun `Should generate key`() {
        val expectedResult = mock<GeneratedKey>()
        val expectedAlias = UUID.randomUUID().toString()
        val context = mapOf(
            "customKey" to "customValue"
        )
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { generateKeyPair(any(), any()) } doReturn expectedResult
            }
        )
        val result = ref.generateKeyPair(
            alias = expectedAlias,
            scheme = scheme,
            context = context
        )
        assertSame(expectedResult, result)
        Mockito.verify(ref.instance, times(1)).generateKeyPair(
            argThat {
                            keyScheme == scheme &&
                            alias == expectedAlias &&
                            masterKeyAlias == ref.masterKeyAlias &&
                            secret.contentEquals(ref.aliasSecret)
            },
            argThat {
                size == 3 &&
                this[CRYPTO_TENANT_ID] == ref.tenantId &&
                this[CRYPTO_CATEGORY] == ref.category &&
                this["customKey"] == "customValue"
            }
        )
    }

    @Test
    fun `Should sign using key material when it's not null`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = mapOf(
            "customKey" to "customValue"
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            schemeCodeName = scheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = 11,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        val result = ref.sign(record, scheme, schemeMetadata.supportedSignatureSpec(scheme).first(), data, context)
        assertSame(expectedResult, result)
        Mockito.verify(ref.instance, times(1)).sign(
            argThat {
                this as SigningWrappedSpec
                keyScheme == scheme &&
                keyMaterialSpec.keyMaterial.contentEquals(record.keyMaterial) &&
                keyMaterialSpec.masterKeyAlias == ref.masterKeyAlias &&
                keyMaterialSpec.encodingVersion == record.encodingVersion
            },
            eq(data),
            argThat {
                        size == 2 &&
                        this[CRYPTO_TENANT_ID] == ref.tenantId &&
                        this["customKey"] == "customValue"
            }
        )
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using empty key material`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val data = UUID.randomUUID().toString().toByteArray()
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = ByteArray(0),
            schemeCodeName = scheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = 11,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        assertThrows<IllegalArgumentException> {
            ref.sign(record, scheme, schemeMetadata.supportedSignatureSpec(scheme).first(), data, context)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using non empty key material and null encodingVersion`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val data = UUID.randomUUID().toString().toByteArray()
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            schemeCodeName = scheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = null,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        assertThrows<IllegalArgumentException> {
            ref.sign(record, scheme, schemeMetadata.supportedSignatureSpec(scheme).first(), data, context)
        }
    }

    @Test
    fun `Should sign using alias when it's not null`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = mapOf(
            "customKey" to "customValue"
        )
        val data = UUID.randomUUID().toString().toByteArray()
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = null,
            schemeCodeName = scheme.codeName,
            masterKeyAlias = null,
            externalId = null,
            encodingVersion = null,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        val result = ref.sign(record, scheme, schemeMetadata.supportedSignatureSpec(scheme).first(), data, context)
        assertSame(expectedResult, result)
        Mockito.verify(ref.instance, times(1)).sign(
            argThat {
                this as SigningAliasSpec
                        keyScheme ==scheme &&
                        hsmAlias == record.hsmAlias
            },
            eq(data),
            argThat {
                size == 2 &&
                this[CRYPTO_TENANT_ID] == ref.tenantId &&
                this["customKey"] == "customValue"
            }
        )
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using alias and null hsm alias`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val data = UUID.randomUUID().toString().toByteArray()
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = UUID.randomUUID().toString(),
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = null,
            schemeCodeName = scheme.codeName,
            masterKeyAlias = null,
            externalId = null,
            encodingVersion = null,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        assertThrows<IllegalArgumentException> {
            ref.sign(record, scheme, schemeMetadata.supportedSignatureSpec(scheme).first(), data, context)
        }
    }

    @Test
    fun `Should derive using key material when it's not null`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = mapOf(
            "customKey" to "customValue"
        )
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { deriveSharedSecret(any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            schemeCodeName = scheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = 11,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        val originalOtherPublicKey = mock<PublicKey>()
        val result = ref.deriveSharedSecret(record, scheme, originalOtherPublicKey, context)
        assertSame(expectedResult, result)
        Mockito.verify(ref.instance, times(1)).deriveSharedSecret(
            argThat {
                this as SharedSecretWrappedSpec
                otherPublicKey == originalOtherPublicKey &&
                keyScheme == scheme &&
                keyMaterialSpec.keyMaterial.contentEquals(record.keyMaterial) &&
                keyMaterialSpec.masterKeyAlias == ref.masterKeyAlias &&
                keyMaterialSpec.encodingVersion == record.encodingVersion
            },
            argThat {
                size == 2 &&
                        this[CRYPTO_TENANT_ID] == ref.tenantId &&
                        this["customKey"] == "customValue"
            }
        )
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving using empty key material`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val keyPair = generateKeyPair(schemeMetadata, scheme.codeName)
        val otherPublicKey = generateKeyPair(schemeMetadata, scheme.codeName).public
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = keyPair.public.encoded,
            keyMaterial = ByteArray(0),
            schemeCodeName = scheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = 11,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        assertThrows<IllegalArgumentException> {
            ref.deriveSharedSecret(record, scheme, otherPublicKey, context)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving using non empty key material and null encodingVersion`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val keyPair = generateKeyPair(schemeMetadata, scheme.codeName)
        val otherPublicKey = generateKeyPair(schemeMetadata, scheme.codeName).public
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = keyPair.public.encoded,
            keyMaterial = keyPair.private.encoded,
            schemeCodeName = scheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = null,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        assertThrows<IllegalArgumentException> {
            ref.deriveSharedSecret(record, scheme, otherPublicKey, context)
        }
    }

    @Test
    fun `Should derive using alias when it's not null`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = mapOf(
            "customKey" to "customValue"
        )
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val keyPair = generateKeyPair(schemeMetadata, scheme.codeName)
        val originalOtherPublicKey = generateKeyPair(schemeMetadata, scheme.codeName).public
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { deriveSharedSecret(any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            publicKey = keyPair.public.encoded,
            keyMaterial = null,
            schemeCodeName = scheme.codeName,
            masterKeyAlias = null,
            externalId = null,
            encodingVersion = null,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        val result = ref.deriveSharedSecret(record, scheme, originalOtherPublicKey, context)
        assertSame(expectedResult, result)
        Mockito.verify(ref.instance, times(1)).deriveSharedSecret(
            argThat {
                this as SharedSecretAliasSpec
                keyScheme == scheme &&
                otherPublicKey == originalOtherPublicKey &&
                hsmAlias == record.hsmAlias
            },
            argThat {
                size == 2 &&
                        this[CRYPTO_TENANT_ID] == ref.tenantId &&
                        this["customKey"] == "customValue"
            }
        )
    }

    @Test
    fun `Should throw IllegalArgumentException when deriving using alias and null hsm alias`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val keyPair = generateKeyPair(schemeMetadata, scheme.codeName)
        val originalOtherPublicKey = generateKeyPair(schemeMetadata, scheme.codeName).public
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.Categories.LEDGER,
            alias = UUID.randomUUID().toString(),
            hsmAlias = null,
            publicKey = keyPair.public.encoded,
            keyMaterial = null,
            schemeCodeName = scheme.codeName,
            masterKeyAlias = null,
            externalId = null,
            encodingVersion = null,
            associationId = ref.associationId,
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        assertThrows<IllegalArgumentException> {
            ref.deriveSharedSecret(record, scheme, originalOtherPublicKey, context)
        }
    }

    @Test
    fun `Should convert to SigningPublicKeySaveContext`() {
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock()
        )
        val generatedKey = GeneratedPublicKey(
            publicKey = mock(),
            hsmAlias = UUID.randomUUID().toString()
        )
        val alias = UUID.randomUUID().toString()
        val result = ref.toSaveKeyContext(generatedKey, alias, scheme, null)
        assertInstanceOf(SigningPublicKeySaveContext::class.java, result)
        result as SigningPublicKeySaveContext
        assertSame(generatedKey, result.key)
        assertEquals(alias, result.alias)
        assertEquals(scheme, result.keyScheme)
        assertEquals(ref.category, result.category)
        assertEquals(ref.associationId, result.associationId)
    }

    @Test
    fun `Should convert to SigningWrappedKeySaveContext`() {
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock()
        )
        val generatedKey = GeneratedWrappedKey(
            publicKey = mock(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            encodingVersion = 12
        )
        val alias = UUID.randomUUID().toString()
        val externalId = UUID.randomUUID().toString()
        val result = ref.toSaveKeyContext(generatedKey, alias, scheme, externalId)
        assertInstanceOf(SigningWrappedKeySaveContext::class.java, result)
        result as SigningWrappedKeySaveContext
        assertSame(generatedKey, result.key)
        assertEquals(alias, result.alias)
        assertEquals(scheme, result.keyScheme)
        assertEquals(ref.category, result.category)
        assertEquals(ref.masterKeyAlias, result.masterKeyAlias)
        assertEquals(ref.associationId, result.associationId)
        assertEquals(externalId, result.externalId)
    }

    @Test
    fun `Should throw IllegalStateException when converting unknown key generation result`() {
        val scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME)
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock()
        )
        val generatedKey = mock<GeneratedKey>()
        val alias = UUID.randomUUID().toString()
        val externalId = UUID.randomUUID().toString()
        assertThrows<IllegalStateException> {
            ref.toSaveKeyContext(generatedKey, alias, scheme, externalId)
        }
    }
}