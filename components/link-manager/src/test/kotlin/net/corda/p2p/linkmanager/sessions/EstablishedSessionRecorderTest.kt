package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.AuthenticatedMessageAndKey
import net.corda.data.p2p.LinkOutMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.LinkManagerSentMarker
import net.corda.p2p.crypto.protocol.api.AuthenticatedSession
import net.corda.p2p.crypto.protocol.api.AuthenticationResult
import net.corda.p2p.linkmanager.utilities.mockMembersAndGroups
import net.corda.schema.Schemas.P2P.LINK_OUT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_MARKERS
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.MockTimeFacilitiesProvider
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.nio.ByteBuffer

class EstablishedSessionRecorderTest {
    private val carol = createTestHoldingIdentity("CN=Carol, O=Corp, L=LDN, C=GB", "group-1")
    private val david = createTestHoldingIdentity("CN=Carol, O=Corp, L=LDN, C=GB", "group-1")
    private val membersAndGroups = mockMembersAndGroups(
        carol,
        david,
    )
    private val mac = mock<AuthenticationResult> {
        on { mac } doReturn "mac".toByteArray()
    }
    private val session = mock<AuthenticatedSession> {
        on { sessionId } doReturn "SessionId"
        on { createMac(any()) } doReturn mac
    }
    private val clock = MockTimeFacilitiesProvider().clock
    private val sessionManager = mock<SessionManager>()
    private val header = AuthenticatedMessageHeader(
        carol.toAvro(),
        david.toAvro(),
        null,
        "msg",
        "",
        "system-1",
        MembershipStatusFilter.ACTIVE
    )
    private val data = ByteBuffer.wrap(byteArrayOf(1 ,3, 4))
    private val messageAndKey = AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")

    private val recorder = EstablishedSessionRecorder(
        membersAndGroups.second,
        membersAndGroups.first,
        clock,
    )

    @Test
    fun `recordsForSessionEstablished will create the message and marker`() {
        val records = recorder.recordsForSessionEstablished(
            sessionManager,
            session,
            1L,
            messageAndKey,
        )

        assertThat(records).hasSize(2)

    }

    @Test
    fun `recordsForSessionEstablished will create link out message`() {
        val records = recorder.recordsForSessionEstablished(
            sessionManager,
            session,
            1L,
            messageAndKey,
        )
        val linkOut = records.firstOrNull {
            it.topic == LINK_OUT_TOPIC
        }?.value as? LinkOutMessage

        assertThat(linkOut).isNotNull()
    }

    @Test
    fun `recordsForSessionEstablished will create marker topic`() {
        val records = recorder.recordsForSessionEstablished(
            sessionManager,
            session,
            1L,
            messageAndKey,
        )
        val marker = records.firstOrNull {
            it.topic == P2P_OUT_MARKERS
        }?.value as? AppMessageMarker

        assertThat(marker?.marker).isInstanceOf(LinkManagerSentMarker::class.java)
    }

    @Test
    fun `recordsForSessionEstablished will notify the session manager`() {
        recorder.recordsForSessionEstablished(
            sessionManager,
            session,
            1L,
            messageAndKey,
        )

        verify(sessionManager).dataMessageSent(session)
    }

    @Test
    fun `recordsForSessionEstablished with unknown member will not create any record`() {
        val bob = createTestHoldingIdentity("CN=Bob, O=Corp, L=LDN, C=GB", "group-1")
        val header = AuthenticatedMessageHeader(
            bob.toAvro(),
            carol.toAvro(),
            null,
            "msg",
            "",
            "system-1",
            MembershipStatusFilter.ACTIVE
        )
        val messageAndKey = AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")

        val records = recorder.recordsForSessionEstablished(
            sessionManager,
            session,
            1L,
            messageAndKey,
        )

        assertThat(records).isEmpty()
    }

    @Test
    fun `recordsForSessionEstablished with unknown member will not notify the manager`() {
        val bob = createTestHoldingIdentity("CN=Bob, O=Corp, L=LDN, C=GB", "group-1")
        val header = AuthenticatedMessageHeader(
            bob.toAvro(),
            carol.toAvro(),
            null,
            "msg",
            "",
            "system-1",
            MembershipStatusFilter.ACTIVE
        )
        val messageAndKey = AuthenticatedMessageAndKey(AuthenticatedMessage(header, data), "key")

        recorder.recordsForSessionEstablished(
            sessionManager,
            session,
            1L,
            messageAndKey,
        )

        verify(sessionManager, never()).dataMessageSent(session)
    }
}
