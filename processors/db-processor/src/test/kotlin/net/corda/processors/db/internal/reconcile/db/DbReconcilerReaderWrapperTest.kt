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
import net.corda.processors.db.internal.reconcile.db.DbReconcilerReaderWrapper.GetRecordsErrorEvent
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.stream.Stream

class DbReconcilerReaderWrapperTest {

    private val getAllVersionRecordsMock: Stream<VersionedRecord<String, Int>> = mock()
    private val nameMock: String = "super-class-name"
    private val lifecycleCoordinatorNameMock: LifecycleCoordinatorName = LifecycleCoordinatorName(nameMock)
    private val dependencyMock: LifecycleCoordinatorName = LifecycleCoordinatorName("dependency")
    private val onStatusUpMock: () -> Unit = mock()
    private val dependenciesMock: Set<LifecycleCoordinatorName> = setOf(dependencyMock)
    private val onStatusDownMock: () -> Unit = mock()

    private val coordinatorNameCaptor = argumentCaptor<LifecycleCoordinatorName>()
    private val lifecycleEventHandlerCaptor = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleEventHandler
        get() = assertDoesNotThrow {
            lifecycleEventHandlerCaptor.firstValue
        }

    private val dependencyRegistrationHandle: RegistrationHandle = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependenciesMock)) } doReturn dependencyRegistrationHandle
    }
    private val coordinatorFactory: LifecycleCoordinatorFactory = mock {
        on {
            createCoordinator(
                coordinatorNameCaptor.capture(),
                lifecycleEventHandlerCaptor.capture()
            )
        } doReturn coordinator
    }

    private val dbReconcilerReader: DbReconcilerReader<String, Int> = mock {
        on { getAllVersionedRecords() } doReturn getAllVersionRecordsMock
        on { name } doReturn nameMock
        on { dependencies } doReturn dependenciesMock
        on { lifecycleCoordinatorName } doReturn lifecycleCoordinatorNameMock
        on { onStatusUp() } doAnswer { onStatusUpMock.invoke() }
        on { onStatusDown() } doAnswer { onStatusDownMock.invoke() }
    }

    private val dbReconcilerReaderComponent = DbReconcilerReaderWrapper(
        coordinatorFactory,
        dbReconcilerReader
    )

    @Nested
    inner class LifecycleCoordinatorTest {
        @Test
        fun `Lifecycle coordinator is created using lifecycle coordinator from reconciler class`() {
            verify(coordinatorFactory).createCoordinator(any(), any())
            assertThat(coordinatorNameCaptor.allValues).hasSize(1)
            assertThat(coordinatorNameCaptor.firstValue.componentName).isEqualTo(nameMock)
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

            verify(coordinator).followStatusChangesByName(eq(dependenciesMock))
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        fun `start event called again closes registration handle and follows dependencies`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)

            verify(coordinator, times(2)).followStatusChangesByName(eq(dependenciesMock))
            verify(dependencyRegistrationHandle).close()
        }
    }

    @Nested
    inner class LifecycleStopTest {
        @Test
        fun `stop stops the coordinator and calls the superclass onStatusDown function`() {
            dbReconcilerReaderComponent.stop()

            verify(coordinator).stop()
            verify(onStatusDownMock).invoke()
        }

        @Test
        fun `stop event calls super class onStatusDown function`() {
            lifecycleEventHandler.processEvent(StopEvent(), coordinator)

            verify(onStatusDownMock).invoke()
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        fun `stop event after start event calls super class onStatusDown function and closes registration handle`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            verify(coordinator).followStatusChangesByName(eq(dependenciesMock))

            lifecycleEventHandler.processEvent(StopEvent(), coordinator)

            verify(onStatusDownMock).invoke()
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

            verify(onStatusUpMock).invoke()
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

            verify(onStatusDownMock).invoke()
            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        @Suppress("MaxLineLength")
        fun `lifecycle status DOWN after start event closes registration handle, calls super class onStatusDown function and sets coordinator status`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(onStatusDownMock).invoke()
            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(dependencyRegistrationHandle).close()
        }
    }
}