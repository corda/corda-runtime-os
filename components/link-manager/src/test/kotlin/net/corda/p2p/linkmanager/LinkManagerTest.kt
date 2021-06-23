package net.corda.p2p.linkmanager

import com.nhaarman.mockito_kotlin.*
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.FlowMessage
import net.corda.p2p.FlowMessageHeader
import net.corda.p2p.HoldingIdentity
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManager.Companion.getSessionKeyFromMessage
import net.corda.p2p.linkmanager.sessions.SessionManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun createSimpleMockFlowMessage(source: HoldingIdentity, dest: HoldingIdentity, data: String): FlowMessage {
        val mockHeader = Mockito.mock(FlowMessageHeader::class.java)
        Mockito.`when`(mockHeader.source).thenReturn(source)
        Mockito.`when`(mockHeader.destination).thenReturn(dest)
        return FlowMessage(mockHeader, ByteBuffer.wrap(data.toByteArray()))
    }

    private fun createComplexMockFlowMessage(source: HoldingIdentity, dest: HoldingIdentity, data: ByteBuffer): FlowMessage {
        val mockHeader = Mockito.mock(FlowMessageHeader::class.java)
        Mockito.`when`(mockHeader.source).thenReturn(source)
        Mockito.`when`(mockHeader.destination).thenReturn(dest)

        val mockFlowMessage = Mockito.mock(FlowMessage::class.java)
        Mockito.`when`(mockFlowMessage.toByteBuffer()).thenReturn(data)
        Mockito.`when`(mockFlowMessage.header).thenReturn(mockHeader)

        return mockFlowMessage
    }


    //We can't use Mockito as Authenticated Session is final
    private fun genAuthenticatedSession(): Session {
        val sessionId = "testSession"
        val maxMessageSize = 1000000
        val groupId = "myGroup"

        val provider = BouncyCastleProvider()
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        val partyAIdentityKey = keyPairGenerator.generateKeyPair()
        val partyBIdentityKey = keyPairGenerator.generateKeyPair()
        val signature = Signature.getInstance("ECDSA", provider)

        val authenticationProtocolA = AuthenticationProtocolInitiator(sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY), maxMessageSize)
        val authenticationProtocolB = AuthenticationProtocolResponder(sessionId, setOf(ProtocolMode.AUTHENTICATION_ONLY), maxMessageSize)

        val initiatorHelloMsg = authenticationProtocolA.generateInitiatorHello()
        authenticationProtocolB.receiveInitiatorHello(initiatorHelloMsg)

        val responderHelloMsg = authenticationProtocolB.generateResponderHello()
        authenticationProtocolA.receiveResponderHello(responderHelloMsg)

        authenticationProtocolA.generateHandshakeSecrets()
        authenticationProtocolB.generateHandshakeSecrets()

        val signingCallbackForA = { data: ByteArray ->
            signature.initSign(partyAIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val initiatorHandshakeMessage = authenticationProtocolA.generateOurHandshakeMessage(partyAIdentityKey.public, partyBIdentityKey.public, groupId, signingCallbackForA)

        authenticationProtocolB.validatePeerHandshakeMessage(initiatorHandshakeMessage) { partyAIdentityKey.public }

        // Step 4: responder sending handshake message and initiator validating it.
        val signingCallbackForB = { data: ByteArray ->
            signature.initSign(partyBIdentityKey.private)
            signature.update(data)
            signature.sign()
        }
        val responderHandshakeMessage = authenticationProtocolB.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

        authenticationProtocolA.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public)
        return authenticationProtocolA.getSession()
    }

    private fun extractPayloadFromLinkOutMessage(message: LinkOutMessage): ByteBuffer {
        return (message.payload as AuthenticatedDataMessage).payload
    }

    @Test
    fun `PendingSessionsMessageQueues queueMessage returns true if a new session is needed`() {
        val message1 = createSimpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0-0")
        val message2 = createSimpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0-1")

        val message3 = createSimpleMockFlowMessage(SECOND_SOURCE, SECOND_DEST, "1-1")
        val message4 = createSimpleMockFlowMessage(SECOND_SOURCE, SECOND_DEST, "1-2")
        val message5 = createSimpleMockFlowMessage(SECOND_SOURCE, SECOND_DEST, "1-3")

        val message6 = createSimpleMockFlowMessage(SECOND_SOURCE, FIRST_DEST, "3-1")
        val message7 = createSimpleMockFlowMessage(SECOND_SOURCE, FIRST_DEST, "3-2")

        val mockPublisherFactory = Mockito.mock(PublisherFactory::class.java)
        val queue = LinkManager.PendingSessionsMessageQueues(mockPublisherFactory)
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
        val message1 = createComplexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, payload1)
        val message2 = createComplexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, payload2)

        //Messages 3, 4, 5 can share another session
        val message3 = createComplexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload3)
        val message4 = createComplexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload4)
        val message5 = createComplexMockFlowMessage(SECOND_SOURCE, SECOND_DEST, payload5)

        val publisher = TestListBasedPublisher()
        val mockPublisherFactory = Mockito.mock(PublisherFactory::class.java)
        Mockito.`when`(mockPublisherFactory.createPublisher(any(), any())).thenReturn(publisher)

        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("10.0.0.1:fake"))

        val queue = LinkManager.PendingSessionsMessageQueues(mockPublisherFactory)

        assertTrue(queue.queueMessage(message1))
        assertFalse(queue.queueMessage(message2))

        assertTrue(queue.queueMessage(message3))
        assertFalse(queue.queueMessage(message4))
        assertFalse(queue.queueMessage(message5))

        //Session is ready for messages 3, 4, 5
        queue.sessionNegotiatedCallback(getSessionKeyFromMessage(message3), genAuthenticatedSession(), mockNetworkMap)
        assertEquals(publisher.list.size, 3)
        assertEquals(payload3, extractPayloadFromLinkOutMessage(publisher.list[0].value as LinkOutMessage))
        assertEquals(payload4, extractPayloadFromLinkOutMessage(publisher.list[1].value as LinkOutMessage))
        assertEquals(payload5, extractPayloadFromLinkOutMessage(publisher.list[2].value as LinkOutMessage))
        publisher.list = mutableListOf()

        //Session is ready for messages 1, 2
        queue.sessionNegotiatedCallback(getSessionKeyFromMessage(message1), genAuthenticatedSession(), mockNetworkMap)
        assertEquals(publisher.list.size, 2)
        assertEquals(payload1, extractPayloadFromLinkOutMessage(publisher.list[0].value as LinkOutMessage))
        assertEquals(payload2, extractPayloadFromLinkOutMessage(publisher.list[1].value as LinkOutMessage))
    }

    @Test
    fun `OutboundMessageProcessor only queues messages if there is a pending session`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)
        Mockito.`when`(mockSessionManager.getInitiatorSession(any())).thenReturn(null)
        val mockQueue = Mockito.mock(LinkManager.PendingSessionsMessageQueues::class.java)
        Mockito.`when`(mockQueue.queueMessage(any())).thenReturn(false)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockQueue, mockNetworkMap)
        val key = "Key"
        val topic = "Topic"

        val message1 = createSimpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0")
        val message2 = createSimpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "1")
        val message3 = createSimpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "2")

        val messages = listOf(EventLogRecord(topic, key, message1, 0, 0),
            EventLogRecord(topic, key, message2, 0, 0 ),
            EventLogRecord(topic, key, message3, 0, 0))

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
        val key = "Key"
        val topic = "Topic"

        val messages = listOf(EventLogRecord(topic, key, createSimpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "0"), 0, 0),
            EventLogRecord(topic, key, createSimpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "1"), 0, 0 ),
            EventLogRecord(topic, key, createSimpleMockFlowMessage(FIRST_SOURCE, FIRST_DEST, "2"), 0, 0))

        val records = processor.onNext(messages)

        assertEquals(records.size, messages.size)

        //We get a dummySessionInit message for each message (as each message requested a new session).
        for (record in records) {
            assertSame(dummySessionInitMessage, record.value)
        }

        for (message in messages) {
            Mockito.verify(mockQueue).queueMessage(message.value)
        }
        Mockito.verify(mockSessionManager, times(messages.size)).getSessionInitMessage(getSessionKeyFromMessage(messages[0].value))
    }

    @Test
    fun `OutboundMessageProcessor processes messages straight away if there is an authenticated session`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)
        Mockito.`when`(mockSessionManager.getInitiatorSession(any())).thenReturn(genAuthenticatedSession())
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getEndPoint(any())).thenReturn(LinkManagerNetworkMap.EndPoint("10.0.0.1:fake"))

        val mockQueue = Mockito.mock(LinkManager.PendingSessionsMessageQueues::class.java)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockQueue, mockNetworkMap)
        val key = "Key"
        val topic = "Topic"

        val messages = listOf(EventLogRecord(topic, key, createComplexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("0".toByteArray())), 0, 0),
            EventLogRecord(topic, key, createComplexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("1".toByteArray())), 0, 0 ),
            EventLogRecord(topic, key, createComplexMockFlowMessage(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("2".toByteArray())), 0, 0))

        val records = processor.onNext(messages)

        assertEquals(records.size, messages.size)
        for (record in records) {
            val payload = (record.value as LinkOutMessage).payload
             assert(payload is AuthenticatedDataMessage)
        }
    }
}