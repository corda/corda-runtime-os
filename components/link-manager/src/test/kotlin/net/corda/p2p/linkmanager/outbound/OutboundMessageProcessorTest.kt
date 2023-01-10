package net.corda.p2p.linkmanager.outbound

import net.corda.data.identity.HoldingIdentity
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.messaging.api.records.EventLogRecord
import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.SessionPartitions
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.data.p2p.crypto.AuthenticatedDataMessage
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.linkmanager.hosting.LinkManagerHostingMap
import net.corda.p2p.linkmanager.inbound.InboundAssignmentListener
import net.corda.p2p.linkmanager.sessions.PendingSessionMessageQueues
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.utilities.mockMembersAndGroups
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerDiscardedMarker
import net.corda.data.p2p.markers.LinkManagerReceivedMarker
import net.corda.data.p2p.markers.LinkManagerSentMarker
import net.corda.data.p2p.markers.LinkManagerProcessedMarker
import net.corda.data.p2p.markers.TtlExpiredMarker
import net.corda.schema.Schemas
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.v5.base.util.seconds
import net.corda.virtualnode.toAvro
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
import java.time.Instant

class OutboundMessageProcessorTest {
    private val myIdentity = createTestHoldingIdentity("CN=PartyA, O=Corp, L=LDN, C=GB", "Group")
    private val localIdentity = createTestHoldingIdentity("CN=PartyB, O=Corp, L=LDN, C=GB", "Group")
    private val remoteIdentity = createTestHoldingIdentity("CN=PartyC, O=Corp, L=LDN, C=GB", "Group")
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
    fun `authenticated messages are dropped when source and destination identities are in different groups`() {
        val payload = "test"
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                myIdentity.toAvro(),
                localIdentity.toAvro(),
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
            val markers = records.filter { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.map { it.value }
                .filterIsInstance<AppMessageMarker>()
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
    fun `authenticated messages are dropped if source and destination are in different groups`() {
        val payload = "test"
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                myIdentity.copy(groupId = "Group-other").toAvro(),
                localIdentity.toAvro(),
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

        assertThat(records.filter { it.topic == Schemas.P2P.P2P_IN_TOPIC }).isEmpty()
        val markers = records.filter { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.map { it.value }
            .filterIsInstance<AppMessageMarker>()
        val discardedMarkers = markers.map { it.marker }.filterIsInstance<LinkManagerDiscardedMarker>()
        assertThat(discardedMarkers).hasSize(1)
        assertThat(discardedMarkers.single().message.message).isEqualTo(appMessage.message)
    }

    @Test
    fun `authenticated messages are dropped if source X500 name is invalid`() {
        val payload = "test"
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                myIdentity.toAvro(),
                HoldingIdentity(
                    "Invalid X500 name",
                    myIdentity.groupId,
                ),
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

        assertThat(records)
            .hasSize(1)
            .allSatisfy { record ->
                assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_MARKERS)
            }.allSatisfy { record ->
                val value = record.value as? AppMessageMarker
                val marker = value?.marker as? LinkManagerDiscardedMarker
                assertThat(marker?.reason).contains("source 'Invalid X500 name' is not a valid X500 name")
            }
    }

    @Test
    fun `authenticated messages are dropped if destination X500 name is invalid`() {
        val payload = "test"
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                HoldingIdentity(
                    "Invalid X500 name",
                    myIdentity.groupId,
                ),
                remoteIdentity.toAvro(),
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

        assertThat(records)
            .hasSize(1)
            .allSatisfy { record ->
                assertThat(record.topic).isEqualTo(Schemas.P2P.P2P_OUT_MARKERS)
            }.allSatisfy { record ->
                val value = record.value as? AppMessageMarker
                val marker = value?.marker as? LinkManagerDiscardedMarker
                assertThat(marker?.reason).contains("destination 'Invalid X500 name' is not a valid X500 name")
            }
    }

    @Test
    fun `if destination identity is hosted locally, unauthenticated messages are looped back`() {
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                myIdentity.toAvro(),
                localIdentity.toAvro(),
                "subsystem",
                "messageId",
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
                remoteIdentity.toAvro(),
                myIdentity.toAvro(),
                "subsystem",
                "messageId",
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
    fun `unauthenticated messages are dropped if source is invalid X500 name`() {
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                remoteIdentity.toAvro(),
                HoldingIdentity(
                    "Invalid name",
                    remoteIdentity.groupId,
                ),
                "subsystem",
                "messageId",
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

        assertThat(records).isEmpty()
    }

    @Test
    fun `unauthenticated messages are dropped if destination is invalid X500 name`() {
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                HoldingIdentity(
                    "Invalid name",
                    myIdentity.groupId,
                ),
                myIdentity.toAvro(),
                "subsystem",
                "messageId",
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

        assertThat(records).isEmpty()
    }

    @Test
    fun `unauthenticated messages are dropped if source and destination are in different groups`() {
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                remoteIdentity.copy(groupId = "Group-other").toAvro(),
                myIdentity.toAvro(),
                "subsystem",
                "messageId",
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

        assertThat(records).isEmpty()
    }

    @Test
    fun `unauthenticated messages are dropped if destination identity is not in the members map or locally hosted`() {
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                HoldingIdentity("CN=PartyE, O=Corp, L=LDN, C=GB", "Group"),
                myIdentity.toAvro(),
                "subsystem",
                "messageId",
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

        assertThat(records).isEmpty()
    }

    @Test
    fun `unauthenticated messages are dropped if group info is not available`() {
        val groupPolicyProvider = mock<GroupPolicyProvider> {
            on { getGroupPolicy(localIdentity) } doReturn null
        }

        val processor = OutboundMessageProcessor(
            sessionManager,
            hostingMap,
            groupPolicyProvider,
            membersAndGroups.first,
            assignedListener,
            messagesPendingSession,
            mockTimeFacilitiesProvider.clock,
        )

        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                remoteIdentity.toAvro(),
                myIdentity.toAvro(),
                "subsystem",
                "messageId",
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

        assertThat(records).isEmpty()
    }

    @Test
    fun `onNext produces only a LinkManagerProcessed marker (per flowMessage) if SessionAlreadyPending`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val numberOfMessages = 3
        val messages = (1..numberOfMessages).map { i ->
            val header = AuthenticatedMessageHeader(
                remoteIdentity.toAvro(),
                myIdentity.toAvro(),
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
                remoteIdentity.toAvro(),
                myIdentity.toAvro(),
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
                remoteIdentity.toAvro(),
                localIdentity.toAvro(),
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
                remoteIdentity.toAvro(),
                localIdentity.toAvro(),
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
                remoteIdentity.toAvro(),
                localIdentity.toAvro(),
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
                remoteIdentity.toAvro(),
                localIdentity.toAvro(),
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(authenticatedMsg, "key")

        processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `processReplayedAuthenticatedMessage will loop back message if destination is locally hosted`() {
        val payload = "test"
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                myIdentity.toAvro(),
                localIdentity.toAvro(),
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap(payload.toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(authenticatedMsg, "key")

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        assertSoftly { softAssertions ->
            softAssertions.assertThat(records).hasSize(2)
            val markers = records.filter { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.map { it.value }
                .filterIsInstance<AppMessageMarker>()
            softAssertions.assertThat(markers).hasSize(1)

            val receivedMarkers = markers.map { it.marker }.filterIsInstance<LinkManagerReceivedMarker>()
            softAssertions.assertThat(receivedMarkers).hasSize(1)

            val messages = records
                .filter {
                    it.topic == Schemas.P2P.P2P_IN_TOPIC
                }.filter {
                    it.key == "key"
                }.map { it.value }.filterIsInstance<AppMessage>()
            softAssertions.assertThat(messages).hasSize(1)
            softAssertions.assertThat(messages.first().message).isEqualTo(authenticatedMessageAndKey.message)
        }
    }

    @Test
    fun `processReplayedAuthenticatedMessage will not write any records if destination is not in the members map or locally hosted`() {
        val state = SessionManager.SessionState.SessionEstablished(authenticatedSession)
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val authenticatedMessage = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                HoldingIdentity("CN=PartyE, O=Corp, L=LDN, C=GB", "Group"),
                localIdentity.toAvro(),
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(authenticatedMessage, "key")

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)
        assertThat(records).isEmpty()
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
                            remoteIdentity.toAvro(),
                            myIdentity.toAvro(),
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
            softly.assertThat(records).hasSize(9)

            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.LINK_OUT_TOPIC }.hasSize(messages.size)
                .extracting<LinkOutMessage> { it.value as LinkOutMessage }
                .allSatisfy { assertThat(it.payload).isInstanceOf(AuthenticatedDataMessage::class.java) }

            val markers = records.filter { it.value is AppMessageMarker }
                softly.assertThat(markers).hasSize(6)
                softly.assertThat(markers.map { it.value as AppMessageMarker }
                    .filter { it.marker is LinkManagerProcessedMarker }).hasSize(3)
                softly.assertThat(markers.map { it.value as AppMessageMarker }
                    .filter { it.marker is LinkManagerSentMarker }).hasSize(3)
                softly.assertThat(markers.map { it.topic }.distinct()).containsOnly(Schemas.P2P.P2P_OUT_MARKERS)
        }

        verify(sessionManager, times(messages.size)).dataMessageSent(state.session)
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `processReplayedAuthenticatedMessage produces a LinkOutMessage and SentMarker and doesn't queue messages if SessionEstablished`() {
        val state = SessionManager.SessionState.SessionEstablished(authenticatedSession)
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity.toAvro(),
                localIdentity.toAvro(),
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("0".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
            authenticatedMsg,
            "key"
        )

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        assertSoftly { softly ->
            softly.assertThat(records).hasSize(2)
            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.LINK_OUT_TOPIC }.hasSize(1)
                .extracting<LinkOutMessage> { it.value as LinkOutMessage }
                .allSatisfy { assertThat(it.payload).isInstanceOf(AuthenticatedDataMessage::class.java) }
            softly.assertThat(records).filteredOn { it.topic == Schemas.P2P.P2P_OUT_MARKERS }.hasSize(1)
                .extracting<AppMessageMarker> { it.value as AppMessageMarker }
                .allSatisfy {
                    assertThat(it.marker).isInstanceOf(LinkManagerSentMarker::class.java)
                }
        }

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
                            remoteIdentity.toAvro(),
                            localIdentity.toAvro(),
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
                        remoteIdentity.toAvro(),
                        localIdentity.toAvro(),
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
    fun `onNext produces only a LinkManagerProcessedMarker if destination is not in the network map or locally hosted`() {
        val state = SessionManager.SessionState.SessionEstablished(authenticatedSession)
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val appMessage = AppMessage(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    HoldingIdentity("CN=PartyE, O=Corp, L=LDN, C=GB", "Group"),
                    localIdentity.toAvro(),
                    null, "message-id", "trace-id", "system-1"
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
        )
        val messages = listOf(EventLogRecord(Schemas.P2P.P2P_OUT_TOPIC, "key", appMessage, 0, 0))

        val records = processor.onNext(messages)

        assertThat(records).hasSize(1)

        val markers = records.filter { it.value is AppMessageMarker }
        assertSoftly {
            it.assertThat(markers.map { it.key }).allMatch {
                it.equals("message-id")
            }
            it.assertThat(markers).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }
                .filter { it.marker is LinkManagerProcessedMarker }).hasSize(1)
            it.assertThat(markers.map { it.topic }.distinct()).containsOnly(Schemas.P2P.P2P_OUT_MARKERS)
        }
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `processReplayedAuthenticatedMessage gives TtlExpiredMarker if TTL expiry true and replay true`() {
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(SessionManager.SessionState.SessionAlreadyPending)
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity.toAvro(),
                localIdentity.toAvro(),
                null, "MessageId", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(
            authenticatedMsg,
            "key"
        )
        authenticatedMessageAndKey.message.header.ttl = Instant.ofEpochMilli(0)

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        val markers = records.filter { it.value is AppMessageMarker }
        assertSoftly {
            it.assertThat(markers.map { it.key }).allMatch {
                it.equals("MessageId")
            }
            it.assertThat(markers).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }.filter { it.marker is TtlExpiredMarker })
                .hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }
                .filter { it.marker is LinkManagerReceivedMarker }).isEmpty()
            it.assertThat(markers.map { it.topic }.distinct()).containsOnly(Schemas.P2P.P2P_OUT_MARKERS)
        }
    }

    @Test
    fun `OutboundMessageProcessor produces TtlExpiredMarker and LinkManagerProcessedMarker if TTL expiry is true and replay is false`() {
        val authenticatedMsg = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                remoteIdentity.toAvro(),
                localIdentity.toAvro(),
                Instant.ofEpochMilli(0), "MessageId", "trace-id", "system-1"
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
            it.assertThat(markers.map { it.value as AppMessageMarker }
                .filter { it.marker is LinkManagerProcessedMarker }).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }.filter { it.marker is TtlExpiredMarker })
                .hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }
                .filter { it.marker is LinkManagerReceivedMarker }).isEmpty()
            it.assertThat(markers.map { it.topic }.distinct()).containsOnly(Schemas.P2P.P2P_OUT_MARKERS)
        }
    }

    @Test
    fun `onNext produces only a LinkManagerDiscardedMarker if source ID is not locally hosted`() {
        val state = SessionManager.SessionState.SessionEstablished(authenticatedSession)
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val appMessage = AppMessage(
            AuthenticatedMessage(
                AuthenticatedMessageHeader(
                    HoldingIdentity("CN=PartyC, O=Corp, L=LDN, C=GB", "Group"),
                    HoldingIdentity("CN=PartyE, O=Corp, L=LDN, C=GB", "Group"),
                    null, "message-id", "trace-id", "system-1"
                ),
                ByteBuffer.wrap("payload".toByteArray())
            )
        )
        val messages = listOf(EventLogRecord(Schemas.P2P.P2P_OUT_TOPIC, "key", appMessage, 0, 0))

        val records = processor.onNext(messages)

        assertThat(records).hasSize(1)

        val markers = records.filter { it.value is AppMessageMarker }
        assertSoftly {
            it.assertThat(markers.map { it.key }).allMatch {
                it.equals("message-id")
            }
            it.assertThat(markers).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }
                .filter { it.marker is LinkManagerDiscardedMarker }).hasSize(1)
            it.assertThat(markers.map { it.topic }.distinct()).containsOnly(Schemas.P2P.P2P_OUT_MARKERS)
        }
        verify(messagesPendingSession, never()).queueMessage(any())
    }

    @Test
    fun `unauthenticated messages are dropped if source ID is not locally hosted`() {
        val payload = "test"
        val unauthenticatedMsg = UnauthenticatedMessage(
            UnauthenticatedMessageHeader(
                HoldingIdentity("CN=PartyC, O=Corp, L=LDN, C=GB", "Group"),
                HoldingIdentity("CN=PartyE, O=Corp, L=LDN, C=GB", "Group"),
                "subsystem",
                "messageId",
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

        assertThat(records).isEmpty()
    }

    @Test
    fun `processReplayedAuthenticatedMessage produces only a LinkManagerDiscardedMarker if source ID is not locally hosted`() {
        val state = SessionManager.SessionState.SessionEstablished(authenticatedSession)
        whenever(sessionManager.processOutboundMessage(any())).thenReturn(state)
        val authenticatedMessage = AuthenticatedMessage(
            AuthenticatedMessageHeader(
                HoldingIdentity("CN=PartyC, O=Corp, L=LDN, C=GB", "Group"),
                HoldingIdentity("CN=PartyE, O=Corp, L=LDN, C=GB", "Group"),
                null, "message-id", "trace-id", "system-1"
            ),
            ByteBuffer.wrap("payload".toByteArray())
        )
        val authenticatedMessageAndKey = AuthenticatedMessageAndKey(authenticatedMessage, "key")

        val records = processor.processReplayedAuthenticatedMessage(authenticatedMessageAndKey)

        assertThat(records).hasSize(1)
        val markers = records.filter { it.value is AppMessageMarker }
        assertSoftly {
            it.assertThat(markers.map { it.key }).allMatch {
                it.equals("message-id")
            }
            it.assertThat(markers).hasSize(1)
            it.assertThat(markers.map { it.value as AppMessageMarker }
                .filter { it.marker is LinkManagerDiscardedMarker }).hasSize(1)
            it.assertThat(markers.map { it.topic }.distinct()).containsOnly(Schemas.P2P.P2P_OUT_MARKERS)
        }
    }

}
