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
    private companion object {
        const val GROUP_ID = "groupId"
        const val ALICE_X500_NAME = "O=Alice, L=London, C=GB"
        const val BOB_X500_NAME = "O=Bob, L=London, C=GB"
        const val SESSION_ID = "sessionId"
        private val commonMetadata = mapOf(
            "sourceVnode" to ALICE_X500_NAME,
            "destinationVnode" to BOB_X500_NAME,
            "groupId" to GROUP_ID,
            "lastSendTimestamp" to 100,
            "expiry" to 900,
        )
    }
    private val publishedRecords = argumentCaptor<List<Record<Any, Any>>>()
    private val publisher = mock<PublisherWithDominoLogic> {
        on { publish(publishedRecords.capture()) } doReturn mock()
    }
    private val sessionManagerImpl = mock<SessionManagerImpl> {
        on { publisher } doReturn publisher
    }
    private val inboundMetadata = Metadata(
        commonMetadata
    )
    private val inboundState = mock<State> {
        on { metadata } doReturn inboundMetadata
        on { key } doReturn SESSION_ID
    }
    private val record = mock<Record<String, AppMessage>> { }
    private val p2pRecordsFactory = mock<P2pRecordsFactory> {
        on {
            createAuthenticatedMessageRecord(
                source = eq(
                    HoldingIdentity(
                        ALICE_X500_NAME,
                        GROUP_ID,
                    ),
                ),
                destination = eq(
                    HoldingIdentity(
                        BOB_X500_NAME,
                        GROUP_ID,
                    ),
                ),
                content = eq(ReEstablishSessionMessage(SESSION_ID)),
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
}
