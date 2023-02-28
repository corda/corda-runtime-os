package net.corda.p2p.linkmanager.inbound

import net.corda.messaging.api.records.EventLogRecord
import net.corda.data.p2p.AuthenticatedMessageAck
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.DataMessagePayload
import net.corda.data.p2p.HeartbeatMessage
import net.corda.data.p2p.HeartbeatMessageAck
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutHeader
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.MessageAck
import net.corda.data.p2p.SessionPartitions
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.crypto.protocol.api.EncryptionResult
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockMembersAndGroups
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.schema.Schemas.P2P.LINK_IN_TOPIC
import net.corda.schema.Schemas.P2P.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.SESSION_OUT_PARTITIONS
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.utilities.seconds
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class InboundMessageProcessorTest {
    companion object {
        private const val SESSION_ID = "Session"
        private const val MESSAGE_ID = "MessageId"
    }
    private val sessionManager = mock<SessionManager>()
    private val myIdentity = createTestHoldingIdentity("CN=PartyA, O=Corp, L=LDN, C=GB", "Group")
    private val remoteIdentity = createTestHoldingIdentity("CN=PartyC, O=Corp, L=LDN, C=GB", "Group")
    private val membersAndGroups = mockMembersAndGroups(
        myIdentity, remoteIdentity
    )
    private val assignedListener = mock<InboundAssignmentListener> {
        on { getCurrentlyAssignedPartitions() } doReturn setOf(1)
    }
    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()
    private val commonHeader = CommonHeader(
        MessageType.DATA,
        1,
        SESSION_ID,
        1,
        1
    )
    private val loggingInterceptor = LoggingInterceptor.setupLogging()

    private val processor = InboundMessageProcessor(
        sessionManager,
        membersAndGroups.second,
        membersAndGroups.first,
        assignedListener,
        mockTimeFacilitiesProvider.clock
    )

    @AfterEach
    fun cleanUp() {
        loggingInterceptor.reset()
    }

    @Test
    fun `ignores messages with null values`() {
        val records = processor.onNext(
            listOf(
                EventLogRecord(LINK_IN_TOPIC, "key", null, 0, 0),
                EventLogRecord(LINK_IN_TOPIC, "key", null, 0, 0)
            )
        )

        assertThat(records).isEmpty()
        assertThat(loggingInterceptor.errors)
            .hasSize(2)
            .contains("Received null message. The message was discarded.")
    }

    @Test
    fun `ignores messages with unknown value`() {
        val records = processor.onNext(
            listOf(
                EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage("payload"), 0, 0),
                EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(33), 0, 0)
            )
        )

        assertThat(records).isEmpty()
        assertThat(loggingInterceptor.errors)
            .contains("Received unknown payload type Integer. The message was discarded.")
            .contains("Received unknown payload type String. The message was discarded.")
    }

    @Nested
    inner class AuthenticatedDataMessageTests {
        @Test
        fun `AuthenticatedDataMessage with Inbound session will produce a message on the P2P_IN_TOPIC and LINK_OUT_TOPIC topics`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1"
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
            val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
                authenticatedMsg,
                "key"
            )
            val messageAndPayload = DataMessagePayload(authenticatedMessageAndKey)
            val authenticationResult = mock<AuthenticationResult> {
                on { header } doReturn commonHeader
                on { mac } doReturn byteArrayOf()
            }
            val session = mock<AuthenticatedSession> {
                on { createMac(any()) } doReturn authenticationResult
            }
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.SessionCounterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                messageAndPayload.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).hasSize(2).anyMatch {
                val value = it.value
                it.topic == P2P_IN_TOPIC &&
                    it.key == "key" &&
                    value is AppMessage &&
                    value.message == authenticatedMsg
            }.anyMatch { record ->
                val value = record.value
                if (value is LinkOutMessage) {
                    val payload = value.payload
                    if (payload is AuthenticatedDataMessage) {
                        val messageAck = MessageAck.fromByteBuffer(payload.payload)
                        val ack = messageAck.ack
                        if (ack is AuthenticatedMessageAck) {
                            return@anyMatch ack.messageId == MESSAGE_ID && record.topic == LINK_OUT_TOPIC
                        }
                    }
                }
                false
            }
        }

        @Test
        fun `AuthenticatedDataMessage with Outbound session will process the message ack`() {
            val session = mock<AuthenticatedSession>()
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Outbound(
                    SessionManager.SessionCounterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )
            val messageAck = MessageAck(
                AuthenticatedMessageAck(
                    MESSAGE_ID
                )
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                messageAck.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )
            mockTimeFacilitiesProvider.advanceTime(1000.seconds)

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).hasSize(1).allMatch {
                val value = it.value
                it.topic == P2P_OUT_MARKERS && value is AppMessageMarker &&
                    value.marker is LinkManagerReceivedMarker && value.timestamp == 1000000L
            }
            verify(sessionManager).messageAcknowledged(SESSION_ID)
        }

        @Test
        fun `AuthenticatedDataMessage with Outbound session will process HeartbeatMessageAck`() {
            val session = mock<AuthenticatedSession>()
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Outbound(
                    SessionManager.SessionCounterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )
            val messageAck = MessageAck(
                HeartbeatMessageAck()
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                messageAck.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).hasSize(0)
            verify(sessionManager).messageAcknowledged(SESSION_ID)
        }

        @Test
        fun `AuthenticatedDataMessage with Outbound session will not process invalid message`() {
            val session = mock<AuthenticatedSession>()
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Outbound(
                    SessionManager.SessionCounterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                ByteBuffer.wrap(byteArrayOf()), ByteBuffer.wrap(byteArrayOf())
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).hasSize(0)
            verify(sessionManager, never()).messageAcknowledged(SESSION_ID)
            assertThat(loggingInterceptor.errors).allSatisfy {
                assertThat(it).matches("Could not deserialize message for session Session.* The message was discarded\\.")
            }
        }

        @Test
        fun `AuthenticatedMessageAck with InboundSession session will discard the message`() {
            val session = mock<AuthenticatedSession>()
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.SessionCounterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )
            val messageAck = MessageAck(
                AuthenticatedMessageAck(
                    MESSAGE_ID
                )
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                messageAck.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).hasSize(0)
            assertThat(loggingInterceptor.errors)
                .hasSize(1)
                .anySatisfy {
                    assertThat(it).matches("Could not deserialize message for session Session\\..* Cannot resolve schema for fingerprint.*")
                }
        }

        @Test
        fun `AuthenticatedDataMessage with no session  will not produce records`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1"
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
            val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
                authenticatedMsg,
                "key"
            )
            val messageAndPayload = DataMessagePayload(authenticatedMessageAndKey)
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.NoSession
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                messageAndPayload.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).isEmpty()
            assertThat(loggingInterceptor.warnings)
                .hasSize(1)
                .contains("Received message with SessionId = Session for which there is no active session. The message was discarded.")
        }
    }

    @Nested
    inner class AuthenticatedEncryptedDataMessageTests {
        @Test
        fun `receiving data message with Inbound session will produce a message on the P2P_IN_TOPIC and LINK_OUT_TOPIC topics`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1"
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
            val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
                authenticatedMsg,
                "key"
            )
            val messageAndPayload = DataMessagePayload(authenticatedMessageAndKey)
            val encryptionResult = mock<EncryptionResult> {
                on { header } doReturn commonHeader
                on { authTag } doReturn byteArrayOf()
                on { encryptedPayload } doReturn messageAndPayload.toByteBuffer().array()
            }
            val dataMessage = AuthenticatedEncryptedDataMessage(
                commonHeader,
                messageAndPayload.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )
            val session = mock<AuthenticatedEncryptionSession> {
                on { encryptData(any()) } doReturn encryptionResult
                on {
                    decryptData(
                        commonHeader, messageAndPayload.toByteBuffer().array(), byteArrayOf()
                    )
                } doReturn messageAndPayload.toByteBuffer().array()
            }
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.SessionCounterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).hasSize(2).anyMatch {
                val value = it.value
                it.topic == P2P_IN_TOPIC &&
                    it.key == "key" &&
                    value is AppMessage &&
                    value.message == authenticatedMsg
            }.anyMatch { record ->
                val value = record.value
                if (value is LinkOutMessage) {
                    val payload = value.payload
                    if (payload is AuthenticatedEncryptedDataMessage) {
                        if (payload.encryptedPayload == messageAndPayload.toByteBuffer()) {
                            return@anyMatch payload.header == commonHeader && record.topic == LINK_OUT_TOPIC
                        }
                    }
                }
                false
            }
            verify(sessionManager).inboundSessionEstablished(anyOrNull())
        }

        @Test
        fun `when the message's source identity does not match the one of the session the message is discarded`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1"
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
            val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
                authenticatedMsg,
                "key"
            )
            val messageAndPayload = DataMessagePayload(authenticatedMessageAndKey)
            val encryptionResult = mock<EncryptionResult> {
                on { header } doReturn commonHeader
                on { authTag } doReturn byteArrayOf()
                on { encryptedPayload } doReturn messageAndPayload.toByteBuffer().array()
            }
            val dataMessage = AuthenticatedEncryptedDataMessage(
                commonHeader,
                messageAndPayload.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )
            val session = mock<AuthenticatedEncryptionSession> {
                on { encryptData(any()) } doReturn encryptionResult
                on {
                    decryptData(
                        commonHeader, messageAndPayload.toByteBuffer().array(), byteArrayOf()
                    )
                } doReturn messageAndPayload.toByteBuffer().array()
            }
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.SessionCounterparties(
                        myIdentity,
                        remoteIdentity,
                    ),
                    session
                )
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).isEmpty()
            assertThat(loggingInterceptor.warnings)
                .hasSize(1)
                .anySatisfy {
                    assertThat(it).matches(
                        "The identity in the message's source header.*" +
                            " does not match the session's source identity.*" +
                            " which indicates a spoofing attempt! The message was discarded\\."
                    )
                }
        }

        @Test
        fun `when the message's dest identity does not match the one of the session the message is discarded`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1"
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
            val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
                authenticatedMsg,
                "key"
            )
            val messageAndPayload = DataMessagePayload(authenticatedMessageAndKey)
            val encryptionResult = mock<EncryptionResult> {
                on { header } doReturn commonHeader
                on { authTag } doReturn byteArrayOf()
                on { encryptedPayload } doReturn messageAndPayload.toByteBuffer().array()
            }
            val dataMessage = AuthenticatedEncryptedDataMessage(
                commonHeader,
                messageAndPayload.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )
            val session = mock<AuthenticatedEncryptionSession> {
                on { encryptData(any()) } doReturn encryptionResult
                on {
                    decryptData(
                        commonHeader, messageAndPayload.toByteBuffer().array(), byteArrayOf()
                    )
                } doReturn messageAndPayload.toByteBuffer().array()
            }
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.SessionCounterparties(
                        myIdentity,
                        myIdentity,
                    ),
                    session
                )
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).isEmpty()
            assertThat(loggingInterceptor.warnings)
                .hasSize(1)
                .allSatisfy {
                    assertThat(it).matches(
                        "The identity in the message's destination header.*" +
                            " does not match the session's destination identity.*" +
                            " which indicates a spoofing attempt! The message was discarded"
                    )
                }
        }

        @Test
        fun `receiving a heatbeat message with Inbound session will produce link out ack message`() {
            val heartbeatMessage = HeartbeatMessage()
            val messageAndPayload = DataMessagePayload(heartbeatMessage)
            val encryptionResult = mock<EncryptionResult> {
                on { header } doReturn commonHeader
                on { authTag } doReturn byteArrayOf()
                on { encryptedPayload } doReturn messageAndPayload.toByteBuffer().array()
            }
            val dataMessage = AuthenticatedEncryptedDataMessage(
                commonHeader,
                messageAndPayload.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )
            val session = mock<AuthenticatedEncryptionSession> {
                on { encryptData(any()) } doReturn encryptionResult
                on {
                    decryptData(
                        commonHeader, messageAndPayload.toByteBuffer().array(), byteArrayOf()
                    )
                } doReturn messageAndPayload.toByteBuffer().array()
            }
            whenever(sessionManager.getSessionById(any())).thenReturn(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.SessionCounterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(records).hasSize(1).anyMatch { record ->
                val value = record.value
                if (value is LinkOutMessage) {
                    val payload = value.payload
                    if (payload is AuthenticatedEncryptedDataMessage) {
                        if (payload.encryptedPayload == messageAndPayload.toByteBuffer()) {
                            return@anyMatch payload.header == commonHeader && record.topic == LINK_OUT_TOPIC
                        }
                    }
                }
                false
            }
        }
    }

    @Nested
    inner class SessionMessageTests {
        @Test
        fun `ResponderHelloMessage calls to processSessionMessage`() {
            val responderHelloMessage = mock<ResponderHelloMessage>()
            val message = LinkInMessage(responderHelloMessage)

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            verify(sessionManager).processSessionMessage(message)
        }
        @Test
        fun `ResponderHandshakeMessage calls to processSessionMessage`() {
            val responderHandshakeMessage = mock<ResponderHandshakeMessage>()
            val message = LinkInMessage(responderHandshakeMessage)

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            verify(sessionManager).processSessionMessage(message)
        }
        @Test
        fun `InitiatorHandshakeMessage calls to processSessionMessage`() {
            val initiatorHandshakeMessage = mock<InitiatorHandshakeMessage>()
            val message = LinkInMessage(initiatorHandshakeMessage)

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            verify(sessionManager).processSessionMessage(message)
        }

        @Test
        fun `InitiatorHelloMessage calls to processSessionMessage`() {
            val initiatorHelloMessage = mock<InitiatorHelloMessage>()
            val message = LinkInMessage(initiatorHelloMessage)

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            verify(sessionManager).processSessionMessage(message)
        }

        @Test
        fun `null response from sessionManager will produce no records`() {
            val initiatorHelloMessage = mock<InitiatorHelloMessage>()
            val message = LinkInMessage(initiatorHelloMessage)
            whenever(sessionManager.processSessionMessage(message)).thenReturn(null)

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            assertThat(records).isEmpty()
        }

        @Test
        fun `non null responses from sessionManager will produce link out message`() {
            val handshake = ResponderHandshakeMessage()
            val message = LinkInMessage(handshake)
            val response = LinkOutMessage(LinkOutHeader(), handshake)
            whenever(sessionManager.processSessionMessage(message)).thenReturn(response)

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            assertThat(records).hasSize(1).allSatisfy {
                assertThat(it.topic).isEqualTo(LINK_OUT_TOPIC)
                assertThat(it.value).isSameAs(response)
            }
        }

        @Test
        fun `InitiatorHelloMessage responses from sessionManager without partitions will produce no records`() {
            val hello = mock<InitiatorHelloMessage> {
                on { header } doReturn commonHeader
            }
            val message = LinkInMessage(hello)
            val response = LinkOutMessage(LinkOutHeader(), hello)
            whenever(sessionManager.processSessionMessage(message)).thenReturn(response)
            whenever(assignedListener.getCurrentlyAssignedPartitions()).thenReturn(emptySet())

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            assertThat(records).isEmpty()
            assertThat(loggingInterceptor.warnings)
                .hasSize(1)
                .contains(
                    "No partitions from topic link.in are currently assigned to the inbound message processor. " +
                            "Not going to reply to session initiation for session Session."
                )
        }
        @Test
        fun `InitiatorHelloMessage responses from sessionManager with partitions will produce records to the correct topics`() {
            val hello = mock<InitiatorHelloMessage> {
                on { header } doReturn commonHeader
            }
            val message = LinkInMessage(hello)
            val response = LinkOutMessage(LinkOutHeader(), hello)
            whenever(sessionManager.processSessionMessage(message)).thenReturn(response)
            whenever(assignedListener.getCurrentlyAssignedPartitions()).thenReturn(setOf(4, 5, 8))

            val records = processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            assertThat(records).hasSize(2).anySatisfy {
                assertThat(it.topic).isEqualTo(LINK_OUT_TOPIC)
                assertThat(it.value).isSameAs(response)
            }.anySatisfy {
                assertThat(it.topic).isEqualTo(SESSION_OUT_PARTITIONS)
                assertThat(it.key).isSameAs(SESSION_ID)
                assertThat(it.value).isEqualTo(SessionPartitions(listOf(4, 5, 8)))
            }
        }
    }

    @Test
    fun `UnauthenticatedMessage will produce message in P2P in topic`() {
        val unauthenticatedMessageHeader = mock<UnauthenticatedMessageHeader> {
            on { messageId } doReturn "messageId"
            on { source } doReturn myIdentity.toAvro()
            on { destination } doReturn remoteIdentity.toAvro()
            on { subsystem } doReturn "application-v1"
        }
        val unauthenticatedMessage = mock<UnauthenticatedMessage> {
            on { header } doReturn unauthenticatedMessageHeader
        }
        val message = LinkInMessage(unauthenticatedMessage)

        val records = processor.onNext(
            listOf(
                EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
            )
        )

        assertThat(records).hasSize(1).anySatisfy {
            assertThat(it.topic).isEqualTo(P2P_IN_TOPIC)
            assertThat(it.value).isInstanceOf(AppMessage::class.java)
            assertThat((it.value as AppMessage).message).isEqualTo(unauthenticatedMessage)
        }
    }
}
