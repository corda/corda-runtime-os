package net.corda.uniqueness.backingstore.jpa.datamodel.tests

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.ClassloaderChangeLog.ChangeLogResourceFiles
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.test.util.time.AutoTickTestClock
import net.corda.uniqueness.backingstore.jpa.datamodel.JPABackingStoreEntities
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessRejectedTransactionEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessStateDetailEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTransactionDetailEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTxAlgoIdKey
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTxAlgoStateRefKey
import net.corda.uniqueness.common.datamodel.UniquenessCheckInternalResult
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
            "net/corda/db/schema/uniqueness/migration/uniqueness-creation-v1.0.xml"
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
            UniquenessCheckInternalResult.RESULT_ACCEPTED_REPRESENTATION
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
}
