package net.corda.p2p.linkmanager

import com.nhaarman.mockito_kotlin.*
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.FlowMessage
import net.corda.p2p.FlowMessageHeader
import net.corda.p2p.HoldingIdentity
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager.Companion.getSessionKeyFromMessage
import net.corda.p2p.linkmanager.messaging.Messaging.Companion.createLinkOutMessageFromFlowMessage
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.slf4j.Logger
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.concurrent.CompletableFuture

class LinkManagerTest {

    companion object {
        val FIRST_SOURCE = HoldingIdentity("PartyA", "Group")
        val SECOND_SOURCE = HoldingIdentity("PartyA", "AnotherGroup")
        val FIRST_DEST = HoldingIdentity("PartyB", "Group")
        val SECOND_DEST = HoldingIdentity("PartyC", "Group")
        const val SESSION_ID = "testSession"
        const val MAX_MESSAGE_SIZE = 1000000
        const val GROUP_ID = "myGroup"
        const val KEY = "Key"
        const val TOPIC = "Topic"
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

    private fun simpleMockFlowMessage(source: HoldingIdentity, dest: HoldingIdentity, data: String): FlowMessage {
        val mockHeader = Mockito.mock(FlowMessageHeader::class.java)
        Mockito.`when`(mockHeader.source).thenReturn(source)
        Mockito.`when`(mockHeader.destination).thenReturn(dest)
        return FlowMessage(mockHeader, ByteBuffer.wrap(data.toByteArray()))
    }

    private fun complexMockFlowMessage(source: HoldingIdentity, dest: HoldingIdentity, data: ByteBuffer): FlowMessage {
        val mockHeader = Mockito.mock(FlowMessageHeader::class.java)
        Mockito.`when`(mockHeader.source).thenReturn(source)
        Mockito.`when`(mockHeader.destination).thenReturn(dest)

        val mockFlowMessage = Mockito.mock(FlowMessage::class.java)
        Mockito.`when`(mockFlowMessage.toByteBuffer()).thenReturn(data)
        Mockito.`when`(mockFlowMessage.header).thenReturn(mockHeader)

        return mockFlowMessage
    }

    private fun initiatorHelloLinkInMessage() : LinkInMessage {
        val session =  AuthenticationProtocolInitiator(SESSION_ID, setOf(ProtocolMode.AUTHENTICATION_ONLY), MAX_MESSAGE_SIZE)
        return LinkInMessage(session.generateInitiatorHello())
    }

    private data class SessionPair(val initiatorSession: Session, val responderSession: Session)

    //We can't use Mockito as Session is final
    private fun createSession(mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY): SessionPair {
        val provider = BouncyCastleProvider()
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        val partyAIdentityKey = keyPairGenerator.generateKeyPair()
        val partyBIdentityKey = keyPairGenerator.generateKeyPair()
        val signature = Signature.getInstance("ECDSA", provider)

        val initiator = AuthenticationProtocolInitiator(SESSION_ID, setOf(mode), MAX_MESSAGE_SIZE)
        val responder = AuthenticationProtocolResponder(SESSION_ID, setOf(mode), MAX_MESSAGE_SIZE)

        val initiatorHelloMsg = initiator.generateInitiatorHello()
        responder.receiveInitiatorHello(initiatorHelloMsg)

        val responderHelloMsg = responder.generateResponderHello()
        initiator.receiveResponderHello(responderHelloMsg)

        initiator.generateHandshakeSecrets()
        responder.generateHandshakeSecrets()

        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = initiator.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, GROUP_ID, signingCallbackForA)

        responder.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public }

        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = responder.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        initiator.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)
        return SessionPair(initiator.getSession(), responder.getSession())
    }

    private fun extractPayloadFromLinkOutMessage(message: LinkOutMessage): ByteBuffer {
        return (message.payload as AuthenticatedDataMessage).payload
    }

    @Test
    fun `PendingSessionsMessageQueues queueMessage returns true if a new session is needed`() {
        val message1 = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0-0")
        val message2 = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0-1")

        val message3 = simpleMockFlowMessage(SECOND_SOURCE, SECOND_DEST, "1-1")
        val message4 = simpleMockFlowMessage(SECOND_SOURCE, SECOND_DEST, "1-2")
        val message5 = simpleMockFlowMessage(SECOND_SOURCE, SECOND_DEST, "1-3")

        val message6 = simpleMockFlowMessage(SECOND_SOURCE, FIRST_DEST, "3-1")
        val message7 = simpleMockFlowMessage(SECOND_SOURCE, FIRST_DEST, "3-2")

        val mockPublisherFactory = Mockito.mock(PublisherFactory::class.java)
        val queue = LinkManager.PendingSessionsMessageQueuesImpl(mockPublisherFactory)
        assertTrue(queue.queueMessage(message1))
        assertFalse(queue.queueMessage(message2))

        assertTrue(queue.queueMessage(message3))
        assertFalse(queue.queueMessage(message4))
        assertFalse(queue.queueMessage(message5))

        assertTrue(queue.queueMessage(message6))
        assertFalse(queue.queueMessage(message7))
    }

    @Test
    fun `PendingSessionsMessageQueues sessionNegotiatedCallback sends the correct queued messages to the Publisher`() {
        val payload1 = ByteBuffer.wrap("0-0".toByteArray())
        val payload2 = ByteBuffer.wrap("0-1".toByteArray())

        val payload3 = ByteBuffer.wrap("1-1".toByteArray())
        val payload4 = ByteBuffer.wrap("1-2".toByteArray())
        val payload5 = ByteBuffer.wrap("1-3".toByteArray())

        //Messages 1 and 2 can share the same session
        val message1 = complexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, payload1)
        val message2 = complexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, payload2)

        //Messages 3, 4, 5 can share another session
        val message3 = complexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload3)
        val message4 = complexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload4)
        val message5 = complexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload5)

        val publisher = TestListBasedPublisher()
        val mockPublisherFactory = Mockito.mock(PublisherFactory::class.java)
        Mockito.`when`(mockPublisherFactory.createPublisher(any(), any())).thenReturn(publisher)

        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("10.0.0.1:fake"))

        val queue = LinkManager.PendingSessionsMessageQueuesImpl(mockPublisherFactory)

        assertTrue(queue.queueMessage(message1))
        assertFalse(queue.queueMessage(message2))

        assertTrue(queue.queueMessage(message3))
        assertFalse(queue.queueMessage(message4))
        assertFalse(queue.queueMessage(message5))

        //Session is ready for messages 3, 4, 5
        queue.sessionNegotiatedCallback(getSessionKeyFromMessage(message3), createSession().initiatorSession, mockNetworkMap)
        assertEquals(publisher.list.size, 3)
        assertEquals(payload3, extractPayloadFromLinkOutMessage(publisher.list[0].value as LinkOutMessage))
        assertEquals(payload4, extractPayloadFromLinkOutMessage(publisher.list[1].value as LinkOutMessage))
        assertEquals(payload5, extractPayloadFromLinkOutMessage(publisher.list[2].value as LinkOutMessage))
        publisher.list = mutableListOf()

        //Session is ready for messages 1, 2
        queue.sessionNegotiatedCallback(getSessionKeyFromMessage(message1), createSession().initiatorSession, mockNetworkMap)
        assertEquals(publisher.list.size, 2)
        assertEquals(payload1, extractPayloadFromLinkOutMessage(publisher.list[0].value as LinkOutMessage))
        assertEquals(payload2, extractPayloadFromLinkOutMessage(publisher.list[1].value as LinkOutMessage))
    }

    @Test
    fun `OutboundMessageProcessor queues messages if there is a pending session`() {
        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getInitiatorSession(any())).thenReturn(null)
        val mockQueue = Mockito.mock(LinkManager.PendingSessionsMessageQueues::class.java)
        Mockito.`when`(mockQueue.queueMessage(any())).thenReturn(false)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockQueue, mockNetworkMap)

        val message1 = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0")
        val message2 = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "1")
        val message3 = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "2")

        val messages = listOf(EventLogRecord(TOPIC, KEY, message1, 0, 0),
            EventLogRecord(TOPIC, KEY, message2, 0, 0 ),
            EventLogRecord(TOPIC, KEY, message3, 0, 0))

        val records = processor.onNext(messages)
        assertEquals(records.size, 0)

        for (message in messages) {
            Mockito.verify(mockQueue).queueMessage(message.value)
        }
    }

    @Test
    fun `OutboundMessageProcessor queues messages and requests a new session if there is no pending session`() {
        val dummySessionInitMessage = LinkOutMessage()

        val mockSessionManager = Mockito.mock(SessionManager::class.java)
        Mockito.`when`(mockSessionManager.getInitiatorSession(any())).thenReturn(null)
        Mockito.`when`(mockSessionManager.getSessionInitMessage(any())).thenReturn(dummySessionInitMessage)

        val mockQueue = Mockito.mock(LinkManager.PendingSessionsMessageQueues::class.java)
        //For every message request a new session
        Mockito.`when`(mockQueue.queueMessage(any())).thenReturn(true)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockQueue, mockNetworkMap)

        val messages = listOf(EventLogRecord(TOPIC, KEY, simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0"), 0, 0),
            EventLogRecord(TOPIC, KEY, simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "1"), 0, 0 ),
            EventLogRecord(TOPIC, KEY, simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "2"), 0, 0))

        val records = processor.onNext(messages)

        assertEquals(records.size, messages.size)

        //We get a dummySessionInit message for each message (as each message requested a new session).
        for (record in records) {
            assertSame(dummySessionInitMessage, record.value)
            assertEquals(LinkManager.LINK_OUT_TOPIC, record.topic)
        }

        for (message in messages) {
            Mockito.verify(mockQueue).queueMessage(message.value)
        }
        Mockito.verify(mockSessionManager, times(messages.size)).getSessionInitMessage(getSessionKeyFromMessage(messages[0].value))
    }

    @Test
    fun `OutboundMessageProcessor processes messages straight away if there is an authenticated session`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)
        Mockito.`when`(mockSessionManager.getInitiatorSession(any())).thenReturn(createSession().initiatorSession)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("10.0.0.1:fake"))

        val mockQueue = Mockito.mock(LinkManager.PendingSessionsMessageQueues::class.java)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockQueue, mockNetworkMap)

        val messages = listOf(EventLogRecord(TOPIC, KEY, complexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("0".toByteArray())), 0, 0),
            EventLogRecord(TOPIC, KEY, complexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("1".toByteArray())), 0, 0 ),
            EventLogRecord(TOPIC, KEY, complexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("2".toByteArray())), 0, 0))

        val records = processor.onNext(messages)

        assertEquals(records.size, messages.size)
        for (record in records) {
            val payload = (record.value as LinkOutMessage).payload
            assert(payload is AuthenticatedDataMessage)
            assertEquals(LinkManager.LINK_OUT_TOPIC, record.topic)
        }
    }

    @Test
    fun `InboundMessageProcessor routes session messages to the session manager and sends the response to the gateway`() {
        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        //Respond to initiator hello message with an initiator hello message (as this response is easy to mock).
        val response = LinkOutMessage(LinkOutHeader("", "10.0.0.1:fake"), initiatorHelloLinkInMessage().payload)
        Mockito.`when`(mockSessionManager.processSessionMessage(any())).thenReturn(response)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager)
        val messages = listOf(EventLogRecord(TOPIC, KEY, initiatorHelloLinkInMessage(), 0, 0),
            EventLogRecord(TOPIC, KEY, initiatorHelloLinkInMessage(), 0, 0))
        val records = processor.onNext(messages)

        assertEquals(records.size, messages.size)
        for (record in records) {
            assertEquals(LinkManager.LINK_OUT_TOPIC, record.topic)
            assertSame(response, record.value)
        }
    }

    private fun testDataMessagesWithInboundMessageProcessor(session: SessionPair) {
        val payload = ByteBuffer.wrap("PAYLOAD".toByteArray())
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("10.0.0.1:fake"))

        val header = FlowMessageHeader(FIRST_DEST, FIRST_SOURCE, null, "", "")
        val flowMessage = FlowMessage(header, payload)

        val linkOutMessage = createLinkOutMessageFromFlowMessage(flowMessage, session.initiatorSession, mockNetworkMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0),
            EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getResponderSession(any())).thenReturn(session.responderSession)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager)

        val records = processor.onNext(messages)
        assertEquals(records.size, messages.size)
        for (record in records) {
            assertEquals(LinkManager.P2P_IN_TOPIC, record.topic)
            assertArrayEquals(flowMessage.payload.array(), (record.value as FlowMessage).payload.array())
        }
    }

    @Test
    fun `InboundMessageProcessor authenticates AuthenticatedDataMessages and routes to the statemachine`() {
        val session = createSession()
        testDataMessagesWithInboundMessageProcessor(session)
    }

    @Test
    fun `InboundMessageProcessor authenticates and decrypts AuthenticatedEncryptedDataMessages and routes to the statemachine`() {
        val session = createSession(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        testDataMessagesWithInboundMessageProcessor(session)
    }

    @Test
    fun `InboundMessageProcessor discards messages with unknown sessionId`() {
        val session = createSession()
        val payload = ByteBuffer.wrap("PAYLOAD".toByteArray())
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("10.0.0.1:fake"))

        val header = FlowMessageHeader(FIRST_DEST, FIRST_SOURCE, null, "", "")
        val flowMessage = FlowMessage(header, payload)

        val linkOutMessage = createLinkOutMessageFromFlowMessage(flowMessage, session.initiatorSession, mockNetworkMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getResponderSession(any())).thenReturn(null)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager)
        val mockLogger = Mockito.mock(Logger::class.java)
        processor.setLogger(mockLogger)

        val records = processor.onNext(messages)
        assertEquals(records.size, 0)

        Mockito.verify(mockLogger).warn("Received message with SessionId = $SESSION_ID for which there is no active session. " +
            "The message was discarded.")
    }
}