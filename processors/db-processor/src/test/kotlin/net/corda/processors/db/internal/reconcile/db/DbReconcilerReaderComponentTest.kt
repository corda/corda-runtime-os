package net.corda.processors.db.internal.reconcile.db

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.processors.db.internal.reconcile.db.DbReconcilerReaderComponent.GetRecordsErrorEvent
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.stream.Stream

class DbReconcilerReaderComponentTest {

    private val superClassAllVersionRecords: Stream<VersionedRecord<String, Int>> = mock()
    private val superClassName: String = "super-class-name"
    private val superClassDependency: LifecycleCoordinatorName = LifecycleCoordinatorName("dependency")
    private val superClassDependencies: Set<LifecycleCoordinatorName> = setOf(superClassDependency)
    private val superClassOnStatusUp: () -> Unit = mock()
    private val superClassOnStatusDown: () -> Unit = mock()

    private val coordinatorNameCaptor = argumentCaptor<LifecycleCoordinatorName>()
    private val lifecycleEventHandlerCaptor = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleEventHandler
        get() = assertDoesNotThrow {
            lifecycleEventHandlerCaptor.firstValue
        }

    private val dependencyRegistrationHandle: RegistrationHandle = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(superClassDependencies)) } doReturn dependencyRegistrationHandle
    }
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on {
            createCoordinator(
                coordinatorNameCaptor.capture(),
                lifecycleEventHandlerCaptor.capture()
            )
        } doReturn coordinator
    }

    private val dbReconcilerReaderComponent =
        object : DbReconcilerReaderComponent<String, Int>(coordinatorFactory) {
            override fun getAllVersionedRecords() = superClassAllVersionRecords
            override val name = superClassName
            override val dependencies = superClassDependencies
            override fun onStatusUp() = superClassOnStatusUp.invoke()
            override fun onStatusDown() = superClassOnStatusDown.invoke()
        }

    @BeforeEach
    fun setup() {
        // call lazy initialized coordinator to capture the event handler
        dbReconcilerReaderComponent.coordinator.status
    }

    @Nested
    inner class LifecycleCoordinatorTest {
        @Test
        fun `Lifecycle coordinator is created using name from super class`() {
            val lifecycleCoordinator = dbReconcilerReaderComponent.coordinator

            verify(coordinatorFactory).createCoordinator(any(), any())
            assertThat(lifecycleCoordinator).isEqualTo(coordinator)
            assertThat(coordinatorNameCaptor.allValues).hasSize(1)
            assertThat(coordinatorNameCaptor.firstValue.componentName).isEqualTo(superClassName)
        }

        @Test
        fun `Component status is based on the coordinator status`() {
            val isRunning = dbReconcilerReaderComponent.isRunning

            verify(coordinator).isRunning
            assertThat(isRunning).isEqualTo(coordinator.isRunning)
        }
    }

    @Nested
    inner class LifecycleStartTest {
        @Test
        fun `start starts the coordinator`() {
            dbReconcilerReaderComponent.start()

            verify(coordinator).start()
        }

        @Test
        fun `start event follows dependencies`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)

            verify(coordinator).followStatusChangesByName(eq(superClassDependencies))
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        fun `start event called again closes registration handle and follows dependencies`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)

            verify(coordinator, times(2)).followStatusChangesByName(eq(superClassDependencies))
            verify(dependencyRegistrationHandle).close()
        }
    }

    @Nested
    inner class LifecycleStopTest {
        @Test
        fun `stop stops the coordinator and calls the superclass onStatusDown function`() {
            dbReconcilerReaderComponent.stop()

            verify(coordinator).stop()
            verify(superClassOnStatusDown).invoke()
        }

        @Test
        fun `stop event calls super class onStatusDown function`() {
            lifecycleEventHandler.processEvent(StopEvent(), coordinator)

            verify(superClassOnStatusDown).invoke()
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        fun `stop event after start event calls super class onStatusDown function and closes registration handle`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            verify(coordinator).followStatusChangesByName(eq(superClassDependencies))

            lifecycleEventHandler.processEvent(StopEvent(), coordinator)

            verify(superClassOnStatusDown).invoke()
            verify(dependencyRegistrationHandle).close()
        }
    }

    @Nested
    inner class GetRecordsErrorEventTest {
        @Test
        fun `Error retrieving records stops the component`() {
            lifecycleEventHandler.processEvent(GetRecordsErrorEvent(CordaRuntimeException("")), coordinator)

            verify(coordinator).postEvent(eq(StopEvent()))
        }
    }

    @Nested
    inner class RegistrationStatusChangeEventTest {
        @Test
        fun `lifecycle status UP calls super class onStatusUp function and sets coordinator status`() {
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.UP
                ),
                coordinator
            )

            verify(superClassOnStatusUp).invoke()
            verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        }

        @Test
        fun `lifecycle status DOWN calls super class onStatusDown function and sets coordinator status`() {
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(superClassOnStatusDown).invoke()
            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        fun `lifecycle status DOWN after start event closes registration handle, calls super class onStatusDown function and sets coordinator status`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(superClassOnStatusDown).invoke()
            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(dependencyRegistrationHandle).close()
        }
    }
}