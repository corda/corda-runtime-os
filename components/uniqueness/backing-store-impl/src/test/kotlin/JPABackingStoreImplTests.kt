package net.corda.uniqueness.backingstore.impl


import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.lifecycle.*
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.junit.jupiter.api.*
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.sql.Connection
import javax.persistence.EntityManagerFactory
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JPABackingStoreImplTests {
    private lateinit var backingStoreImpl: JPABackingStoreImpl

    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    private val dummyDataSource = mock<CloseableDataSource>().apply {
        whenever(connection) doReturn mock<Connection>()
    }
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry>().apply {
        whenever(get(any())) doReturn mock<JpaEntitiesSet>()
    }

    // NOTE: While expecting refactoring around createDefaultUniquenessDb(), it's mocked for testing
    //  convenience. Since it's a final class, MockMaker's been added under resources with content "mock-maker-inline".
    private val schemaMigrator = mock<LiquibaseSchemaMigratorImpl>()
    private val dbConnectionManager = mock<DbConnectionManager>().apply {
        whenever(getClusterDataSource()) doReturn dummyDataSource
        whenever(getOrCreateEntityManagerFactory(any(), any(), any())) doReturn mock<EntityManagerFactory>()
    }

    @BeforeEach
    fun init() {
        backingStoreImpl = JPABackingStoreImpl(
            lifecycleCoordinatorFactory,
            jpaEntitiesRegistry,
            dbConnectionManager,
            schemaMigrator = schemaMigrator
        )
    }

    inner class DummyLifecycle : LifecycleEvent

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
        fun `isRunning returns life cycle isRunning`() {
            backingStoreImpl.isRunning
            Mockito.verify(lifecycleCoordinator).isRunning
        }

        // FIXME: it's currently failing because entityManagerFactory never got a chance to get initialised with
        //   a mock lifecycleCoordinator. In normal scenario, an instance is created then start() gets invoked which
        //   in turn invokes the event handler where entityManagerFactor creates an instance of EntityManager.
        //   Q. What'd be the desired behaviour - is it allowed to invoke close() before doing anything?
        @Test
        fun `Closing backing store invokes life cycle stop`() {
            Mockito.verify(lifecycleCoordinator, never()).stop()
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
}