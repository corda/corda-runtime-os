package net.corda.processors.db.internal.reconcile.db

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.StartEvent
import net.corda.lifecycle.StopEvent
import net.corda.processors.db.internal.reconcile.db.DbReconcilerReader.GetRecordsErrorEvent
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
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class DbReconcilerReaderTest {

    private val transaction: EntityTransaction = mock()
    private val em: EntityManager = mock {
        on { transaction } doReturn transaction
    }
    private val emf: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn em
    }

    private val streamOnCloseCaptor = argumentCaptor<Runnable>()
    private val versionedRecordsStream: Stream<VersionedRecord<String, Int>> = mock {
        on { onClose(streamOnCloseCaptor.capture()) } doReturn mock
    }

    private val dependencyMock: LifecycleCoordinatorName = LifecycleCoordinatorName("dependency")
    private val dependenciesMock: Set<LifecycleCoordinatorName> = setOf(dependencyMock)
    private val reconciliationInfoFactory: () -> Collection<ReconciliationInfo> = mock {
        on { invoke() } doReturn listOf(ReconciliationInfo.ClusterReconciliationInfo(emf))
    }
    private val getAllVersionRecordsMock: (EntityManager, ReconciliationInfo) -> Stream<VersionedRecord<String, Int>> = mock {
        on { invoke(eq(em), any()) } doReturn versionedRecordsStream
    }
    private val onStatusUpMock: () -> Unit = mock()
    private val onStatusDownMock: () -> Unit = mock()
    private val onStreamCloseMock: (ReconciliationInfo) -> Unit = mock()

    private val coordinatorNameCaptor = argumentCaptor<LifecycleCoordinatorName>()
    private val lifecycleEventHandlerCaptor = argumentCaptor<LifecycleEventHandler>()
    private val lifecycleEventHandler
        get() = assertDoesNotThrow {
            lifecycleEventHandlerCaptor.firstValue
        }

    private val postEventCaptor = argumentCaptor<LifecycleEvent>()
    private val dependencyRegistrationHandle: RegistrationHandle = mock()
    private val coordinator: LifecycleCoordinator = mock {
        on { followStatusChangesByName(eq(dependenciesMock)) } doReturn dependencyRegistrationHandle
        on { postEvent(postEventCaptor.capture()) } doAnswer {}
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
        reconciliationInfoFactory,
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
        fun `stop stops the coordinator`() {
            dbReconcilerReader.stop()

            verify(coordinator).stop()
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
                GetRecordsErrorEvent(CordaRuntimeException("")),
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

        @Test
        fun `lifecycle status ERROR calls onStatusDown function and sets coordinator status`() {
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.ERROR
                ),
                coordinator
            )

            verify(onStatusDownMock).invoke()
            verify(coordinator).updateStatus(eq(LifecycleStatus.ERROR), any())
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        @Suppress("MaxLineLength")
        fun `lifecycle status ERROR after start event closes registration handle, calls onStatusDown function and sets coordinator status`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.ERROR
                ),
                coordinator
            )

            verify(onStatusDownMock).invoke()
            verify(coordinator).updateStatus(eq(LifecycleStatus.ERROR), any())
            verify(dependencyRegistrationHandle).close()
        }
    }

    @Nested
    inner class GetAllVersionedRecordsTest {

        @Test
        fun `Expected services called when get versioned records executed successfully`() {
            dbReconcilerReader.getAllVersionedRecords()

            verify(reconciliationInfoFactory).invoke()
            verify(emf).createEntityManager()
            verify(em).transaction
            verify(transaction).begin()
            verify(getAllVersionRecordsMock).invoke(eq(em), any())
            verify(versionedRecordsStream).onClose(any())
        }

        @Test
        fun `onClose callback call expected services`() {
            dbReconcilerReader.getAllVersionedRecords()
            val onClose = streamOnCloseCaptor.firstValue

            verify(transaction, never()).rollback()
            verify(em, never()).close()
            verify(onStreamCloseMock, never()).invoke(any())

            onClose.run()

            verify(transaction).rollback()
            verify(em).close()
            verify(onStreamCloseMock).invoke(any())
        }
    }

    @Nested
    inner class GetAllVersionedRecordsFailureTest {

        private val errorMsg = "FOO-BAR"

        @Test
        fun `Failure to create entity manager factory posts error event`() {
            whenever(reconciliationInfoFactory.invoke()) doThrow RuntimeException(errorMsg)

            val output = assertDoesNotThrow {
                dbReconcilerReader.getAllVersionedRecords()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(reconciliationInfoFactory).invoke()
            assertThat(output).isNull()
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }

        @Test
        fun `Failure to create entity manager posts error event`() {
            whenever(emf.createEntityManager()) doThrow RuntimeException(errorMsg)

            val output = assertDoesNotThrow {
                dbReconcilerReader.getAllVersionedRecords()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(emf).createEntityManager()
            assertThat(output).isNull()
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }

        @Test
        fun `Failure to get entity transaction posts error event`() {
            whenever(em.transaction) doThrow RuntimeException(errorMsg)

            val output = assertDoesNotThrow {
                dbReconcilerReader.getAllVersionedRecords()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(em).transaction
            assertThat(output).isNull()
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }

        @Test
        fun `Failure to start transaction posts error event`() {
            whenever(transaction.begin()) doThrow RuntimeException(errorMsg)

            val output = assertDoesNotThrow {
                dbReconcilerReader.getAllVersionedRecords()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(transaction).begin()
            assertThat(output).isNull()
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }

        @Test
        fun `Failure to get all versioned records posts error event`() {
            whenever(getAllVersionRecordsMock.invoke(eq(em), any())) doThrow RuntimeException(errorMsg)

            val output = assertDoesNotThrow {
                dbReconcilerReader.getAllVersionedRecords()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(getAllVersionRecordsMock).invoke(eq(em), any())
            assertThat(output).isNull()
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }
    }
}