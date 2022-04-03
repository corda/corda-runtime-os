package net.corda.crypto.service.impl.signing

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyCache
import net.corda.crypto.persistence.SigningKeyCacheActions
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.KeyOrderBy
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_SHA256_TEMPLATE
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.security.PublicKey
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class SigningServiceGeneralTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @BeforeAll
        @JvmStatic
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
        }
    }

    @Test
    fun `Should throw IllegalArgumentException when the filter my keys is passed more than 20 items`() {
        val signingService = SigningServiceImpl(
            cache = mock(),
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val keys = (0 until 21).map {
            mock<PublicKey> {
                on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
            }
        }
        assertThrows<IllegalArgumentException> {
            signingService.filterMyKeys(UUID.randomUUID().toString(), keys)
        }
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing signing`() {
        val exception = CryptoServiceException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing signing`() {
        val exception = RuntimeException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing signing with explicit signature scheme`() {
        val exception = CryptoServiceException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                signatureSpec = schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }.signatureSpec,
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing signing with explicit signature scheme`() {
        val exception = RuntimeException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                signatureSpec = schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }.signatureSpec,
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should throw CryptoServiceBadRequestException when key is not found for signing`() {
        val actions = mock<SigningKeyCacheActions> {
            on { find(any<PublicKey>()) } doReturn null
        }
        val cache = mock<SigningKeyCache> {
            on { act(any()) } doReturn actions
            on { act<SigningKeyCacheActions>(any(), any()) }.thenCallRealMethod()
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        assertThrows<CryptoServiceBadRequestException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                data = ByteArray(2),
                context = emptyMap()
            )
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `Should throw CryptoServiceBadRequestException when key is not found for signing with explicit signature scheme`() {
        val actions = mock<SigningKeyCacheActions> {
            on { find(any<PublicKey>()) } doReturn null
        }
        val cache = mock<SigningKeyCache> {
            on { act(any()) } doReturn actions
            on { act<SigningKeyCacheActions>(any(), any()) }.thenCallRealMethod()
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        assertThrows<CryptoServiceBadRequestException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                signatureSpec = schemeMetadata.schemes.first { it.codeName == ECDSA_SECP256R1_CODE_NAME }.signatureSpec,
                data = ByteArray(2),
                context = emptyMap()
            )
        }
    }

    @Test
    fun `Should throw CryptoServiceBadRequestException when generating key with existing alias`() {
        val existingKey = SigningCachedKey(
            id = UUID.randomUUID().toString(),
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = UUID.randomUUID().toString(),
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            masterKeyAlias = UUID.randomUUID().toString(),
            externalId = null,
            schemeCodeName = ECDSA_SECP256R1_CODE_NAME,
            encodingVersion = 1,
            created = Instant.now()
        )
        val actions = mock<SigningKeyCacheActions> {
            on { find(any<String>()) } doReturn existingKey
        }
        val cache = mock<SigningKeyCache> {
            on { act(any()) } doReturn actions
            on { act<SigningKeyCacheActions>(any(), any()) }.thenCallRealMethod()
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        assertThrows<CryptoServiceBadRequestException> {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.HsmCategories.LEDGER,
                alias = UUID.randomUUID().toString(),
                context = emptyMap()
            )
        }
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing aliased key generation`() {
        val exception = CryptoServiceException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.HsmCategories.LEDGER,
                alias = UUID.randomUUID().toString(),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing aliased key generation`() {
        val exception = RuntimeException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.HsmCategories.LEDGER,
                alias = UUID.randomUUID().toString(),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing fresh key generation`() {
        val exception = CryptoServiceException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.freshKey(
                tenantId = UUID.randomUUID().toString()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing fresh key generation`() {
        val exception = RuntimeException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.freshKey(
                tenantId = UUID.randomUUID().toString()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should re-throw same CryptoServiceException when failing fresh key generation with external id`() {
        val exception = CryptoServiceException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.freshKey(
                tenantId = UUID.randomUUID().toString(),
                externalId = UUID.randomUUID().toString()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing fresh key generation with external id`() {
        val exception = RuntimeException("")
        val cache = mock<SigningKeyCache> {
            on { act<SigningKeyCacheActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.freshKey(
                tenantId = UUID.randomUUID().toString(),
                externalId = UUID.randomUUID().toString()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(cache, times(1)).act<SigningKeyCacheActions>(any(), any())
    }

    @Test
    fun `Should pass all parameters to cache for lookup function`() {
        val skip = 17
        val take = 21
        val orderBy: KeyOrderBy = KeyOrderBy.ALIAS
        val tenantId: String = UUID.randomUUID().toString()
        val category: String = CryptoConsts.HsmCategories.TLS
        val schemeCodeName: String = UUID.randomUUID().toString()
        val alias: String = UUID.randomUUID().toString()
        val masterKeyAlias: String = UUID.randomUUID().toString()
        val createdAfter: Instant = Instant.now().plusSeconds(-5)
        val createdBefore: Instant = Instant.now()
        val actions = mock<SigningKeyCacheActions>()
        val cache = mock<SigningKeyCache> {
            on { act(tenantId) } doReturn actions
            on { act<SigningKeyCacheActions>(any(), any()) }.thenCallRealMethod()
        }
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val result = signingService.lookup(
                skip,
                take,
                orderBy,
                tenantId,
                category,
                schemeCodeName,
                alias,
                masterKeyAlias,
                createdAfter,
                createdBefore
            )
        assertNotNull(result)
        assertEquals(0, result.size)
        Mockito.verify(actions, times(1)).lookup(
            skip,
            take,
            SigningKeyOrderBy.ALIAS,
            category,
            schemeCodeName,
            alias,
            masterKeyAlias,
            createdAfter,
            createdBefore
        )
    }

    @Test
    fun `Should save generated key with alias`() {
        val generatedKey = GeneratedPublicKey(
            publicKey = mock(),
            hsmAlias = UUID.randomUUID().toString()
        )
        val tenantId = UUID.randomUUID().toString()
        val expectedAlias = UUID.randomUUID().toString()
        val actions = mock<SigningKeyCacheActions>()
        val cache = mock<SigningKeyCache> {
            on { act(tenantId) } doReturn actions
            on { act<SigningKeyCacheActions>(any(), any()) }.thenCallRealMethod()
        }
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.HsmCategories.LEDGER,
            signatureScheme = ECDSA_SECP256R1_SHA256_TEMPLATE.makeScheme("BC"),
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            instance = mock {
                on { generateKeyPair(any(), any()) } doReturn generatedKey
            }
        )
        val signingService = SigningServiceImpl(
            cache = cache,
            cryptoServiceFactory = mock {
                on { this.getInstance(tenantId, CryptoConsts.HsmCategories.LEDGER) } doReturn ref
            },
            schemeMetadata = schemeMetadata
        )
        val result = signingService.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.HsmCategories.LEDGER,
            alias = expectedAlias
        )
        assertSame(generatedKey.publicKey, result)
        Mockito.verify(actions, times(1)).save(
            argThat {
                this as SigningPublicKeySaveContext
                key == generatedKey &&
                        alias == expectedAlias &&
                        signatureScheme == ref.signatureScheme &&
                        category == ref.category
            }
        )
    }
}