package net.corda.uniqueness.backingstore.impl


import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.lifecycle.*
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.sql.Connection
import javax.persistence.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue


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

    // NOTE: While expecting refactoring around createDefaultUniquenessDb(), it's mocked for testing
    //  convenience. Since it's a final class, MockMaker's been added under resources with content "mock-maker-inline".
    private lateinit var schemaMigrator: LiquibaseSchemaMigratorImpl
    private lateinit var dbConnectionManager: DbConnectionManager

    inner class DummyLifecycle : LifecycleEvent

    class DummyException(message: String) : Exception(message)

    @BeforeEach
    fun init() {
        lifecycleCoordinator = mock<LifecycleCoordinator>()
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn lifecycleCoordinator
        }
        entityTransaction = mock<EntityTransaction>().apply {
            whenever(isActive) doReturn true
        }
        entityManager = mock<EntityManager>().apply {
            whenever(transaction) doReturn entityTransaction
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
        schemaMigrator = mock<LiquibaseSchemaMigratorImpl>()
        dbConnectionManager = mock<DbConnectionManager>().apply {
            whenever(getClusterDataSource()) doReturn dummyDataSource
            whenever(getOrCreateEntityManagerFactory(any(), any(), any())) doReturn entityManagerFactory
        }

        backingStoreImpl = JPABackingStoreImpl(
            lifecycleCoordinatorFactory,
            jpaEntitiesRegistry,
            dbConnectionManager,
            schemaMigrator = schemaMigrator
        )
    }

    @Nested
    inner class LifeCycleTests {
        @Test
        fun `Starting backing store invokes life cycle start`() {
            Mockito.verify(lifecycleCoordinator, never()).start()
            backingStoreImpl.start()
            Mockito.verify(lifecycleCoordinator).start()
        }

        @Test
        fun `Stopping backing store invokes life cycle stop`() {
            Mockito.verify(lifecycleCoordinator, never()).stop()
            backingStoreImpl.stop()
            Mockito.verify(lifecycleCoordinator).stop()
        }

        @Test
        fun `Get running life cycle status`() {
            backingStoreImpl.isRunning
            Mockito.verify(lifecycleCoordinator).isRunning
        }

        @Test
        fun `Closing backing store invokes life cycle stop`() {
            Mockito.verify(lifecycleCoordinator, never()).stop()
            backingStoreImpl.eventHandler(
                RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator
            )
            backingStoreImpl.close()
            Mockito.verify(lifecycleCoordinator).stop()
        }
    }

    @Nested
    inner class EventHandlerTests {
        @Test
        fun `StartEvent register and start all`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(StartEvent(), mockCoordinator)

            Mockito.verify(mockCoordinator).followStatusChangesByName(
                eq(
                    setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>())
                )
            )
            // FIXME: it's failing. review the expected behaviour and update test accordingly
            assertTrue(backingStoreImpl.isRunning)
            Mockito.verify(lifecycleCoordinator).updateStatus(LifecycleStatus.UP)
        }

        @Test
        fun `StopEvent stops all`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(StopEvent(), mockCoordinator)

            Mockito.verify(mockCoordinator).followStatusChangesByName(
                eq(
                    setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>())
                )
            )
            // FIXME: it's failing. review the expected behaviour and update test accordingly
            assertFalse(backingStoreImpl.isRunning)
            Mockito.verify(lifecycleCoordinator).updateStatus(LifecycleStatus.DOWN)
        }

        @Test
        fun `Registration status change event instantiates entity manager when event status is up`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), mockCoordinator)
            Mockito.verify(dbConnectionManager).getOrCreateEntityManagerFactory(any(), any(), any())
        }

        @Test
        fun `Registration status change event does not instantiate entity manager if event status is not up`() {
            val mockCoordinator = mock<LifecycleCoordinator>()
            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.DOWN), mockCoordinator)
            Mockito.verify(mockCoordinator).updateStatus(LifecycleStatus.DOWN)
            backingStoreImpl.eventHandler(RegistrationStatusChangeEvent(mock(), LifecycleStatus.ERROR), mockCoordinator)
            Mockito.verify(mockCoordinator).updateStatus(LifecycleStatus.ERROR)
        }

        @Test
        fun `Unknown life cycle event does not throw exception`() {
            assertDoesNotThrow {
                backingStoreImpl.eventHandler(DummyLifecycle(), mock())
            }
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
        fun `Session closes entity manager after use`() {
            backingStoreImpl.session { }
            Mockito.verify(entityManager).close()
        }

        @Test
        fun `Session closes entity manager even when exception occurs`() {
            assertThrows<java.lang.RuntimeException> {
                backingStoreImpl.session { throw java.lang.RuntimeException("test exception") }
            }
            Mockito.verify(entityManager).close()
        }
    }

    @Nested
    inner class TransactionTests {
        // FIXME: a temporary constant until MAX_RETRIES is configurable.
        private val maxRetriesCnt = 10
        private val expectedTxnExceptions = mapOf(
            "EntityExistsException" to EntityExistsException(),
            "RollbackException" to RollbackException(),
            "OptimisticLockException" to OptimisticLockException()
        )

        @BeforeEach
        fun init() {
            backingStoreImpl.eventHandler(
                RegistrationStatusChangeEvent(mock(), LifecycleStatus.UP), lifecycleCoordinator
            )
        }

        @Test
        fun `Executing transaction runs with transaction begin and commit`() {
            backingStoreImpl.session { session ->
                session.executeTransaction { _, _ -> }
            }

            Mockito.verify(entityTransaction, times(1)).begin()
            Mockito.verify(entityTransaction, times(1)).commit()
            Mockito.verify(entityManager, times(1)).close()
        }

        @ParameterizedTest
        @ValueSource(strings = ["EntityExistsException", "RollbackException", "OptimisticLockException"])
        fun `Executing transaction retries upon expected exceptions`(exception: String) {
            assertThrows<IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ -> throw expectedTxnExceptions[exception]!! }
                }
            }

            // FIXME: this fails because the logic executes MAX_RETRIES + 1 times.
            //  This should be refactored to get the value from the configuration which doesn't exist yet.
            Mockito.verify(entityTransaction, times(maxRetriesCnt)).begin()
            Mockito.verify(entityTransaction, never()).commit()
        }

        @Test
        fun `Executing transaction does not retry upon unexpected exception`() {
            assertThrows<DummyException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ -> throw DummyException("dummy exception") }
                }
            }
            Mockito.verify(entityTransaction, times(1)).begin()
            Mockito.verify(entityTransaction, never()).commit()
        }

        @Test
        fun `Executing transaction triggers rollback upon receiving expected exception if transaction is active`() {
            assertThrows<java.lang.IllegalStateException> {
                backingStoreImpl.session { session ->
                    session.executeTransaction { _, _ -> throw EntityExistsException() }
                }
            }
            Mockito.verify(entityTransaction, times(maxRetriesCnt)).rollback()
        }

        @Test
        fun `Get state details`() {
            throw NotImplementedError()
//            backingStoreImpl.session { session -> session.getStateDetails() }
        }

        @Test
        fun `Get transaction details`() {
            throw NotImplementedError()
//            backingStoreImpl.session { session -> session.getTransactionDetails() }
        }

        @Test
        fun `Get transaction errors`() {
            throw NotImplementedError()
//            backingStoreImpl.session { session -> session.getTransactionDetails() }
        }
    }
}