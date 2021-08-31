package net.corda.p2p.linkmanager

import org.mockito.kotlin.any
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkInMessage
import net.corda.p2p.LinkOutHeader
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.MessageAck
import net.corda.p2p.NetworkType
import net.corda.p2p.SessionPartitions
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.HoldingIdentity
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.p2p.crypto.InitiatorHandshakeMessage
import net.corda.p2p.crypto.ProtocolMode
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolInitiator
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocolResponder
import net.corda.p2p.crypto.protocol.api.KeyAlgorithm
import net.corda.p2p.crypto.protocol.api.Session
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
import net.corda.p2p.linkmanager.messaging.AvroSealedClasses.DataMessage
import net.corda.p2p.linkmanager.messaging.MessageConverter
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromAck
import net.corda.p2p.linkmanager.messaging.MessageConverter.Companion.linkOutMessageFromFlowMessageAndKey
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.Companion.getSessionKeyFromMessage
import net.corda.p2p.linkmanager.sessions.SessionManagerImpl.SessionKey
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.MockNetworkMap
import net.corda.p2p.markers.FlowMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.schema.Schema
import net.corda.p2p.schema.Schema.Companion.LINK_OUT_TOPIC
import net.corda.p2p.schema.Schema.Companion.P2P_IN_TOPIC
import net.corda.p2p.schema.Schema.Companion.P2P_OUT_MARKERS
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.concurrent.CompletableFuture

class LinkManagerTest {

    companion object {
        private val FIRST_SOURCE = HoldingIdentity("PartyA", "Group")
        private val SECOND_SOURCE = HoldingIdentity("PartyA", "AnotherGroup")
        private val FIRST_DEST = HoldingIdentity("PartyB", "Group")
        private val SECOND_DEST = HoldingIdentity("PartyC", "Group")
        private const val FAKE_ADDRESS = "http://10.0.0.1/"
        private val provider = BouncyCastleProvider()
        private val keyPairGenerator = KeyPairGenerator.getInstance("EC", provider)
        private val FAKE_ENDPOINT = LinkManagerNetworkMap.EndPoint(FAKE_ADDRESS)
        val FIRST_DEST_MEMBER_INFO = LinkManagerNetworkMap.MemberInfo(
            FIRST_DEST.toHoldingIdentity(),
            keyPairGenerator.generateKeyPair().public,
            KeyAlgorithm.ECDSA,
            FAKE_ENDPOINT
        )

        private val netMap = MockNetworkMap(listOf(FIRST_SOURCE.toHoldingIdentity(), SECOND_SOURCE.toHoldingIdentity(),
            FIRST_DEST.toHoldingIdentity(), SECOND_DEST.toHoldingIdentity())).getSessionNetworkMapForNode(FIRST_SOURCE.toHoldingIdentity())

        private const val MAX_MESSAGE_SIZE = 1000000
        private const val GROUP_ID = "myGroup"
        private const val KEY = "Key"
        private const val TOPIC = "Topic"
        private const val MESSAGE_ID = "MessageId"
        private val PAYLOAD = ByteBuffer.wrap("PAYLOAD".toByteArray())
        private const val SESSION_ID = "SessionId"

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

            val initiator = AuthenticationProtocolInitiator(SESSION_ID, setOf(mode), MAX_MESSAGE_SIZE, partyAIdentityKey.public, GROUP_ID)
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
            val initiatorHandshakeMessage = initiator.generateOurHandshakeMessage(partyBIdentityKey.public,
                signingCallbackForA
            )

            responder.validatePeerHandshakeMessage(initiatorHandshakeMessage, partyAIdentityKey.public, KeyAlgorithm.ECDSA)

            val signingCallbackForB = { data: ByteArray ->
                signature.initSign(partyBIdentityKey.private)
                signature.update(data)
                signature.sign()
            }
            val responderHandshakeMessage = responder.generateOurHandshakeMessage(partyBIdentityKey.public, signingCallbackForB)

            initiator.validatePeerHandshakeMessage(responderHandshakeMessage, partyBIdentityKey.public, KeyAlgorithm.ECDSA)
            return SessionPair(initiator.getSession(), responder.getSession())
        }

        fun authenticatedMessageAndKey(
            source: HoldingIdentity,
            dest: HoldingIdentity,
            data: ByteBuffer,
            messageId: String = ""
        ): AuthenticatedMessageAndKey {
            val header = AuthenticatedMessageHeader(dest, source, null, messageId, "", "system-1")
            return AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), KEY)
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

    private fun authenticatedMessage(source: HoldingIdentity, dest: HoldingIdentity, data: String, messageId: String): AuthenticatedMessage {
        val header = AuthenticatedMessageHeader(dest, source, null, messageId, "", "system-1")
        return AuthenticatedMessage(header, ByteBuffer.wrap(data.toByteArray()))
    }

    private fun initiatorHelloLinkInMessage() : LinkInMessage {
        val session =  AuthenticationProtocolInitiator(
            SESSION_ID,
            setOf(ProtocolMode.AUTHENTICATION_ONLY),
            MAX_MESSAGE_SIZE,
            FIRST_DEST_MEMBER_INFO.publicKey,
            FIRST_DEST_MEMBER_INFO.holdingIdentity.groupId
        )
        return LinkInMessage(session.generateInitiatorHello())
    }

    private fun createDataMessage(message: LinkOutMessage): DataMessage {
        return when (val payload = message.payload) {
            is AuthenticatedDataMessage -> DataMessage.Authenticated(payload)
            is AuthenticatedEncryptedDataMessage -> DataMessage.AuthenticatedAndEncrypted(payload)
            else -> fail("Tried to create a DataMessage from a LinkOutMessage which doesn't contain a AVRO data message.")
        }
    }

    private fun extractPayload(session: Session, message: LinkOutMessage): ByteBuffer {
        val dataMessage = createDataMessage(message)
        val payload = MessageConverter.extractPayload(session, "", dataMessage, AuthenticatedMessageAndKey::fromByteBuffer)
        assertNotNull(payload)
        assertNotNull(payload!!.message)
        return payload.message!!.payload
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
        val message = AuthenticatedMessageAndKey(authenticatedMessage(FIRST_SOURCE, FIRST_DEST, "0-0", MESSAGE_ID), KEY)

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

        val message1 = authenticatedMessageAndKey(FIRST_SOURCE, FIRST_DEST, payload1)
        val message2 = authenticatedMessageAndKey(FIRST_SOURCE, FIRST_DEST, payload2)
        val key1 = getSessionKeyFromMessage(message1.message)

        //Messages 3, 4, 5 can share another session
        val message3 = authenticatedMessageAndKey(SECOND_SOURCE, SECOND_DEST, payload3)
        val message4 = authenticatedMessageAndKey(SECOND_SOURCE, SECOND_DEST, payload4)
        val message5 = authenticatedMessageAndKey(SECOND_SOURCE, SECOND_DEST, payload5)
        val key2 = getSessionKeyFromMessage(message3.message)

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
    fun `OutboundMessageProcessor forwards unauthenticated messages directly to link out topic`() {
        val processor = LinkManager.OutboundMessageProcessor(Mockito.mock(SessionManagerImpl::class.java), netMap, assignedListener(listOf(1)))
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(UnauthenticatedMessageHeader(FIRST_SOURCE, FIRST_DEST), ByteBuffer.wrap(payload.toByteArray()))
        val appMessage = AppMessage(unauthenticatedMsg)

        val records = processor.onNext(listOf(EventLogRecord(TOPIC, KEY, appMessage, 1, 0)))

        assertThat(records).hasSize(1)
        val newMessage = records.first()
        assertThat(newMessage.topic).isEqualTo(LINK_OUT_TOPIC)
        assertThat(newMessage.value).isInstanceOf(LinkOutMessage::class.java)
        assertThat((newMessage.value as LinkOutMessage).payload).isEqualTo(unauthenticatedMsg)
    }

    @Test
    fun `OutboundMessageProcessor produces only a LinkManagerSent maker (per flowMessage) if SessionAlreadyPending`() {
        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, netMap, assignedListener(listOf(1)))

        val numberOfMessages = 3
        val messages = mutableListOf<EventLogRecord<String, AppMessage>>()
        for (i in 0 until numberOfMessages) {
            messages.add(EventLogRecord(TOPIC, KEY, AppMessage(authenticatedMessage(FIRST_SOURCE, FIRST_DEST, "$i", "MessageId$i")), 0, 0 ))
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

        val sessionInitMessage = LinkOutMessage()
        val state = SessionManager.SessionState.NewSessionNeeded(SESSION_ID, sessionInitMessage)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(state)

        val inboundSubscribedTopics = listOf(1, 5, 9)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, netMap, assignedListener(inboundSubscribedTopics))
        val messages = listOf(EventLogRecord(TOPIC, KEY, AppMessage(authenticatedMessage(FIRST_SOURCE, FIRST_DEST, "0", MESSAGE_ID)), 0, 0))
        val records = processor.onNext(messages)

        assertThat(records).hasSize(3 * messages.size)

        assertThat(records).filteredOn { it.topic == LINK_OUT_TOPIC }.hasSize(messages.size)
            .extracting<LinkOutMessage> { it.value as LinkOutMessage }
            .allSatisfy { assertThat(it).isSameAs(sessionInitMessage) }

        assertThat(records).filteredOn { it.topic == Schema.SESSION_OUT_PARTITIONS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isSameAs(SESSION_ID) }
            .extracting<SessionPartitions> { it.value as SessionPartitions }
            .allSatisfy { assertThat(it.partitions.toIntArray()).isEqualTo(inboundSubscribedTopics.toIntArray()) }

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isEqualTo(MESSAGE_ID) }
            .extracting<FlowMessageMarker> { it.value as FlowMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerSentMarker::class.java) }
    }

    @Test
    fun `OutboundMessageProcessor produces a LinkOutMessage and a LinkManagerSentMarker per message if SessionEstablished`() {
        val mockSessionManager = Mockito.mock(SessionManager::class.java)

        val state = SessionManager.SessionState.SessionEstablished(createSessionPair().initiatorSession)
        Mockito.`when`(mockSessionManager.processOutboundFlowMessage(any())).thenReturn(state)

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, netMap, assignedListener(listOf(1)))
        val messageIds = listOf("Id1", "Id2", "Id3")

        val messages = listOf(
            EventLogRecord(TOPIC, KEY,
                AppMessage(authenticatedMessageAndKey(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("0".toByteArray()), messageIds[0]).message),
            0, 0
            ),
            EventLogRecord(TOPIC, KEY,
                AppMessage(authenticatedMessageAndKey(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("1".toByteArray()), messageIds[1]).message),
                0, 0
            ),
            EventLogRecord(TOPIC, KEY,
                AppMessage(authenticatedMessageAndKey(FIRST_SOURCE, FIRST_DEST, ByteBuffer.wrap("2".toByteArray()), messageIds[2]).message)
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

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, netMap, assignedListener(listOf(1)))
        val messages = listOf(EventLogRecord(TOPIC, KEY, AppMessage(authenticatedMessage(FIRST_SOURCE, FIRST_DEST, "0", MESSAGE_ID)), 0, 0))
        val records = processor.onNext(messages)

        assertThat(records).hasSize(messages.size)

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isEqualTo(MESSAGE_ID) }
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

        val processor = LinkManager.OutboundMessageProcessor(mockSessionManager, mockNetworkMap, assignedListener(listOf(1)))
        val messages = listOf(EventLogRecord(TOPIC, KEY, AppMessage(authenticatedMessage(FIRST_SOURCE, FIRST_DEST, "0", MESSAGE_ID)), 0, 0))
        val records = processor.onNext(messages)

        assertThat(records).hasSize(messages.size)

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isEqualTo(MESSAGE_ID) }
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

        val header = AuthenticatedMessageHeader(FIRST_DEST, FIRST_SOURCE, null, MESSAGE_ID, "", "system-1")
        val flowMessageAndKey = AuthenticatedMessageAndKey(AuthenticatedMessage(header, PAYLOAD), KEY)

        val linkOutMessage = linkOutMessageFromFlowMessageAndKey(flowMessageAndKey, session.initiatorSession, netMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0),
            EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getSessionById(any())).thenReturn(
            SessionManager.SessionDirection.Inbound(session.responderSession)
        )

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, netMap)

        val records = processor.onNext(messages)
        assertThat(records).filteredOn { it.value is AuthenticatedMessage }.hasSize(messages.size)
        assertThat(records).filteredOn { it.value is LinkOutMessage }.hasSize(messages.size)
        for (record in records) {
            when (val value = record.value) {
                is AuthenticatedMessage -> {
                    assertEquals(P2P_IN_TOPIC, record.topic)
                    assertArrayEquals(flowMessageAndKey.message.payload.array(), value.payload.array())
                    assertEquals(flowMessageAndKey.key, record.key)
                }
                is LinkOutMessage -> {
                    assertEquals(LINK_OUT_TOPIC, record.topic)
                    val messageAck = MessageConverter.extractPayload(
                        session.initiatorSession,
                        SESSION_ID,
                        createDataMessage(value),
                        MessageAck::fromByteBuffer
                    )
                    assertNotNull(messageAck)
                    assertEquals(MESSAGE_ID, messageAck!!.messageId)
                }
                else -> {
                    fail("Inbound message processor should only produce records with ${AuthenticatedMessage::class.java} and " +
                        "${LinkOutMessage::class.java}")
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
        val session = createSessionPair(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val linkOutMessage = linkOutMessageFromAck(MessageAck(MESSAGE_ID), FIRST_SOURCE, FIRST_DEST, session.initiatorSession, netMap)
        val message = LinkInMessage(linkOutMessage?.payload)

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getSessionById(any())).thenReturn(SessionManager.SessionDirection.Outbound(session.responderSession))

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, netMap)
        val records = processor.onNext(listOf(EventLogRecord(TOPIC, KEY, message, 0, 0)) )

        assertThat(records).hasSize(1)

        assertThat(records).filteredOn { it.topic == P2P_OUT_MARKERS }.hasSize(1)
            .allSatisfy { assertThat(it.key).isEqualTo(MESSAGE_ID) }
            .extracting<FlowMessageMarker> { it.value as FlowMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerReceivedMarker::class.java) }
    }

    @Test
    fun `InboundMessageProcessor produces a FlowMessage only if the sender is removed from the network map before creating an ACK`() {
        val session = createSessionPair()

        val header = AuthenticatedMessageHeader(FIRST_DEST, FIRST_SOURCE, null, MESSAGE_ID, "", "system-1")
        val flowMessageWrapper = AuthenticatedMessageAndKey(AuthenticatedMessage(header, PAYLOAD), KEY)

        val linkOutMessage = linkOutMessageFromFlowMessageAndKey(flowMessageWrapper, session.initiatorSession, netMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0),
            EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getSessionById(any())).thenReturn(
            SessionManager.SessionDirection.Inbound(session.responderSession)
        )

        val networkMapAfterRemoval = Mockito.mock(LinkManagerNetworkMap::class.java)
        Mockito.`when`(networkMapAfterRemoval.getMemberInfo(FIRST_SOURCE.toHoldingIdentity())).thenReturn(null)
        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, networkMapAfterRemoval)

        val records = processor.onNext(messages)
        assertEquals(messages.size, records.size)
        for (record in records) {
            assertEquals(P2P_IN_TOPIC, record.topic)
            assertTrue(record.value is AuthenticatedMessage)
            assertArrayEquals(flowMessageWrapper.message.payload.array(), (record.value as AuthenticatedMessage).payload.array())
            assertEquals(flowMessageWrapper.key, record.key)
        }
    }

    @Test
    fun `InboundMessageProcessor discards messages with unknown sessionId`() {
        val session = createSessionPair()

        val header = AuthenticatedMessageHeader(FIRST_DEST, FIRST_SOURCE, null, "", "", "system-1")
        val flowMessageWrapper = AuthenticatedMessageAndKey(AuthenticatedMessage(header, PAYLOAD), KEY)

        val linkOutMessage = linkOutMessageFromFlowMessageAndKey(flowMessageWrapper, session.initiatorSession, netMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getSessionById(any())).thenReturn(SessionManager.SessionDirection.NoSession)

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, netMap)

        val records = processor.onNext(messages)
        assertEquals(records.size, 0)

        loggingInterceptor.assertSingleWarning("Received message with SessionId = $SESSION_ID for which there is no active session. " +
            "The message was discarded.")
    }

    @Test
    fun `InboundMessageProcessor discards a FlowMessage on a OutboundSession`() {
        val session = createSessionPair()

        val header = AuthenticatedMessageHeader(FIRST_DEST, FIRST_SOURCE, null, MESSAGE_ID, "", "system-1")
        val flowMessageWrapper = AuthenticatedMessageAndKey(AuthenticatedMessage(header, PAYLOAD), KEY)

        val linkOutMessage = linkOutMessageFromFlowMessageAndKey(flowMessageWrapper, session.initiatorSession, netMap)
        val linkInMessage = LinkInMessage(linkOutMessage!!.payload)

        val messages = listOf(EventLogRecord(TOPIC, KEY, linkInMessage, 0, 0))

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getSessionById(any())).thenReturn(
            SessionManager.SessionDirection.Outbound(session.responderSession)
        )

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, netMap)

        val records = processor.onNext(messages)
        assertEquals(records.size, 0)

        loggingInterceptor.assertErrorContains("Could not deserialize message for session $SESSION_ID.")
        loggingInterceptor.assertErrorContains("The message was discarded.")
    }

    @Test
    fun `InboundMessageProcessor discards a MessageAck on a InboundSession`() {
        val session = createSessionPair(ProtocolMode.AUTHENTICATED_ENCRYPTION)
        val linkOutMessage = linkOutMessageFromAck(MessageAck(MESSAGE_ID), FIRST_SOURCE, FIRST_DEST, session.initiatorSession, netMap)
        val message = LinkInMessage(linkOutMessage?.payload)

        val mockSessionManager = Mockito.mock(SessionManagerImpl::class.java)
        Mockito.`when`(mockSessionManager.getSessionById(any())).thenReturn(SessionManager.SessionDirection.Inbound(session.responderSession))

        val processor = LinkManager.InboundMessageProcessor(mockSessionManager, netMap)
        val records = processor.onNext(listOf(EventLogRecord(TOPIC, KEY, message, 0, 0)) )
        assertEquals(records.size, 0)

        loggingInterceptor.assertErrorContains("Could not deserialize message for session $SESSION_ID.")
        loggingInterceptor.assertErrorContains("The message was discarded.")
    }
}
