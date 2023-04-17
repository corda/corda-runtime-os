package net.corda.membership.service.impl

import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.data.membership.async.request.MembershipAsyncRequestStates
import net.corda.data.membership.async.request.RegistrationAsyncRequest
import net.corda.data.membership.async.request.RetriableFailure
import net.corda.data.membership.async.request.SentToMgmWaitingForNetwork
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import net.corda.utilities.time.UTCClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class CommandsRetryManagerTest {
    private val messagingConfig = mock<SmartConfig>()
    private val publisher = mock<Publisher>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), same(messagingConfig)) } doReturn publisher
    }
    private val future = mock<ScheduledFuture<Unit>>()
    private val task = argumentCaptor<Runnable>()
    private val scheduledExecutorService = mock<ScheduledExecutorService> {
        on { schedule(task.capture(), any(), any()) } doReturn future
    }
    private val now = Instant.ofEpochMilli(30000)
    private val clock = mock<UTCClock> {
        on { instant() } doReturn now
    }

    private val manager = CommandsRetryManager(
        publisherFactory,
        messagingConfig,
        clock,
        scheduledExecutorService,
    )

    @Test
    fun `constructor will start the publisher`() {
        verify(publisher).start()
    }

    @Test
    fun `close will close the publisher`() {
        manager.close()

        verify(publisher).close()
    }

    @Test
    fun `close will shutdown the scheduler`() {
        manager.close()

        verify(scheduledExecutorService).shutdownNow()
        verify(scheduledExecutorService).awaitTermination(any(), any())
    }

    @Test
    fun `onPartitionSynced will add a timer for each command`() {
        val states = (1..3).associate { keyIndex ->
            "key-$keyIndex" to
                MembershipAsyncRequestStates(
                    (1..5).map { requestIndex ->
                        mockState("$keyIndex-$requestIndex")
                    },
                )
        }

        manager.onPartitionSynced(states)

        task.allValues.forEach {
            it.run()
        }

        states.forEach { (key, states) ->
            states.states.forEach { state ->
                verify(publisher).publish(
                    listOf(
                        Record(
                            MEMBERSHIP_ASYNC_REQUEST_TOPIC,
                            key,
                            state.request,
                        ),
                    ),
                )
            }
        }
    }

    @Test
    fun `onPartitionLost will cancel the timer for each command`() {
        val keyCount = 3
        val requestPerKey = 2
        val states = (1..keyCount).associate { keyIndex ->
            "key-$keyIndex" to
                MembershipAsyncRequestStates(
                    (1..requestPerKey).map { requestIndex ->
                        mockState("$keyIndex-$requestIndex")
                    },
                )
        }
        manager.onPartitionSynced(states)

        manager.onPartitionLost(states)

        verify(future, times(keyCount * requestPerKey)).cancel(false)
    }

    @Test
    fun `onPostCommit will cancel the timers for removed state`() {
        val startStates = mapOf(
            "key" to MembershipAsyncRequestStates(
                listOf(
                    mockState("requestId"),
                ),
            ),
        )
        manager.onPartitionSynced(startStates)
        val states = mapOf(
            "key" to null,
        )

        manager.onPostCommit(states)

        verify(future).cancel(false)
    }

    @Test
    fun `onPostCommit will cancel the timers for removed command`() {
        val startStates = mapOf(
            "key" to MembershipAsyncRequestStates(
                listOf(
                    mockState("requestId"),
                ),
            ),
        )
        manager.onPartitionSynced(startStates)
        val states = mapOf(
            "key" to MembershipAsyncRequestStates(emptyList()),
        )

        manager.onPostCommit(states)

        verify(future).cancel(false)
    }

    @Test
    fun `onPostCommit will not cancel the timers if command is still running`() {
        val state = mockState("requestId")
        val startStates = mapOf(
            "key" to MembershipAsyncRequestStates(
                listOf(
                    state,
                ),
            ),
        )
        manager.onPartitionSynced(startStates)
        val states = mapOf(
            "key" to MembershipAsyncRequestStates(listOf(state)),
        )

        manager.onPostCommit(states)

        verify(future, never()).cancel(false)
    }

    @Test
    fun `onPostCommit will create a timer for new commands commands`() {
        val state = mockState("requestId")
        val states = mapOf(
            "key-1" to null,
            "key-2" to MembershipAsyncRequestStates(listOf(state)),
        )

        manager.onPostCommit(states)

        verify(scheduledExecutorService).schedule(any(), any(), any())
    }

    @Test
    fun `addTimer will use the correct delay after failure`() {
        val state = mockState("id")
        whenever(state.cause).doReturn(
            RetriableFailure(
                5,
                now.plusSeconds(2),
            ),
        )
        val states = mapOf(
            "key" to MembershipAsyncRequestStates(listOf(state)),
        )

        manager.onPostCommit(states)

        verify(scheduledExecutorService).schedule(any(), eq(2000), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `addTimer will publish without waiting if no waiting is needed`() {
        val state = mockState("id")
        whenever(state.cause).doReturn(
            RetriableFailure(
                5,
                now.minusSeconds(2),
            ),
        )
        val states = mapOf(
            "key" to MembershipAsyncRequestStates(listOf(state)),
        )

        manager.onPostCommit(states)

        verify(publisher).publish(argThat { size == 1 })
    }

    @Test
    fun `addTimer will use the correct delay after sending`() {
        val state = mockState("id")
        whenever(state.cause).doReturn(SentToMgmWaitingForNetwork())
        val states = mapOf(
            "key" to MembershipAsyncRequestStates(listOf(state)),
        )

        manager.onPostCommit(states)

        verify(scheduledExecutorService).schedule(any(), eq(40000L), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `timer will republish the command`() {
        val records = argumentCaptor<List<Record<String, Any>>>()
        whenever(publisher.publish(records.capture())).doReturn(emptyList())
        val state = mockState("id")
        val states = mapOf(
            "key" to MembershipAsyncRequestStates(listOf(state)),
        )
        manager.onPostCommit(states)

        task.firstValue.run()

        assertThat(records.firstValue).hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(MEMBERSHIP_ASYNC_REQUEST_TOPIC)
            }
            .allSatisfy {
                assertThat(it.key).isEqualTo("key")
            }
            .allSatisfy {
                assertThat(it.value).isSameAs(state.request)
            }
    }

    private fun mockState(id: String): MembershipAsyncRequestState {
        val registrationAsyncRequest = mock<RegistrationAsyncRequest> {
            on { requestId } doReturn id
        }
        val membershipAsyncRequest = mock<MembershipAsyncRequest> {
            on { request } doReturn registrationAsyncRequest
        }
        return mock<MembershipAsyncRequestState> {
            on { request } doReturn membershipAsyncRequest
        }
    }
}
