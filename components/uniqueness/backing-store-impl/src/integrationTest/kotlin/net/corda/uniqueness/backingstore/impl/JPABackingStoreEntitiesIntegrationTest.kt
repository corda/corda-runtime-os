package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.datamodel.common.UniquenessConstants.RESULT_ACCEPTED_REPRESENTATION
import org.hibernate.Session
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.persistence.EntityManagerFactory

@Suppress("FunctionNaming")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreEntitiesIntegrationTest {
    private val dbConfig = DbUtils.getEntityManagerConfiguration("uniqueness_db")
    private val entityManagerFactory: EntityManagerFactory

    // Test clock is restricted to milliseconds because this is the granularity stored in the DB
    private val testClock =
        AutoTickTestClock(Instant.now().truncatedTo(ChronoUnit.MILLIS), Duration.ofMillis(1))

    private companion object {
        private const val MIGRATION_FILE_LOCATION =
            "net/corda/db/schema/vnode-uniqueness/migration/vnode-uniqueness-creation-v1.0.xml"
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts, and initialises
     * the entityManagerFactory.
     */
    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf(MIGRATION_FILE_LOCATION),
                    DbSchema::class.java.classLoader
                )
            )
        )
        dbConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            "test_unit",
            JPABackingStoreEntities.classes.toList(),
            dbConfig
        )
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        dbConfig.close()
        entityManagerFactory.close()
    }

    @Test
    fun `can persist and read back unconsumed state details entity`() {
        val issueTxId = SecureHashUtils.randomSecureHash()
        val stateDetails = UniquenessStateDetailEntity(
            issueTxId.algorithm,
            issueTxId.bytes,
            0,
            null,
            null
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(stateDetails)
        }

        val retrieved = entityManagerFactory.createEntityManager().find(
            UniquenessStateDetailEntity::class.java,
            UniquenessTxAlgoStateRefKey(issueTxId.algorithm, issueTxId.bytes)
        )

        assertEquals(stateDetails, retrieved)
    }

    @Test
    fun `can persist and read back consumed state details entity`() {
        val issueTxId = SecureHashUtils.randomSecureHash()
        val consumingTxId = SecureHashUtils.randomSecureHash()
        val stateDetails = UniquenessStateDetailEntity(
            issueTxId.algorithm,
            issueTxId.bytes,
            0,
            consumingTxId.algorithm,
            consumingTxId.bytes
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(stateDetails)
        }

        val retrieved = entityManagerFactory.createEntityManager().find(
            UniquenessStateDetailEntity::class.java,
            UniquenessTxAlgoStateRefKey(issueTxId.algorithm, issueTxId.bytes)
        )

        assertEquals(stateDetails, retrieved)
    }

    @Test
    fun `can persist and read back transaction details entity`() {
        val txId = SecureHashUtils.randomSecureHash()
        val txDetails = UniquenessTransactionDetailEntity(
            txId.algorithm,
            txId.bytes,
            testClock.instant(),
            testClock.instant(),
            RESULT_ACCEPTED_REPRESENTATION
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(txDetails)
        }

        val retrieved = entityManagerFactory.createEntityManager().find(
            UniquenessTransactionDetailEntity::class.java,
            UniquenessTxAlgoIdKey(txId.algorithm, txId.bytes)
        )

        assertEquals(txDetails, retrieved)
    }

    @Test
    fun `can persist and read back rejected transaction entity`() {
        val txId = SecureHashUtils.randomSecureHash()
        val rejectedTx = UniquenessRejectedTransactionEntity(
            txId.algorithm,
            txId.bytes,
            SecureHashUtils.randomBytes()
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(rejectedTx)
        }

        val retrieved = entityManagerFactory.createEntityManager().find(
            UniquenessRejectedTransactionEntity::class.java,
            UniquenessTxAlgoIdKey(txId.algorithm, txId.bytes)
        )

        assertEquals(rejectedTx, retrieved)
    }

    @Test
    fun `equality consistency - state details entity`() {
        val issueTxId = SecureHashUtils.randomSecureHash()
        val consumingTxId = SecureHashUtils.randomSecureHash()

        assertEqualityConsistency(
            UniquenessStateDetailEntity(
                issueTxId.algorithm,
                issueTxId.bytes,
                0,
                consumingTxId.algorithm,
                consumingTxId.bytes),
            UniquenessTxAlgoStateRefKey(
                issueTxId.algorithm,
                issueTxId.bytes,
                0)
        )
    }

    @Test
    fun `equality consistency - transaction details entity`() {
        val txId = SecureHashUtils.randomSecureHash()

        assertEqualityConsistency(
            UniquenessTransactionDetailEntity(
                txId.algorithm,
                txId.bytes,
                testClock.instant(),
                testClock.instant(),
                RESULT_ACCEPTED_REPRESENTATION),
            UniquenessTxAlgoIdKey(txId.algorithm, txId.bytes)
        )
    }

    @Test
    fun `equality consistency - rejected transaction entity`() {
        val txId = SecureHashUtils.randomSecureHash()

        assertEqualityConsistency(
            UniquenessRejectedTransactionEntity(
                txId.algorithm,
                txId.bytes,
                SecureHashUtils.randomBytes()
            ),
            UniquenessTxAlgoIdKey(txId.algorithm, txId.bytes)
        )
    }

    private fun assertEqualityConsistency(entity: Any, pk: Any) {
        val clazz = entity::class.java
        val tuples: MutableSet<Any> = HashSet()

        assertFalse(tuples.contains(entity))
        tuples.add(entity)
        assertTrue(tuples.contains(entity))

        entityManagerFactory.createEntityManager().transaction { entityManager ->
            entityManager.persist(entity)
            entityManager.flush()
            assertTrue(
                tuples.contains(entity),
                "Entity is not found in set after it's persisted."
            )
        }
        assertTrue(tuples.contains(entity))
        entityManagerFactory.createEntityManager().transaction { entityManager ->
            val entityProxy = entityManager.getReference(clazz, pk)
            assertNotNull(entity)
            assertTrue(
                entityProxy.equals(entity),
                "Entity proxy is not equal with the entity.",
            )
        }

        entityManagerFactory.createEntityManager().transaction { entityManager ->
            val entityProxy = entityManager.getReference(clazz, pk)
            assertNotNull(entity)
            assertTrue(
                entity.equals(entityProxy),
                "Entity is not equal with the entity proxy."
            )
        }

        entityManagerFactory.createEntityManager().transaction { entityManager ->
            val newEntity = entityManager.merge(entity)
            assertTrue(
                tuples.contains(newEntity),
                "Entity is not found in the Set after it's merged."
            )
        }

        entityManagerFactory.createEntityManager().transaction { entityManager ->
            entityManager.unwrap(Session::class.java).update(entity)
            assertTrue(
                tuples.contains(entity),
                "Entity is not found in the Set after it's reattached."
            )
        }

        entityManagerFactory.createEntityManager().transaction { entityManager ->
            val newEntity = entityManager.find(clazz, pk)
            assertTrue(
                tuples.contains(newEntity),
                "Entity is not found in the Set after it's loaded in a different Persistence Context."
            )
        }

        entityManagerFactory.createEntityManager().transaction { entityManager ->
            val newEntity = entityManager.getReference(clazz, pk)
            assertTrue(
                tuples.contains(newEntity),
                "Entity is not found in the Set after it's loaded as a proxy in a different Persistence Context."
            )
        }

        val deletedEntity = entityManagerFactory.createEntityManager().transaction { entityManager ->
            val _entity = entityManager.getReference(clazz, pk)
            entityManager.remove(_entity)
            _entity
        }

        assertTrue(
            tuples.contains(deletedEntity),
            "Entity is not found in the Set after it's deleted."
        )
    }
}
