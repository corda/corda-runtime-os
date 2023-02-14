package net.corda.crypto.softhsm.impl

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import net.corda.crypto.core.aes.WrappingKey
import net.corda.crypto.persistence.CryptoConnectionsFactory
import net.corda.crypto.persistence.db.model.WrappingKeyEntity
import net.corda.crypto.softhsm.SoftCacheConfig
import net.corda.crypto.softhsm.WRAPPING_KEY_ENCODING_VERSION
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * There is no way of unit testing the eviction in timely manner or without having special setup for the cache during
 * testing as by default the eviction is scheduled on an executor, so it's not exactly deterministic.
 */
class CachingSoftWrappingKeyMapTests {
    private val schemeMetadata = CipherSchemeMetadataImpl()
    private val master = WrappingKey.generateWrappingKey(schemeMetadata)
    private val expected1 = WrappingKey.generateWrappingKey(schemeMetadata)
    private val expected2 = WrappingKey.generateWrappingKey(schemeMetadata)

    private val alias1 = "master-alias-1"
    private val alias2 = "master-alias-2"

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

    @Test
    fun `putWrappingKey should put to cache using public key as cache key`() {
        val entityTransaction: EntityTransaction = mock()
        val entityManager = mock<EntityManager> {
            on { transaction } doReturn entityTransaction
        }
        val cut = makeCachingSoftWrappingKeyMapWithMockedDatabase(entityManager)
        cut.putWrappingKey(alias1, expected1)
        val key = cut.getWrappingKey(alias1)
        assertEquals(expected1, key)
        Mockito.verify(entityManager, times(1)).persist(any())
        Mockito.verify(entityManager, never()).find(eq(WrappingKeyEntity::class.java), any())
    }

    @Test
    fun `exists should return true whenever key exist in cache or store and false otherwise`() {
        val alias3 = "master-alias-3"
        val entity1 =
            WrappingKeyEntity(alias1, now, WRAPPING_KEY_ENCODING_VERSION, expected1.algorithm, master.wrap(expected1))
        val entity2 =
            WrappingKeyEntity(alias2, now, WRAPPING_KEY_ENCODING_VERSION, expected2.algorithm, master.wrap(expected2))
        val entityTransaction: EntityTransaction = mock()
        val entityManager = mock<EntityManager> {
            on { transaction } doReturn entityTransaction
            on { find(WrappingKeyEntity::class.java, alias1) } doReturn entity1
            on { find(WrappingKeyEntity::class.java, alias2) } doReturn entity2
            on { find(WrappingKeyEntity::class.java, alias3) } doReturn null
        }
        val cut = makeCachingSoftWrappingKeyMapWithMockedDatabase(entityManager)
        cut.putWrappingKey(alias1, expected1)

        assertTrue(cut.exists(alias1), "Should exist from cache")
        Mockito.verify(entityManager, never()).find(eq(WrappingKeyEntity::class.java), any())
        assertTrue(cut.exists(alias2), "Should exist from store")
        Mockito.verify(entityManager, times(1)).find(eq(WrappingKeyEntity::class.java), any())
        assertFalse(cut.exists(alias3), "Should not exist")
        Mockito.verify(entityManager, times(2)).find(eq(WrappingKeyEntity::class.java), any())

    }
}