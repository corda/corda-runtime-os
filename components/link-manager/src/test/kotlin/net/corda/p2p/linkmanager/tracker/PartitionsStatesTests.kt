package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
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
import net.corda.p2p.linkmanager.delivery.ReplayScheduler
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.tracker.PartitionState.Companion.stateKey
import net.corda.utilities.time.Clock
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity

class PartitionsStatesTests {
    private companion object {
        const val STATE_JSON = """
{
    "processRecordsFromOffset": 300,
    "readRecordsFromOffset": 204,
    "messages": {
        "group-2": {
            "CN=Alice, O=Alice Corp, L=LDN, C=GB": {
                "CN=Bob, O=Bob Corp, L=LDN, C=GB": {
                    "id-2": {
                        "id": "id-2",
                        "ts": 10.231
                    }
                }
            }
        },
        "group": {
            "CN=Alice, O=Alice Corp, L=LDN, C=GB": {
                "CN=Carol, O=Carol Corp, L=LDN, C=GB": {
                    "id-3": {
                        "id": "id-3",
                        "ts": 10.231
                    }
                },
                "CN=Bob, O=Bob Corp, L=LDN, C=GB": {
                    "id-1": {
                        "id": "id-1",
                        "ts": 10.231
                    }
                }
            }
        }
    },
    "version": 0
}
    """
        private fun createMessage(
            id: String,
            group: String,
            from: String,
            to: String,
        ): AuthenticatedMessage {
            val headers = mock<AuthenticatedMessageHeader> {
                on { messageId } doReturn id
                on { source } doReturn AvroHoldingIdentity(
                    from,
                    group,
                )
                on { destination } doReturn AvroHoldingIdentity(
                    to,
                    group,
                )
            }

            return mock {
                on { header } doReturn headers
            }
        }
    }
    private val partitionsIndices = setOf(
        1,
        5,
        12,
    )
    private val savedState =
        State(
            key = stateKey(1),
            value = STATE_JSON.toByteArray(),
        )

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
        on { createOperationGroup() } doReturn operationGroup
        on {
            get(
                partitionsIndices.map {
                    stateKey(it)
                },
            )
        } doReturn mapOf(stateKey(5) to savedState)

        on { name } doReturn mock()
    }
    private val configDominoTile = mock<ComplexDominoTile> {
        on { coordinatorName } doReturn mock()
    }
    private val config = mock<DeliveryTrackerConfiguration> {
        on { dominoTile } doReturn configDominoTile
        on { config } doReturn DeliveryTrackerConfiguration.Configuration(
            maxCacheOffsetAge = 50000,
            statePersistencePeriodSeconds = 1.0,
            outboundBatchProcessingTimeoutSeconds = 30.0,
            maxNumberOfPersistenceRetries = 3,
        )
    }
    private val now = Instant.ofEpochMilli(2001)
    private val clock = mock<Clock> {
        on { instant() } doReturn now
    }
    private val replayScheduler = mock<ReplayScheduler<SessionManager.Counterparties, String>>()
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

    private val partitionsStates = PartitionsStates(
        coordinatorFactory,
        stateManager,
        config,
        clock,
        replayScheduler,
        executor,
    )

    @Test
    fun `getEventToProcess return the events to process`() {
        partitionsStates.loadPartitions(partitionsIndices)
        val records = listOf(
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 100,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 299,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 300,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 301,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 302,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 12,
                partition = 1,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 13,
                partition = 1,
            ),
        )

        val returnedRecords = partitionsStates.getEventToProcess(records)

        val partitionsAndOffset = returnedRecords.map {
            it.partition to it.offset
        }
        assertThat(partitionsAndOffset)
            .hasSize(4)
            .contains(5 to 301)
            .contains(5 to 302)
            .contains(1 to 12)
            .contains(1 to 13)
    }

    @Test
    fun `read will replay the messages`() {
        partitionsStates.loadPartitions(partitionsIndices)
        val records = listOf(
            MessageRecord(
                message = createMessage(
                    id = "id-2",
                    from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                    to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    group = "group-2",
                ),
                offset = 400,
                partition = 5,
            ),
            MessageRecord(
                message = createMessage(
                    id = "id-12",
                    from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                    to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    group = "group-2",
                ),
                offset = 404,
                partition = 5,
            ),
            MessageRecord(
                message = createMessage(
                    id = "id-22",
                    from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                    to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    group = "group-3",
                ),
                offset = 500,
                partition = 12,
            ),
        )

        partitionsStates.read(records)

        verify(replayScheduler).addForReplay(
            originalAttemptTimestamp = now.toEpochMilli(),
            messageId = "id-12",
            message = "id-12",
            counterparties = SessionManager.Counterparties(
                ourId = HoldingIdentity(
                    groupId = "group-2",
                    x500Name = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
                ),
                counterpartyId = HoldingIdentity(
                    groupId = "group-2",
                    x500Name = MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"),
                ),
            ),
        )
        verify(replayScheduler).addForReplay(
            originalAttemptTimestamp = now.toEpochMilli(),
            messageId = "id-22",
            message = "id-22",
            counterparties = SessionManager.Counterparties(
                ourId = HoldingIdentity(
                    groupId = "group-3",
                    x500Name = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
                ),
                counterpartyId = HoldingIdentity(
                    groupId = "group-3",
                    x500Name = MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"),
                ),
            ),
        )
    }

    @Test
    fun `read will not replay messages that had been replayed`() {
        partitionsStates.loadPartitions(partitionsIndices)

        val records = listOf(
            MessageRecord(
                message = createMessage(
                    id = "id-2",
                    from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                    to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    group = "group-2",
                ),
                offset = 400,
                partition = 5,
            ),
        )

        partitionsStates.read(records)

        verify(replayScheduler, never()).addForReplay(
            originalAttemptTimestamp = now.toEpochMilli(),
            messageId = "id-2",
            message = "id-2",
            counterparties = SessionManager.Counterparties(
                ourId = HoldingIdentity(
                    groupId = "group-2",
                    x500Name = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
                ),
                counterpartyId = HoldingIdentity(
                    groupId = "group-2",
                    x500Name = MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"),
                ),
            ),
        )
    }

    @Test
    fun `forget will remove the message from the state`() {
        partitionsStates.loadPartitions(partitionsIndices)

        partitionsStates.forget(
            MessageRecord(
                partition = 5,
                offset = 1002,
                message = createMessage(
                    id = "id-2",
                    from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                    to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    group = "group-2",
                ),
            ),
        )

        partitionsStates.read(
            listOf(
                MessageRecord(
                    message = createMessage(
                        id = "id-2",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                        group = "group-2",
                    ),
                    offset = 400,
                    partition = 5,
                ),
            ),
        )
        verify(replayScheduler).addForReplay(
            originalAttemptTimestamp = now.toEpochMilli(),
            messageId = "id-2",
            message = "id-2",
            counterparties = SessionManager.Counterparties(
                ourId = HoldingIdentity(
                    groupId = "group-2",
                    x500Name = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
                ),
                counterpartyId = HoldingIdentity(
                    groupId = "group-2",
                    x500Name = MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"),
                ),
            ),
        )
    }

    @Test
    fun `forget will not throw an exception for unknown message partition`() {
        partitionsStates.loadPartitions(partitionsIndices)

        assertThatCode {
            partitionsStates.forget(
                MessageRecord(
                    partition = 105,
                    offset = 1002,
                    message = createMessage(
                        id = "id-2",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                        group = "group-2",
                    ),
                ),
            )
        }.doesNotThrowAnyException()
    }

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

        verify(config).lister(partitionsStates)
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

        partitionsStates.close()

        verify(future).cancel(false)
    }

    @Test
    fun `onStop will not cancel anything if not started before`() {
        partitionsStates.close()

        verify(future, never()).cancel(false)
    }

    @Test
    fun `changed will recreate the task`() {
        partitionsStates.changed()
        partitionsStates.changed()

        verify(executor, times(2)).scheduleAtFixedRate(
            any(),
            eq(1000L),
            eq(1000L),
            eq(TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun `changed will cancel the previous task`() {
        partitionsStates.changed()
        partitionsStates.changed()

        verify(future, times(1)).cancel(false)
    }

    @Nested
    inner class PersistTests {
        @Test
        fun `persist will persist the data`() {
            partitionsStates.changed()
            val key = stateKey(3)
            val json = """
            {
                "processRecordsFromOffset": 300,
                "readRecordsFromOffset": 204,
                "messages": {},
                "version": 1
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
            partitionsStates.loadPartitions(
                setOf(1, 3),
            )
            val records = listOf(
                MessageRecord(
                    message = createMessage(
                        id = "id-1",
                        from = "CN=Alice-1, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob-1, O=Bob Corp, L=LDN, C=GB",
                        group = "group-1",
                    ),
                    partition = 1,
                    offset = 101,
                ),
                MessageRecord(
                    message = createMessage(
                        id = "id-3",
                        from = "CN=Alice-3, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob-3, O=Bob Corp, L=LDN, C=GB",
                        group = "group-3",
                    ),
                    partition = 3,
                    offset = 3003,
                ),
            )
            partitionsStates.read(records)

            persist.firstValue.run()

            verify(operationGroup).execute()
            verify(operationGroup, times(1)).create(any<State>())
            verify(operationGroup, times(1)).update(any<State>())
        }

        @Test
        fun `persist will not run anything if not needed`() {
            partitionsStates.changed()

            persist.firstValue.run()

            verify(operationGroup, never()).execute()
        }

        @Test
        fun `persist failure will set as error if the state manager is out of sync`() {
            whenever(operationGroup.execute()).doReturn(mapOf(stateKey(3) to mock()))
            val events = argumentCaptor<LifecycleEvent>()
            whenever(coordinator.postEvent(events.capture())).doAnswer { }
            partitionsStates.changed()
            partitionsStates.loadPartitions(
                setOf(
                    1,
                    3,
                ),
            )
            val records = listOf(
                MessageRecord(
                    message = createMessage(
                        id = "id-1",
                        from = "CN=Alice-1, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob-1, O=Bob Corp, L=LDN, C=GB",
                        group = "group-1",
                    ),
                    partition = 1,
                    offset = 101,
                ),
                MessageRecord(
                    message = createMessage(
                        id = "id-3",
                        from = "CN=Alice-3, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob-3, O=Bob Corp, L=LDN, C=GB",
                        group = "group-3",
                    ),
                    partition = 3,
                    offset = 3003,
                ),
            )
            partitionsStates.read(records)

            persist.firstValue.run()

            val error = (events.firstValue as? ErrorEvent)?.cause
            assertThat(error).isExactlyInstanceOf(IllegalStateException::class.java)
        }

        @Test
        fun `persist exceptional failure will not post an error for the first time`() {
            whenever(operationGroup.execute())
                .doThrow(CordaRuntimeException("Ooops"))
            partitionsStates.changed()
            partitionsStates.loadPartitions(
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
            partitionsStates.changed()
            partitionsStates.loadPartitions(
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
            partitionsStates.changed()
            partitionsStates.loadPartitions(
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
    }

    @Test
    fun `forgetPartitions forget the partitions`() {
        partitionsStates.changed()
        partitionsStates.loadPartitions(
            setOf(
                1,
                2,
                3,
                4,
            ),
        )

        partitionsStates.forgetPartitions(
            setOf(
                2,
                4,
            ),
        )

        persist.firstValue.run()

        verify(operationGroup, times(2)).create(any<State>())
    }

    @Test
    fun `loadPartitions return the states`() {
        val states = partitionsStates.loadPartitions(partitionsIndices)

        assertThat(states)
            .hasSize(3)
            .anySatisfy { index, state ->
                assertThat(index)
                    .isEqualTo(1)
                assertThat(state.readRecordsFromOffset).isEqualTo(0)
            }
            .anySatisfy { index, state ->
                assertThat(index)
                    .isEqualTo(12)
                assertThat(state.readRecordsFromOffset).isEqualTo(0)
            }
            .anySatisfy { index, state ->
                assertThat(index)
                    .isEqualTo(5)
                assertThat(state.readRecordsFromOffset).isEqualTo(204)
            }
    }

    @Test
    fun `loadPartitions will replay messages`() {
        partitionsStates.loadPartitions(partitionsIndices)

        verify(replayScheduler).addForReplay(
            originalAttemptTimestamp = 10231,
            message = "id-2",
            messageId = "id-2",
            counterparties = SessionManager.Counterparties(
                ourId = HoldingIdentity(
                    x500Name = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
                    groupId = "group-2",
                ),
                counterpartyId = HoldingIdentity(
                    x500Name = MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"),
                    groupId = "group-2",
                ),
            ),
        )
        verify(replayScheduler).addForReplay(
            originalAttemptTimestamp = 10231,
            message = "id-3",
            messageId = "id-3",
            counterparties = SessionManager.Counterparties(
                ourId = HoldingIdentity(
                    x500Name = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
                    groupId = "group",
                ),
                counterpartyId = HoldingIdentity(
                    x500Name = MemberX500Name.parse("CN=Carol, O=Carol Corp, L=LDN, C=GB"),
                    groupId = "group",
                ),
            ),
        )
        verify(replayScheduler).addForReplay(
            originalAttemptTimestamp = 10231,
            message = "id-1",
            messageId = "id-1",
            counterparties = SessionManager.Counterparties(
                ourId = HoldingIdentity(
                    x500Name = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB"),
                    groupId = "group",
                ),
                counterpartyId = HoldingIdentity(
                    x500Name = MemberX500Name.parse("CN=Bob, O=Bob Corp, L=LDN, C=GB"),
                    groupId = "group",
                ),
            ),
        )
    }

    @Test
    fun `loadPartitions will not throw an exception for empty list`() {
        assertThatCode {
            partitionsStates.loadPartitions(emptySet())
        }.doesNotThrowAnyException()
    }

    @Test
    fun `offsetsToReadFromChanged update the states`() {
        partitionsStates.loadPartitions(partitionsIndices)

        partitionsStates.offsetsToReadFromChanged(
            listOf(
                1 to 1000L,
                5 to 3000L,
                7 to 7000L,
            ),
        )

        val states = partitionsStates.loadPartitions(partitionsIndices)

        assertThat(states[1]?.readRecordsFromOffset).isEqualTo(1000L)
    }

    @Test
    fun `handled update the states`() {
        partitionsStates.loadPartitions(partitionsIndices)
        val records = listOf(
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 1000L,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 1001L,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 999L,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 301L,
                partition = 5,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 13L,
                partition = 1,
            ),
            EventLogRecord(
                topic = "topic",
                key = "key1",
                value = mock<AppMessage> { },
                offset = 24L,
                partition = 100,
            ),
        )

        partitionsStates.handled(
            records,
        )

        val states = partitionsStates.loadPartitions(partitionsIndices)

        assertThat(states[5]?.processRecordsFromOffset).isEqualTo(1001L)
    }
}
