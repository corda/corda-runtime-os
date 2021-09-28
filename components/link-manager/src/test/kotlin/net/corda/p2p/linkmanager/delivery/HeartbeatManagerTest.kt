package net.corda.p2p.linkmanager.delivery

import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.emulation.publisher.factory.CordaPublisherFactory
import net.corda.messaging.emulation.subscription.factory.InMemSubscriptionFactory
import net.corda.messaging.emulation.topic.service.impl.TopicServiceImpl
import net.corda.p2p.HeartbeatMessage
import net.corda.p2p.LinkManagerPayload
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap
import net.corda.p2p.linkmanager.LinkManagerTest.Companion.createSessionPair
import net.corda.p2p.linkmanager.messaging.AvroSealedClasses
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.MockNetworkMap
import net.corda.p2p.schema.Schema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail
import org.mockito.Mockito
import org.mockito.kotlin.anyOrNull
import java.lang.RuntimeException
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HeartbeatManagerTest {

    companion object {
        private const val SESSION_MESSAGE_ID = "MySessionMessage"
        private const val MESSAGE_ID = "MyMessage"
        private const val SESSION_ID = "MySession"
        private const val MY_GROUP = "testGroup"
        private val INBOUND_PARTY = LinkManagerNetworkMap.HoldingIdentity("In", "Group")
        private val OUTBOUND_PARTY = LinkManagerNetworkMap.HoldingIdentity("Out", "Group")
        lateinit var loggingInterceptor: LoggingInterceptor
        private val heartbeatPeriod = Duration.ofMillis(200)
        private const val heartbeatsBeforeTimeout = 4
        private val sessionTimeoutPeriod =  Duration.ofMillis((heartbeatsBeforeTimeout + 1) * heartbeatPeriod.toMillis())

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

    private val netMap = MockNetworkMap(listOf(INBOUND_PARTY, OUTBOUND_PARTY)).getSessionNetworkMapForNode(INBOUND_PARTY)
    private val topicService = TopicServiceImpl()
    private val publisherFactory = CordaPublisherFactory(topicService)
    private val subscriptionFactory = InMemSubscriptionFactory(topicService)

    @Test
    fun `An added session message will eventually timeout`() {
        val timeoutLatch = CountDownLatch(1)

        val heartbeatManager = HeartbeatManagerImpl(publisherFactory, netMap, Duration.ofMillis(2), Duration.ofMillis(2))
        heartbeatManager.start()
        heartbeatManager.sessionMessageSent(SESSION_MESSAGE_ID, SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY), SESSION_ID) { _, _ -> timeoutLatch.countDown() }
        assertTrue(timeoutLatch.await(20, TimeUnit.MILLISECONDS))
        heartbeatManager.stop()
    }

    @Test
    fun `An added message will cause a heartbeat message to be sent periodically and will eventually timeout`() {
        val interceptingProcessor = InterceptingProcessor()
        val timeoutLatch = CountDownLatch(1)
        val (inboundSession, outboundSession) = createSessionPair()
        val subscriptionConfig = SubscriptionConfig(MY_GROUP, Schema.LINK_OUT_TOPIC, 1)
        val subscription = subscriptionFactory.createEventLogSubscription(
            subscriptionConfig,
            interceptingProcessor,
            partitionAssignmentListener = null
        )
        subscription.start()

        val heartbeatManager = HeartbeatManagerImpl(publisherFactory, netMap, heartbeatPeriod, sessionTimeoutPeriod)
        heartbeatManager.start()
        heartbeatManager.sessionMessageSent(
            SESSION_MESSAGE_ID,
            SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY),
            SESSION_ID
        ) { _, _ -> timeoutLatch.countDown() }
        heartbeatManager.messageSent(MESSAGE_ID,
            SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY),
            outboundSession
        )
        assertTrue(timeoutLatch.await(5 * sessionTimeoutPeriod.toMillis(), TimeUnit.MILLISECONDS))

        assertEquals(heartbeatsBeforeTimeout, interceptingProcessor.messages.size)

        var sequenceNumber = 1L
        for (message in interceptingProcessor.messages) {
            assertNotNull(message)
            val decryptedMessage = extractPayload(inboundSession, message!!)
            assertTrue(decryptedMessage is HeartbeatMessage)
            assertEquals(OUTBOUND_PARTY.toHoldingIdentity(), (decryptedMessage as HeartbeatMessage).source)
            assertEquals(INBOUND_PARTY.toHoldingIdentity(), decryptedMessage.destination)
            assertEquals(sequenceNumber++, decryptedMessage.sequenceNumber)
        }

        subscription.stop()
        heartbeatManager.stop()
    }

    @Test
    fun `An added message will cause a heartbeat message to be sent periodically (if these are acknowledged) the session never times`() {
        val timeoutLatch = CountDownLatch(1)
        val (inboundSession, outboundSession) = createSessionPair()

        val heartbeatManager = HeartbeatManagerImpl(publisherFactory, netMap, heartbeatPeriod, sessionTimeoutPeriod)
        heartbeatManager.start()
        heartbeatManager.sessionMessageSent(
            SESSION_MESSAGE_ID,
            SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY),
            SESSION_ID
        ) { _, _ -> timeoutLatch.countDown() }
        heartbeatManager.messageSent(MESSAGE_ID,
            SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY),
            outboundSession
        )

        fun processorCallback(message: LinkOutMessage?) {
            val decryptedMessage = extractPayload(inboundSession, message!!)
            assertTrue(decryptedMessage is HeartbeatMessage)
            assertEquals(OUTBOUND_PARTY.toHoldingIdentity(), (decryptedMessage as HeartbeatMessage).source)
            assertEquals(INBOUND_PARTY.toHoldingIdentity(), decryptedMessage.destination)
            heartbeatManager.messageAcknowledged(decryptedMessage.messageId)
        }

        val interceptingProcessor = InterceptingProcessor(::processorCallback)
        val subscriptionConfig = SubscriptionConfig(MY_GROUP, Schema.LINK_OUT_TOPIC, 1)
        val subscription = subscriptionFactory.createEventLogSubscription(
            subscriptionConfig,
            interceptingProcessor,
            partitionAssignmentListener = null
        )
        subscription.start()

        assertFalse(timeoutLatch.await(5 * sessionTimeoutPeriod.toMillis(), TimeUnit.MILLISECONDS))
        assertTrue(interceptingProcessor.messages.size >= heartbeatsBeforeTimeout)

        subscription.stop()
        heartbeatManager.stop()
    }

    @Test
    fun `An added and acknowledged message and will cause a heartbeat message to be sent periodically the session eventually times out`() {

        val timeoutLatch = CountDownLatch(1)
        val (_, outboundSession) = createSessionPair()

        val heartbeatManager = HeartbeatManagerImpl(publisherFactory, netMap, heartbeatPeriod, sessionTimeoutPeriod)
        heartbeatManager.start()
        heartbeatManager.sessionMessageSent(
            SESSION_MESSAGE_ID,
            SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY),
            SESSION_ID
        ) { _, _ -> timeoutLatch.countDown() }
        heartbeatManager.messageAcknowledged(SESSION_MESSAGE_ID)
        heartbeatManager.messageSent(MESSAGE_ID, SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY), outboundSession)
        heartbeatManager.messageAcknowledged(MESSAGE_ID)

        val interceptingProcessor = InterceptingProcessor()
        val subscriptionConfig = SubscriptionConfig(MY_GROUP, Schema.LINK_OUT_TOPIC, 1)
        val subscription = subscriptionFactory.createEventLogSubscription(
            subscriptionConfig,
            interceptingProcessor,
            partitionAssignmentListener = null
        )
        subscription.start()

        assertTrue(timeoutLatch.await(5 * sessionTimeoutPeriod.toMillis(), TimeUnit.MILLISECONDS))
        assertTrue(interceptingProcessor.messages.size >= heartbeatsBeforeTimeout)

        subscription.stop()
        heartbeatManager.stop()
    }

    @Test
    fun `If an exception is thrown when sending a heartbeat the task is rescheduled again`() {
        val mockPublisherFactory = Mockito.mock(PublisherFactory::class.java)
        //First time we throw an exception so nothing gets published.
        val publishLatch = CountDownLatch(heartbeatsBeforeTimeout - 1)
        val throwingPublisher = ThrowingPublisher(publishLatch)

        Mockito.`when`(mockPublisherFactory.createPublisher(anyOrNull(), anyOrNull())).thenReturn(throwingPublisher)

        val timeoutLatch = CountDownLatch(1)
        val (_, outboundSession) = createSessionPair()

        val heartbeatManager = HeartbeatManagerImpl(mockPublisherFactory, netMap, heartbeatPeriod, sessionTimeoutPeriod)
        heartbeatManager.start()
        heartbeatManager.sessionMessageSent(
            SESSION_MESSAGE_ID,
            SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY),
            SESSION_ID
        ) { _, _ -> timeoutLatch.countDown() }
        heartbeatManager.messageSent(MESSAGE_ID, SessionManager.SessionKey(OUTBOUND_PARTY, INBOUND_PARTY), outboundSession)
        assertTrue(publishLatch.await(5 * sessionTimeoutPeriod.toMillis(), TimeUnit.MILLISECONDS))
        assertTrue(timeoutLatch.await(5 * sessionTimeoutPeriod.toMillis(), TimeUnit.MILLISECONDS))
        loggingInterceptor.assertErrorContains("An exception was thrown when sending a heartbeat message.")
    }

    class InterceptingProcessor(private val callback: ((LinkOutMessage?) -> Any)? = null): EventLogProcessor<String, LinkOutMessage> {

        val messages = mutableListOf<LinkOutMessage?>()

        override fun onNext(events: List<EventLogRecord<String, LinkOutMessage>>): List<Record<*, *>> {
            events.forEach { event ->
                messages.add(event.value)
                callback?.let { callback -> callback(event.value) }
            }
            return emptyList()
        }

        override val keyClass = String::class.java
        override val valueClass = LinkOutMessage::class.java
    }

    class ThrowingPublisher(val latch: CountDownLatch): Publisher {
        var firstPublish = true

        override fun publishToPartition(records: List<Pair<Int, Record<*, *>>>): List<CompletableFuture<Unit>> {
            fail("This should not be called in this test.")
        }

        override fun publish(records: List<Record<*, *>>): List<CompletableFuture<Unit>> {
            if (firstPublish) {
                firstPublish = false
                throw RuntimeException("Ohh No something went wrong.")
            }
            latch.countDown()
            return listOf(CompletableFuture.completedFuture(Unit))
        }

        override fun close() {}
    }

    private fun createDataMessage(message: LinkOutMessage): AvroSealedClasses.DataMessage {
        return when (val payload = message.payload) {
            is AuthenticatedDataMessage -> AvroSealedClasses.DataMessage.Authenticated(payload)
            is AuthenticatedEncryptedDataMessage -> AvroSealedClasses.DataMessage.AuthenticatedAndEncrypted(payload)
            else -> Assertions.fail("Tried to create a DataMessage from a LinkOutMessage which doesn't contain a AVRO data message.")
        }
    }

    private fun extractPayload(session: Session, message: LinkOutMessage): Any {
        val dataMessage = createDataMessage(message)
        val payload = MessageConverter.extractPayload(session, "", dataMessage, LinkManagerPayload::fromByteBuffer)
        assertNotNull(payload)
        assertNotNull(payload!!.message)
        return payload.message
    }
}