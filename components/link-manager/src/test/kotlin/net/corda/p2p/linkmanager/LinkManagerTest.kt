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
import net.corda.p2p.NetworkType
import net.corda.p2p.SessionPartitions
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.createLinkOutMessageFromFlowMessage
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.Companion.getSessionKeyFromMessage
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.SessionKey
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.schema.Schema
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import net.corda.p2p.schema.Schema.Companion.P2P_IN_TOPIC
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
        const val FAKE_ADDRESS = "http://10.0.0.1/"
        val provider = BouncyCastleProvider()
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        private val FAKE_ENDPOINT = LinkManagerNetworkMap.EndPoint(FAKE_ADDRESS)
        val FIRST_DEST_MEMBER_INFO = LinkManagerNetworkMap.MemberInfo(
            FIRST_DEST.toHoldingIdentity(),
            keyPairGenerator.generateKeyPair().public,
            FAKE_ENDPOINT
        )

        const val SESSION_ID = "testSession"
        const val MAX_MESSAGE_SIZE = 1000000
        const val GROUP_ID = "myGroup"
        const val KEY = "Key"
        const val TOPIC = "Topic"

        lateinit var loggingInterceptor: LoggingInterceptor

        @BeforeAll
        @JvmStatic
        fun setup() {
            loggingInterceptor = LoggingInterceptor.setupLogging()
        }

        data class SessionPair(val initiatorSession: Session, val responderSession: Session)

        fun createSessionPair(mode: ProtocolMode = ProtocolMode.AUTHENTICATION_ONLY): SessionPair {
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
    }

    @BeforeEach
    fun resetLogging() {
        loggingInterceptor.reset()
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

    fun simpleMockFlowMessage(source: HoldingIdentity, dest: HoldingIdentity, data: String): FlowMessage {
        val mockHeader = Mockito.mock(FlowMessageHeader::class.java)
        Mockito.`when`(mockHeader.source).thenReturn(source)
        Mockito.`when`(mockHeader.destination).thenReturn(dest)
        return FlowMessage(mockHeader, ByteBuffer.wrap(data.toByteArray()))
    }

    fun complexMockFlowMessage(source: HoldingIdentity, dest: HoldingIdentity, data: ByteBuffer): FlowMessage {
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

    private fun extractPayloadFromLinkOutMessage(message: LinkOutMessage): ByteBuffer {
        return (message.payload as AuthenticatedDataMessage).payload
    }

    private fun assignedListener(partitions: List<Int>): InboundAssignmentListener {
        val listener = InboundAssignmentListener()
        for (partition in partitions) {
            listener.onPartitionsAssigned(listOf(Schema.LINK_IN_TOPIC to partition))
        }
        return listener
    }

    @Test
    fun `PendingSessionsMessageQueues queueMessage returns true if a new session is needed`() {
        val message = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0-0")

        val key1 = SessionKey(FIRST_SOURCE.toHoldingIdentity(), FIRST_DEST.toHoldingIdentity())
        val key2 = SessionKey(SECOND_SOURCE.toHoldingIdentity(), SECOND_DEST.toHoldingIdentity())

        val mockPublisherFactory = Mockito.mock(PublisherFactory::class.java)
        val queue = LinkManager.PendingSessionMessageQueuesImpl(mockPublisherFactory)
        assertTrue(queue.queueMessage(message, key1))
        assertFalse(queue.queueMessage(message, key1))

        assertTrue(queue.queueMessage(message, key2))
        assertFalse(queue.queueMessage(message, key2))
        assertFalse(queue.queueMessage(message, key2))
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
        val key1 = getSessionKeyFromMessage(message1)

        //Messages 3, 4, 5 can share another session
        val message3 = complexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload3)
        val message4 = complexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload4)
        val message5 = complexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload5)
        val key2 = getSessionKeyFromMessage(message3)

        val publisher = TestListBasedPublisher()
        val mockPublisherFactory = Mockito.mock(PublisherFactory::class.java)
        Mockito.`when`(mockPublisherFactory.createPublisher(any(), any())).thenReturn(publisher)

        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(any())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)

        val queue = LinkManager.PendingSessionMessageQueuesImpl(mockPublisherFactory)

        assertTrue(queue.queueMessage(message1, key1))
        assertFalse(queue.queueMessage(message2, key1))

        assertTrue(queue.queueMessage(message3, key2))
        assertFalse(queue.queueMessage(message4, key2))
        assertFalse(queue.queueMessage(message5, key2))

        //Session is ready for messages 3, 4, 5
        queue.sessionNegotiatedCallback(key2, createSessionPair().initiatorSession, mockNetworkMap)
        assertEquals(publisher.list.size, 3)
        assertEquals(payload3, extractPayloadFromLinkOutMessage(publisher.list[0].value as LinkOutMessage))
        assertEquals(payload4, extractPayloadFromLinkOutMessage(publisher.list[1].value as LinkOutMessage))
        assertEquals(payload5, extractPayloadFromLinkOutMessage(publisher.list[2].value as LinkOutMessage))
        publisher.list = mutableListOf()

        //Session is ready for messages 1, 2
        queue.sessionNegotiatedCallback(key1, createSessionPair().initiatorSession, mockNetworkMap)
        assertEquals(publisher.list.size, 2)
        assertEquals(payload1, extractPayloadFromLinkOutMessage(publisher.list[0].value as LinkOutMessage))
        assertEquals(payload2, extractPayloadFromLinkOutMessage(publisher.list[1].value as LinkOutMessage))
    }

    @Test
    fun `OutboundMessageProcessor does not process messages if there is a pending session`() {
        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)

        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(listOf(1)))

        val message1 = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0")
        val message2 = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "1")
        val message3 = simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "2")

        val messages = listOf(EventLogRecord(TOPIC, KEY, message1, 0, 0),
            EventLogRecord(TOPIC, KEY, message2, 0, 0 ),
            EventLogRecord(TOPIC, KEY, message3, 0, 0))

        val records = processor.onNext(messages)
        assertEquals(records.size, 0)
    }

    @Test
    fun `OutboundMessageProcess routes the session init messages and persists a list of partitions for a specific sessionId`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)

        val sessionId = "SessionId"
        val sessionInitMessage = LinkOutMessage()
        val state = SessionManager.SessionState.NewSessionNeeded(sessionId, sessionInitMessage)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(state)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val inboundSubscribedTopics = listOf(1, 5, 9)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(inboundSubscribedTopics))
        val messages = listOf(EventLogRecord(TOPIC, KEY, simpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0"), 0, 0))
        val records = processor.onNext(messages)

        assertEquals(records.size, 2 * messages.size)
        assertEquals(records[0].topic, LINK_OUT_TOPIC)
        assertSame(records[0].value, sessionInitMessage)
        assertEquals(records[1].topic, Schema.SESSION_OUT_PARTITIONS)
        assertEquals(records[1].key, sessionId)
        assertTrue(records[1].value is SessionPartitions)
        assertArrayEquals(inboundSubscribedTopics.toIntArray(), (records[1].value as SessionPartitions).partitions.toIntArray())
    }

    @Test
    fun `OutboundMessageProcessor processes messages straight away if there is an authenticated session`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)

        val state = SessionManager.SessionState.SessionEstablished(createSessionPair().initiatorSession)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(state)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(listOf(1)))

        val messages = listOf(EventLogRecord(TOPIC, KEY, complexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("0".toByteArray())), 0, 0),
            EventLogRecord(TOPIC, KEY, complexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("1".toByteArray())), 0, 0 ),
            EventLogRecord(TOPIC, KEY, complexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("2".toByteArray())), 0, 0))

        val records = processor.onNext(messages)

        assertEquals(records.size, messages.size)
        for (record in records) {
            val payload = (record.value as LinkOutMessage).payload
            assert(payload is AuthenticatedDataMessage)
            assertEquals(LINK_OUT_TOPIC, record.topic)
        }
    }

    @Test
    fun `InboundMessageProcessor routes session messages to the session manager and sends the response to the gateway`() {
        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        //Respond to initiator hello message with an initiator hello message (as this response is easy to mock).
        val response = LinkOutMessage(LinkOutHeader("", NetworkType.CORDA_5, FAKE_ADDRESS), initiatorHelloLinkInMessage().payload)
        Mockito.`when`(mockSessionManager.processSessionMessage(any())).thenReturn(response)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager)
        val messages = listOf(EventLogRecord(TOPIC, KEY, initiatorHelloLinkInMessage(), 0, 0),
            EventLogRecord(TOPIC, KEY, initiatorHelloLinkInMessage(), 0, 0))
        val records = processor.onNext(messages)

        assertEquals(records.size, messages.size)
        for (record in records) {
            assertEquals(LINK_OUT_TOPIC, record.topic)
            assertSame(response, record.value)
        }
    }

    private fun testDataMessagesWithInboundMessageProcessor(session: SessionPair) {
        val payload = ByteBuffer.wrap("PAYLOAD".toByteArray())
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)

        val header = FlowMessageHeader(FIRST_DEST, FIRST_SOURCE, null, "", "")
        val flowMessage = FlowMessage(header, payload)

        val linkOutMessage = createLinkOutMessageFromFlowMessage(flowMessage, session.initiatorSession, mockNetworkMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0),
            EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getInboundSession(any())).thenReturn(session.responderSession)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager)

        val records = processor.onNext(messages)
        assertEquals(records.size, messages.size)
        for (record in records) {
            assertEquals(P2P_IN_TOPIC, record.topic)
            assertArrayEquals(flowMessage.payload.array(), (record.value as FlowMessage).payload.array())
        }
    }

    @Test
    fun `InboundMessageProcessor authenticates AuthenticatedDataMessages and routes to the statemachine`() {
        val session = createSessionPair()
        testDataMessagesWithInboundMessageProcessor(session)
    }

    @Test
    fun `InboundMessageProcessor authenticates and decrypts AuthenticatedEncryptedDataMessages and routes to the statemachine`() {
        val session = createSessionPair(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        testDataMessagesWithInboundMessageProcessor(session)
    }

    @Test
    fun `InboundMessageProcessor discards messages with unknown sessionId`() {
        val session = createSessionPair()
        val payload = ByteBuffer.wrap("PAYLOAD".toByteArray())
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)

        val header = FlowMessageHeader(FIRST_DEST, FIRST_SOURCE, null, "", "")
        val flowMessage = FlowMessage(header, payload)

        val linkOutMessage = createLinkOutMessageFromFlowMessage(flowMessage, session.initiatorSession, mockNetworkMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getInboundSession(any())).thenReturn(null)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager)

        val records = processor.onNext(messages)
        assertEquals(records.size, 0)

        loggingInterceptor.assertSingleWarning("Received message with SessionId = $SESSION_ID for which there is no active session. " +
            "The message was discarded.")
    }
}