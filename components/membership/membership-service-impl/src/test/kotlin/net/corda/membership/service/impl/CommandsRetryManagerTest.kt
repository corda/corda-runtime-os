package net.corda.membership.service.impl

import net.corda.data.membership.async.request.MembershipAsyncRequest
import net.corda.data.membership.async.request.MembershipAsyncRequestState
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.TimerEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Membership.MEMBERSHIP_ASYNC_REQUEST_TOPIC
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class CommandsRetryManagerTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val messagingConfig = mock<SmartConfig>()
    private val publisher = mock<Publisher>()
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), same(messagingConfig)) } doReturn publisher
    }
    private val nowInMillis = 10000L
    private val now = mock<Instant> {
        on { toEpochMilli() } doReturn nowInMillis
    }
    private val clock = mock<Clock> {
        on { instant() } doReturn now
    }

    private val manager = CommandsRetryManager(
        coordinatorFactory,
        publisherFactory,
        messagingConfig,
        clock,
    )

    @Test
    fun `constructor will start the publisher`() {
        verify(publisher).start()
    }

    @Test
    fun `constructor will start the coordinator`() {
        verify(coordinator).start()
    }

    @Test
    fun `constructor will set the coordinator status to UP`() {
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `close will close the publisher`() {
        manager.close()

        verify(publisher).close()
    }

    @Test
    fun `close will close the coordinator`() {
        manager.close()

        verify(coordinator).close()
    }

    @Test
    fun `onPartitionSynced will add a timer for each command`() {
        val states = (1..3).associate {
            "key-$it" to mock<MembershipAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            }
        }

        manager.onPartitionSynced(states)

        verify(coordinator).setTimer(eq("retry-key-1"), eq(2000), any())
        verify(coordinator).setTimer(eq("retry-key-2"), eq(2000), any())
        verify(coordinator).setTimer(eq("retry-key-3"), eq(2000), any())
    }

    @Test
    fun `onPartitionLost will cancel the timer for each command`() {
        val states = (1..3).associate {
            "key-$it" to mock<MembershipAsyncRequestState>()
        }

        manager.onPartitionLost(states)

        verify(coordinator).cancelTimer("retry-key-1")
        verify(coordinator).cancelTimer("retry-key-2")
        verify(coordinator).cancelTimer("retry-key-3")
    }

    @Test
    fun `onPostCommit will cancel the timers for removed commands`() {
        val states = mapOf(
            "key-1" to null,
            "key-2" to mock<MembershipAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            },
            "key-3" to null,
            "key-4" to mock<MembershipAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            },
        )

        manager.onPostCommit(states)

        verify(coordinator).cancelTimer("retry-key-1")
        verify(coordinator, never()).cancelTimer("retry-key-2")
        verify(coordinator).cancelTimer("retry-key-3")
        verify(coordinator, never()).cancelTimer("retry-key-4")
    }

    @Test
    fun `onPostCommit will create a timer for new commands commands`() {
        val states = mapOf(
            "key-1" to null,
            "key-2" to mock<MembershipAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            },
            "key-3" to null,
            "key-4" to mock<MembershipAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            },
        )

        manager.onPostCommit(states)

        verify(coordinator, never()).setTimer(eq("retry-key-1"), eq(2000), any())
        verify(coordinator).setTimer(eq("retry-key-2"), eq(2000), any())
        verify(coordinator, never()).setTimer(eq("retry-key-3"), eq(2000), any())
        verify(coordinator).setTimer(eq("retry-key-4"), eq(2000), any())
    }

    @Test
    fun `addTimer will use the correct delay`() {
        val failedOn = mock<Instant> {
            on { toEpochMilli() } doReturn nowInMillis - 500
        }
        val states = mapOf(
            "key" to mock<MembershipAsyncRequestState> {
                on { lastFailedOn } doReturn failedOn
                on { request } doReturn mock()
            },
        )

        manager.onPostCommit(states)

        verify(coordinator).setTimer(eq("retry-key"), eq(1500), any())
    }

    @Test
    fun `addTimer will not try to use negative duration`() {
        val failedOn = mock<Instant> {
            on { toEpochMilli() } doReturn nowInMillis - 3500
        }
        val states = mapOf(
            "key" to mock<MembershipAsyncRequestState> {
                on { lastFailedOn } doReturn failedOn
                on { request } doReturn mock()
            },
        )

        manager.onPostCommit(states)

        verify(coordinator).setTimer(eq("retry-key"), eq(0), any())
    }

    @Test
    fun `timer will republish the command`() {
        val records = argumentCaptor<List<Record<String, Any>>>()
        whenever(publisher.publish(records.capture())).doReturn(emptyList())
        val eventFactory = argumentCaptor<(String) -> TimerEvent>()
        whenever(coordinator.setTimer(any(), any(), eventFactory.capture())).doAnswer { }
        val command = mock<MembershipAsyncRequest>()
        val states = mapOf(
            "key" to mock<MembershipAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn command
            },
        )
        manager.onPostCommit(states)
        val event = eventFactory.firstValue.invoke("")

        handler.firstValue.processEvent(event, coordinator)

        assertThat(records.firstValue).hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(MEMBERSHIP_ASYNC_REQUEST_TOPIC)
            }
            .allSatisfy {
                assertThat(it.key).isEqualTo("key")
            }
            .allSatisfy {
                assertThat(it.value).isSameAs(command)
            }
    }
}
