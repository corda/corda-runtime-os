package net.corda.membership.impl.persistence.service

import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequest
import net.corda.data.membership.db.request.async.MembershipPersistenceAsyncRequestState
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.TimerEvent
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.utilities.time.Clock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class MembershipPersistenceAsyncRetryManagerTest {
    private val handler = argumentCaptor<LifecycleEventHandler>()
    private val coordinator = mock<LifecycleCoordinator>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory>() {
        on { createCoordinator(any(), handler.capture()) } doReturn coordinator
    }
    private val publisher = mock<Publisher> {
        on { publish(any()) } doReturn emptyList()
    }
    private val publisherFactory = mock<PublisherFactory> {
        on { createPublisher(any(), any()) } doReturn publisher
    }
    private val now = Instant.ofEpochMilli(300)
    private val clock = mock<Clock> {
        on { instant() } doReturn now
    }

    private val manager = MembershipPersistenceAsyncRetryManager(
        coordinatorFactory,
        publisherFactory,
        mock(),
        clock,

    )

    @Test
    fun `constructor start the publisher`() {
        verify(publisher).start()
    }

    @Test
    fun `constructor start the coordinator`() {
        verify(coordinator).start()
    }

    @Test
    fun `constructor set state to started`() {
        verify(coordinator).updateStatus(LifecycleStatus.UP)
    }

    @Test
    fun `close close the publisher`() {
        manager.close()

        verify(publisher).close()
    }

    @Test
    fun `close close the coordinator`() {
        manager.close()

        verify(coordinator).close()
    }

    @Test
    fun `onPartitionSynced will create a timer`() {
        val request = mock<MembershipPersistenceAsyncRequest>()
        val state = MembershipPersistenceAsyncRequestState(
            request,
            5,
            Instant.ofEpochMilli(300)
        )

        manager.onPartitionSynced(mapOf("key" to state))

        verify(coordinator).setTimer(eq("retry-key"), eq(2000), any())
    }

    @Test
    fun `onPartitionLost will stop the timer`() {
        val request = mock<MembershipPersistenceAsyncRequest>()
        val state = MembershipPersistenceAsyncRequestState(
            request,
            5,
            Instant.ofEpochMilli(300)
        )

        manager.onPartitionLost(mapOf("key" to state))

        verify(coordinator).cancelTimer(eq("retry-key"))
    }

    @Test
    fun `onPostCommit will cancel the timers for removed commands`() {
        val states = mapOf(
            "key-1" to null,
            "key-2" to mock<MembershipPersistenceAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            },
            "key-3" to null,
            "key-4" to mock {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            },
        )

        manager.onPostCommit(states)

        verify(coordinator).cancelTimer("retry-key-1")
        verify(coordinator, Mockito.never()).cancelTimer("retry-key-2")
        verify(coordinator).cancelTimer("retry-key-3")
        verify(coordinator, Mockito.never()).cancelTimer("retry-key-4")
    }

    @Test
    fun `onPostCommit will create a timer for new commands commands`() {
        val states = mapOf(
            "key-1" to null,
            "key-2" to mock {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            },
            "key-3" to null,
            "key-4" to mock<MembershipPersistenceAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn mock()
            },
        )

        manager.onPostCommit(states)

        verify(coordinator, Mockito.never()).setTimer(eq("retry-key-1"), eq(2000), any())
        verify(coordinator).setTimer(eq("retry-key-2"), eq(2000), any())
        verify(coordinator, Mockito.never()).setTimer(eq("retry-key-3"), eq(2000), any())
        verify(coordinator).setTimer(eq("retry-key-4"), eq(2000), any())
    }

    @Test
    fun `addTimer will use the correct delay`() {
        val failedOn = mock<Instant> {
            on { toEpochMilli() } doReturn now.toEpochMilli() - 500
        }
        val states = mapOf(
            "key" to mock<MembershipPersistenceAsyncRequestState> {
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
            on { toEpochMilli() } doReturn now.toEpochMilli() - 3500
        }
        val states = mapOf(
            "key" to mock<MembershipPersistenceAsyncRequestState> {
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
        val command = mock<MembershipPersistenceAsyncRequest>()
        val states = mapOf(
            "key" to mock<MembershipPersistenceAsyncRequestState> {
                on { lastFailedOn } doReturn now
                on { request } doReturn command
            },
        )
        manager.onPostCommit(states)
        val event = eventFactory.firstValue.invoke("")

        handler.firstValue.processEvent(event, coordinator)

        assertThat(records.firstValue).hasSize(1)
            .allSatisfy {
                assertThat(it.topic).isEqualTo(Schemas.Membership.MEMBERSHIP_DB_ASYNC_TOPIC)
            }
            .allSatisfy {
                assertThat(it.key).isEqualTo("key")
            }
            .allSatisfy {
                assertThat(it.value).isSameAs(command)
            }
    }
}
