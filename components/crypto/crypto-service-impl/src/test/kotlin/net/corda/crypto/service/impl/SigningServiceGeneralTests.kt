package net.corda.crypto.service.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.softhsm.SigningRepository
import net.corda.v5.crypto.SignatureSpec
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.security.PublicKey
import java.util.UUID

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
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doThrow exception
        }
        val signingService = SigningServiceImpl(
            cryptoServiceFactory = mock(),
            signingRepositoryFactory = { repo },
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = mock()
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
        Mockito.verify(repo, times(1)).findKey(any<PublicKey>())
    }

    @Test
    fun `Should throw IllegalArgumentException when key is not found for signing`() {
        val repo = mock<SigningRepository> {
            on { findKey(any<PublicKey>()) } doReturn null
        }
        val signingService = SigningServiceImpl(
            cryptoServiceFactory = mock(),
            signingRepositoryFactory = { repo },
            schemeMetadata = schemeMetadata,
            digestService = mock(),
            cache = mock()
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
//
//    @Test
//    fun `Should throw original exception failing derivation`() {
//        val exception = RuntimeException("")
//        val store = mock<SigningRepository> {
//            on { findKey(any<PublicKey>()) } doThrow exception
//        }
//        val signingService = SigningServiceImpl(
//            store = store,
//            cryptoServiceFactory = mock(),
//            schemeMetadata = schemeMetadata,
//            digestService = mock()
//        )
//        val thrown = assertThrows(exception::class.java) {
//            signingService.deriveSharedSecret(
//                tenantId = UUID.randomUUID().toString(),
//                publicKey = mock {
//                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
//                },
//                otherPublicKey = mock {
//                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
//                },
//                context = emptyMap()
//            )
//        }
//        assertSame(exception, thrown)
//        Mockito.verify(store, times(1)).find(any(), any<PublicKey>())
//    }
//
//    @Test
//    fun `Should throw IllegalArgumentException when key is not found for deriving`() {
//        val store = mock<SigningKeyStore> {
//            on { find(any(), any<PublicKey>()) } doReturn null
//        }
//        val signingService = SigningServiceImpl(
//            store = store,
//            cryptoServiceFactory = mock(),
//            schemeMetadata = schemeMetadata,
//            digestService = mock()
//        )
//        assertThrows(IllegalArgumentException::class.java) {
//            signingService.deriveSharedSecret(
//                tenantId = UUID.randomUUID().toString(),
//                publicKey = mock {
//                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
//                },
//                otherPublicKey = mock {
//                    on { encoded } doReturn UUID.randomUUID().toString().toByteArray()
//                },
//                context = emptyMap()
//            )
//        }
//    }
//
//    @Test
//    fun `Should throw KeyAlreadyExistsException when generating key with existing alias`() {
//        val existingKey = SigningKeyInfo(
//            id = ShortHash.of("0123456789AB"),
//            fullId = parseSecureHash("SHA-256:0123456789ABCDEF"),
//            tenantId = UUID.randomUUID().toString(),
//            category = CryptoConsts.Categories.LEDGER,
//            alias = "alias1",
//            hsmAlias = null,
//            publicKey = UUID.randomUUID().toString().toByteArray(),
//            keyMaterial = UUID.randomUUID().toString().toByteArray(),
//            masterKeyAlias = UUID.randomUUID().toString(),
//            externalId = null,
//            schemeCodeName = ECDSA_SECP256R1_CODE_NAME,
//            encodingVersion = 1,
//            hsmId = UUID.randomUUID().toString(),
//            timestamp = Instant.now(),
//            status = SigningKeyStatus.NORMAL
//        )
//        val store = mock<SigningKeyStore> {
//            on { find(any(), any<String>()) } doReturn existingKey
//        }
//        val signingService = SigningServiceImpl(
//            store = store,
//            cryptoServiceFactory = mock(),
//            schemeMetadata = schemeMetadata,
//            digestService = mock()
//        )
//        assertThrows(KeyAlreadyExistsException::class.java) {
//            signingService.generateKeyPair(
//                tenantId = UUID.randomUUID().toString(),
//                category = CryptoConsts.Categories.LEDGER,
//                alias = "alias1",
//                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
//                context = emptyMap()
//            )
//        }
//        assertThrows(KeyAlreadyExistsException::class.java) {
//            signingService.generateKeyPair(
//                tenantId = UUID.randomUUID().toString(),
//                category = CryptoConsts.Categories.LEDGER,
//                alias = "alias1",
//                externalId = UUID.randomUUID().toString(),
//                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
//                context = emptyMap()
//            )
//        }
//    }
//
//    @Test
//    fun `Should throw original when failing key generation with alias`() {
//        val exception = RuntimeException("")
//        val store = mock<SigningKeyStore> {
//            on { find(any(), any<String>()) } doThrow exception
//        }
//        val signingService = SigningServiceImpl(
//            store = store,
//            cryptoServiceFactory = mock(),
//            schemeMetadata = schemeMetadata,
//            digestService = mock()
//        )
//        var thrown = assertThrows(exception::class.java) {
//            signingService.generateKeyPair(
//                tenantId = UUID.randomUUID().toString(),
//                category = CryptoConsts.Categories.LEDGER,
//                alias = UUID.randomUUID().toString(),
//                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
//                context = emptyMap()
//            )
//        }
//        assertSame(exception, thrown)
//        thrown = assertThrows(exception::class.java) {
//            signingService.generateKeyPair(
//                tenantId = UUID.randomUUID().toString(),
//                category = CryptoConsts.Categories.LEDGER,
//                alias = UUID.randomUUID().toString(),
//                externalId = UUID.randomUUID().toString(),
//                scheme = schemeMetadata.findKeyScheme(ECDSA_SECP256R1_CODE_NAME),
//                context = emptyMap()
//            )
//        }
//        assertSame(exception, thrown)
//        Mockito.verify(store, times(2)).find(any(), any<String>())
//    }
//
//    @Test
//    fun `Should pass all parameters to cache for lookup function`() {
//        val skip = 17
//        val take = 21
//        val orderBy: KeyOrderBy = KeyOrderBy.ALIAS
//        val tenantId: String = UUID.randomUUID().toString()
//        val category: String = CryptoConsts.Categories.TLS
//        val schemeCodeName: String = UUID.randomUUID().toString()
//        val alias: String = UUID.randomUUID().toString()
//        val masterKeyAlias: String = UUID.randomUUID().toString()
//        val createdAfter: Instant = Instant.now().plusSeconds(-5)
//        val createdBefore: Instant = Instant.now()
//        val store = mock<SigningKeyStore> {
//            on { lookup(any(), any(), any(), any(), any()) } doReturn emptyList()
//        }
//        val signingService = SigningServiceImpl(
//            store = store,
//            cryptoServiceFactory = mock(),
//            schemeMetadata = schemeMetadata,
//            digestService = mock()
//        )
//        val filter = mapOf(
//            CATEGORY_FILTER to category,
//            SCHEME_CODE_NAME_FILTER to schemeCodeName,
//            ALIAS_FILTER to alias,
//            MASTER_KEY_ALIAS_FILTER to masterKeyAlias,
//            CREATED_AFTER_FILTER to createdAfter.toString(),
//            CREATED_BEFORE_FILTER to createdBefore.toString()
//        )
//        val result = signingService.lookup(
//            tenantId,
//            skip,
//            take,
//            orderBy,
//            filter
//        )
//        assertNotNull(result)
//        assertEquals(0, result.size)
//        Mockito.verify(store, times(1)).lookup(
//            tenantId,
//            skip,
//            take,
//            SigningKeyOrderBy.ALIAS,
//            filter
//        )
//    }
//
//    @Test
//    fun `Should pass order by to lookup function`() {
//        KeyOrderBy.values().forEach { orderBy ->
//            val skip = 17
//            val take = 21
//            val tenantId: String = UUID.randomUUID().toString()
//            val store = mock<SigningRepository> {
//                on { lookup(any(), any(), any(), any(), any()) } doReturn emptyList()
//            }
//            val signingService = SigningServiceImpl(
//                store = store,
//                cryptoServiceFactory = mock(),
//                schemeMetadata = schemeMetadata,
//                digestService = mock()
//            )
//            val filter = emptyMap<String, String>()
//            val result = signingService.lookup(
//                tenantId,
//                skip,
//                take,
//                orderBy,
//                filter
//            )
//            assertNotNull(result)
//            assertEquals(0, result.size)
//            Mockito.verify(store, times(1)).lookup(
//                tenantId,
//                skip,
//                take,
//                SigningKeyOrderBy.valueOf(orderBy.toString()),
//                filter
//            )
//        }
//    }
//
//    @Test
//    @Suppress("ComplexMethod")
//    fun `Should save generated key with alias`() {
//        val generatedKey = GeneratedPublicKey(
//            publicKey = generateKeyPair(schemeMetadata, ECDSA_SECP256R1_CODE_NAME).public,
//            hsmAlias = UUID.randomUUID().toString()
//        )
//        val tenantId = UUID.randomUUID().toString()
//        val expectedAlias = UUID.randomUUID().toString()
//        val store = mock<SigningKeyStore>()
//        val scheme = ECDSA_SECP256R1_TEMPLATE.makeScheme("BC")
//        val ref = CryptoServiceRef(
//            tenantId = UUID.randomUUID().toString(),
//            category = CryptoConsts.Categories.LEDGER,
//            masterKeyAlias = UUID.randomUUID().toString(),
//            hsmId = UUID.randomUUID().toString(),
//            instance = mock {
//                on { generateKeyPair(any(), any()) } doReturn generatedKey
//            }
//        )
//        val signingService = SigningServiceImpl(
//            store = store,
//            cryptoServiceFactory = mock {
//                on { this.findInstance(tenantId, CryptoConsts.Categories.LEDGER) } doReturn ref
//            },
//            schemeMetadata = schemeMetadata,
//            digestService = mock()
//        )
//        var result = signingService.generateKeyPair(
//            tenantId = tenantId,
//            category = CryptoConsts.Categories.LEDGER,
//            scheme = scheme,
//            alias = expectedAlias
//        )
//        assertThat(generatedKey.publicKey).isEqualTo(result)
//        val expectedExternalId = UUID.randomUUID().toString()
//        result = signingService.generateKeyPair(
//            tenantId = tenantId,
//            category = CryptoConsts.Categories.LEDGER,
//            externalId = expectedExternalId,
//            scheme = scheme,
//            alias = expectedAlias
//        )
//        assertThat(generatedKey.publicKey).isEqualTo(result)
//        Mockito.verify(store, times(1)).save(
//            eq(tenantId),
//            argThat {
//                this as SigningPublicKeySaveContext
//                key == generatedKey &&
//                        alias == expectedAlias &&
//                        externalId == null &&
//                        keyScheme == scheme &&
//                        hsmId == ref.hsmId &&
//                        category == ref.category
//            }
//        )
//        Mockito.verify(store, times(1)).save(
//            eq(tenantId),
//            argThat {
//                this as SigningPublicKeySaveContext
//                key == generatedKey &&
//                        alias == expectedAlias &&
//                        externalId == expectedExternalId &&
//                        keyScheme == scheme &&
//                        hsmId == ref.hsmId &&
//                        category == ref.category
//            }
//        )
//    }
//
//    @Test
//    fun `repository can correctly looks up a signing key by short ids`() {
//        val hashA = ShortHash.of("0123456789AB")
//        val hashB = ShortHash.of("123456789ABC")
//        val keys = setOf(hashA, hashB)
//        val mockCachedKey = mock<SigningKeyInfo> { on { id } doReturn hashA }
//        val queryCap = argumentCaptor<Iterable<SigningKeyRe.CacheKey>>()
//        val cache = mock<Cache<V1SigningKeyStore.CacheKey, SigningKeyInfo>> {
//            on { getAllPresent(queryCap.capture()) } doReturn mapOf(
//                V1SigningKeyStore.CacheKey(
//                    "tenant",
//                    hashA
//                ) to mockCachedKey
//            )
//        }
//        val tenantCap = argumentCaptor<String>()
//        val keyIdsCap = argumentCaptor<List<String>>()
//        val em = mock<EntityManager> {
//            on { createQuery(any(), eq(SigningKeyEntity::class.java)) } doAnswer {
//                mock {
//                    on { setParameter(eq("tenantId"), tenantCap.capture()) } doReturn it
//                    on { setParameter(eq("keyIds"), keyIdsCap.capture()) } doReturn it
//                    on { resultList } doReturn listOf(signingKey)
//                }
//            }
//        }
//
//        val repo = V1CryptoRepositoryImpl(
//            mock { on { createEntityManager() } doReturn em },
//            cache,
//            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
//            mock { on { hash(any<ByteArray>(), any()) } doReturn hash },
//            mock(),
//        )
//
//        repo.lookupSigningKeysByPublicKeyShortHash("tenant", keys)
//
//        val cacheKeys = setOf(V1SigningKeyStore.CacheKey("tenant", hashA), V1SigningKeyStore.CacheKey("tenant", hashB))
//        queryCap.allValues.single().forEach {
//            assertThat(it in cacheKeys)
//        }
//        assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
//        assertThat(keyIdsCap.allValues.single()).isEqualTo(listOf(hashB.value))
//    }

//    @Test
//    fun `repository correctly looks up a signing key by full ids when needs both cache and database`() {
//        val hashA = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "0123456789AB".toByteArray())
//        val hashB = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "123456789ABC".toByteArray())
//        val shortA = ShortHash.of(hashA)
//        val shortB = ShortHash.of(hashB)
//        val keys = setOf(hashA, hashB)
//        val queryCap = argumentCaptor<Iterable<V1SigningKeyStore.CacheKey>>()
//        val mockCachedKey = mock<SigningKeyInfo> { on { fullId } doReturn hashA }
//        val cache = mock<Cache<V1SigningKeyStore.CacheKey, SigningKeyInfo>> {
//            on { getAllPresent(queryCap.capture()) } doReturn mapOf(
//                V1SigningKeyStore.CacheKey(
//                    "tenant",
//                    shortA
//                ) to mockCachedKey
//            )
//        }
//        val tenantCap = argumentCaptor<String>()
//        val fullIdsCap = argumentCaptor<List<String>>()
//        val em = mock<EntityManager> {
//            on { createQuery(any(), eq(SigningKeyEntity::class.java)) } doAnswer {
//                mock {
//                    on { setParameter(eq("tenantId"), tenantCap.capture()) } doReturn it
//                    on { setParameter(eq("fullKeyIds"), fullIdsCap.capture()) } doReturn it
//                    on { resultList } doReturn listOf(signingKey)
//                }
//            }
//        }
//
//        val repo = V1CryptoRepositoryImpl(
//            mock { on { createEntityManager() } doReturn em },
//            cache,
//            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
//            mock { on { hash(any<ByteArray>(), any()) } doReturn hash },
//            mock(),
//        )
//
//        repo.lookupSigningKeysByPublicKeyHashes("tenant", keys)
//
//        val cacheKeys = setOf(V1SigningKeyStore.CacheKey("tenant", shortA), V1SigningKeyStore.CacheKey("tenant", shortB))
//        queryCap.allValues.single().forEach {
//            assertThat(it in cacheKeys)
//        }
//        assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
//        assertThat(fullIdsCap.allValues.single()).isEqualTo(listOf(hashB.toString()))
//    }

}