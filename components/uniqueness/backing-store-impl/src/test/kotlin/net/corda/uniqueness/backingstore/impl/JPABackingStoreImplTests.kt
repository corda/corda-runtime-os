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
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTxAlgoIdKey
import net.corda.uniqueness.backingstore.jpa.datamodel.UniquenessTxAlgoStateRefKey
import net.corda.uniqueness.datamodel.common.UniquenessConstants
import net.corda.uniqueness.datamodel.impl.UniquenessCheckErrorMalformedRequestImpl
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckResultFailure
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.MultiIdentifierLoadAccess
import org.hibernate.Session
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
import java.util.*
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

    /* These lists act as the database, the data added to these lists will be returned by the multi loads */
    private lateinit var txnDetails: MutableList<UniquenessTransactionDetailEntity>
    private lateinit var stateEntities: MutableList<UniquenessStateDetailEntity>
    private lateinit var errorEntities: MutableList<UniquenessRejectedTransactionEntity>

    private lateinit var dbConnectionManager: DbConnectionManager

    private val groupId = UUID.randomUUID().toString()
    private val aliceIdentity = createTestHoldingIdentity("C=GB, L=London, O=Alice", groupId)

    inner class DummyLifecycle : LifecycleEvent

    @Suppress("ComplexMethod")
    @BeforeEach
    fun init() {
        lifecycleCoordinator = mock()
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn lifecycleCoordinator
        }
        entityTransaction = mock<EntityTransaction>().apply {
            whenever(isActive) doReturn true
        }

        // These lists will act as the "dataset" returned from the database, by default they can be empty
        // as some of the tests are not using these at all
        stateEntities = mutableListOf()
        txnDetails = mutableListOf()
        errorEntities = mutableListOf()

        val stateMultiLoad = mock<MultiIdentifierLoadAccess<UniquenessStateDetailEntity>>().apply {
            whenever(multiLoad(any<List<UniquenessTxAlgoStateRefKey>>())) doReturn stateEntities
        }
        val txMultiLoad = mock<MultiIdentifierLoadAccess<UniquenessTransactionDetailEntity>>().apply {
            whenever(multiLoad(any<List<UniquenessTxAlgoIdKey>>())) doReturn txnDetails
        }

        val txnErrorQuery = mock<TypedQuery<UniquenessRejectedTransactionEntity>>().apply {
            whenever(setParameter(eq("txAlgo"), any())) doReturn this
            whenever(setParameter(eq("txId"), any())) doReturn this
            whenever(resultList) doReturn errorEntities
        }

        val dummySession = mock<Session>().apply {
            // No need do anything here as this will have no effect in a unit test
            whenever(setJdbcBatchSize(any())).thenAnswer {  }

            whenever(byMultipleIds(UniquenessStateDetailEntity::class.java)) doReturn stateMultiLoad
            whenever(byMultipleIds(UniquenessTransactionDetailEntity::class.java)) doReturn txMultiLoad
        }

        entityManager = mock<EntityManager>().apply {
            whenever(transaction) doReturn entityTransaction
            whenever(unwrap(Session::class.java)) doReturn dummySession
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
    inner class BackingStoreApiTests {
        @Test
        fun `Starting backing store starts life cycle coordinator`() {
            backingStoreImpl.start()
            Mockito.verify(lifecycleCoordinator, times(1)).start()
        }

        @Test
        fun `Stopping backing store stops life cycle coordinator`() {
            backingStoreImpl.stop()
            Mockito.verify(lifecycleCoordinator, times(1)).stop()
        }

        @Test
        fun `Getting running life cycle status returns life cycle running status`() {
            backingStoreImpl.isRunning
            Mockito.verify(lifecycleCoordinator, times(1)).isRunning
        }
    }

    @Nested
    inner class EventHandlerTests {
        @Test
        fun `Start event starts following the statuses of the required dependencies`() {
            backingStoreImpl.eventHandler(StartEvent(), lifecycleCoordinator)
            Mockito.verify(lifecycleCoordinator).followStatusChangesByName(
                eq(setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>()))
            )
            Mockito.verify(dbConnectionManager, times(1)).start()
        }

        @Test
        fun `Stop event stops the required dependency`() {
            backingStoreImpl.eventHandler(StopEvent(), lifecycleCoordinator)
            Mockito.verify(dbConnectionManager, times(1)).stop()
        }

        @Test
        fun `Registration status change event registers jpa entries`() {
            val lifeCycleStatus = LifecycleStatus.UP
            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), lifeCycleStatus), lifecycleCoordinator)

            Mockito.verify(jpaEntitiesRegistry, times(1)).register(any(), any())
            Mockito.verify(jpaEntitiesRegistry, times(1)).get(CordaDb.Uniqueness.persistenceUnitName)
            Mockito.verify(lifecycleCoordinator, times(1)).updateStatus(lifeCycleStatus)
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
            backingStoreImpl.session(aliceIdentity) { }
            Mockito.verify(entityManager, times(1)).close()
        }

        @Test
        fun `Session closes entity manager even when exception occurs`() {
            assertThrows<java.lang.RuntimeException> {
                backingStoreImpl.session(aliceIdentity) { throw java.lang.RuntimeException("test exception") }
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
            backingStoreImpl.session(aliceIdentity) { session ->
                session.executeTransaction { _, _ -> }
            }

            Mockito.verify(entityTransaction, times(1)).begin()
            Mockito.verify(entityTransaction, times(1)).commit()
            Mockito.verify(entityManager, times(1)).close()
        }

        @Test
        fun `Throw if no error detail is available for a failed transaction`() {
            // Prepare a rejected transaction
            txnDetails.add(UniquenessTransactionDetailEntity(
                "SHA-256",
                "0xA1".toByteArray(),
                LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                UniquenessConstants.RESULT_REJECTED_REPRESENTATION
            ))

            // Expect an exception because no error details is available from the mock.
            assertThrows<IllegalStateException> {
                backingStoreImpl.session(aliceIdentity) { session ->
                    session.getTransactionDetails(List(1) { SecureHashUtils.randomSecureHash() })
                }
            }
        }

        @Test
        fun `Retrieve correct failed status without exceptions when both tx details and rejection details are present`() {
            val txId = SecureHashUtils.randomSecureHash()
            // Prepare a rejected transaction
            txnDetails.add(UniquenessTransactionDetailEntity(
                "SHA-256",
                txId.bytes,
                LocalDate.parse("2099-12-12").atStartOfDay().toInstant(ZoneOffset.UTC),
                LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC),
                UniquenessConstants.RESULT_REJECTED_REPRESENTATION
            ))

            errorEntities.add(UniquenessRejectedTransactionEntity(
                "SHA-256",
                txId.bytes,
                jpaBackingStoreObjectMapper().writeValueAsBytes(
                    UniquenessCheckErrorMalformedRequestImpl("Error")
                )
            ))

            backingStoreImpl.session(aliceIdentity) { session ->
                val txResult = session.getTransactionDetails(listOf(txId))[txId]?.result!!

                assertThat(txResult).isInstanceOf(UniquenessCheckResultFailure::class.java)
                assertThat((txResult as UniquenessCheckResultFailure).error)
                    .isInstanceOf(UniquenessCheckErrorMalformedRequest::class.java)
            }
        }
    }
}
