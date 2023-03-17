package net.corda.crypto.softhsm.impl

import com.github.benmanes.caffeine.cache.Cache
import java.time.Instant
import java.util.Random
import javax.persistence.EntityManager
import net.corda.crypto.cipher.suite.GeneratedPublicKey
import net.corda.crypto.cipher.suite.GeneratedWrappedKey
import net.corda.crypto.cipher.suite.schemes.RSA_TEMPLATE
import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.ShortHash
import net.corda.crypto.persistence.SigningCachedKey
import net.corda.crypto.persistence.SigningPublicKeySaveContext
import net.corda.crypto.persistence.SigningWrappedKeySaveContext
import net.corda.crypto.persistence.WrappingKeyInfo
import net.corda.crypto.persistence.db.model.SigningKeyEntity
import net.corda.crypto.persistence.db.model.SigningKeyEntityStatus
import net.corda.crypto.persistence.impl.toSigningCachedKey
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.assertNotNull


class TestV1CryptoRepositoryImpl {

    @Test
    fun `JPA equality on primary key only rule for WrappingKeyEntities`() {
        val alpha1 = WrappingKeyEntity("alpha", Instant.now(), 1, "DES", byteArrayOf())
        val alpha2 = WrappingKeyEntity("alpha", Instant.now(), 2, "AES", byteArrayOf())
        val beta = WrappingKeyEntity("beta", Instant.now(), 42, "DES", byteArrayOf())
        assertThat(alpha1).isEqualTo(alpha2)
        assertThat(alpha1).isNotEqualTo(beta)
    }

    @Test
    fun `save a wrapping key`() {
        val stored = ArrayList<WrappingKeyEntity>()
        val em = mock<EntityManager> {
            on { persist(any()) } doAnswer {
                stored.add(it.getArgument(0))
                Unit
            }
            on { find<WrappingKeyEntity>(any(), any()) } doAnswer { stored.first() }
            on { transaction } doReturn mock()
        }
        val repo = V1CryptoRepositoryImpl(
            mock {
                on { createEntityManager() } doReturn em
            },
            mock(),
            mock(),
            mock(),
            mock(),
        )
        val wrappingKeyInfo = WrappingKeyInfo(1, "caesar", byteArrayOf())
        repo.saveWrappingKey("a", wrappingKeyInfo)
        val retrievedWrappingKeyInfo = repo.findWrappingKey("a")
        assertNotNull(retrievedWrappingKeyInfo)
        assertThat(wrappingKeyInfo.encodingVersion).isEqualTo(retrievedWrappingKeyInfo.encodingVersion)
    }

    @ParameterizedTest
    @ValueSource(classes = [SigningPublicKeySaveContext::class, SigningWrappedKeySaveContext::class])
    fun `repository can save a signing key`(contextType: Class<*>) {
        var stored: SigningKeyEntity? = null
        val secureHash = SecureHashImpl("", "1".toByteArray())
        val em = mock<EntityManager> {
            on { persist(any()) } doAnswer {
                stored = it.getArgument(0)
            }
        }
        val repo = V1CryptoRepositoryImpl(
            mock { on { createEntityManager() } doReturn em },
            mock { on { build<V1CryptoRepositoryImpl.CacheKey, SigningCachedKey>(any(), any()) } doReturn mock() },
            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
            mock { on { hash(any<ByteArray>(), any()) } doReturn secureHash },
            mock(),
        )

        val context = if (contextType == SigningPublicKeySaveContext::class.java) {
            mock<SigningPublicKeySaveContext> {
                val generatedKey = mock<GeneratedPublicKey> { on { publicKey } doReturn mock() }
                on { key } doReturn generatedKey
                on { alias } doReturn "alias"
                on { category } doReturn "category"
                on { keyScheme } doReturn RSA_TEMPLATE.makeScheme("R3")
                on { externalId } doReturn "externalId"
                on { hsmId } doReturn "hsmId"
            }
        } else {
            mock<SigningWrappedKeySaveContext> {
                val generatedKey = mock<GeneratedWrappedKey> { on { publicKey } doReturn mock() }
                on { key } doReturn generatedKey
                on { alias } doReturn "alias"
                on { category } doReturn "category"
                on { keyScheme } doReturn RSA_TEMPLATE.makeScheme("R3")
                on { externalId } doReturn "externalId"
                on { hsmId } doReturn "hsmId"
            }
        }

        repo.saveSigningKey("a", context)

        verify(em).persist(any())
        assertThat(stored!!.tenantId).isEqualTo("a")
        assertThat(stored!!.fullKeyId).isEqualTo(secureHash.toString())
    }

    // These are for the signing key tests
    private val random = Random(0)
    private val hash = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32).also(random::nextBytes))
    private val signingKey = SigningKeyEntity(
        "tenant",
        "0123456789AB",
        hash.toString(),
        Instant.ofEpochMilli(1),
        "category",
        "schemeCodeName",
        "1".toByteArray(),
        "2".toByteArray(),
        0,
        "masterKeyAlias",
        "alias",
        "hsmAlias",
        "externalId",
        "hsmId",
        SigningKeyEntityStatus.NORMAL,
    )

    @Test
    fun `repository can find a saved signing key by alias`() {
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(SigningKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(anyString(), anyString()) } doReturn it
                    on { setParameter(anyString(), anyCollection<String>()) } doReturn it
                    on { resultList } doReturn listOf(signingKey)
                }
            }
        }

        val repo = V1CryptoRepositoryImpl(
            mock { on { createEntityManager() } doReturn em },
            mock { on { build<V1CryptoRepositoryImpl.CacheKey, SigningCachedKey>(any(), any()) } doReturn mock() },
            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
            mock { on { hash(any<ByteArray>(), any()) } doReturn hash },
            mock(),
        )

        val ret = repo.findSigningKey("tenant", "alias")
        assertNotNull(ret)
        assertThat(ret).usingRecursiveComparison().isEqualTo(signingKey.toSigningCachedKey())
    }

    // Note some keys will be in the cache and some in the database.  We expect all to be returned
    @Test
    fun `repository can correctly looks up a signing key by short ids`() {
        val hashA = ShortHash.of("0123456789AB")
        val hashB = ShortHash.of("123456789ABC")
        val keys = setOf(hashA, hashB)
        val mockCachedKey = mock<SigningCachedKey> { on { id } doReturn hashA }
        val cache = mock<Cache<V1CryptoRepositoryImpl.CacheKey, SigningCachedKey>> {
            on { getAllPresent(any()) } doReturn mapOf(V1CryptoRepositoryImpl.CacheKey("tenant", hashA) to mockCachedKey)
        }
        val tenantCap = argumentCaptor<String>()
        val keyIdsCap = argumentCaptor<List<String>>()
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(SigningKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(eq("tenantId"), tenantCap.capture()) } doReturn it
                    on { setParameter(eq("keyIds"), keyIdsCap.capture()) } doReturn it
                    on { resultList } doReturn listOf(signingKey)
                }
            }
        }

        val repo = V1CryptoRepositoryImpl(
            mock { on { createEntityManager() } doReturn em },
            mock { on { build<V1CryptoRepositoryImpl.CacheKey, SigningCachedKey>(any(), any()) } doReturn cache },
            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
            mock { on { hash(any<ByteArray>(), any()) } doReturn hash },
            mock(),
        )

        repo.lookupSigningKeysByIds("tenant", keys)

        val cacheKeys = setOf(V1CryptoRepositoryImpl.CacheKey("tenant", hashA), V1CryptoRepositoryImpl.CacheKey("tenant", hashB))
        verify(cache).getAllPresent(eq(cacheKeys))
        assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
        assertThat(keyIdsCap.allValues.single()).isEqualTo(listOf(hashB.value))
    }

    @Test
    fun `repository can correctly looks up a signing key by full ids`() {
        val hashA = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "0123456789AB".toByteArray())
        val hashB = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, "123456789ABC".toByteArray())
        val shortA = ShortHash.of(hashA)
        val shortB = ShortHash.of(hashB)
        val keys = setOf(hashA, hashB)
        val mockCachedKey = mock<SigningCachedKey> { on { fullId } doReturn hashA }
        val cache = mock<Cache<V1CryptoRepositoryImpl.CacheKey, SigningCachedKey>> {
            on { getAllPresent(any()) } doReturn mapOf(V1CryptoRepositoryImpl.CacheKey("tenant", shortA) to mockCachedKey)
        }
        val tenantCap = argumentCaptor<String>()
        val fullIdsCap = argumentCaptor<List<String>>()
        val em = mock<EntityManager> {
            on { createQuery(any(), eq(SigningKeyEntity::class.java)) } doAnswer {
                mock {
                    on { setParameter(eq("tenantId"), tenantCap.capture()) } doReturn it
                    on { setParameter(eq("fullKeyIds"), fullIdsCap.capture()) } doReturn it
                    on { resultList } doReturn listOf(signingKey)
                }
            }
        }

        val repo = V1CryptoRepositoryImpl(
            mock { on { createEntityManager() } doReturn em },
            mock { on { build<V1CryptoRepositoryImpl.CacheKey, SigningCachedKey>(any(), any()) } doReturn cache },
            mock { on { encodeAsByteArray(any()) } doReturn "2".toByteArray() },
            mock { on { hash(any<ByteArray>(), any()) } doReturn hash },
            mock(),
        )

        repo.lookupSigningKeysByFullIds("tenant", keys)

        val cacheKeys = setOf(V1CryptoRepositoryImpl.CacheKey("tenant", shortA), V1CryptoRepositoryImpl.CacheKey("tenant", shortB))
        verify(cache).getAllPresent(eq(cacheKeys))
        assertThat(tenantCap.allValues.single()).isEqualTo("tenant")
        assertThat(fullIdsCap.allValues.single()).isEqualTo(listOf(hashB.toString()))
    }
}