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
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.util.UUID

class JPABackingStoreImplLifecycleTests {
    private lateinit var backingStoreImpl: JPABackingStoreLifecycleImpl

    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var jpaEntitiesRegistry: JpaEntitiesRegistry

    private lateinit var dbConnectionManager: DbConnectionManager

    private val groupId = UUID.randomUUID().toString()

    inner class DummyLifecycle : LifecycleEvent

    @Suppress("ComplexMethod")
    @BeforeEach
    fun init() {
        lifecycleCoordinator = mock()
        lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>().apply {
            whenever(createCoordinator(any(), any())) doReturn lifecycleCoordinator
        }

        jpaEntitiesRegistry = mock<JpaEntitiesRegistry>().apply {
            whenever(get(any())) doReturn mock<JpaEntitiesSet>()
        }

        backingStoreImpl = JPABackingStoreLifecycleImpl(
            lifecycleCoordinatorFactory,
            jpaEntitiesRegistry,
            mock(),
            mock()
        )
    }

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
