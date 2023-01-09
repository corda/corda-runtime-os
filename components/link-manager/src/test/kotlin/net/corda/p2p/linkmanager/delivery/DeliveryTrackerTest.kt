package net.corda.p2p.linkmanager.delivery

import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.lifecycle.domino.logic.util.StateAndEventSubscriptionDominoTile
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.AuthenticatedMessageDeliveryState
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerProcessedMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.virtualnode.toAvro
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.*

class DeliveryTrackerTest {

    companion object {
        const val timeStamp = 2635L
        const val groupId = "group"

        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }
    }

    @AfterEach
    fun cleanUp() {
        publisherWithDominoLogic.close()
        dominoTile.close()
        subscriptionTile.close()
        replayScheduler.close()
        loggingInterceptor.reset()
    }

    private fun processAuthenticatedMessage(messageAndKey: AuthenticatedMessageAndKey): List<Record<String, *>> {
        return listOf(Record("TOPIC", "Key", messageAndKey))
    }

    private val publisherWithDominoLogic = Mockito.mockConstruction(PublisherWithDominoLogic::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.toNamedLifecycle()).thenReturn(mock())
        }
        whenever(mock.dominoTile).thenReturn(mockDominoTile)
    }
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { mock, _ ->
        @Suppress("UNCHECKED_CAST")
        whenever(mock.withLifecycleLock(any<() -> Any>())).doAnswer { (it.arguments.first() as () -> Any).invoke() }
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
    }
    private val subscriptionTile = Mockito.mockConstruction(StateAndEventSubscriptionDominoTile::class.java) { mock, context ->
        whenever(mock.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        @Suppress("UNCHECKED_CAST")
        (context.arguments()[1] as (() -> StateAndEventSubscription<String, AuthenticatedMessageDeliveryState, AppMessageMarker>)).invoke()
    }

    private val replayScheduler = Mockito.mockConstruction(ReplayScheduler::class.java) { mock, _ ->
        val mockDominoTile = mock<ComplexDominoTile> {
            whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
        }
        whenever(mock.dominoTile).doReturn(mockDominoTile)
    }

    private val source = createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)
    private val dest = createTestHoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", groupId)
    private val header = mock<AuthenticatedMessageHeader> {
        on { source } doReturn source.toAvro()
        on { destination } doReturn dest.toAvro()
    }
    private val message = mock<AuthenticatedMessage> {
        on { header } doReturn header
    }
    private val messageAndKey = mock<AuthenticatedMessageAndKey> {
        on { message } doReturn message
    }

    class MockStateAndEventSubscription<K : Any, S: Any, E: Any>: StateAndEventSubscription<K, S, E> {
        @Volatile
        var isRunning = false

        override fun close() {
            isRunning = false
            return
        }

        override fun start() {
            isRunning = true
            return
        }

        override val subscriptionName: LifecycleCoordinatorName
            get() = LifecycleCoordinatorName("MockStateAndEventSubscription")

    }

    private fun createTracker(
    ): Triple<
        DeliveryTracker,
        StateAndEventProcessor<String, AuthenticatedMessageDeliveryState, AppMessageMarker>,
        StateAndEventListener<String, AuthenticatedMessageDeliveryState>
    > {
        val publisherFactory = Mockito.mock(PublisherFactory::class.java)

        val subscriptionFactory = Mockito.mock(SubscriptionFactory::class.java)
        val mockSubscription = MockStateAndEventSubscription<String, AuthenticatedMessageDeliveryState, AppMessageMarker>()
        Mockito.`when`(subscriptionFactory
            .createStateAndEventSubscription<String, AuthenticatedMessageDeliveryState, AppMessageMarker>(any(), any(), any(), any()))
            .thenReturn(mockSubscription)

        val tracker = DeliveryTracker(
            mock(),
            mock(),
            publisherFactory,
            mock(),
            subscriptionFactory,
            mock {
                val mockDominoTile = mock<ComplexDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                whenever(it.dominoTile).thenReturn(mockDominoTile)
            },
            mock {
                val mockDominoTile = mock<ComplexDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                whenever(it.dominoTile).thenReturn(mockDominoTile)
            },
            mock {
                val mockNamedLifecycle = mock<NamedLifecycle> {
                    whenever(it.name).doReturn(LifecycleCoordinatorName("", ""))
                }
                whenever(it.namedLifecycle).thenReturn(mockNamedLifecycle)
            },
            mock {
                val mockDominoTile = mock<ComplexDominoTile> {
                    whenever(it.coordinatorName).doReturn(LifecycleCoordinatorName("", ""))
                }
                whenever(it.dominoTile).thenReturn(mockDominoTile)
            },
            mock(),
            ::processAuthenticatedMessage
        )

        val processorCaptor = argumentCaptor<StateAndEventProcessor<String, AuthenticatedMessageDeliveryState, AppMessageMarker>>()
        val listenerCaptor = argumentCaptor<StateAndEventListener<String, AuthenticatedMessageDeliveryState>>()

        Mockito.verify(subscriptionFactory)
            .createStateAndEventSubscription(anyOrNull(), processorCaptor.capture(), anyOrNull(), listenerCaptor.capture())
        return Triple(tracker, processorCaptor.firstValue , listenerCaptor.firstValue)
    }

    @Test
    fun `The DeliveryTracker updates the markers state topic after observing a LinkManagerProcessedMarker`() {
        val (tracker, processor) = createTracker()
        tracker.start()
        val messageId = UUID.randomUUID().toString()
        val event = Record("topic", messageId, AppMessageMarker(LinkManagerProcessedMarker(messageAndKey), timeStamp))
        val response = processor.onNext(null, event)
        tracker.stop()

        assertEquals(0, response.responseEvents.size)
        assertNotNull(response.updatedState)
        assertSame(messageAndKey, response.updatedState!!.message)
        assertEquals(timeStamp, response.updatedState!!.timestamp)
    }

    @Test
    fun `The DeliveryTracker deletes the markers state after observing a LinkManagerReceivedMarker`() {
        val (tracker, processor) = createTracker()

        val messageId = UUID.randomUUID().toString()
        val event = Record("topic", messageId, AppMessageMarker(LinkManagerReceivedMarker(), timeStamp))
        val response = processor.onNext(null, event)
        tracker.stop()

        assertEquals(0, response.responseEvents.size)
        assertNull(response.updatedState)
    }

    @Test
    fun `The DeliveryTracker adds a message to be replayed (by the replayScheduler) after the markers state topic is committed`() {
        val (tracker, _, listener) = createTracker()

        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val state = AuthenticatedMessageDeliveryState(messageAndKey, Instant.now().toEpochMilli())
        listener.onPostCommit(mapOf(messageId to state))
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<AuthenticatedMessageAndKey>)
            .addForReplay(
                any(),
                eq(messageId),
                eq(messageAndKey),
                eq(SessionManager.SessionCounterparties(source, dest))
            )
        tracker.stop()
    }

    @Test
    fun `The DeliveryTracker adds a message to be replayed when their is state in the markers state topic (on assignment)`() {
        val (tracker, _, listener) = createTracker()

        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val state = AuthenticatedMessageDeliveryState(messageAndKey, Instant.now().toEpochMilli())
        listener.onPartitionSynced(mapOf(messageId to state))
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<AuthenticatedMessageAndKey>)
            .addForReplay(
                any(),
                eq(messageId),
                eq(messageAndKey),
                eq(SessionManager.SessionCounterparties(source, dest))
            )
        tracker.stop()
    }

    @Test
    fun `The DeliverTracker stops replaying a message after observing a LinkManagerReceivedMarker`() {
        val (tracker, _, listener) = createTracker()
        tracker.start()

        val messageId = UUID.randomUUID().toString()

        val state = AuthenticatedMessageDeliveryState(messageAndKey, Instant.now().toEpochMilli())
        listener.onPartitionSynced(mapOf(messageId to state))
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<AuthenticatedMessageAndKey>)
            .addForReplay(
                any(),
                eq(messageId),
                eq(messageAndKey),
                eq(SessionManager.SessionCounterparties(source, dest))
            )

        listener.onPostCommit(mapOf(messageId to null))
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<AuthenticatedMessageAndKey>)
            .removeFromReplay(messageId, SessionManager.SessionCounterparties(source, dest))
        tracker.stop()
    }

    @Test
    fun `The DeliverTracker stops replaying a message if the state is reassigned`() {
        val (tracker, _, listener) = createTracker()
        tracker.start()

        val messageId = UUID.randomUUID().toString()
        val state = AuthenticatedMessageDeliveryState(messageAndKey, Instant.now().toEpochMilli())
        listener.onPartitionSynced(mapOf(messageId to state))
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<AuthenticatedMessageAndKey>)
            .addForReplay(
                any(),
                eq(messageId),
                eq(messageAndKey),
                eq(SessionManager.SessionCounterparties(source, dest))
            )

        listener.onPartitionLost(mapOf(messageId to state))
        @Suppress("UNCHECKED_CAST")
        verify(replayScheduler.constructed().last() as ReplayScheduler<AuthenticatedMessageAndKey>)
            .removeFromReplay(messageId, SessionManager.SessionCounterparties(source, dest))
        tracker.stop()
    }
}
