package net.corda.p2p.linkmanager

import org.mockito.kotlin.any
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.NetworkType
import net.corda.p2p.SessionPartitions
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromAck
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromFlowMessageAndKey
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.Companion.getSessionKeyFromMessage
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.SessionKey
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.markers.FlowMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.payload.FlowMessage
import net.corda.p2p.payload.FlowMessageAndKey
import net.corda.p2p.payload.FlowMessageHeader
import net.corda.p2p.payload.HoldingIdentity
import net.corda.p2p.payload.MessageAck
import net.corda.p2p.schema.Schema
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import net.corda.p2p.schema.Schema.Companion.P2P_IN_TOPIC
import net.corda.p2p.schema.Schema.Companion.P2P_OUT_MARKERS
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
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
        val KEY_BYTES: ByteBuffer = ByteBuffer.wrap(KEY.toByteArray())
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

        fun flowMessageAndKey(
            source: HoldingIdentity,
            dest: HoldingIdentity,
            data: ByteBuffer,
            messageId: String = ""
        ): FlowMessageAndKey {
            val header = FlowMessageHeader(dest, source, null, messageId, "")
            return FlowMessageAndKey(FlowMessage(header, data), KEY_BYTES)
        }
    }

    @AfterEach
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

    private fun flowMessage(source: HoldingIdentity, dest: HoldingIdentity, data: String, messageId: String): FlowMessage {
        val header = FlowMessageHeader(dest, source, null, messageId, "")
        return FlowMessage(header, ByteBuffer.wrap(data.toByteArray()))
    }

    private fun initiatorHelloLinkInMessage() : LinkInMessage {
        val session =  AuthenticationProtocolInitiator(SESSION_ID, setOf(ProtocolMode.AUTHENTICATION_ONLY), MAX_MESSAGE_SIZE)
        return LinkInMessage(session.generateInitiatorHello())
    }

    fun extractPayload(session: Session, message: LinkOutMessage): ByteBuffer {
        val payload = MessageConverter.extractPayload(session, "", message.payload)
        return (payload?.content as FlowMessageAndKey).flowMessage.payload
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
        val message = FlowMessageAndKey(flowMessage(FIRST_SOURCE, FIRST_DEST, "0-0", "MessageId"), KEY_BYTES)

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

        val message1 = flowMessageAndKey(FIRST_SOURCE, FIRST_DEST, payload1)
        val message2 = flowMessageAndKey(FIRST_SOURCE, FIRST_DEST, payload2)
        val key1 = getSessionKeyFromMessage(message1.flowMessage)

        //Messages 3, 4, 5 can share another session
        val message3 = flowMessageAndKey(SECOND_SOURCE, SECOND_DEST, payload3)
        val message4 = flowMessageAndKey(SECOND_SOURCE, SECOND_DEST, payload4)
        val message5 = flowMessageAndKey(SECOND_SOURCE, SECOND_DEST, payload5)
        val key2 = getSessionKeyFromMessage(message3.flowMessage)

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
        val sessionPair = createSessionPair()
        queue.sessionNegotiatedCallback(key2, sessionPair.initiatorSession, mockNetworkMap)
        assertEquals(publisher.list.size, 3)
        assertEquals(payload3, extractPayload(sessionPair.responderSession, publisher.list[0].value as LinkOutMessage))
        assertEquals(payload4, extractPayload(sessionPair.responderSession, publisher.list[1].value as LinkOutMessage))
        assertEquals(payload5, extractPayload(sessionPair.responderSession, publisher.list[2].value as LinkOutMessage))
        publisher.list = mutableListOf()

        //Session is ready for messages 1, 2
        queue.sessionNegotiatedCallback(key1, sessionPair.initiatorSession, mockNetworkMap)
        assertEquals(publisher.list.size, 2)
        assertEquals(payload1, extractPayload(sessionPair.responderSession, publisher.list[0].value as LinkOutMessage))
        assertEquals(payload2, extractPayload(sessionPair.responderSession, publisher.list[1].value as LinkOutMessage))
    }

    @Test
    fun `OutboundMessageProcessor produces only a LinkManagerSent maker (per flowMessage) if SessionAlreadyPending`() {
        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)

        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(listOf(1)))

        val numberOfMessages = 3
        val messages = mutableListOf<EventLogRecord<ByteBuffer, FlowMessage>>()
        for (i in 0 until numberOfMessages) {
            messages.add(EventLogRecord(TOPIC, KEY_BYTES, flowMessage(FIRST_SOURCE, FIRST_DEST, "$i", "MessageId$i"), 0, 0 ))
        }

        val records = processor.onNext(messages)

        assertEquals(numberOfMessages, records.size)
        val keys = records.map { it.key }
        for (i in 0 until numberOfMessages) {
            assertThat(keys).contains("MessageId$i")
        }
        for (record in records) {
            assertEquals(P2P_OUT_MARKERS, record.topic)
            assert(record.value is FlowMessageMarker)
            val marker = (record.value as FlowMessageMarker)
            assertTrue(marker.marker is LinkManagerSentMarker)
        }
    }

    @Test
    fun `OutboundMessageProcess produces a session init message, a LinkManagerSent maker and a list of partitions if NewSessionNeeded`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)

        val sessionId = "SessionId"
        val messageId = "MessageId"
        val sessionInitMessage = LinkOutMessage()
        val state = SessionManager.SessionState.NewSessionNeeded(sessionId, sessionInitMessage)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(state)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)

        val inboundSubscribedTopics = listOf(1, 5, 9)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(inboundSubscribedTopics))
        val messages = listOf(EventLogRecord(TOPIC, KEY_BYTES, flowMessage(FIRST_SOURCE, FIRST_DEST, "0", messageId), 0, 0))
        val records = processor.onNext(messages)

        assertThat(records).hasSize(3 * messages.size)

        assertThat(records).filteredOn { it.topic == LINK_OUT_TOPIC }.hasSize(messages.size)
            .extracting<LinkOutMessage> { it.value as LinkOutMessage }
            .allSatisfy { assertThat(it).isSameAs(sessionInitMessage) }

        assertThat(records).filteredOn { it.topic == Schema.SESSION_OUT_PARTITIONS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isSameAs(sessionId) }
            .extracting<SessionPartitions> { it.value as SessionPartitions }
            .allSatisfy { assertThat(it.partitions.toIntArray()).isEqualTo(inboundSubscribedTopics.toIntArray()) }

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isEqualTo(messageId) }
            .extracting<FlowMessageMarker> { it.value as FlowMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerSentMarker::class.java) }
    }

    @Test
    fun `OutboundMessageProcessor produces a LinkOutMessage and a LinkManagerSentMarker per message if SessionEstablished`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)

        val state = SessionManager.SessionState.SessionEstablished(createSessionPair().initiatorSession)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(state)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(listOf(1)))
        val messageIds = listOf("Id1", "Id2", "Id3")

        val messages = listOf(
            EventLogRecord(TOPIC, KEY_BYTES,
                flowMessageAndKey(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("0".toByteArray()), messageIds[0]).flowMessage,
            0, 0
            ),
            EventLogRecord(TOPIC, KEY_BYTES,
                flowMessageAndKey(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("1".toByteArray()), messageIds[1]).flowMessage,
                0, 0
            ),
            EventLogRecord(TOPIC, KEY_BYTES,
                flowMessageAndKey(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("2".toByteArray()), messageIds[2]).flowMessage
                , 0, 0
            )
        )

        val records = processor.onNext(messages)

        assertThat(records).hasSize(2 * messages.size)

        assertThat(records).filteredOn { it.topic == LINK_OUT_TOPIC }.hasSize(messages.size)
            .extracting<LinkOutMessage> { it.value as LinkOutMessage }
            .allSatisfy{ assertThat(it.payload).isInstanceOf(AuthenticatedDataMessage::class.java) }

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(messages.size)
            .extracting<FlowMessageMarker> { it.value as FlowMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerSentMarker::class.java) }

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.extracting<String> { it.key as String }
            .containsExactly(*messageIds.toTypedArray())
    }

    @Test
    fun `OutboundMessageProcessor produces only a LinkManagerSentMarker if CannotEstablishSession`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(SessionManager.SessionState.CannotEstablishSession)

        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        val messageId = "messageId"
        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(listOf(1)))
        val messages = listOf(EventLogRecord(TOPIC, KEY_BYTES, flowMessage(FIRST_SOURCE, FIRST_DEST, "0", messageId), 0, 0))
        val records = processor.onNext(messages)

        assertThat(records).hasSize(messages.size)

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isEqualTo(messageId) }
            .extracting<FlowMessageMarker> { it.value as FlowMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerSentMarker::class.java) }
    }

    @Test
    fun `OutboundMessageProcessor produces only a LinkManagerSentMarker if SessionEstablished and receiver is not in the network map`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)

        val state = SessionManager.SessionState.SessionEstablished(createSessionPair().initiatorSession)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(state)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_SOURCE.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(null)
        val messageId = "messageId"

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(listOf(1)))
        val messages = listOf(EventLogRecord(TOPIC, KEY_BYTES, flowMessage(FIRST_SOURCE, FIRST_DEST, "0", messageId), 0, 0))
        val records = processor.onNext(messages)

        assertThat(records).hasSize(messages.size)

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isEqualTo(messageId) }
            .extracting<FlowMessageMarker> { it.value as FlowMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerSentMarker::class.java) }    }

    @Test
    fun `InboundMessageProcessor routes session messages to the session manager and sends the response to the gateway`() {
        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        //Respond to initiator hello message with an initiator hello message (as this response is easy to mock).
        val response = LinkOutMessage(LinkOutHeader("", NetworkType.CORDA_5, FAKE_ADDRESS), initiatorHelloLinkInMessage().payload)
        Mockito.`when`(mockSessionManager.processSessionMessage(any())).thenReturn(response)

        val mockMessage = Mockito.mock(InitiatorHandshakeMessage::class.java)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, Mockito.mock(LinkManagerNetworkMap::class.java))
        val messages = listOf(EventLogRecord(TOPIC, KEY, LinkInMessage(mockMessage), 0, 0),
            EventLogRecord(TOPIC, KEY, LinkInMessage(mockMessage), 0, 0))
        val records = processor.onNext(messages)

        assertEquals(messages.size, records.size)
        for (record in records) {
            assertEquals(LINK_OUT_TOPIC, record.topic)
            assertSame(response, record.value)
        }
    }

    private fun testDataMessagesWithInboundMessageProcessor(session: SessionPair) {
        val payload = ByteBuffer.wrap("PAYLOAD".toByteArray())
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_SOURCE.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        val messageId = "messageId"

        val header = FlowMessageHeader(FIRST_DEST, FIRST_SOURCE, null, messageId, "")
        val flowMessageAndKey = FlowMessageAndKey(FlowMessage(header, payload), KEY_BYTES)

        val linkOutMessage = linkOutMessageFromFlowMessageAndKey(flowMessageAndKey, session.initiatorSession, mockNetworkMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0),
            EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getInboundSession(any())).thenReturn(session.responderSession)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, mockNetworkMap)

        val records = processor.onNext(messages)
        assertThat(records).filteredOn { it.value is FlowMessage }.hasSize(messages.size)
        assertThat(records).filteredOn { it.value is LinkOutMessage }.hasSize(messages.size)
        for (record in records) {
            when (val value = record.value) {
                is FlowMessage -> {
                    assertEquals(P2P_IN_TOPIC, record.topic)
                    assertArrayEquals(flowMessageAndKey.flowMessage.payload.array(), value.payload.array())
                    assertEquals(flowMessageAndKey.key, record.key)
                }
                is LinkOutMessage -> {
                    assertEquals(LINK_OUT_TOPIC, record.topic)
                    val linkManagerPayload = MessageConverter.extractPayload(session.initiatorSession, "sessionId", value.payload)
                    assertTrue(linkManagerPayload!!.content is MessageAck)
                    assertEquals(messageId, (linkManagerPayload.content as MessageAck).messageId)
                }
                else -> {
                    Assertions.fail("Inbound message processor should only produce records with " +
                        "${FlowMessage::class.java} and ${LinkOutMessage::class.java}")
                }
            }
        }
    }

    @Test
    fun `InboundMessageProcessor authenticates AuthenticatedDataMessages producing a FlowMessage and an ACK`() {
        val session = createSessionPair()
        testDataMessagesWithInboundMessageProcessor(session)
    }

    @Test
    fun `InboundMessageProcessor authenticates and decrypts AuthenticatedEncryptedDataMessages producing a FlowMessage and an ACK`() {
        val session = createSessionPair(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        testDataMessagesWithInboundMessageProcessor(session)
    }

    @Test
    fun `InboundMessageProcessor processes an ACK message producing a LinkManagerReceivedMarker`() {
        val acknowledgedMessageId = "MessageId"
        val session = createSessionPair(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_SOURCE.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)
        val linkOutMessage = linkOutMessageFromAck(MessageAck(acknowledgedMessageId), FIRST_SOURCE, FIRST_DEST, session.initiatorSession, mockNetworkMap)
        val message = LinkInMessage(linkOutMessage?.payload)

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getInboundSession(any())).thenReturn(session.responderSession)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, mockNetworkMap)
        val records = processor.onNext(listOf(EventLogRecord(TOPIC, KEY, message, 0, 0)) )

        assertThat(records).hasSize(1)

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(1)
            .allSatisfy { assertThat(it.key).isEqualTo(acknowledgedMessageId) }
            .extracting<FlowMessageMarker> { it.value as FlowMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerReceivedMarker::class.java) }
    }

    @Test
    fun `InboundMessageProcessor produces a FlowMessage only if the sender is removed from the network map before creating an ACK`() {
        val payload = ByteBuffer.wrap("PAYLOAD".toByteArray())
        val session = createSessionPair()
        val messageId = "messageId"

        val header = FlowMessageHeader(FIRST_DEST, FIRST_SOURCE, null, messageId, "")
        val flowMessageWrapper = FlowMessageAndKey(FlowMessage(header, payload), KEY_BYTES)

        val networkMapToMakeMessage = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(networkMapToMakeMessage.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(networkMapToMakeMessage.getMemberInfo(FIRST_SOURCE.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(networkMapToMakeMessage.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)

        val linkOutMessage = linkOutMessageFromFlowMessageAndKey(flowMessageWrapper, session.initiatorSession, networkMapToMakeMessage)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0),
            EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getInboundSession(any())).thenReturn(session.responderSession)

        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_SOURCE.toHoldingIdentity())).thenReturn(null)
        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, mockNetworkMap)

        val records = processor.onNext(messages)
        assertEquals(messages.size, records.size)
        for (record in records) {
            assertEquals(P2P_IN_TOPIC, record.topic)
            assertTrue(record.value is FlowMessage)
            assertArrayEquals(flowMessageWrapper.flowMessage.payload.array(), (record.value as FlowMessage).payload.array())
            assertEquals(flowMessageWrapper.key, record.key)
        }
    }

    @Test
    fun `InboundMessageProcessor discards messages with unknown sessionId`() {
        val session = createSessionPair()
        val payload = ByteBuffer.wrap("PAYLOAD".toByteArray())
        val mockNetworkMap = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(mockNetworkMap.getMemberInfo(FIRST_DEST.toHoldingIdentity())).thenReturn(FIRST_DEST_MEMBER_INFO)
        Mockito.`when`(mockNetworkMap.getNetworkType(any())).thenReturn(LinkManagerNetworkMap.NetworkType.CORDA_5)

        val header = FlowMessageHeader(FIRST_DEST, FIRST_SOURCE, null, "", "")
        val flowMessageWrapper = FlowMessageAndKey(FlowMessage(header, payload), KEY_BYTES)

        val linkOutMessage = linkOutMessageFromFlowMessageAndKey(flowMessageWrapper, session.initiatorSession, mockNetworkMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getInboundSession(any())).thenReturn(null)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, mockNetworkMap)

        val records = processor.onNext(messages)
        assertEquals(records.size, 0)

        loggingInterceptor.assertSingleWarning("Received message with SessionId = $SESSION_ID for which there is no active session. " +
            "The message was discarded.")
    }
}
