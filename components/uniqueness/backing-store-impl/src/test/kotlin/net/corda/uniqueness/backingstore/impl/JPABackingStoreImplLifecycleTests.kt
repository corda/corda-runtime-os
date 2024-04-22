package net.corda.uniqueness.backingstore.impl

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever

class JPABackingStoreImplLifecycleTests {

    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry>()
    private val dbConnectionManager = mock<DbConnectionManager>()
    private lateinit var backingStore: JPABackingStoreLifecycleImpl

    inner class DummyLifecycle : LifecycleEvent

    @BeforeEach
    fun init() {
        whenever(lifecycleCoordinatorFactory.createCoordinator(any(), any())).thenReturn(lifecycleCoordinator)
        whenever(jpaEntitiesRegistry.get(any())) doReturn mock<JpaEntitiesSet>()
        backingStore = JPABackingStoreLifecycleImpl(
            lifecycleCoordinatorFactory,
            jpaEntitiesRegistry,
            dbConnectionManager,
            mock()
        )
    }

    @Test
    fun `Starting backing store starts life cycle coordinator`() {
        backingStore.start()
        verify(lifecycleCoordinator, times(1)).start()
    }

    @Test
    fun `Stopping backing store stops life cycle coordinator`() {
        backingStore.stop()
        verify(lifecycleCoordinator, times(1)).stop()
    }

    @Test
    fun `Getting running life cycle status returns life cycle running status`() {
        backingStore.isRunning
        verify(lifecycleCoordinator, times(1)).isRunning
    }

    @Test
    fun `Start event starts following the statuses of the required dependencies`() {
        backingStore.eventHandler(StartEvent(), lifecycleCoordinator)
        verify(lifecycleCoordinator).followStatusChangesByName(
            eq(setOf(LifecycleCoordinatorName.forComponent<DbConnectionManager>()))
        )
        verify(dbConnectionManager, times(1)).start()
    }

    @Test
    fun `Stop event stops the required dependency`() {
        backingStore.eventHandler(StopEvent(), lifecycleCoordinator)
        verify(dbConnectionManager, times(1)).stop()
    }

    @Test
    fun `Registration status change event registers jpa entries`() {
        val lifeCycleStatus = LifecycleStatus.UP
        backingStore.eventHandler(RegistrationStatusChangeEvent(mock(), lifeCycleStatus), lifecycleCoordinator)

        verify(jpaEntitiesRegistry, times(1)).get(CordaDb.Uniqueness.persistenceUnitName)
        verify(lifecycleCoordinator, times(1)).updateStatus(lifeCycleStatus)
    }

    @Test
    fun `Unknown life cycle event does not throw exception`() {
        assertDoesNotThrow { backingStore.eventHandler(DummyLifecycle(), mock()) }
    }
}
