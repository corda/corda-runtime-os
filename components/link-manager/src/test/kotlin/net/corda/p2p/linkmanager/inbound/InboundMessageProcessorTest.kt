package net.corda.p2p.linkmanager.inbound

import net.corda.data.p2p.AuthenticatedMessageAck
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.DataMessagePayload
import net.corda.data.p2p.LinkInMessage
import net.corda.data.p2p.LinkOutHeader
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.MessageAck
import net.corda.data.p2p.NetworkType
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.InboundUnauthenticatedMessage
import net.corda.data.p2p.app.InboundUnauthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.data.p2p.crypto.AuthenticatedEncryptedDataMessage
import net.corda.data.p2p.crypto.CommonHeader
import net.corda.data.p2p.crypto.InitiatorHandshakeMessage
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.MessageType
import net.corda.data.p2p.crypto.ResponderHandshakeMessage
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.event.SessionCreated
import net.corda.data.p2p.event.SessionDirection
import net.corda.data.p2p.event.SessionEvent
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.p2p.crypto.protocol.api.AuthenticatedEncryptionSession
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.crypto.protocol.api.EncryptionResult
import net.corda.p2p.linkmanager.TraceableItem
import net.corda.p2p.linkmanager.membership.NetworkMessagingValidator
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.sessions.StatefulSessionManagerImpl.Companion.LINK_MANAGER_SUBSYSTEM
import net.corda.p2p.linkmanager.utilities.LoggingInterceptor
import net.corda.p2p.linkmanager.utilities.mockMembersAndGroups
import net.corda.schema.Schemas.P2P.LINK_IN_TOPIC
import net.corda.schema.Schemas.P2P.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_IN_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_MARKERS
import net.corda.schema.Schemas.P2P.SESSION_EVENTS
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.utilities.Either
import net.corda.utilities.flags.Features
import net.corda.utilities.seconds
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture

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
    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()
    private val commonHeader = CommonHeader(
        MessageType.DATA,
        1,
        SESSION_ID,
        1,
        1
    )
    private val loggingInterceptor = LoggingInterceptor.setupLogging()

    private val networkMessagingValidator = mock<NetworkMessagingValidator> {
        on { isValidInbound(any(), any()) } doReturn true
        on { validateInbound(any(), any()) } doReturn Either.Left(Unit)
    }
    private val features = mock<Features> {
        on { enableP2PGatewayToLinkManagerOverHttp } doReturn false
    }
    private val publishedRecords = argumentCaptor<List<Record<*, *>>>()
    private val publisher = mock<PublisherWithDominoLogic> {
        on { publish(publishedRecords.capture()) } doReturn listOf(CompletableFuture.completedFuture(Unit))
    }

    private val processor = InboundMessageProcessor(
        sessionManager,
        membersAndGroups.second,
        membersAndGroups.first,
        publisher,
        mockTimeFacilitiesProvider.clock,
        networkMessagingValidator,
        features,
    )

    private val status = MembershipStatusFilter.ACTIVE

    @AfterEach
    fun cleanUp() {
        loggingInterceptor.reset()
    }

    private fun setupGetSessionsById(direction: SessionManager.SessionDirection) {
        val captor = argumentCaptor<List<InboundMessageProcessor.SessionIdAndMessage<*>>>()
        whenever(sessionManager.getSessionsById(captor.capture(), any())).thenAnswer {
            captor.firstValue.map { it to direction }
        }
    }

    @Test
    fun `ignores messages with null values`() {
        processor.onNext(
            listOf(
                EventLogRecord(LINK_IN_TOPIC, "key", null, 0, 0),
                EventLogRecord(LINK_IN_TOPIC, "key", null, 0, 0)
            )
        )

        assertThat(publishedRecords.allValues).isEmpty()
        assertThat(loggingInterceptor.errors)
            .hasSize(2)
            .contains("Received null message. The message was discarded.")
    }

    @Test
    fun `ignores messages with unknown value`() {
        processor.onNext(
            listOf(
                EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage("payload"), 0, 0),
                EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(33), 0, 0)
            )
        )

        assertThat(publishedRecords.allValues).isEmpty()
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
                    null, MESSAGE_ID, "trace-id", "system-1", status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
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

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            val records = publishedRecords.allValues.flatten()
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
        fun `AuthenticatedDataMessage with Inbound session will not publish ack if failed to publish the message`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1", status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
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
            val publishedRecords = argumentCaptor<List<Record<*, *>>>()
            whenever(
                publisher.publish(publishedRecords.capture())
            ).doReturn(listOf(CompletableFuture.failedFuture(CordaRuntimeException("Oops"))))

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(publishedRecords.allValues.flatten()).hasSize(1)
                .allMatch {
                    it.topic == P2P_IN_TOPIC
                }
        }

        @Test
        fun `AuthenticatedDataMessage with Inbound session will produce a message on the P2P_IN_TOPIC topics and a response`() {
            val features = mock<Features> {
                on { enableP2PGatewayToLinkManagerOverHttp } doReturn true
            }

            val processor = InboundMessageProcessor(
                sessionManager,
                membersAndGroups.second,
                membersAndGroups.first,
                publisher,
                mockTimeFacilitiesProvider.clock,
                networkMessagingValidator,
                features,
            )

            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1", status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
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
            val message = object : InboundMessage {
                override val message = LinkInMessage(dataMessage)
            }

            val replies = processor.handleRequests(
                listOf(
                    message,
                )
            )

            assertSoftly { softly ->
                assertThat(replies).hasSize(1)
                val reply = replies.firstOrNull()
                val record = reply?.item?.records?.firstOrNull()
                val value = record?.value as? AppMessage
                softly.assertThat(record?.topic).isEqualTo(P2P_IN_TOPIC)
                softly.assertThat(record?.key).isEqualTo("key")
                softly.assertThat(value?.message).isEqualTo(authenticatedMsg)
                val payload = reply?.item?.ack?.asLeft()?.payload as? AuthenticatedDataMessage
                val messageAck = MessageAck.fromByteBuffer(payload?.payload)
                val ack = messageAck.ack as? AuthenticatedMessageAck
                softly.assertThat(ack?.messageId).isEqualTo(MESSAGE_ID)
            }
        }

        @Test
        fun `AuthenticatedDataMessage with Outbound session will process the message ack`() {
            val session = mock<AuthenticatedSession>()
            setupGetSessionsById(
                SessionManager.SessionDirection.Outbound(
                    SessionManager.Counterparties(
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

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            val records = publishedRecords.allValues.flatten()
            assertThat(records).hasSize(1).allMatch {
                val value = it.value
                it.topic == P2P_OUT_MARKERS && value is AppMessageMarker &&
                        value.marker is LinkManagerReceivedMarker && value.timestamp == 1000000L
            }
            verify(sessionManager).messageAcknowledged(SESSION_ID)
        }

        @Test
        fun `AuthenticatedDataMessage with Outbound session will not process invalid message`() {
            val session = mock<AuthenticatedSession>()
            setupGetSessionsById(
                SessionManager.SessionDirection.Outbound(
                    SessionManager.Counterparties(
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

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(publishedRecords.allValues).hasSize(0)
            verify(sessionManager, never()).messageAcknowledged(SESSION_ID)
            assertThat(loggingInterceptor.errors).allSatisfy {
                assertThat(it).matches("Could not deserialize message for session Session.* The message was discarded\\.")
            }
        }

        @Test
        fun `AuthenticatedMessageAck with InboundSession session will discard the message`() {
            val session = mock<AuthenticatedSession>()
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
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

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(publishedRecords.allValues).hasSize(0)
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
                    null, MESSAGE_ID, "trace-id", "system-1", status
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
            val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
                authenticatedMsg,
                "key"
            )
            val messageAndPayload = DataMessagePayload(authenticatedMessageAndKey)
            setupGetSessionsById(
                SessionManager.SessionDirection.NoSession
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                messageAndPayload.toByteBuffer(), ByteBuffer.wrap(byteArrayOf())
            )

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(publishedRecords.allValues).isEmpty()
            assertThat(loggingInterceptor.warnings)
                .hasSize(1)
                .contains("Received message with SessionId = Session for which there is no active session. The message was discarded.")
        }

        @Test
        fun `AuthenticatedDataMessage with Inbound session which fails membership messaging validation will drop messages`() {
            whenever(
                networkMessagingValidator.isValidInbound(any(), any())
            ).doReturn(false)

            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(remoteIdentity, myIdentity),
                    mock<AuthenticatedSession>()
                )
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                ByteBuffer.wrap("payload".toByteArray()),
                ByteBuffer.wrap("authTag".toByteArray())
            )

            processor.onNext(
                listOf(EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0))
            )

            assertThat(publishedRecords.allValues).isEmpty()
            verify(networkMessagingValidator).isValidInbound(eq(myIdentity), eq(remoteIdentity))
        }

        @Test
        fun `AuthenticatedDataMessage with Outbound session which fails membership messaging validation will drop messages`() {
            whenever(
                networkMessagingValidator.isValidInbound(any(), any())
            ).doReturn(false)

            setupGetSessionsById(
                SessionManager.SessionDirection.Outbound(
                    SessionManager.Counterparties(remoteIdentity, myIdentity),
                    mock<AuthenticatedSession>()
                )
            )
            val dataMessage = AuthenticatedDataMessage(
                commonHeader,
                ByteBuffer.wrap("payload".toByteArray()),
                ByteBuffer.wrap("authTag".toByteArray())
            )

            processor.onNext(
                listOf(EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0))
            )

            assertThat(publishedRecords.allValues).isEmpty()
            verify(sessionManager, never()).messageAcknowledged(any())
            verify(networkMessagingValidator).isValidInbound(eq(myIdentity), eq(remoteIdentity))
        }

        @Test
        fun `AuthenticatedDataMessage with Inbound session and Link Manager subsystem will delete corresponding outbound session`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    myIdentity.toAvro(),
                    remoteIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", LINK_MANAGER_SUBSYSTEM, status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
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

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )


            verify(sessionManager).deleteOutboundSession(
                SessionManager.Counterparties(myIdentity, remoteIdentity),
                authenticatedMsg
            )
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
                    null, MESSAGE_ID, "trace-id", "system-1", status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            val records = publishedRecords.allValues.flatten()
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
        }

        @Test
        fun `receiving data message with Inbound session will not ack if the session message failed`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1", status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )
            val publishedRecords = argumentCaptor<List<Record<*, *>>>()
            whenever(
                publisher.publish(publishedRecords.capture())
            ).doReturn(listOf(CompletableFuture.failedFuture(CordaRuntimeException("Oops"))))

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(publishedRecords.allValues.flatten()).hasSize(1)
                .allMatch {
                    it.topic == P2P_IN_TOPIC
                }
        }

        @Test
        fun `receiving data message with Inbound session will produce a message on the P2P_IN_TOPIC and a response`() {
            val features = mock<Features> {
                on { enableP2PGatewayToLinkManagerOverHttp } doReturn true
            }

            val processor = InboundMessageProcessor(
                sessionManager,
                membersAndGroups.second,
                membersAndGroups.first,
                publisher,
                mockTimeFacilitiesProvider.clock,
                networkMessagingValidator,
                features,
            )
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1", status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )
            val message = object : InboundMessage {
                override val message = LinkInMessage(dataMessage)
            }

            val replies = processor.handleRequests(
                listOf(
                    message,
                )
            )

            assertSoftly { softly ->
                softly.assertThat(replies).hasSize(1)
                val record = replies.firstOrNull()?.item?.records?.firstOrNull()
                softly.assertThat(record?.topic).isEqualTo(P2P_IN_TOPIC)
                softly.assertThat(record?.key).isEqualTo("key")
                val value = record?.value as? AppMessage
                softly.assertThat(value?.message).isEqualTo(authenticatedMsg)
                val payload = replies.firstOrNull()?.item?.ack?.asLeft()?.payload as? AuthenticatedEncryptedDataMessage
                softly.assertThat(payload?.header).isEqualTo(commonHeader)
                softly.assertThat(payload?.encryptedPayload).isEqualTo(messageAndPayload.toByteBuffer())
            }
        }

        @Test
        fun `when the message's source identity does not match the one of the session the message is discarded`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    remoteIdentity.toAvro(),
                    myIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", "system-1", status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
                        myIdentity,
                        remoteIdentity,
                    ),
                    session
                )
            )

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(publishedRecords.allValues).isEmpty()
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
                    null, MESSAGE_ID, "trace-id", "system-1", status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
                        myIdentity,
                        myIdentity,
                    ),
                    session
                )
            )

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )

            assertThat(publishedRecords.allValues).isEmpty()
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
        fun `receiving data message with Inbound session that fails membership messaging validation will not produce any messages`() {
            whenever(
                networkMessagingValidator.isValidInbound(any(), any())
            ).doReturn(false)

            val dataMessage = AuthenticatedEncryptedDataMessage(
                commonHeader,
                ByteBuffer.wrap("authTag".toByteArray()),
                ByteBuffer.wrap("encryptedPayload".toByteArray())
            )

            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(remoteIdentity, myIdentity),
                    mock<AuthenticatedEncryptionSession>()
                )
            )

            processor.onNext(
                listOf(EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0))
            )

            assertThat(publishedRecords.allValues).isEmpty()
            verify(networkMessagingValidator).isValidInbound(myIdentity, remoteIdentity)
        }

        @Test
        fun `receiving data message with Outbound session that fails membership messaging validation will not produce any messages`() {
            whenever(
                networkMessagingValidator.isValidInbound(any(), any())
            ).doReturn(false)

            val dataMessage = AuthenticatedEncryptedDataMessage(
                commonHeader,
                ByteBuffer.wrap("authTag".toByteArray()),
                ByteBuffer.wrap("encryptedPayload".toByteArray())
            )

            setupGetSessionsById(
                SessionManager.SessionDirection.Outbound(
                    SessionManager.Counterparties(remoteIdentity, myIdentity),
                    mock<AuthenticatedEncryptionSession>()
                )
            )

            processor.onNext(
                listOf(EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0))
            )

            assertThat(publishedRecords.allValues).isEmpty()
            verify(sessionManager, never()).messageAcknowledged(any())
            verify(networkMessagingValidator).isValidInbound(myIdentity, remoteIdentity)
        }

        @Test
        fun `receiving data message with Inbound session and Link Manager subsystem will delete corresponding outbound session`() {
            val authenticatedMsg = AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    myIdentity.toAvro(),
                    remoteIdentity.toAvro(),
                    null, MESSAGE_ID, "trace-id", LINK_MANAGER_SUBSYSTEM, status
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
            setupGetSessionsById(
                SessionManager.SessionDirection.Inbound(
                    SessionManager.Counterparties(
                        remoteIdentity,
                        myIdentity
                    ),
                    session
                )
            )

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", LinkInMessage(dataMessage), 0, 0),
                )
            )


            verify(sessionManager).deleteOutboundSession(
                SessionManager.Counterparties(myIdentity, remoteIdentity),
                authenticatedMsg
            )
        }
    }

    @Nested
    inner class SessionMessageTests {
        @Test
        fun `ResponderHelloMessage calls to processSessionMessage`() {
            val hello = mock<InitiatorHelloMessage> {
                on { header } doReturn commonHeader
            }
            val responderHelloMessage = mock<ResponderHelloMessage>()
            val message = LinkInMessage(responderHelloMessage)
            val header =
                LinkOutHeader(myIdentity.toAvro(), remoteIdentity.toAvro(), NetworkType.CORDA_5, "https://example.com")
            val response = LinkOutMessage(header, hello)
            val captor = argumentCaptor<List<TraceableItem<LinkInMessage, LinkInMessage>>>()
            whenever(sessionManager.processSessionMessages(captor.capture(), any())).doAnswer {
                captor.firstValue.map { it to SessionManager.ProcessSessionMessagesResult(response, emptyList()) }
            }

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            assertThat(captor.firstValue.single().item).isEqualTo(message)
        }

        @Test
        fun `ResponderHandshakeMessage calls to processSessionMessage`() {
            val hello = mock<InitiatorHelloMessage> {
                on { header } doReturn commonHeader
            }
            val responderHandshakeMessage = mock<ResponderHandshakeMessage>()
            val message = LinkInMessage(responderHandshakeMessage)
            val header =
                LinkOutHeader(myIdentity.toAvro(), remoteIdentity.toAvro(), NetworkType.CORDA_5, "https://example.com")
            val response = LinkOutMessage(header, hello)
            val captor = argumentCaptor<List<TraceableItem<LinkInMessage, LinkInMessage>>>()
            whenever(sessionManager.processSessionMessages(captor.capture(), any())).doAnswer {
                captor.firstValue.map { it to SessionManager.ProcessSessionMessagesResult(response, emptyList()) }
            }

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            assertThat(captor.firstValue.single().item).isEqualTo(message)
        }

        @Test
        fun `InitiatorHandshakeMessage calls to processSessionMessage`() {
            val hello = mock<InitiatorHelloMessage> {
                on { header } doReturn commonHeader
            }
            val initiatorHandshakeMessage = mock<InitiatorHandshakeMessage>()
            val message = LinkInMessage(initiatorHandshakeMessage)
            val header =
                LinkOutHeader(myIdentity.toAvro(), remoteIdentity.toAvro(), NetworkType.CORDA_5, "https://example.com")
            val response = LinkOutMessage(header, hello)
            val captor = argumentCaptor<List<TraceableItem<LinkInMessage, LinkInMessage>>>()
            whenever(sessionManager.processSessionMessages(captor.capture(), any())).doAnswer {
                captor.firstValue.map { it to SessionManager.ProcessSessionMessagesResult(response, emptyList()) }
            }

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            assertThat(captor.firstValue.single().item).isEqualTo(message)
        }

        @Test
        fun `InitiatorHelloMessage calls to processSessionMessage`() {
            val hello = mock<InitiatorHelloMessage> {
                on { header } doReturn commonHeader
            }
            val initiatorHelloMessage = mock<InitiatorHelloMessage>()
            val message = LinkInMessage(initiatorHelloMessage)
            val header =
                LinkOutHeader(myIdentity.toAvro(), remoteIdentity.toAvro(), NetworkType.CORDA_5, "https://example.com")
            val response = LinkOutMessage(header, hello)
            val captor = argumentCaptor<List<TraceableItem<LinkInMessage, LinkInMessage>>>()
            whenever(sessionManager.processSessionMessages(captor.capture(), any())).doAnswer {
                captor.firstValue.map { it to SessionManager.ProcessSessionMessagesResult(response, emptyList()) }
            }

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            assertThat(captor.firstValue.single().item).isEqualTo(message)
        }

        @Test
        fun `null response from sessionManager will produce session creation records`() {
            val initiatorHelloMessage = mock<InitiatorHelloMessage>()
            val message = LinkInMessage(initiatorHelloMessage)
            val sessionCreated = SessionEvent(SessionCreated(SessionDirection.OUTBOUND, "test"))
            val sessionCreationRecord = mock<Record<String, *>> {
                on { topic } doReturn SESSION_EVENTS
                on { value } doReturn sessionCreated
            }
            val captor = argumentCaptor<List<TraceableItem<LinkInMessage, LinkInMessage>>>()
            whenever(sessionManager.processSessionMessages(captor.capture(), any())).doAnswer {
                captor.firstValue.map { it to SessionManager.ProcessSessionMessagesResult(null, listOf(sessionCreationRecord)) }
            }

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            val records = publishedRecords.allValues.flatten()
            assertThat(records).hasSize(1).allSatisfy {
                assertThat(it.topic).isEqualTo(SESSION_EVENTS)
                assertThat(it.value).isSameAs(sessionCreated)
            }
        }

        @Test
        fun `non null responses from sessionManager will produce link out message and session creation records`() {
            val handshake = ResponderHandshakeMessage()
            val message = LinkInMessage(handshake)
            val header = LinkOutHeader(myIdentity.toAvro(), remoteIdentity.toAvro(), NetworkType.CORDA_5, "https://example.com")
            val sessionCreated = SessionEvent(SessionCreated(SessionDirection.OUTBOUND, "test"))
            val sessionCreationRecord = mock<Record<String, *>> {
                on { topic } doReturn SESSION_EVENTS
                on { value } doReturn sessionCreated
            }
            val response = SessionManager.ProcessSessionMessagesResult(LinkOutMessage(header, handshake), listOf(sessionCreationRecord))
            val captor = argumentCaptor<List<TraceableItem<LinkInMessage, LinkInMessage>>>()
            whenever(sessionManager.processSessionMessages(captor.capture(), any())).doAnswer {
                captor.firstValue.map { it to response }
            }

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            val records = publishedRecords.allValues.flatten()
            val result = records.associate { it.topic to it.value }
            assertThat(result).hasSize(2)
            assertThat(result.keys).containsExactlyInAnyOrder(LINK_OUT_TOPIC, SESSION_EVENTS)
            assertThat(result.values).containsExactlyInAnyOrder(response.message, sessionCreated)
        }

        @Test
        fun `InitiatorHelloMessage responses from sessionManager will produce records to the correct topics`() {
            val hello = mock<InitiatorHelloMessage> {
                on { header } doReturn commonHeader
            }
            val responderHello = mock<ResponderHelloMessage> {
                on { header } doReturn commonHeader
            }
            val message = LinkInMessage(hello)
            val header = LinkOutHeader(myIdentity.toAvro(), remoteIdentity.toAvro(), NetworkType.CORDA_5, "https://example.com")
            val sessionCreated = SessionEvent(SessionCreated(SessionDirection.OUTBOUND, "test"))
            val sessionCreationRecord = mock<Record<String, *>> {
                on { topic } doReturn SESSION_EVENTS
                on { value } doReturn sessionCreated
            }
            val response = SessionManager.ProcessSessionMessagesResult(
                LinkOutMessage(header, responderHello), listOf(sessionCreationRecord)
            )
            val captor = argumentCaptor<List<TraceableItem<LinkInMessage, LinkInMessage>>>()
            whenever(sessionManager.processSessionMessages(captor.capture(), any())).doAnswer {
                captor.firstValue.map { it to response }
            }

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            val records = publishedRecords.allValues.flatten()
            val result = records.associate { it.topic to it.value }
            assertThat(result).hasSize(2)
            assertThat(result.keys).containsExactlyInAnyOrder(LINK_OUT_TOPIC, SESSION_EVENTS)
            assertThat(result.values).containsExactlyInAnyOrder(response.message, sessionCreated)
        }
    }

    @Nested
    inner class InboundUnauthenticatedMessageTests {
        @Test
        fun `InboundUnauthenticatedMessage will produce message in P2P in topic`() {
            val inboundUnauthenticatedMessageHeader = mock<InboundUnauthenticatedMessageHeader> {
                on { messageId } doReturn "messageId"
                on { subsystem } doReturn "application-v1"
            }
            val inboundUnauthenticatedMessage = mock<InboundUnauthenticatedMessage> {
                on { header } doReturn inboundUnauthenticatedMessageHeader
            }
            val message = LinkInMessage(inboundUnauthenticatedMessage)

            processor.onNext(
                listOf(
                    EventLogRecord(LINK_IN_TOPIC, "key", message, 0, 0),
                )
            )

            val records = publishedRecords.allValues.flatten()
            assertThat(records).hasSize(1).anySatisfy {
                assertThat(it.topic).isEqualTo(P2P_IN_TOPIC)
                assertThat(it.value).isInstanceOf(AppMessage::class.java)
                assertThat((it.value as AppMessage).message).isEqualTo(inboundUnauthenticatedMessage)
            }
        }
    }
}
