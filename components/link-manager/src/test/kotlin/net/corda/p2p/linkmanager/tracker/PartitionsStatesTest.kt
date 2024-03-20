package net.corda.p2p.linkmanager.tracker

import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateManager
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.lifecycle.ErrorEvent
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEvent
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.RegistrationHandle
import net.corda.lifecycle.RegistrationStatusChangeEvent
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.messaging.api.records.EventLogRecord
import net.corda.p2p.linkmanager.tracker.PartitionState.Companion.stateKey
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PartitionsStatesTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val followStatusChangesByNameHandlers = mutableSetOf<RegistrationHandle>()
    private val coordinator = mock<LifecycleCoordinator> {
        on { followStatusChangesByName(any()) } doAnswer {
            val handler = mock<RegistrationHandle>()
            followStatusChangesByNameHandlers.add(handler)
            handler
        }
    }
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val operationGroup = mock<StateOperationGroup> {
        on { execute() } doReturn emptyMap()
    }
    private val stateManager = mock<StateManager> {
        on { name } doReturn mock()
        on { createOperationGroup() } doReturn operationGroup
    }
    private val configDominoTile = mock<ComplexDominoTile> {
        on { coordinatorName } doReturn mock()
    }
    private val config = mock<DeliveryTrackerConfiguration> {
        on { dominoTile } doReturn configDominoTile
        on { config } doReturn DeliveryTrackerConfiguration.Configuration(
            maxCacheSizeMegabytes = 100,
            maxCacheOffsetAge = 50000,
            statePersistencePeriodSeconds = 1.0,
            outboundBatchProcessingTimeoutSeconds = 30.0,
            maxNumberOfPersistenceRetries = 3,
        )
    }
    private val ends = mock<Instant>()
    private val now = mock<Instant> {
        on { plusMillis(any()) } doReturn ends
    }
    private val clock = mock<Clock> {
        on { instant() } doReturn now
    }
    private val future = mock<ScheduledFuture<*>>()
    private val persist = argumentCaptor<Runnable>()
    private val executor = mock<ScheduledExecutorService> {
        on {
            scheduleAtFixedRate(
                persist.capture(),
                any(),
                any(),
                any(),
            )
        } doReturn future
    }

    private val states = PartitionsStates(
        coordinatorFactory,
        stateManager,
        config,
        clock,
        executor,
    )

    @Test
    fun `onStart will listen to configuration changes`() {
        followStatusChangesByNameHandlers.forEach { followStatusChangesByNameHandler ->
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    followStatusChangesByNameHandler,
                    LifecycleStatus.UP,
                ),
                coordinator,
            )
        }

        verify(config).lister(states)
    }

    @Test
    fun `onStart will start the task`() {
        followStatusChangesByNameHandlers.forEach { followStatusChangesByNameHandler ->
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    followStatusChangesByNameHandler,
                    LifecycleStatus.UP,
                ),
                coordinator,
            )
        }

        verify(executor).scheduleAtFixedRate(
            any(),
            eq(1000L),
            eq(1000L),
            eq(TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun `onStop will cancel the task`() {
        followStatusChangesByNameHandlers.forEach { followStatusChangesByNameHandler ->
            handler.firstValue.processEvent(
                RegistrationStatusChangeEvent(
                    followStatusChangesByNameHandler,
                    LifecycleStatus.UP,
                ),
                coordinator,
            )
        }

        states.close()

        verify(future).cancel(false)
    }

    @Test
    fun `onStop will not cancel anything if not started before`() {
        states.close()

        verify(future, never()).cancel(false)
    }

    @Test
    fun `loadPartitions will create new partition if partition is not in the state manager`() {
        states.loadPartitions(
            setOf(1, 3),
        )

        val partition = states.get(3)

        assertThat(partition?.restartOffset).isEqualTo(0)
    }

    @Test
    fun `loadPartitions will read partition from the state manager`() {
        val key = stateKey(3)
        val json = """
            {
                "restartOffset": 301,
                "lastSentOffset": 403,
                "partition": 3,
                "messages": {}
            }
        """.trimIndent()
        whenever(
            stateManager.get(
                argThat {
                    contains(key)
                },
            ),
        ).doReturn(
            mapOf(
                key to State(
                    key = key,
                    value = json.toByteArray(),
                ),
            ),
        )
        states.loadPartitions(
            setOf(
                1,
                3,
            ),
        )

        val partition = states.get(3)

        assertThat(partition?.restartOffset).isEqualTo(301)
    }

    @Test
    fun `forgetPartitions will forget the partition details`() {
        states.loadPartitions(
            setOf(1, 3),
        )
        states.forgetPartitions(
            setOf(2, 3),
        )

        val partition = states.get(3)

        assertThat(partition).isNull()
    }

    @Test
    fun `read will update the offsets`() {
        states.loadPartitions(
            setOf(
                1,
                3,
            ),
        )
        val records = listOf(
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 1,
                offset = 100,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 1,
                offset = 200,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 1,
                offset = 101,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 3,
                offset = 303,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 1,
                offset = 102,
            ),
        )

        states.read(records)

        assertSoftly {
            val partitionOne = states.get(1)
            it.assertThat(partitionOne?.lastSentOffset).isEqualTo(200)
            val partitionThree = states.get(3)
            it.assertThat(partitionThree?.lastSentOffset).isEqualTo(303)
        }
    }

    @Test
    fun `sent will update the offsets`() {
        states.loadPartitions(
            setOf(
                1,
                3,
            ),
        )
        val records = listOf(
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 1,
                offset = 100,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 1,
                offset = 200,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 1,
                offset = 101,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 3,
                offset = 303,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key",
                value = null,
                partition = 1,
                offset = 102,
            ),
        )

        states.sent(records)

        assertSoftly {
            val partitionOne = states.get(1)
            it.assertThat(partitionOne?.restartOffset).isEqualTo(200)
            val partitionThree = states.get(3)
            it.assertThat(partitionThree?.restartOffset).isEqualTo(303)
        }
    }

    @Test
    fun `changed will recreate the task`() {
        states.changed()
        states.changed()

        verify(executor, times(2)).scheduleAtFixedRate(
            any(),
            eq(1000L),
            eq(1000L),
            eq(TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun `changed will cancel the previous task`() {
        states.changed()
        states.changed()

        verify(future, times(1)).cancel(false)
    }

    @Nested
    inner class PersistTests {
        @Test
        fun `persist will persist the data`() {
            states.changed()
            val key = stateKey(3)
            val json = """
            {
                "restartOffset": 301,
                "lastSentOffset": 403,
                "partition": 3,
                "version": 12,
                "messages": {}
            }
            """.trimIndent()
            whenever(
                stateManager.get(
                    argThat {
                        contains(key)
                    },
                ),
            ).doReturn(
                mapOf(
                    key to State(
                        key = key,
                        value = json.toByteArray(),
                        version = 12,
                    ),
                ),
            )
            states.loadPartitions(
                setOf(1, 3),
            )
            val records = listOf(
                EventLogRecord(
                    topic = "topic",
                    key = "key",
                    value = null,
                    partition = 1,
                    offset = 101,
                ),
                EventLogRecord(
                    topic = "topic",
                    key = "key",
                    value = null,
                    partition = 3,
                    offset = 3003,
                ),
            )
            states.read(records)

            persist.firstValue.run()

            verify(operationGroup).execute()
            verify(operationGroup, times(1)).create(any<State>())
            verify(operationGroup, times(1)).update(any<State>())
        }

        @Test
        fun `persist will not run anything if not needed`() {
            states.changed()

            persist.firstValue.run()

            verify(operationGroup, never()).execute()
        }

        @Test
        fun `persist failure will set as error if the state manager is out of sync`() {
            whenever(operationGroup.execute()).doReturn(mapOf(stateKey(3) to mock()))
            val events = argumentCaptor<LifecycleEvent>()
            whenever(coordinator.postEvent(events.capture())).doAnswer { }
            states.changed()
            states.loadPartitions(
                setOf(
                    1,
                    3,
                ),
            )
            val records = listOf(
                EventLogRecord(
                    topic = "topic",
                    key = "key",
                    value = null,
                    partition = 1,
                    offset = 101,
                ),
                EventLogRecord(
                    topic = "topic",
                    key = "key",
                    value = null,
                    partition = 3,
                    offset = 3003,
                ),
            )
            states.read(records)

            persist.firstValue.run()

            val error = (events.firstValue as? ErrorEvent)?.cause
            assertThat(error).isExactlyInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun `persist exceptional failure will not post an error for the first time`() {
            whenever(operationGroup.execute())
                .doThrow(CordaRuntimeException("Ooops"))
            states.changed()
            states.loadPartitions(
                setOf(
                    1,
                    3,
                ),
            )

            persist.firstValue.run()

            verify(coordinator, never()).postEvent(any())
        }

        @Test
        fun `persist exceptional failure will post an error after the Nth time`() {
            val e = CordaRuntimeException("Ooops")
            whenever(operationGroup.execute())
                .doThrow(e)
            val events = argumentCaptor<LifecycleEvent>()
            whenever(coordinator.postEvent(events.capture())).doAnswer { }
            states.changed()
            states.loadPartitions(
                setOf(
                    1,
                    3,
                ),
            )

            persist.firstValue.run()
            persist.firstValue.run()
            persist.firstValue.run()

            val error = (events.firstValue as? ErrorEvent)?.cause
            assertThat(error).isSameAs(e)
        }

        @Test
        fun `persist exceptional failure will not post an error after the Nth time if it had any success in between`() {
            whenever(operationGroup.execute())
                .thenThrow(CordaRuntimeException("Ooops"))
                .thenThrow(CordaRuntimeException("Ooops"))
                .thenReturn(emptyMap())
                .thenThrow(CordaRuntimeException("Ooops"))
                .thenThrow(CordaRuntimeException("Ooops"))
                .thenReturn(emptyMap())
            states.changed()
            states.loadPartitions(
                setOf(
                    1,
                    3,
                ),
            )

            persist.firstValue.run()
            persist.firstValue.run()
            persist.firstValue.run()
            persist.firstValue.run()
            persist.firstValue.run()

            verify(coordinator, never()).postEvent(any())
        }

        @Test
        fun `persist will update the state`() {
            states.changed()
            states.loadPartitions(
                setOf(
                    1,
                    3,
                ),
            )

            persist.firstValue.run()

            val info = states.get(3)
            val group = mock<StateOperationGroup>()
            info?.addToOperationGroup(group)
            verify(group).update(any<State>())
        }
    }
}
