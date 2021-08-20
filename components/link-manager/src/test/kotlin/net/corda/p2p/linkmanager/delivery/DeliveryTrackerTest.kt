package net.corda.p2p.linkmanager.delivery

import com.typesafe.config.Config
import net.corda.messaging.api.processor.StateAndEventProcessorWithReassignment
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.RandomAccessSubscription
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.p2p.AuthenticatedMessageDeliveryState
import net.corda.p2p.app.AppMessage
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.schema.Schema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.Mockito
import org.mockito.kotlin.any
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

class DeliveryTrackerTest {

    companion object {
        const val partition = 10L
        const val offset = 50L
        const val replayPeriod = 10L
        const val timeStamp = 2635L

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

    private fun processEvent(event: EventLogRecord<ByteBuffer, AppMessage>): List<Record<String, *>> {
        return listOf(Record("TOPIC", "Key", event.value))
    }

    private class MapBasedRandomAccessSubscription(
        val latch: CountDownLatch? = null,
        val waitLatch: CountDownLatch? = null
    ): RandomAccessSubscription<ByteBuffer, AppMessage> {

        val records = ConcurrentHashMap<DeliveryTracker.PositionInTopic, Record<ByteBuffer, AppMessage>>()

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

        override fun getRecord(partition: Int, offset: Long): Record<ByteBuffer, AppMessage>? {
            latch?.countDown()
            if (latch?.count == 0L) waitLatch?.await()
            return records[DeliveryTracker.PositionInTopic(partition, offset)]
        }
    }

    class TestListBasedPublisher: Publisher {

        var list = mutableListOf<Record<*, *>>()

        override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
            throw RuntimeException("publishToPartition should never be called in this test.")
        }

        override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            list.addAll(records)
            return emptyList()
        }

        override fun close() {
            throw RuntimeException("close should never be called in this test.")
        }
    }

    private class MockStateAndEventSubscriptionFactory: StateAndEventSubscriptionWithReassignmentFactory {

        var interceptedProcessor: StateAndEventProcessorWithReassignment<*, *, *>? = null

        override fun <K : Any, S : Any, E : Any> createStateAndEventSubscription(
            subscriptionConfig: SubscriptionConfig,
            processor: StateAndEventProcessorWithReassignment<K, S, E>,
            nodeConfig: Config
        ): StateAndEventSubscription<K, S, E> {
            interceptedProcessor = processor
            return MockStateAndEventSubscription()
        }
    }

    class MockStateAndEventSubscription<K : Any, S: Any, E: Any>(): StateAndEventSubscription<K, S, E> {
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
        randomAccessSubscription: MapBasedRandomAccessSubscription
    ): Pair<
        DeliveryTracker,
        StateAndEventProcessorWithReassignment<String, AuthenticatedMessageDeliveryState, AppMessageMarker>
    > {
        val publisherFactory = Mockito.mock(PublisherFactory::class.java)
        Mockito.`when`(publisherFactory.createPublisher(any(), any())).thenReturn(publisher)

        val subscriptionFactory = Mockito.mock(SubscriptionFactory::class.java)
        Mockito.`when`(subscriptionFactory.createRandomAccessSubscription<ByteBuffer, AppMessage>(any(), any(), any(), any()))
            .thenReturn(randomAccessSubscription)

        val mockStateAndEventSubscriptionFactory = MockStateAndEventSubscriptionFactory()
        val tracker = DeliveryTracker(
            replayPeriod,
            publisherFactory,
            subscriptionFactory,
            mockStateAndEventSubscriptionFactory,
            ::processEvent
        )
        @Suppress("UNCHECKED_CAST")
        val processor = mockStateAndEventSubscriptionFactory.interceptedProcessor
                as StateAndEventProcessorWithReassignment<String, AuthenticatedMessageDeliveryState, AppMessageMarker>
        return tracker to processor
    }

    @Test
    fun `The DeliveryTracker updates the markers state topic after observing a LinkManagerSentMarker`() {

        val (tracker, processor) = createTracker(TestListBasedPublisher(), MapBasedRandomAccessSubscription())
        tracker.start()
        val messageId = UUID.randomUUID().toString()
        val event = Record("topic", messageId, AppMessageMarker(LinkManagerSentMarker(partition, offset), timeStamp))
        val response = processor.onNext(null, event)
        tracker.stop()

        assertEquals(0, response.responseEvents.size)
        assertNotNull(response.updatedState)
        assertEquals(partition, response.updatedState!!.partition)
        assertEquals(offset, response.updatedState!!.offset)
        assertEquals(timeStamp, response.updatedState!!.timestamp)
    }

    @Test
    fun `The DeliveryTracker deletes the markers state after observing a LinkManagerReceivedMarker`() {
        val (tracker, processor) = createTracker(TestListBasedPublisher(), MapBasedRandomAccessSubscription())
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

        val latch = CountDownLatch(replays + 1)
        val waitLatch = CountDownLatch(1)

        val randomAccessSubscription = MapBasedRandomAccessSubscription(latch, waitLatch)
        val publisher = TestListBasedPublisher()
        val (tracker, processor) = createTracker(publisher, randomAccessSubscription)

        val flowMessage = Mockito.mock(AppMessage::class.java)
        randomAccessSubscription.records[DeliveryTracker.PositionInTopic(partition.toInt(), offset)] =
            Record(Schema.P2P_OUT_TOPIC, ByteBuffer.wrap("".toByteArray()), flowMessage)

        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val state = AuthenticatedMessageDeliveryState(partition, offset, Instant.now().toEpochMilli())
        processor.onCommit(mapOf(messageId to state))
        latch.await()

        assertEquals(replays, publisher.list.size)
        assertEquals(publisher.list[0], publisher.list[1])
        assertSame(flowMessage, publisher.list[0].value)

        waitLatch.countDown()
        tracker.stop()
    }

    @Test
    fun `The DeliveryTracker replays messages when their is state in the markers state topic (on assignment)`() {
        val replays = 2

        val latch = CountDownLatch(replays + 1)
        val waitLatch = CountDownLatch(1)
        val publisher = TestListBasedPublisher()

        val randomAccessSubscription = MapBasedRandomAccessSubscription(latch, waitLatch)
        val (tracker, processor) = createTracker(publisher, randomAccessSubscription)

        val flowMessage = Mockito.mock(AppMessage::class.java)
        randomAccessSubscription.records[DeliveryTracker.PositionInTopic(partition.toInt(), offset)] =
            Record(Schema.P2P_OUT_TOPIC, ByteBuffer.wrap("".toByteArray()), flowMessage)

        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val state = AuthenticatedMessageDeliveryState(partition, offset, Instant.now().toEpochMilli())
        processor.onPartitionsAssigned(mapOf(messageId to state))
        latch.await()

        assertEquals(replays, publisher.list.size)
        assertEquals(publisher.list[0], publisher.list[1])
        assertSame(flowMessage, publisher.list[0].value)

        waitLatch.countDown()
        tracker.stop()
    }

    @Test
    fun `The DeliverTracker stops replaying a message after observing a LinkManagerReceivedMarker`() {
        val replays = 2

        val latch = CountDownLatch(replays + 1)
        val waitLatch = CountDownLatch(1)
        val publisher = TestListBasedPublisher()

        val randomAccessSubscription = MapBasedRandomAccessSubscription(latch, waitLatch)
        val flowMessage = Mockito.mock(AppMessage::class.java)
        randomAccessSubscription.records[DeliveryTracker.PositionInTopic(partition.toInt(), offset)] =
            Record(Schema.P2P_OUT_TOPIC, ByteBuffer.wrap("".toByteArray()), flowMessage)

        val (tracker, processor) = createTracker(publisher, randomAccessSubscription)
        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val state = AuthenticatedMessageDeliveryState(partition, offset, Instant.now().toEpochMilli())
        processor.onPartitionsAssigned(mapOf(messageId to state))
        latch.await()

        assertEquals(replays, publisher.list.size)
        assertEquals(publisher.list[0], publisher.list[1])
        assertSame(flowMessage, publisher.list[0].value)
        processor.onCommit(mapOf(messageId to null))

        waitLatch.countDown()
        Thread.sleep(5 * replayPeriod)

        //We get one more replay (which was in progress while waitLatch.await()).
        assertEquals(replays + 1, publisher.list.size)

        tracker.stop()
    }

    @Test
    fun `The DeliverTracker stops replaying a message if the state is reassigned`() {
        val replays = 2

        val latch = CountDownLatch(replays + 1)
        val waitLatch = CountDownLatch(1)
        val publisher = TestListBasedPublisher()

        val flowMessage = Mockito.mock(AppMessage::class.java)
        val randomAccessSubscription = MapBasedRandomAccessSubscription(latch, waitLatch)
        randomAccessSubscription.records[DeliveryTracker.PositionInTopic(partition.toInt(), offset)] =
            Record(Schema.P2P_OUT_TOPIC, ByteBuffer.wrap("".toByteArray()), flowMessage)

        val (tracker, processor) = createTracker(publisher, randomAccessSubscription)
        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val state = AuthenticatedMessageDeliveryState(partition, offset, Instant.now().toEpochMilli())
        processor.onPartitionsAssigned(mapOf(messageId to state))
        latch.await()

        assertEquals(replays, publisher.list.size)
        assertEquals(publisher.list[0], publisher.list[1])
        assertSame(flowMessage, publisher.list[0].value)
        processor.onPartitionsRevoked(mapOf(messageId to state))

        waitLatch.countDown()
        Thread.sleep(5 * replayPeriod)

        //We get one more replay (which was in progress while waitLatch.await()).
        assertEquals(replays + 1, publisher.list.size)

        tracker.stop()
    }

    @Test
    fun `The DeliveryTracker logs an error if it can't find a message to replay`() {
        val replays = 1

        val publisher = TestListBasedPublisher()

        val latch = CountDownLatch(replays + 1)
        val waitLatch = CountDownLatch(1)

        val (tracker, processor) = createTracker(publisher, MapBasedRandomAccessSubscription(latch, waitLatch))
        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val state = AuthenticatedMessageDeliveryState(partition, offset, Instant.now().toEpochMilli())
        processor.onCommit(mapOf(messageId to state))
        latch.await()
        loggingInterceptor.assertSingleError("Could not find a message for replay at partition $partition and offset $offset in topic " +
                "p2p.out. The message was not replayed.")
        waitLatch.countDown()

        Thread.sleep(5 * replayPeriod)

        tracker.stop()
        assertEquals(0, publisher.list.size)
    }

}