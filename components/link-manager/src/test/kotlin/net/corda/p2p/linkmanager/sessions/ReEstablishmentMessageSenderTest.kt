package net.corda.p2p.linkmanager.sessions

import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.Record
import net.corda.p2p.messaging.P2pRecordsFactory
import net.corda.p2p.messaging.Subsystem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class ReEstablishmentMessageSenderTest {
    private val publishedRecords = argumentCaptor<List<Record<Any, Any>>>()
    private val publisher = mock<PublisherWithDominoLogic> {
        on { publish(publishedRecords.capture()) } doReturn mock()
    }
    private val sessionManagerImpl = mock<SessionManagerImpl> {
        on { publisher } doReturn publisher
    }
    private val inboundMetadata = Metadata(
        mapOf(
            "sourceVnode" to "O=Alice, L=London, C=GB",
            "destinationVnode" to "O=Bob, L=London, C=GB",
            "groupId" to "groupId",
            "lastSendTimestamp" to 100,
            "expiry" to 900,
        ),
    )
    private val outboundMetadata = Metadata(
        mapOf(
            "sessionId" to "sessionId",
            "sourceVnode" to "O=Alice, L=London, C=GB",
            "destinationVnode" to "O=Bob, L=London, C=GB",
            "groupId" to "groupId",
            "lastSendTimestamp" to 100,
            "expiry" to 900,
            "status" to "SessionReady",
            "serial" to 4L,
            "membershipStatus" to MembershipStatusFilter.ACTIVE_OR_SUSPENDED.toString(),
            "communicationWithMgm" to true,
            "initiationTimestampMillis" to 10L,
        ),
    )
    private val inboundState = mock<State> {
        on { metadata } doReturn inboundMetadata
        on { key } doReturn "sessionId"
    }
    private val outboundState = mock<State> {
        on { metadata } doReturn outboundMetadata
    }
    private val record = mock<Record<String, AppMessage>> { }
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                source = eq(
                    HoldingIdentity(
                        "O=Alice, L=London, C=GB",
                        "groupId",
                    ),
                ),
                destination = eq(
                    HoldingIdentity(
                        "O=Bob, L=London, C=GB",
                        "groupId",
                    ),
                ),
                content = eq(ReEstablishSessionMessage("sessionId")),
                subsystem = eq(Subsystem.LINK_MANAGER),
                recordKey = any(),
                minutesToWait = anyOrNull(),
                messageId = any(),
                filter = eq(MembershipStatusFilter.ACTIVE),
            )
        } doReturn record
    }
    private val sender = ReEstablishmentMessageSender(
        p2pRecordsFactory,
        sessionManagerImpl,
    )

    @Test
    fun `send inbound message will send the correct data`() {
        sender.send(inboundState)

        assertThat(publishedRecords.firstValue.firstOrNull()).isEqualTo(record)
    }

    @Test
    fun `send outbound message will send the correct data`() {
        sender.send(outboundState)

        assertThat(publishedRecords.firstValue.firstOrNull()).isEqualTo(record)
    }
}
