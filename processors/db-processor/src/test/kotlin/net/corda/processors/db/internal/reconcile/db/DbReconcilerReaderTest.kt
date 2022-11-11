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
import net.corda.reconciliation.VersionedRecord
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
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
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class DbReconcilerReaderTest {

    private val dependencyMock: LifecycleCoordinatorName = LifecycleCoordinatorName("dependency")
    private val dependenciesMock: Set<LifecycleCoordinatorName> = setOf(dependencyMock)
    private val entityManagerFactoryFactoryMock: () -> EntityManagerFactory = mock()
    private val getAllVersionRecordsMock: (EntityManager) -> Stream<VersionedRecord<String, Int>> = mock()
    private val onStatusUpMock: () -> Unit = mock()
    private val onStatusDownMock: () -> Unit = mock()
    private val onStreamCloseMock: () -> Unit = mock()

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

    private val dbReconcilerReader = DbReconcilerReader(
        coordinatorFactory,
        String::class.java,
        Int::class.java,
        dependenciesMock,
        entityManagerFactoryFactoryMock,
        getAllVersionRecordsMock,
        onStatusUpMock,
        onStatusDownMock,
        onStreamCloseMock
    )

    @Nested
    inner class LifecycleCoordinatorTest {
        @Test
        fun `Component status is based on the coordinator status`() {
            val isRunning = dbReconcilerReader.isRunning

            verify(coordinator).isRunning
            assertThat(isRunning).isEqualTo(coordinator.isRunning)
        }

        @Test
        fun `Lifecycle coordinator name is as expected`() {
            val name = coordinatorNameCaptor.firstValue

            assertThat(name.componentName).isEqualTo("DbReconcilerReader<String, int>")
        }
    }

    @Nested
    inner class LifecycleStartTest {
        @Test
        fun `start starts the coordinator`() {
            dbReconcilerReader.start()

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
        fun `stop stops the coordinator and calls the onStatusDown function`() {
            dbReconcilerReader.stop()

            verify(coordinator).stop()
            verify(onStatusDownMock).invoke()
        }

        @Test
        fun `stop event calls onStatusDown function`() {
            lifecycleEventHandler.processEvent(StopEvent(), coordinator)

            verify(onStatusDownMock).invoke()
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        fun `stop event after start event calls onStatusDown function and closes registration handle`() {
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
            lifecycleEventHandler.processEvent(
                DbReconcilerReader.GetRecordsErrorEvent(CordaRuntimeException("")),
                coordinator
            )

            verify(coordinator).postEvent(eq(StopEvent()))
        }
    }

    @Nested
    inner class RegistrationStatusChangeEventTest {
        @Test
        fun `lifecycle status UP calls onStatusUp function and sets coordinator status`() {
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
        fun `lifecycle status DOWN calls onStatusDown function and sets coordinator status`() {
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
        fun `lifecycle status DOWN after start event closes registration handle, calls onStatusDown function and sets coordinator status`() {
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