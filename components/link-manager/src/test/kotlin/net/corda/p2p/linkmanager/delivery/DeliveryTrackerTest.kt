package net.corda.p2p.linkmanager.delivery

import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.AuthenticatedMessageDeliveryState
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

class DeliveryTrackerTest {

    companion object {
        const val timeStamp = 2635L
        private val replayPeriod = Duration.ofMillis(10)

        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @AfterEach
    fun resetLogging() {
        loggingInterceptor.reset()
    }

    private fun processAuthenticatedMessage(messageAndKey: AuthenticatedMessageAndKey): List<Record<String, *>> {
        return listOf(Record("TOPIC", "Key", messageAndKey))
    }

    class TestListBasedPublisher(private val publishLatch: CountDownLatch? = null, val waitLatch: CountDownLatch? = null): Publisher {

        var list: MutableList<Record<*, *>> = Collections.synchronizedList(mutableListOf<Record<*, *>>())

        override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
            fail("publishToPartition should never be called in this test.")
        }

        override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            list.addAll(records)
            publishLatch?.countDown()
            publishLatch?.let {
                if (it.count == 0L) waitLatch?.await()
            }
            return emptyList()
        }

        override fun close() {}
    }

    class MockStateAndEventSubscription<K : Any, S: Any, E: Any>: StateAndEventSubscription<K, S, E> {
        @Volatile
        override var isRunning = false

        override fun stop() {
            isRunning = false
            return
        }

        override fun start() {
            isRunning = true
            return
        }

        override fun getValue(key: K): S? {
            fail("getValue should not be called in this test.")
        }
    }

    private fun createTracker(
        publisher: TestListBasedPublisher,
    ): Triple<
        DeliveryTracker,
        StateAndEventProcessor<String, AuthenticatedMessageDeliveryState, AppMessageMarker>,
        StateAndEventListener<String, AuthenticatedMessageDeliveryState>
    > {
        val publisherFactory = Mockito.mock(PublisherFactory::class.java)
        Mockito.`when`(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)

        val subscriptionFactory = Mockito.mock(SubscriptionFactory::class.java)
        val mockSubscription = MockStateAndEventSubscription<String, AuthenticatedMessageDeliveryState, AppMessageMarker>()
        Mockito.`when`(subscriptionFactory
            .createStateAndEventSubscription<String, AuthenticatedMessageDeliveryState, AppMessageMarker>(any(), any(), any(), any()))
            .thenReturn(mockSubscription)

        val tracker = DeliveryTracker(
            replayPeriod,
            publisherFactory,
            subscriptionFactory,
            ::processAuthenticatedMessage
        )

        val processorCaptor = argumentCaptor<StateAndEventProcessor<String, AuthenticatedMessageDeliveryState, AppMessageMarker>>()
        val listenerCaptor = argumentCaptor<StateAndEventListener<String, AuthenticatedMessageDeliveryState>>()

        Mockito.verify(subscriptionFactory)
            .createStateAndEventSubscription(anyOrNull(), processorCaptor.capture(), anyOrNull(), listenerCaptor.capture())
        return Triple(tracker, processorCaptor.firstValue , listenerCaptor.firstValue)
    }

    @Test
    fun `The DeliveryTracker updates the markers state topic after observing a LinkManagerSentMarker`() {

        val (tracker, processor) = createTracker(TestListBasedPublisher())
        tracker.start()
        val messageId = UUID.randomUUID().toString()
        val messageAndKey = Mockito.mock(AuthenticatedMessageAndKey::class.java)
        val event = Record("topic", messageId, AppMessageMarker(LinkManagerSentMarker(messageAndKey), timeStamp))
        val response = processor.onNext(null, event)
        tracker.stop()

        assertEquals(0, response.responseEvents.size)
        assertNotNull(response.updatedState)
        assertSame(messageAndKey, response.updatedState!!.message)
        assertEquals(timeStamp, response.updatedState!!.timestamp)
    }

    @Test
    fun `The DeliveryTracker deletes the markers state after observing a LinkManagerReceivedMarker`() {
        val (tracker, processor) = createTracker(TestListBasedPublisher())
        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val event = Record("topic", messageId, AppMessageMarker(LinkManagerReceivedMarker(), timeStamp))
        val response = processor.onNext(null, event)
        tracker.stop()

        assertEquals(0, response.responseEvents.size)
        assertNull(response.updatedState)
    }

    @Test
    fun `The DeliveryTracker replays messages after the markers state topic is committed`() {
        val replays = 2

        val latch = CountDownLatch(replays)

        val publisher = TestListBasedPublisher(latch)
        val (tracker, _, listener) = createTracker(publisher)

        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val messageAndKey = Mockito.mock(AuthenticatedMessageAndKey::class.java)
        val state = AuthenticatedMessageDeliveryState(messageAndKey, Instant.now().toEpochMilli())
        listener.onPostCommit(mapOf(messageId to state))
        latch.await()

        assertTrue(publisher.list.size >= replays)
        assertEquals(publisher.list[0], publisher.list[1])
        assertSame(messageAndKey, publisher.list[0].value)

        tracker.stop()
    }

    @Test
    fun `The DeliveryTracker replays messages when their is state in the markers state topic (on assignment)`() {
        val replays = 2

        val latch = CountDownLatch(replays)
        val publisher = TestListBasedPublisher(latch)

        val (tracker, _, listener) = createTracker(publisher)

        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val messageAndKey = Mockito.mock(AuthenticatedMessageAndKey::class.java)
        val state = AuthenticatedMessageDeliveryState(messageAndKey, Instant.now().toEpochMilli())
        listener.onPartitionSynced(mapOf(messageId to state))
        latch.await()

        assertTrue(publisher.list.size >= replays)
        assertEquals(publisher.list[0], publisher.list[1])
        assertSame(messageAndKey, publisher.list[0].value)

        tracker.stop()
    }

    @Test
    fun `The DeliverTracker stops replaying a message after observing a LinkManagerReceivedMarker`() {
        val replays = 2

        val testWaitLatch = CountDownLatch(replays)
        val publisherWaitLatch = CountDownLatch(1)
        val publisher = TestListBasedPublisher(testWaitLatch, publisherWaitLatch)

        val (tracker, _, listener) = createTracker(publisher)
        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val messageAndKey = Mockito.mock(AuthenticatedMessageAndKey::class.java)
        val state = AuthenticatedMessageDeliveryState(messageAndKey, Instant.now().toEpochMilli())
        listener.onPartitionSynced(mapOf(messageId to state))
        testWaitLatch.await()

        assertEquals(replays, publisher.list.size)
        assertEquals(publisher.list[0], publisher.list[1])
        assertSame(messageAndKey, publisher.list[0].value)
        listener.onPostCommit(mapOf(messageId to null))

        publisherWaitLatch.countDown()

        Thread.sleep(5 * replayPeriod.toMillis())

        assertEquals(replays, publisher.list.size)

        tracker.stop()
    }

    @Test
    fun `The DeliverTracker stops replaying a message if the state is reassigned`() {
        val replays = 2

        val testWaitLatch = CountDownLatch(replays)
        val publisherWaitLatch = CountDownLatch(1)

        val publisher = TestListBasedPublisher(testWaitLatch, publisherWaitLatch)

        val (tracker, _, listener) = createTracker(publisher)
        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val messageAndKey = Mockito.mock(AuthenticatedMessageAndKey::class.java)
        val state = AuthenticatedMessageDeliveryState(messageAndKey, Instant.now().toEpochMilli())
        listener.onPartitionSynced(mapOf(messageId to state))
        testWaitLatch.await()

        assertEquals(replays, publisher.list.size)
        assertEquals(publisher.list[0], publisher.list[1])
        assertSame(messageAndKey, publisher.list[0].value)
        listener.onPartitionLost(mapOf(messageId to state))

        publisherWaitLatch.countDown()

        Thread.sleep(5 * replayPeriod.toMillis())

        assertEquals(replays, publisher.list.size)

        tracker.stop()
    }
}