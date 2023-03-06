package net.corda.crypto.service.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.schemes.ECDSA_SECP256R1_TEMPLATE
import net.corda.crypto.component.test.utils.generateKeyPair
import net.corda.crypto.core.CryptoConsts
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CATEGORY_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_AFTER_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.CREATED_BEFORE_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.MASTER_KEY_ALIAS_FILTER
import net.corda.crypto.core.CryptoConsts.SigningKeyFilters.SCHEME_CODE_NAME_FILTER
import net.corda.crypto.core.KeyAlreadyExistsException
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningKeyOrderBy
import net.corda.crypto.persistence.SigningKeyStatus
import net.corda.crypto.persistence.SigningKeyStore
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.service.CryptoServiceRef
import net.corda.crypto.service.KeyOrderBy
import net.corda.v5.crypto.KeySchemeCodes.ECDSA_SECP256R1_CODE_NAME
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
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
    fun `Should throw original exception failing signing`() {
        val exception = RuntimeException("")
        val store = mock<SigningKeyStore> {
            on { find(any(), any<PublicKey>()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock()
        )
        val thrown = assertThrows(exception::class.java) {
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
        assertSame(exception, thrown)
        Mockito.verify(store, times(1)).find(any(), any<PublicKey>())
    }

    @Test
    fun `Should throw IllegalArgumentException when key is not found for signing`() {
        val store = mock<SigningKeyStore> {
            on { find(any(), any<PublicKey>()) } doReturn null
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock()
        )
        assertThrows(IllegalArgumentException::class.java) {
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
    fun `Should throw original exception failing derivation`() {
        val exception = RuntimeException("")
        val store = mock<SigningKeyStore> {
            on { find(any(), any<PublicKey>()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock()
        )
        val thrown = assertThrows(exception::class.java) {
            signingService.deriveSharedSecret(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                otherPublicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(store, times(1)).find(any(), any<PublicKey>())
    }

    @Test
    fun `Should throw IllegalArgumentException when key is not found for deriving`() {
        val store = mock<SigningKeyStore> {
            on { find(any(), any<PublicKey>()) } doReturn null
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock()
        )
        assertThrows(IllegalArgumentException::class.java) {
            signingService.deriveSharedSecret(
                tenantId = UUID.randomUUID().toString(),
                publicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                otherPublicKey = mock {
                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
                },
                context = emptyMap()
            )
        }
    }

    @Test
    fun `Should throw KeyAlreadyExistsException when generating key with existing alias`() {
        val existingKey = SigningCachedKey(
            id = ShortHash.of("0123456789AB"),
            fullId = SecureHash.parse("SHA-256:0123456789ABCDEF"),
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
            hsmId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            status = SigningKeyStatus.NORMAL
        )
        val store = mock<SigningKeyStore> {
            on { find(any(), any<String>()) } doReturn existingKey
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock()
        )
        assertThrows(KeyAlreadyExistsException::class.java) {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = "alias1",
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertThrows(KeyAlreadyExistsException::class.java) {
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
    fun `Should throw original when failing key generation with alias`() {
        val exception = RuntimeException("")
        val store = mock<SigningKeyStore> {
            on { find(any(), any<String>()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock()
        )
        var thrown = assertThrows(exception::class.java) {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        thrown = assertThrows(exception::class.java) {
            signingService.generateKeyPair(
                tenantId = UUID.randomUUID().toString(),
                category = CryptoConsts.Categories.LEDGER,
                alias = UUID.randomUUID().toString(),
                externalId = UUID.randomUUID().toString(),
                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
                context = emptyMap()
            )
        }
        assertSame(exception, thrown)
        Mockito.verify(store, times(2)).find(any(), any<String>())
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
        val store = mock<SigningKeyStore> {
            on { lookup(any(), any(), any(), any(), any()) } doReturn emptyList()
        }
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock(),
            schemeMetadata = schemeMetadata,
            digestService = mock()
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
        Mockito.verify(store, times(1)).lookup(
            tenantId,
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
            val store = mock<SigningKeyStore> {
                on { lookup(any(), any(), any(), any(), any()) } doReturn emptyList()
            }
            val signingService = SigningServiceImpl(
                store = store,
                cryptoServiceFactory = mock(),
                schemeMetadata = schemeMetadata,
                digestService = mock()
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
            Mockito.verify(store, times(1)).lookup(
                tenantId,
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
            publicKey = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
            hsmAlias = UUID.randomUUID().toString()
        )
        val tenantId = UUID.randomUUID().toString()
        val expectedAlias = UUID.randomUUID().toString()
        val store = mock<SigningKeyStore>()
        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
        val ref = CryptoServiceRef(
            tenantId = UUID.randomUUID().toString(),
            category = CryptoConsts.Categories.LEDGER,
            masterKeyAlias = UUID.randomUUID().toString(),
            hsmId = UUID.randomUUID().toString(),
            instance = mock {
                on { generateKeyPair(any(), any()) } doReturn generatedKey
            }
        )
        val signingService = SigningServiceImpl(
            store = store,
            cryptoServiceFactory = mock {
                on { this.findInstance(tenantId, CryptoConsts.Categories.LEDGER) } doReturn ref
            },
            schemeMetadata = schemeMetadata,
            digestService = mock()
        )
        var result = signingService.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            scheme = scheme,
            alias = expectedAlias
        )
        assertThat(generatedKey.publicKey).isEqualTo(result)
        val expectedExternalId = UUID.randomUUID().toString()
        result = signingService.generateKeyPair(
            tenantId = tenantId,
            category = CryptoConsts.Categories.LEDGER,
            externalId = expectedExternalId,
            scheme = scheme,
            alias = expectedAlias
        )
        assertThat(generatedKey.publicKey).isEqualTo(result)
        Mockito.verify(store, times(1)).save(
            eq(tenantId),
            argThat {
                this as SigningPublicKeySaveContext
                key == generatedKey &&
                        alias == expectedAlias &&
                        externalId == null &&
                        keyScheme == scheme &&
                        hsmId == ref.hsmId &&
                        category == ref.category
            }
        )
        Mockito.verify(store, times(1)).save(
            eq(tenantId),
            argThat {
                this as SigningPublicKeySaveContext
                key == generatedKey &&
                        alias == expectedAlias &&
                        externalId == expectedExternalId &&
                        keyScheme == scheme &&
                        hsmId == ref.hsmId &&
                        category == ref.category
            }
        )
    }
}