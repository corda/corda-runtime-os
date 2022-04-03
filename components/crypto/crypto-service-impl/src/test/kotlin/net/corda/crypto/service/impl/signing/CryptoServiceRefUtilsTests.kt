package net.corda.crypto.service.impl.signing

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.service.CryptoServiceRef
import net.corda.v5.cipher.suite.GeneratedKey
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.GeneratedWrappedKey
import net.corda.v5.cipher.suite.SigningAliasSpec
import net.corda.v5.cipher.suite.SigningWrappedSpec
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_SHA256_TEMPLATE
import net.corda.v5.cipher.suite.schemes.RSA_CODE_NAME
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_TEMPLATE
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
    @Test
    fun `Should return supported schemes`() {
        val expectedResult = arrayOf(
            ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            RSA_SHA256_TEMPLATE.makeScheme("BC"),
        )
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = expectedResult[0],
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock {
                on { supportedSchemes() } doReturn expectedResult
            }
        )
        val result = ref.getSupportedSchemes()
        assertEquals(2, result.size)
        assertThat(result, contains(ECDSA_SECP256R1_CODE_NAME, RSA_CODE_NAME))
        Mockito.verify(ref.instance, times(1)).supportedSchemes()
    }

    @Test
    fun `Should create wrapping key`() {
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock()
        )
        ref.createWrappingKey(true)
        Mockito.verify(ref.instance, times(1)).createWrappingKey(
            eq(ref.masterKeyAlias!!),
            eq(true),
            argThat { isEmpty() }
        )
    }

    @Test
    fun `Should throw IllegalArgumentException when creating wrapping key and master key alias is null`() {
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = null,
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock()
        )
        assertThrows<IllegalArgumentException> {
            ref.createWrappingKey(true)
        }
    }

    @Test
    fun `Should generate key`() {
        val expectedResult = mock<GeneratedKey>()
        val expectedAlias = UUID.randomUUID().toString()
        val context = emptyMap<String, String>()
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock {
                on { generateKeyPair(any(), any()) } doReturn expectedResult
            }
        )
        val result = ref.generateKeyPair(expectedAlias, context)
        assertSame(expectedResult, result)
        Mockito.verify(ref.instance, times(1)).generateKeyPair(
            argThat {
                            tenantId == ref.tenantId &&
                            signatureScheme == ref.signatureScheme &&
                            alias == expectedAlias &&
                            masterKeyAlias == ref.masterKeyAlias &&
                            secret.contentEquals(ref.aliasSecret)
            },
            eq(context)
        )
    }

    @Test
    fun `Should sign using key material when it's not null`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val data = UUID.randomUUID().toString().toByteArray()
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            schemeCodeName = ref.signatureScheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = 11,
            created = Instant.now()
        )
        val result = ref.sign(record, ref.signatureScheme, data, context)
        assertSame(expectedResult, result)
        Mockito.verify(ref.instance, times(1)).sign(
            argThat {
                this as SigningWrappedSpec
                tenantId == ref.tenantId &&
                        signatureScheme == ref.signatureScheme &&
                        keyMaterial.contentEquals(record.keyMaterial) &&
                        masterKeyAlias == ref.masterKeyAlias &&
                        encodingVersion == record.encodingVersion
            },
            eq(data),
            eq(context)
        )
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using empty key material`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val data = UUID.randomUUID().toString().toByteArray()
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = ByteArray(0),
            schemeCodeName = ref.signatureScheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = 11,
            created = Instant.now()
        )
        assertThrows<IllegalArgumentException> {
            ref.sign(record, ref.signatureScheme, data, context)
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using non empty key material and null encodingVersion`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val data = UUID.randomUUID().toString().toByteArray()
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = null,
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            schemeCodeName = ref.signatureScheme.codeName,
            masterKeyAlias = ref.masterKeyAlias,
            externalId = null,
            encodingVersion = null,
            created = Instant.now()
        )
        assertThrows<IllegalArgumentException> {
            ref.sign(record, ref.signatureScheme, data, context)
        }
    }

    @Test
    fun `Should sign using alias when it's not null`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val data = UUID.randomUUID().toString().toByteArray()
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = UUID.randomUUID().toString(),
            hsmAlias = UUID.randomUUID().toString(),
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = null,
            schemeCodeName = ref.signatureScheme.codeName,
            masterKeyAlias = null,
            externalId = null,
            encodingVersion = null,
            created = Instant.now()
        )
        val result = ref.sign(record, ref.signatureScheme, data, context)
        assertSame(expectedResult, result)
        Mockito.verify(ref.instance, times(1)).sign(
            argThat {
                this as SigningAliasSpec
                tenantId == ref.tenantId &&
                        signatureScheme == ref.signatureScheme &&
                        hsmAlias == record.hsmAlias
            },
            eq(data),
            eq(context)
        )
    }

    @Test
    fun `Should throw IllegalArgumentException when signing using alias and null hsm alias`() {
        val expectedResult = UUID.randomUUID().toString().toByteArray()
        val context = emptyMap<String, String>()
        val data = UUID.randomUUID().toString().toByteArray()
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock {
                on { sign(any(), any(), any()) } doReturn expectedResult
            }
        )
        val record = SigningCachedKey(
            id = "123",
            tenantId = ref.tenantId,
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = UUID.randomUUID().toString(),
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = null,
            schemeCodeName = ref.signatureScheme.codeName,
            masterKeyAlias = null,
            externalId = null,
            encodingVersion = null,
            created = Instant.now()
        )
        assertThrows<IllegalArgumentException> {
            ref.sign(record, ref.signatureScheme, data, context)
        }
    }

    @Test
    fun `Should convert to SigningPublicKeySaveContext`() {
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock()
        )
        val generatedKey = GeneratedPublicKey(
            publicKey = mock(),
            hsmAlias = UUID.randomUUID().toString()
        )
        val alias = UUID.randomUUID().toString()
        val result = ref.toSaveKeyContext(generatedKey, alias, null)
        assertInstanceOf(SigningPublicKeySaveContext::class.java, result)
        result as SigningPublicKeySaveContext
        assertSame(generatedKey, result.key)
        assertEquals(alias, result.alias)
        assertEquals(ref.signatureScheme, result.signatureScheme)
        assertEquals(ref.category, result.category)
    }

    @Test
    fun `Should convert to SigningWrappedKeySaveContext`() {
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock()
        )
        val generatedKey = GeneratedWrappedKey(
            publicKey = mock(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            encodingVersion = 12
        )
        val alias = UUID.randomUUID().toString()
        val externalId = UUID.randomUUID().toString()
        val result = ref.toSaveKeyContext(generatedKey, alias, externalId)
        assertInstanceOf(SigningWrappedKeySaveContext::class.java, result)
        result as SigningWrappedKeySaveContext
        assertSame(generatedKey, result.key)
        assertEquals(alias, result.alias)
        assertEquals(ref.signatureScheme, result.signatureScheme)
        assertEquals(ref.category, result.category)
        assertEquals(ref.masterKeyAlias, result.masterKeyAlias)
        assertEquals(externalId, result.externalId)
    }

    @Test
    fun `Should throw CryptoServiceException when converting unknown key generation result`() {
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock()
        )
        val generatedKey = mock<GeneratedKey>()
        val alias = UUID.randomUUID().toString()
        val externalId = UUID.randomUUID().toString()
        assertThrows<CryptoServiceException> {
            ref.toSaveKeyContext(generatedKey, alias, externalId)
        }
    }
}