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
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.persistence.EntityManager
import javax.persistence.EntityTransaction

class DbReconcilerReaderTest {

    private val transaction1: EntityTransaction = mock()
    private val transaction2: EntityTransaction = mock()
    private val em1: EntityManager = mock {
        on { transaction } doReturn transaction1
    }
    private val em2: EntityManager = mock {
        on { transaction } doReturn transaction2
    }

    private val mockVersionedRecord1: VersionedRecord<String, Int> = mock()
    private val mockVersionedRecord2: VersionedRecord<String, Int> = mock()
    private val realStream1 = Stream.of(mockVersionedRecord1)
    private val realStream2 = Stream.of(mockVersionedRecord2)
    private val reconciliationContext1: ReconciliationContext = mock {
        on { getOrCreateEntityManager() } doReturn em1
    }
    private val reconciliationContext2: ReconciliationContext = mock {
        on { getOrCreateEntityManager() } doReturn em2
    }

    private val dependencyMock: LifecycleCoordinatorName = LifecycleCoordinatorName("dependency")
    private val dependenciesMock: Set<LifecycleCoordinatorName> = setOf(dependencyMock)
    private val reconciliationContexts = Stream.of(
        reconciliationContext1,
        reconciliationContext2
    )
    private val reconciliationContextFactory: () -> Stream<ReconciliationContext> = mock {
        on { invoke() } doReturn reconciliationContexts
    }
    private val getAllVersionRecordsMock: (ReconciliationContext) -> Stream<VersionedRecord<String, Int>> = mock {
        on { invoke(eq(reconciliationContext1)) } doReturn realStream1
        on { invoke(eq(reconciliationContext2)) } doReturn realStream2
    }

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
        reconciliationContextFactory,
        getAllVersionRecordsMock
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

            assertThat(name.componentName).isEqualTo(
                "net.corda.processors.db.internal.reconcile.db.DbReconcilerReader<java.lang.String, int>"
            )
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
        fun `stop event does nothing if it hasn't started`() {
            lifecycleEventHandler.processEvent(StopEvent(), coordinator)

            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        fun `stop event after start event closes registration handle`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            verify(coordinator).followStatusChangesByName(eq(dependenciesMock))

            lifecycleEventHandler.processEvent(StopEvent(), coordinator)

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
        fun `lifecycle status UP sets coordinator status`() {
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.UP
                ),
                coordinator
            )

            verify(coordinator).updateStatus(eq(LifecycleStatus.UP), any())
        }

        @Test
        fun `lifecycle status DOWN sets coordinator status`() {
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        @Suppress("MaxLineLength")
        fun `lifecycle status DOWN after start event closes registration handle and sets coordinator status`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.DOWN
                ),
                coordinator
            )

            verify(coordinator).updateStatus(eq(LifecycleStatus.DOWN), any())
            verify(dependencyRegistrationHandle).close()
        }

        @Test
        fun `lifecycle status ERROR sets coordinator status`() {
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.ERROR
                ),
                coordinator
            )

            verify(coordinator).updateStatus(eq(LifecycleStatus.ERROR), any())
            verify(dependencyRegistrationHandle, never()).close()
        }

        @Test
        fun `lifecycle status ERROR after start event closes registration handle, and sets coordinator status`() {
            lifecycleEventHandler.processEvent(StartEvent(), coordinator)
            lifecycleEventHandler.processEvent(
                RegistrationStatusChangeEvent(
                    dependencyRegistrationHandle,
                    LifecycleStatus.ERROR
                ),
                coordinator
            )

            verify(coordinator).updateStatus(eq(LifecycleStatus.ERROR), any())
            verify(dependencyRegistrationHandle).close()
        }
    }

    @Nested
    inner class GetAllVersionedRecordsTest {

        @Test
        fun `Expected services called when get versioned records executed successfully`() {
            // call terminal operation to process stream
            dbReconcilerReader.getAllVersionedRecords()?.count()

            verify(reconciliationContextFactory).invoke()

            verify(em1).transaction
            verify(transaction1).begin()
            verify(em2).transaction
            verify(transaction2).begin()

            verify(getAllVersionRecordsMock, times(2)).invoke(any())

            verify(transaction1).rollback()
            verify(reconciliationContext1).close()
            verify(transaction2).rollback()
            verify(reconciliationContext2).close()
        }

        @Test
        fun `onClose callback closes the open transaction`() {
            val versionedRecordsStream = dbReconcilerReader.getAllVersionedRecords()


            verify(transaction1, never()).rollback()
            verify(transaction2, never()).rollback()
            verify(reconciliationContext1, never()).close()
            verify(reconciliationContext2, never()).close()

            // run terminal operation on stream to process and close
            versionedRecordsStream?.collect(Collectors.toList())

            verify(transaction1).rollback()
            verify(transaction2).rollback()
            verify(reconciliationContext1).close()
            verify(reconciliationContext2).close()
        }
    }

    @Nested
    inner class GetAllVersionedRecordsFailureTest {

        private val errorMsg = "FOO-BAR"

        @Test
        fun `Failure to create reconciliation posts error event`() {
            whenever(reconciliationContextFactory.invoke()) doThrow RuntimeException(errorMsg)

            val output = assertDoesNotThrow {
                dbReconcilerReader.getAllVersionedRecords()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(reconciliationContextFactory).invoke()
            assertThat(output).isNull()
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }

        @Test
        fun `Failure to get entity transaction posts error event`() {
            whenever(em1.transaction) doThrow RuntimeException(errorMsg)

            val numResults = assertDoesNotThrow {
                // call terminal operation to process stream
                dbReconcilerReader.getAllVersionedRecords()?.count()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(em1).transaction
            assertThat(numResults).isEqualTo(1)
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }

        @Test
        fun `Failure to start transaction posts error event`() {
            whenever(transaction1.begin()) doThrow RuntimeException(errorMsg)

            val numResults = assertDoesNotThrow {
                // call terminal operation to process stream
                dbReconcilerReader.getAllVersionedRecords()?.count()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(transaction1).begin()
            assertThat(numResults).isEqualTo(1)
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }

        @Test
        fun `Failure to get all versioned records posts error event`() {
            whenever(getAllVersionRecordsMock.invoke(eq(reconciliationContext2))) doThrow RuntimeException(errorMsg)

            val numResults = assertDoesNotThrow {
                // call terminal operation to process stream
                dbReconcilerReader.getAllVersionedRecords()?.count()
            }
            val postedEvent = postEventCaptor.firstValue

            verify(getAllVersionRecordsMock, times(2)).invoke(any())
            assertThat(numResults).isEqualTo(1)
            assertThat(postedEvent).isInstanceOf(GetRecordsErrorEvent::class.java)
            assertThat((postedEvent as GetRecordsErrorEvent).exception.message).isEqualTo(errorMsg)
        }
    }
}