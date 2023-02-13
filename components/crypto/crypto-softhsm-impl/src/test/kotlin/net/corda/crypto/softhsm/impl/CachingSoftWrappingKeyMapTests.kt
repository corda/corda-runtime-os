package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.WRAPPING_KEY_ENCODING_VERSION
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * There is no way of unit testing the eviction in timely manner or without having special setup for the cache during
 * testing as by default the eviction is scheduled on an executor, so it's not exactly deterministic.
 */
class CachingSoftWrappingKeyMapTests {
    companion object {
        private lateinit var schemeMetadata: CipherSchemeMetadata

        @JvmStatic
        @BeforeAll
        fun setup() {
            schemeMetadata = CipherSchemeMetadataImpl()
        }
    }

    private val master = WrappingKey.generateWrappingKey(schemeMetadata)
    private val expected1 = WrappingKey.generateWrappingKey(schemeMetadata)

    private val alias1 = "master-alias-1"
    private val now = Instant.now()


    private fun makeCachingSoftWrappingKeyMapWithMockedDatabase(entityManager: EntityManager): CachingSoftWrappingKeyMap {
        val emf = mock<EntityManagerFactory> {
            on { createEntityManager() } doReturn entityManager
        }
        val connectionsFactory = mock<CryptoConnectionsFactory> {
            on { getEntityManagerFactory(any()) } doReturn emf
        }
        val cut = CachingSoftWrappingKeyMap(
            SoftCacheConfig(expireAfterAccessMins = 2, maximumSize = 3),
            master,
            connectionsFactory
        )
        return cut
    }

    @Test
    fun `getWrappingKey should cache requested key using alias as cache key`() {
        val expected2 = WrappingKey.generateWrappingKey(schemeMetadata)
        val alias2 = "master-alias-2"
        val info1 =
            WrappingKeyEntity(
                alias1,
                now,
                WRAPPING_KEY_ENCODING_VERSION,
                expected1.algorithm,
                master.wrap(expected1)
            ) // TODO renaame
        val info2 =
            WrappingKeyEntity(
                alias2,
                now,
                WRAPPING_KEY_ENCODING_VERSION,
                expected2.algorithm,
                master.wrap(expected2)
            ) // TODO renaame
        val entityManager = mock<EntityManager> {
            on { find(WrappingKeyEntity::class.java, alias1) } doReturn info1
            on { find(WrappingKeyEntity::class.java, alias2) } doReturn info2
        }
        val cut = makeCachingSoftWrappingKeyMapWithMockedDatabase(entityManager)
        val key11 = cut.getWrappingKey(alias1)
        assertEquals(expected1, key11)
        val key21 = cut.getWrappingKey(alias2)
        assertEquals(expected2, key21)
        assertNotEquals(key11, key21)

        val key12 = cut.getWrappingKey(alias1)
        assertEquals(expected1, key12)
        val key22 = cut.getWrappingKey(alias2)
        assertEquals(expected2, key22)

        Mockito.verify(entityManager, times(1)).find(WrappingKeyEntity::class.java, alias1)
        Mockito.verify(entityManager, times(1)).find(WrappingKeyEntity::class.java, alias2)
    }

    @Test
    fun `getWrappingKey should throw IllegalArgumentException when encoding version is not recognised`() {
        val badVersion = WRAPPING_KEY_ENCODING_VERSION + 1
        val entity1 = WrappingKeyEntity(alias1, now, badVersion, expected1.algorithm, master.wrap(expected1))
        val entityManager = mock<EntityManager> {
            on { find(WrappingKeyEntity::class.java, alias1) } doReturn entity1
        }
        val cut = makeCachingSoftWrappingKeyMapWithMockedDatabase(entityManager)
        assertThrows<IllegalArgumentException> {
            cut.getWrappingKey(alias1)
        }
    }

    @Test
    fun `getWrappingKey should throw IllegalArgumentException when key algorithm does not match master key`() {
        val entity1 = WrappingKeyEntity(
            alias1,
            now,
            WRAPPING_KEY_ENCODING_VERSION,
            expected1.algorithm + "!",
            master.wrap(expected1)
        )
        val entityManager = mock<EntityManager> {
            on { find(WrappingKeyEntity::class.java, alias1) } doReturn entity1
        }
        val cut = makeCachingSoftWrappingKeyMapWithMockedDatabase(entityManager)
        assertThrows<IllegalArgumentException> {
            cut.getWrappingKey(alias1)
        }
    }

    @Test
    fun `getWrappingKey should throw IllegalStateException when wrapping key is not found`() {
        val entityManager = mock<EntityManager> {
            on { find(WrappingKeyEntity::class.java, alias1) } doReturn null
        }
        val cut = makeCachingSoftWrappingKeyMapWithMockedDatabase(entityManager)
        assertThrows<IllegalStateException> {
            cut.getWrappingKey(alias1)
        }
    }
//
//    @Test
//    fun `putWrappingKey should put to cache using public key as cache key`() {
//        val master = WrappingKey.generateWrappingKey(schemeMetadata)
//        val expected = WrappingKey.generateWrappingKey(schemeMetadata)
//        val alias = "master-alias"
//        val info = WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, expected.algorithm, master.wrap(expected))
//        val store = mock<WrappingKeyStore> {
//            on { findWrappingKey(alias) } doReturn info
//        }
//        val cut = CachingSoftWrappingKeyMap(
//            SoftCacheConfig(expireAfterAccessMins = 2, maximumSize = 3),
//            store,
//            master
//        )
//        cut.putWrappingKey(alias, expected)
//        val key = cut.getWrappingKey(alias)
//        assertEquals(expected, key)
//        Mockito.verify(store, times(1)).saveWrappingKey(any(), any())
//        Mockito.verify(store, never()).findWrappingKey(any())
//    }
//
//    @Test
//    fun `exists should return true whenever key exist in cache or store and false otherwise`() {
//        val master = WrappingKey.generateWrappingKey(CipherSchemeMetadataImpl())
//        val expected1 = WrappingKey.generateWrappingKey(CipherSchemeMetadataImpl())
//        val expected2 = WrappingKey.generateWrappingKey(CipherSchemeMetadataImpl())
//        val alias1 = "master-alias-1"
//        val alias2 = "master-alias-2"
//        val alias3 = "master-alias-3"
//        val info1 = WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, expected1.algorithm, master.wrap(expected1))
//        val info2 = WrappingKeyInfo(WRAPPING_KEY_ENCODING_VERSION, expected2.algorithm, master.wrap(expected2))
//        val store = mock<WrappingKeyStore> {
//            on { findWrappingKey(alias1) } doReturn info1
//            on { findWrappingKey(alias2) } doReturn info2
//            on { findWrappingKey(alias3) } doReturn null
//        }
//        val cut = CachingSoftWrappingKeyMap(
//            SoftCacheConfig(expireAfterAccessMins = 2, maximumSize = 3),
//            store,
//            master
//        )
//        cut.putWrappingKey(alias1, expected1)
//
//        assertTrue(cut.exists(alias1), "Should exist from cache")
//        assertTrue(cut.exists(alias2), "Should exist from store")
//        assertFalse(cut.exists(alias3), "Should not exist")
//    }
}