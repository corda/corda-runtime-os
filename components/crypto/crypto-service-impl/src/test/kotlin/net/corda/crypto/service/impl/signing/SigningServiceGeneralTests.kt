package net.corda.crypto.service.impl.signing

import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.publicKeyIdFromBytes
import net.corda.crypto.impl.components.CipherSchemeMetadataImpl
import net.corda.crypto.persistence.signing.SigningCachedKey
import net.corda.crypto.persistence.signing.SigningKeyStore
import net.corda.crypto.persistence.signing.SigningKeyStoreActions
import net.corda.crypto.persistence.signing.SigningKeyOrderBy
import net.corda.crypto.persistence.signing.SigningKeyStatus
import net.corda.crypto.persistence.signing.SigningPublicKeySaveContext
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.KeyOrderBy
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.GeneratedPublicKey
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SignatureSpec
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
import kotlin.test.assertNotSame
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
    fun `Should throw IllegalArgumentException when the lookup by ids keys is passed more than 20 items`() {
        val signingService = SigningServiceImpl(
            store = mock(),
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val keys = (0 until 21).map {
            publicKeyIdFromBytes(
                mock<PublicKey> {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                }.encoded
            )
        }
        assertThrows<IllegalArgumentException> {
            signingService.lookup(UUID.randomUUID().toString(), keys)
        }
    }

    @Test
    fun `Should wrap CryptoServiceException into another with more information when failing signing`() {
        val exception = CryptoServiceException("")
        val store = mock<SigningKeyStore> {
            on { act<SigningKeyStoreActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                signatureSpec = SignatureSpec("NONE"),
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertNotSame(exception, thrown)
        Mockito.verify(store, times(1)).act<SigningKeyStoreActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing signing`() {
        val exception = RuntimeException("")
        val store = mock<SigningKeyStore> {
            on { act<SigningKeyStoreActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                signatureSpec = SignatureSpec("NONE"),
                data = ByteArray(2),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(store, times(1)).act<SigningKeyStoreActions>(any(), any())
    }

    @Test
    fun `Should throw CryptoServiceException when key is not found for signing`() {
        val actions = mock<SigningKeyStoreActions> {
            on { find(any<PublicKey>()) } doReturn null
        }
        val store = mock<SigningKeyStore> {
            on { act(any()) } doReturn actions
            on { act<SigningKeyStoreActions>(any(), any()) }.thenCallRealMethod()
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        assertThrows<CryptoServiceException> {
            signingService.sign(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                signatureSpec = SignatureSpec("NONE"),
                data = ByteArray(2),
                context = emptyMap()
            )
        }
    }

    @Test
    fun `Should throw CryptoServiceException when generating key with existing alias`() {
        val existingKey = SigningCachedKey(
            id = UUID.randomUUID().toString(),
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            alias = "alias1",
            hsmAlias = null,
            publicKey = UUID.randomUUID().toString().toByteArray(),
            keyMaterial = UUID.randomUUID().toString().toByteArray(),
            masterKeyAlias = UUID.randomUUID().toString(),
            externalId = null,
            schemeCodeName = ECDSA_SECP256R1_CODE_NAME,
            encodingVersion = 1,
            associationId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        val actions = mock<SigningKeyStoreActions> {
            on { find(any<String>()) } doReturn existingKey
        }
        val store = mock<SigningKeyStore> {
            on { act(any()) } doReturn actions
            on { act<SigningKeyStoreActions>(any(), any()) }.thenCallRealMethod()
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        assertThrows<CryptoServiceException> {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = "alias1",
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertThrows<CryptoServiceException> {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = "alias1",
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
    }

    @Test
    fun `Should throw CryptoServiceException when failing aliased key generation`() {
        val exception = CryptoServiceException("")
        val store = mock<SigningKeyStore> {
            on { act<SigningKeyStoreActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        var thrown = assertThrows<CryptoServiceException> {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertNotSame(exception, thrown)
        thrown = assertThrows {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertNotSame(exception, thrown)
        Mockito.verify(store, times(2)).act<SigningKeyStoreActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing aliased key generation`() {
        val exception = RuntimeException("")
        val store = mock<SigningKeyStore> {
            on { act<SigningKeyStoreActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        var thrown = assertThrows<CryptoServiceException> {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
        thrown = assertThrows {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(store, times(2)).act<SigningKeyStoreActions>(any(), any())
    }

    @Test
    fun `Should throw CryptoServiceException when failing fresh key generation`() {
        val exception = CryptoServiceException("")
        val store = mock<SigningKeyStore> {
            on { act<SigningKeyStoreActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.freshKey(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.CI,
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
            )
        }
        assertNotSame(exception, thrown)
        Mockito.verify(store, times(1)).act<SigningKeyStoreActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing fresh key generation`() {
        val exception = RuntimeException("")
        val store = mock<SigningKeyStore> {
            on { act<SigningKeyStoreActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.freshKey(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.CI,
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(store, times(1)).act<SigningKeyStoreActions>(any(), any())
    }

    @Test
    fun `Should throw CryptoServiceException when failing fresh key generation with external id`() {
        val exception = CryptoServiceException("")
        val store = mock<SigningKeyStore> {
            on { act<SigningKeyStoreActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.freshKey(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.CI,
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
            )
        }
        assertNotSame(exception, thrown)
        Mockito.verify(store, times(1)).act<SigningKeyStoreActions>(any(), any())
    }

    @Test
    fun `Should wrap in CryptoServiceException when failing fresh key generation with external id`() {
        val exception = RuntimeException("")
        val store = mock<SigningKeyStore> {
            on { act<SigningKeyStoreActions>(any(), any()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val thrown = assertThrows<CryptoServiceException> {
            signingService.freshKey(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.CI,
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
            )
        }
        assertSame(exception, thrown.cause)
        Mockito.verify(store, times(1)).act<SigningKeyStoreActions>(any(), any())
    }

    @Test
    fun `Should pass all parameters to cache for lookup function`() {
        val skip = 17
        val take = 21
        val orderBy: KeyOrderBy = KeyOrderBy.ALIAS
        val tenantId: String = UUID.randomUUID().toString()
        val category: String = CryptoConsts.Categories.TLS
        val schemeCodeName: String = UUID.randomUUID().toString()
        val alias: String = UUID.randomUUID().toString()
        val masterKeyAlias: String = UUID.randomUUID().toString()
        val createdAfter: Instant = Instant.now().plusSeconds(-5)
        val createdBefore: Instant = Instant.now()
        val actions = mock<SigningKeyStoreActions>()
        val store = mock<SigningKeyStore> {
            on { act(tenantId) } doReturn actions
            on { act<SigningKeyStoreActions>(any(), any()) }.thenCallRealMethod()
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata
        )
        val filter = mapOf(
            CATEGORY_FILTER to category,
            SCHEME_CODE_NAME_FILTER to schemeCodeName,
            ALIAS_FILTER to alias,
            MASTER_KEY_ALIAS_FILTER to masterKeyAlias,
            CREATED_AFTER_FILTER to createdAfter.toString(),
            CREATED_BEFORE_FILTER to createdBefore.toString()
        )
        val result = signingService.lookup(
            tenantId,
            skip,
            take,
            orderBy,
            filter
        )
        assertNotNull(result)
        assertEquals(0, result.size)
        Mockito.verify(actions, times(1)).lookup(
            skip,
            take,
            SigningKeyOrderBy.ALIAS,
            filter
        )
    }

    @Test
    fun `Should pass order by to lookup function`() {
        KeyOrderBy.values().forEach { orderBy ->
            val skip = 17
            val take = 21
            val tenantId: String = UUID.randomUUID().toString()
            val actions = mock<SigningKeyStoreActions>()
            val store = mock<SigningKeyStore> {
                on { act(tenantId) } doReturn actions
                on { act<SigningKeyStoreActions>(any(), any()) }.thenCallRealMethod()
            }
            val signingService = SigningServiceImpl(
                store = store,
                cryptoServiceFactory = mock(),
                schemeMetadata = schemeMetadata
            )
            val filter = emptyMap<String, String>()
            val result = signingService.lookup(
                tenantId,
                skip,
                take,
                orderBy,
                filter
            )
            assertNotNull(result)
            assertEquals(0, result.size)
            Mockito.verify(actions, times(1)).lookup(
                skip,
                take,
                SigningKeyOrderBy.valueOf(orderBy.toString()),
                filter
            )
        }
    }

    @Test
    @Suppress("ComplexMethod")
    fun `Should save generated key with alias`() {
        val generatedKey = GeneratedPublicKey(
            publicKey = mock(),
            hsmAlias = UUID.randomUUID().toString()
        )
        val tenantId = UUID.randomUUID().toString()
        val expectedAlias = UUID.randomUUID().toString()
        val actions = mock<SigningKeyStoreActions>()
        val store = mock<SigningKeyStore> {
            on { act(tenantId) } doReturn actions
            on { act<SigningKeyStoreActions>(any(), any()) }.thenCallRealMethod()
        }
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            aliasSecret = UUID.randomUUID().toString().toByteArray(),
            associationId = UUID.randomUUID().toString(),
            instance = mock {
                on { generateKeyPair(any(), any()) } doReturn generatedKey
            }
        )
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock {
                on { this.getInstance(tenantId, CryptoConsts.Categories.LEDGER) } doReturn ref
            },
            schemeMetadata = schemeMetadata
        )
        var result = signingService.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            scheme = scheme,
            alias = expectedAlias
        )
        assertSame(generatedKey.publicKey, result)
        val expectedExternalId = UUID.randomUUID().toString()
        result = signingService.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            externalId = expectedExternalId,
            scheme = scheme,
            alias = expectedAlias
        )
        assertSame(generatedKey.publicKey, result)
        Mockito.verify(actions, times(1)).save(
            argThat {
                this as SigningPublicKeySaveContext
                key == generatedKey &&
                        alias == expectedAlias &&
                        externalId == null &&
                        keyScheme == scheme &&
                        associationId == ref.associationId &&
                        category == ref.category
            }
        )
        Mockito.verify(actions, times(1)).save(
            argThat {
                this as SigningPublicKeySaveContext
                key == generatedKey &&
                        alias == expectedAlias &&
                        externalId == expectedExternalId &&
                        keyScheme == scheme &&
                        associationId == ref.associationId &&
                        category == ref.category
            }
        )
    }
}