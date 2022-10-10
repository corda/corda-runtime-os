package net.corda.uniqueness.backingstore.impl

import net.corda.crypto.testkit.SecureHashUtils
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessRejectedTransactionEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessStateDetailEntity
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTransactionDetailEntity
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import java.sql.Connection
import java.time.LocalDate
import java.time.ZoneOffset
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplTests {
    private lateinit var backingStoreImpl: JPABackingStoreImpl

    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var entityManager: EntityManager
    private lateinit var entityTransaction: EntityTransaction
    private lateinit var entityManagerFactory: EntityManagerFactory
    private lateinit var dummyDataSource: CloseableDataSource
    private lateinit var jpaEntitiesRegistry: JpaEntitiesRegistry

    private lateinit var txnDetails: List<UniquenessTransactionDetailEntity>
    private lateinit var txnDetailQuery: TypedQuery<UniquenessTransactionDetailEntity>
    private lateinit var stateEntities: List<UniquenessStateDetailEntity>
    private lateinit var stateDetailSelectQuery: TypedQuery<UniquenessStateDetailEntity>
    private lateinit var txnErrors: List<UniquenessRejectedTransactionEntity>
    private lateinit var txnErrorQuery: TypedQuery<UniquenessRejectedTransactionEntity>
    private lateinit var dbConnectionManager: DbConnectionManager

    companion object {
        val TEST_IDENTITY = createTestHoldingIdentity("C=GB, L=London, O=Alice", "Test Group")
    }

    inner class DummyLifecycle : LifecycleEvent

    @Suppress("ComplexMethod")
    @BeforeEach
    fun init() {
        lifecycleCoordinator = mock<LifecycleCoordinator>()
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn lifecycleCoordinator
        }
        entityTransaction = mock<EntityTransaction>().apply {
            whenever(isActive) doReturn true
        }

        stateEntities = mock<List<UniquenessStateDetailEntity>>()
        stateDetailSelectQuery = mock<TypedQuery<UniquenessStateDetailEntity>>().apply {
            whenever(setParameter(eq("txAlgo"), any())) doReturn this
            whenever(setParameter(eq("txId"), any())) doReturn this
            whenever(setParameter(eq("stateIndex"), any())) doReturn this
            whenever(resultList) doReturn stateEntities
        }

        txnDetails = mock<List<UniquenessTransactionDetailEntity>>()
        txnDetailQuery = mock<TypedQuery<UniquenessTransactionDetailEntity>>().apply {
            whenever(setParameter(eq("txAlgo"), any())) doReturn this
            whenever(setParameter(eq("txId"), any())) doReturn this
            whenever(resultList) doReturn txnDetails
        }

        txnErrors = mock<List<UniquenessRejectedTransactionEntity>>()
        txnErrorQuery = mock<TypedQuery<UniquenessRejectedTransactionEntity>>().apply {
            whenever(setParameter(eq("txAlgo"), any())) doReturn this
            whenever(setParameter(eq("txId"), any())) doReturn this
            whenever(resultList) doReturn txnErrors
        }

        entityManager = mock<EntityManager>().apply {
            whenever(transaction) doReturn entityTransaction
            whenever(
                createNamedQuery("UniquenessStateDetailEntity.select", UniquenessStateDetailEntity::class.java)
            ) doReturn stateDetailSelectQuery
            whenever(
                createNamedQuery(
                    "UniquenessTransactionDetailEntity.select",
                    UniquenessTransactionDetailEntity::class.java
                )
            ) doReturn txnDetailQuery
            whenever(
                createNamedQuery(
                    "UniquenessRejectedTransactionEntity.select",
                    UniquenessRejectedTransactionEntity::class.java
                )
            ) doReturn txnErrorQuery
        }

        entityManagerFactory = mock<EntityManagerFactory>().apply {
            whenever(createEntityManager()) doReturn entityManager
        }

        dummyDataSource = mock<CloseableDataSource>().apply {
            whenever(connection) doReturn mock<Connection>()
        }

        jpaEntitiesRegistry = mock<JpaEntitiesRegistry>().apply {
            whenever(get(any())) doReturn mock<JpaEntitiesSet>()
        }

        dbConnectionManager = mock<DbConnectionManager>().apply {
            whenever(getClusterDataSource()) doReturn dummyDataSource
            whenever(getOrCreateEntityManagerFactory(any(), any(), any())) doReturn entityManagerFactory
        }

        backingStoreImpl = JPABackingStoreImpl(
            lifecycleCoordinatorFactory,
            jpaEntitiesRegistry,
            dbConnectionManager
        )
    }

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `Starting backing store invokes life cycle start`() {
            backingStoreImpl.start()
            Mockito.verify(lifecycleCoordinator).start()
        }

        @Test
        fun `Stopping backing store invokes life cycle stop`() {
            backingStoreImpl.stop()
            Mockito.verify(lifecycleCoordinator).stop()
        }

        @Test
        fun `Get running life cycle status`() {
            backingStoreImpl.isRunning
            Mockito.verify(lifecycleCoordinator).isRunning
        }
    }

    @Nested
    inner class EventHandlerTests {
        @Test
        fun `Start event starts following the statuses of the required dependencies`() {
            backingStoreImpl.eventHandler(StartEvent(), lifecycleCoordinator)
            Mockito.verify(lifecycleCoordinator).followStatusChangesByName(
                eq(
                    setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>())
                )
            )
        }

        @Test
        fun `Stop event TODO`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(StopEvent(), mockCoordinator)
        }

        @Test
        fun `Registration status change event registers jpa entries`() {
            val lifeCycleStatus = LifecycleStatus.UP
            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), lifeCycleStatus), lifecycleCoordinator)

            Mockito.verify(jpaEntitiesRegistry, times(1)).register(any(), any())
            Mockito.verify(jpaEntitiesRegistry, times(1)).get(CordaDb.Uniqueness.persistenceUnitName)
            Mockito.verify(lifecycleCoordinator).updateStatus(lifeCycleStatus)
        }

        @Test
        fun `Unknown life cycle event does not throw exception`() {
            assertDoesNotThrow { backingStoreImpl.eventHandler(DummyLifecycle(), mock()) }
        }
    }

    @Nested
    inner class ClosingSessionBlockTests {
        @BeforeEach
        fun init() {
            backingStoreImpl.eventHandler(
                RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator
            )
        }

        @Test
        fun `Session always closes entity manager after use`() {
            backingStoreImpl.session(TEST_IDENTITY) { }
            Mockito.verify(entityManager, times(1)).close()
        }

        @Test
        fun `Session closes entity manager even when exception occurs`() {
            assertThrows<java.lang.RuntimeException> {
                backingStoreImpl.session(TEST_IDENTITY) { throw java.lang.RuntimeException("test exception") }
            }
            Mockito.verify(entityManager, times(1)).close()
        }
    }

    @Nested
    inner class TransactionTests {
        @BeforeEach
        fun init() {
            backingStoreImpl.eventHandler(
                RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator
            )
        }

        @Test
        fun `Executing transaction runs with transaction begin and commit`() {
            backingStoreImpl.session(TEST_IDENTITY) { session ->
                session.executeTransaction { _, _ -> }
            }

            Mockito.verify(entityTransaction, times(1)).begin()
            Mockito.verify(entityTransaction, times(1)).commit()
            Mockito.verify(entityManager, times(1)).close()
        }

        @Test
        fun `Throw if no error detail is available for a failed transaction`() {
            // Prepare a rejected transaction
            txnDetails.apply {
                whenever(firstOrNull()) doReturn UniquenessTransactionDetailEntity(
                    "SHA-256",
                    "0xA1".toByteArray(),
                    LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                    LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                    UniquenessConstants.RESULT_REJECTED_REPRESENTATION
                )
            }

            // Expect an exception because no error details is available from the mock.
            assertThrows<IllegalStateException> {
                backingStoreImpl.session(TEST_IDENTITY) { session ->
                    session.getTransactionDetails(List(1) { SecureHashUtils.randomSecureHash() } )
                }
            }
        }
    }
}