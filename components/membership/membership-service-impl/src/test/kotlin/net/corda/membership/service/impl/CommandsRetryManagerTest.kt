package net.corda.membership.service.impl

import net.corda.data.KeyValuePairList
import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
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
    private val holdingId = "holdingId"

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
        val requests = (1..3).associate { requestIndex ->
            "request-$requestIndex" to mockState("request-$requestIndex")
        }
        val records = argumentCaptor<List<Record<*, *>>>()
        whenever(publisher.publish(records.capture())).doReturn(emptyList())

        manager.onPartitionSynced(requests)

        task.allValues.forEach {
            it.run()
        }

        requests.forEach { (_, state) ->
            verify(publisher).publish(
                listOf(
                    Record(
                        MEMBERSHIP_ASYNC_REQUEST_TOPIC,
                        holdingId,
                        MembershipAsyncRequest(
                            state.request,
                            state,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `onPartitionLost will cancel the timer for each command`() {
        val requests = (1..3).associate { requestIndex ->
            "request-$requestIndex" to mockState("request-$requestIndex")
        }
        manager.onPartitionSynced(requests)

        manager.onPartitionLost(requests)

        verify(future, times(3)).cancel(false)
    }

    @Test
    fun `onPostCommit will cancel the timers for removed requst`() {
        val startRequests = mapOf(
            "requestId" to mockState("requestId"),
        )
        manager.onPartitionSynced(startRequests)
        val requests = mapOf(
            "requestId" to null,
        )

        manager.onPostCommit(requests)

        verify(future).cancel(false)
    }

    @Test
    fun `onPostCommit will create a timer for new commands commands`() {
        val state = mockState("requestId")
        val states = mapOf(
            "requestId" to state,
        )

        manager.onPostCommit(states)

        verify(scheduledExecutorService).schedule(any(), any(), any())
    }

    @Test
    fun `addTimer will use the correct delay after failure`() {
        val state = mockState("id")
        state.cause = RetriableFailure(
            5,
            now.plusSeconds(2),
        )
        val states = mapOf(
            "id" to state,
        )

        manager.onPostCommit(states)

        verify(scheduledExecutorService).schedule(any(), eq(2000), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun `addTimer will publish without waiting if no waiting is needed`() {
        val state = mockState("id")
        state.cause = RetriableFailure(
            5,
            now.minusSeconds(2),
        )
        val states = mapOf(
            "id" to state,
        )

        manager.onPostCommit(states)

        verify(publisher).publish(argThat { size == 1 })
    }

    @Test
    fun `addTimer will use the correct delay after sending`() {
        val state = mockState("id")
        state.cause = SentToMgmWaitingForNetwork()
        val states = mapOf(
            "id" to state,
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
            "id" to state,
        )
        manager.onPostCommit(states)

        task.firstValue.run()

        assertThat(records.firstValue).hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(MEMBERSHIP_ASYNC_REQUEST_TOPIC)
            }
            .allSatisfy {
                assertThat(it.key).isEqualTo(holdingId)
            }
            .allSatisfy {
                assertThat(it.value).isEqualTo(
                    MembershipAsyncRequest(
                        state.request,
                        state,
                    ),
                )
            }
    }

    private fun mockState(id: String): MembershipAsyncRequestState {
        return MembershipAsyncRequestState(
            RegistrationAsyncRequest(
                holdingId,
                id,
                KeyValuePairList(),
            ),
            null,
        )
    }
}
