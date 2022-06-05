package net.corda.crypto.service.impl.hsm.soft

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
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
import net.corda.v5.cipher.suite.SigningAliasSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.v5.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.RSA_CODE_NAME
import net.corda.v5.crypto.exceptions.CryptoServiceException
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
        val expectedResult = listOf(
            ECDSA_SECP256R1_TEMPLATE.makeScheme("BC"),
            RSA_TEMPLATE.makeScheme("BC"),
        )
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { supportedSchemes() } doReturn expectedResult
            }
        )
        val result = ref.getSupportedSchemes()
        assertEquals(2, result.size)
        assertThat(result).containsAll(listOf(ECDSA_SECP256R1_CODE_NAME, RSA_CODE_NAME))
        Mockito.verify(ref.instance, times(1)).supportedSchemes()
    }

    @Test
    fun `Should generate key`() {
        val expectedResult = mock<GeneratedKey>()
        val expectedAlias = UUID.randomUUID().toString()
        val context = mapOf(
            "customKey" to "customValue"
        )
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
                        keyMaterial.contentEquals(record.keyMaterial) &&
                        masterKeyAlias == ref.masterKeyAlias &&
                        encodingVersion == record.encodingVersion
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
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
    fun `Should convert to SigningPublicKeySaveContext`() {
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
    fun `Should throw CryptoServiceException when converting unknown key generation result`() {
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
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
        assertThrows<CryptoServiceException> {
            ref.toSaveKeyContext(generatedKey, alias, scheme, externalId)
        }
    }
}