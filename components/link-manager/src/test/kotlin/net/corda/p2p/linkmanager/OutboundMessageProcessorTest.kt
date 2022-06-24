package net.corda.p2p.linkmanager

import net.corda.data.identity.HoldingIdentity
import net.corda.messaging.api.records.EventLogRecord
import net.corda.p2p.AuthenticatedMessageAndKey
import net.corda.p2p.LinkOutMessage
import net.corda.p2p.SessionPartitions
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.AuthenticatedMessageHeader
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessageHeader
import net.corda.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.mockMembersAndGroups
import net.corda.p2p.markers.AppMessageMarker
import net.corda.p2p.markers.LinkManagerReceivedMarker
import net.corda.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.markers.LinkManagerProcessedMarker
import net.corda.p2p.markers.TtlExpiredMarker
import net.corda.schema.Schemas
import net.corda.test.util.MockTimeFacilitiesProvider
import net.corda.v5.base.util.seconds
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class OutboundMessageProcessorTest {
    private val myIdentity = HoldingIdentity("PartyA", "Group")
    private val localIdentity = HoldingIdentity("PartyB", "Group")
    private val remoteIdentity = HoldingIdentity("PartyC", "Group")
    private val membersAndGroups = mockMembersAndGroups(
        myIdentity, localIdentity, remoteIdentity
    )
    private val hostingMap = mock<LinkManagerHostingMap> {
        whenever(it.isHostedLocally(myIdentity)).thenReturn(true)
        whenever(it.isHostedLocally(localIdentity)).thenReturn(true)
    }
    private val assignedListener = mock<InboundAssignmentListener> {
        on { getCurrentlyAssignedPartitions() } doReturn setOf(1)
    }
    private val mockTimeFacilitiesProvider = MockTimeFacilitiesProvider()
    private val sessionManager = mock<SessionManager>()
    private val messagesPendingSession = mock<PendingSessionMessageQueues>()
    private val authenticationResult = mock<AuthenticationResult> {
        on { mac } doReturn byteArrayOf()
    }
    private val authenticatedSession = mock<AuthenticatedSession> {
        on { createMac(any()) } doReturn authenticationResult
    }

    private val processor = OutboundMessageProcessor(
        sessionManager,
        hostingMap,
        membersAndGroups.second,
        membersAndGroups.first,
        assignedListener,
        messagesPendingSession,
        mockTimeFacilitiesProvider.clock,
    )

    @Test
    fun `if destination identity is hosted locally, authenticated messages are looped back and immediately acknowledged`() {
        val payload = "test"
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                myIdentity,
                localIdentity,
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap(payload.toByteArray())
        )
        val appMessage = AppMessage(authenticatedMsg)

        val records = processor.onNext(
            listOf(
                EventLogRecord(
                    Schemas.P2P.P2P_OUT_TOPIC,
                    "key",
                    appMessage, 1, 0
                )
            )
        )

        assertSoftly { softAssertions ->
            softAssertions.assertThat(records).hasSize(3)
            val markers = records.filter { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.map { it.value }.filterIsInstance<AppMessageMarker>()
            softAssertions.assertThat(markers).hasSize(2)

            val processedMarkers = markers.map { it.marker }.filterIsInstance<LinkManagerProcessedMarker>()
            softAssertions.assertThat(processedMarkers).hasSize(1)
            softAssertions.assertThat(processedMarkers.first().message.message).isSameAs(authenticatedMsg)
            softAssertions.assertThat(processedMarkers.first().message.key).isEqualTo("key")

            val receivedMarkers = markers.map { it.marker }.filterIsInstance<LinkManagerReceivedMarker>()
            softAssertions.assertThat(receivedMarkers).hasSize(1)

            val messages = records
                .filter {
                    it.topic == Schemas.P2P.P2P_IN_TOPIC
                }.filter {
                    it.key == "key"
                }.map { it.value }.filterIsInstance<AppMessage>()
            softAssertions.assertThat(messages).hasSize(1)
            softAssertions.assertThat(messages.first()).isEqualTo(appMessage)
        }
    }

    @Test
    fun `if destination identity is hosted locally, replaying an authenticated messages results in no records`() {
        val payload = "test"
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                myIdentity,
                localIdentity,
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap(payload.toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
            authenticatedMsg,
            "key"
        )

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)
        assertThat(records).isEmpty()
    }

    @Test
    fun `if destination identity is hosted locally, unauthenticated messages are looped back`() {
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                myIdentity,
                localIdentity,
                "subsystem"
            ),
            ByteBuffer.wrap(payload.toByteArray())
        )
        val appMessage = AppMessage(unauthenticatedMsg)

        val records = processor.onNext(
            listOf(
                EventLogRecord(
                    Schemas.P2P.P2P_OUT_TOPIC,
                    "key", appMessage, 1, 0
                )
            )
        )

        assertThat(records).hasSize(1).allMatch {
            it.topic == Schemas.P2P.P2P_IN_TOPIC
        }.allMatch {
            it.value == appMessage
        }
    }

    @Test
    fun `onNext forwards unauthenticated messages directly to link out topic`() {
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                remoteIdentity,
                myIdentity,
                "subsystem"
            ),
            ByteBuffer.wrap(payload.toByteArray()),
        )
        val appMessage = AppMessage(unauthenticatedMsg)

        val records = processor.onNext(
            listOf(
                EventLogRecord(
                    Schemas.P2P.P2P_OUT_TOPIC,
                    "key",
                    appMessage,
                    1,
                    0
                )
            )
        )

        assertThat(records).hasSize(1).allMatch {
            it.topic == Schemas.P2P.LINK_OUT_TOPIC
        }.allMatch {
            (it.value as? LinkOutMessage)?.payload == unauthenticatedMsg
        }
    }

    @Test
    fun `onNext produces only a LinkManagerProcessed marker (per flowMessage) if SessionAlreadyPending`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val numberOfMessages = 3
        val messages = (1..numberOfMessages).map { i ->
            val header = AuthenticatedMessageHeader(
                remoteIdentity,
                myIdentity,
                null,
                "MessageId$i",
                "trace-$i",
                "system"

            )
            val message = AuthenticatedMessage(header, ByteBuffer.wrap("$i".toByteArray()))

            EventLogRecord(
                Schemas.P2P.P2P_OUT_TOPIC,
                "key",
                AppMessage(message), 0, 1
            )
        }

        val records = processor.onNext(messages)

        assertThat(records).hasSize(numberOfMessages)
            .allMatch {
                it.topic == Schemas.P2P.P2P_OUT_MARKERS
            }.allMatch {
                (it.value as? AppMessageMarker)?.marker is LinkManagerProcessedMarker
            }.extracting("key")
            .isEqualTo((1..numberOfMessages).map { "MessageId$it" })
    }

    @Test
    fun `onNext queue messages if SessionAlreadyPending`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val numberOfMessages = 3
        val messages = (1..numberOfMessages).map { i ->
            val header = AuthenticatedMessageHeader(
                remoteIdentity,
                myIdentity,
                null,
                "MessageId$i",
                "trace-$i",
                "system"

            )
            val message = AuthenticatedMessage(header, ByteBuffer.wrap("$i".toByteArray()))

            EventLogRecord(
                Schemas.P2P.P2P_OUT_TOPIC,
                "key",
                AppMessage(message), 0, 1
            )
        }

        processor.onNext(messages)

        messages.forEach {
            verify(messagesPendingSession)
                .queueMessage(AuthenticatedMessageAndKey(it.value?.message as AuthenticatedMessage?, it.key))
        }
    }

    @Test
    fun `processReplayedAuthenticatedMessage produces no records and queues no messages if SessionAlreadyPending`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity,
                localIdentity,
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
            authenticatedMsg,
            "key"
        )

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        assertThat(records).isEmpty()
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `onNext produces session init messages, a LinkManagerProcessed marker and lists of partitions if NewSessionsNeeded`() {
        val firstSessionInitMessage = mock<LinkOutMessage>()
        val secondSessionInitMessage = mock<LinkOutMessage>()
        val state = SessionManager.SessionState.NewSessionsNeeded(
            listOf(
                "session-id" to firstSessionInitMessage,
                "another-session-id" to secondSessionInitMessage
            )
        )
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val inboundSubscribedTopics = setOf(1, 5, 9)
        whenever(assignedListener.getCurrentlyAssignedPartitions()).doReturn(inboundSubscribedTopics)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity,
                localIdentity,
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val appMessage = AppMessage(authenticatedMsg)
        val messages = listOf(EventLogRecord(Schemas.P2P.P2P_OUT_TOPIC, "key", appMessage, 0, 0))

        val records = processor.onNext(messages)

        assertSoftly { softly ->
            softly.assertThat(records).hasSize(2 * state.messages.size + messages.size)
            softly.assertThat(records)
                .filteredOn { it.topic == Schemas.P2P.LINK_OUT_TOPIC }
                .hasSize(state.messages.size)
                .extracting<LinkOutMessage> { it.value as LinkOutMessage }
                .containsExactlyInAnyOrderElementsOf(listOf(firstSessionInitMessage, secondSessionInitMessage))
            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.SESSION_OUT_PARTITIONS }
                .hasSize(state.messages.size)
                .extracting<SessionPartitions> { it.value as SessionPartitions }
                .allSatisfy {
                    assertThat(it.partitions.toIntArray())
                        .isEqualTo(inboundSubscribedTopics.toIntArray())
                }
            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.SESSION_OUT_PARTITIONS }
                .hasSize(state.messages.size)
                .extracting<String> { it.key as String }.containsExactlyInAnyOrder(
                    "session-id",
                    "another-session-id"
                )
            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.P2P_OUT_MARKERS }
                .hasSize(messages.size)
                .allSatisfy { assertThat(it.key).isEqualTo("message-id") }
                .extracting<AppMessageMarker> { it.value as AppMessageMarker }
                .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerProcessedMarker::class.java) }
        }
    }

    @Test
    fun `processReplayedAuthenticatedMessage produces the correct records if NewSessionsNeeded`() {
        val firstSessionInitMessage = mock<LinkOutMessage>()
        val secondSessionInitMessage = mock<LinkOutMessage>()
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(
            SessionManager.SessionState.NewSessionsNeeded(
                listOf(
                    "session-id" to firstSessionInitMessage,
                    "another-session-id" to secondSessionInitMessage
                )
            )
        )
        val inboundSubscribedTopics = setOf(1, 5, 9)
        whenever(assignedListener.getCurrentlyAssignedPartitions()).doReturn(inboundSubscribedTopics)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity,
                localIdentity,
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(authenticatedMsg, "key")

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        assertSoftly { softly ->
            softly.assertThat(records).hasSize(4)
            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.LINK_OUT_TOPIC }.hasSize(2)
                .extracting<LinkOutMessage> { it.value as LinkOutMessage }
                .containsExactlyInAnyOrderElementsOf(listOf(firstSessionInitMessage, secondSessionInitMessage))
            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.SESSION_OUT_PARTITIONS }.hasSize(2)
                .extracting<SessionPartitions> { it.value as SessionPartitions }
                .allSatisfy { assertThat(it.partitions.toIntArray()).isEqualTo(inboundSubscribedTopics.toIntArray()) }
        }
    }

    @Test
    fun `processReplayedAuthenticatedMessage will not add to queue if NewSessionsNeeded`() {
        val firstSessionInitMessage = mock<LinkOutMessage>()
        val secondSessionInitMessage = mock<LinkOutMessage>()
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(
            SessionManager.SessionState.NewSessionsNeeded(
                listOf(
                    "session-id" to firstSessionInitMessage,
                    "another-session-id" to secondSessionInitMessage
                )
            )
        )
        val inboundSubscribedTopics = setOf(1, 5, 9)
        whenever(assignedListener.getCurrentlyAssignedPartitions()).doReturn(inboundSubscribedTopics)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity,
                localIdentity,
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(authenticatedMsg, "key")

        processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `onNext produces a LinkOutMessage and a LinkManagerProcessedMarker per message if SessionEstablished`() {
        val state = SessionManager.SessionState.SessionEstablished(authenticatedSession)
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val messageIds = (1..3).map { i ->
            "Id$i"
        }
        val messages = messageIds.map { id ->
            EventLogRecord(
                Schemas.P2P.P2P_OUT_TOPIC,
                "key",
                AppMessage(
                    AuthenticatedMessage(
                        AuthenticatedMessageHeader(
                            remoteIdentity,
                            myIdentity,
                            null, id, "trace-id", "system-1"
                        ),
                        ByteBuffer.wrap(id.toByteArray())
                    )
                ),
                0, 0
            )
        }

        val records = processor.onNext(messages)

        assertSoftly { softly ->
            softly.assertThat(records).hasSize(2 * messages.size)

            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.LINK_OUT_TOPIC }.hasSize(messages.size)
                .extracting<LinkOutMessage> { it.value as LinkOutMessage }
                .allSatisfy { assertThat(it.payload).isInstanceOf(AuthenticatedDataMessage::class.java) }

            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.hasSize(messages.size)
                .extracting<AppMessageMarker> { it.value as AppMessageMarker }
                .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerProcessedMarker::class.java) }

            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.extracting<String> { it.key as String }
                .containsExactlyElementsOf(messageIds)
        }

        verify(sessionManager, times(messages.size)).dataMessageSent(state.session)
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `processReplayedAuthenticatedMessage produces a LinkOutMessage and doesn't queue messages if SessionEstablished`() {
        val state = SessionManager.SessionState.SessionEstablished(authenticatedSession)
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity,
                localIdentity,
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("0".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
            authenticatedMsg,
            "key"
        )

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        assertThat(records)
            .hasSize(1)
            .filteredOn { it.topic == Schemas.P2P.LINK_OUT_TOPIC }
            .extracting<LinkOutMessage> { it.value as LinkOutMessage }
            .allSatisfy { assertThat(it.payload).isInstanceOf(AuthenticatedDataMessage::class.java) }

        verify(sessionManager, times(1)).dataMessageSent(state.session)
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `onNext produces only a LinkManagerProcessedMarker if CannotEstablishSession`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.CannotEstablishSession)
        val messages = listOf(
            EventLogRecord(
                Schemas.P2P.P2P_OUT_TOPIC,
                "key",
                AppMessage(
                    AuthenticatedMessage(
                        AuthenticatedMessageHeader(
                            remoteIdentity,
                            localIdentity,
                            null, "message-id", "trace-id", "system-1"
                        ),
                        ByteBuffer.wrap("payload".toByteArray())
                    )
                ),
                0, 1
            )
        )

        val records = processor.onNext(messages)

        assertThat(records).hasSize(messages.size)

        assertThat(records).filteredOn { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isEqualTo("message-id") }
            .extracting<AppMessageMarker> { it.value as AppMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerProcessedMarker::class.java) }
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `processReplayedAuthenticatedMessage doesn't queue messages when CannotEstablishSession`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.CannotEstablishSession)
        val records = processor.processReplayedAuthenticatedMessage(
            AuthenticatedMessageAndKey(
                AuthenticatedMessage(
                    AuthenticatedMessageHeader(
                        remoteIdentity,
                        localIdentity,
                        null, "message-id", "trace-id", "system-1"
                    ),
                    ByteBuffer.wrap("payload".toByteArray())
                ),
                "key"
            )
        )

        assertThat(records).isEmpty()
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `onNext produces only a LinkManagerProcessedMarker if SessionEstablished and receiver is not in the network map`() {
        val state = SessionManager.SessionState.SessionEstablished(authenticatedSession)
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val appMessage = AppMessage(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    HoldingIdentity("PartyE", "Group"),
                    localIdentity,
                    null, "message-id", "trace-id", "system-1"
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
        )
        val messages = listOf(EventLogRecord(Schemas.P2P.P2P_OUT_TOPIC, "key", appMessage, 0, 0))

        val records = processor.onNext(messages)

        assertThat(records).hasSize(messages.size)

        assertThat(records).filteredOn { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.hasSize(messages.size)
            .allSatisfy { assertThat(it.key).isEqualTo("message-id") }
            .extracting<AppMessageMarker> { it.value as AppMessageMarker }
            .allSatisfy { assertThat(it.marker).isInstanceOf(LinkManagerProcessedMarker::class.java) }
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `processReplayedAuthenticatedMessage gives TtlExpiredMarker, LinkManagerSentMarker if TTL expiry true, replay true`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity,
                localIdentity,
                null, "MessageId", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
            authenticatedMsg,
            "key"
        )
        authenticatedMessageAndKey.message.header.ttl = 0L

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        val markers = records.filter { it.value is AppMessageMarker }
        assertSoftly {
            it.assertThat(markers.map { it.key }).allMatch {
                it.equals("MessageId")
            }
            it.assertThat(markers).hasSize(2)
            it.assertThat(markers.map { it.value as AppMessageMarker }.filter { it.marker is LinkManagerSentMarker }).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }.filter { it.marker is TtlExpiredMarker }).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }.filter { it.marker is LinkManagerReceivedMarker }).isEmpty()
            it.assertThat(markers.map { it.topic }.distinct()).containsOnly(Schemas.P2P.P2P_OUT_MARKERS)
        }
    }

    @Test
    fun `OutboundMessageProcessor produces TtlExpiredMarker and LinkManagerProcessedMarker if TTL expiry is true and replay is false`() {
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity,
                localIdentity,
                0, "MessageId", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        mockTimeFacilitiesProvider.advanceTime(20.seconds)
        val appMessage = AppMessage(authenticatedMsg)
        val messages = listOf(EventLogRecord(Schemas.P2P.P2P_OUT_TOPIC, "key", appMessage, 0, 0))

        val records = processor.onNext(messages)

        val markers = records.filter { it.value is AppMessageMarker }
        assertSoftly {
            it.assertThat(markers.map { it.key }).allMatch {
                it.equals("MessageId")
            }
            it.assertThat(markers).hasSize(2)
            it.assertThat(markers.map { it.value as AppMessageMarker }.filter { it.marker is LinkManagerProcessedMarker }).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }.filter { it.marker is TtlExpiredMarker }).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }.filter { it.marker is LinkManagerReceivedMarker }).isEmpty()
            it.assertThat(markers.map { it.topic }.distinct()).containsOnly(Schemas.P2P.P2P_OUT_MARKERS)
        }
    }

    @Test
    fun `processReplayedAuthenticatedMessage produces LinkManagerSentMarker if TTL expiry is false and replay is true`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity,
                localIdentity,
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
            authenticatedMsg,
            "key"
        )

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        assertThat(records).allSatisfy { record ->
            assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_MARKERS)
            assertThat(record.value).isInstanceOf(AppMessageMarker::class.java)
            val marker = record.value as AppMessageMarker
            assertThat(marker.marker).isInstanceOf(LinkManagerSentMarker::class.java)
        }
    }
}
