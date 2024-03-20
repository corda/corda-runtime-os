package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.Metadata
import net.corda.libs.statemanager.api.State
import net.corda.lifecycle.domino.logic.util.PublisherWithDominoLogic
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.sessions.StatefulSessionManagerImpl.Companion.LINK_MANAGER_SUBSYSTEM
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer

class ReEstablishmentMessageSenderTest {
    private val serializedObject = argumentCaptor<Any>()
    private val rawData = ByteBuffer.wrap(byteArrayOf(1))
    private val schemaRegistry = mock<AvroSchemaRegistry> {
        on { serialize(serializedObject.capture()) } doReturn rawData
    }
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

    private val sender = ReEstablishmentMessageSender(
        schemaRegistry,
        sessionManagerImpl,
    )

    @Test
    fun `send inbound message will send the key as session ID`() {
        sender.send(inboundState)

        assertThat(serializedObject.firstValue).isEqualTo(
            ReEstablishSessionMessage(
                "sessionId",
            ),
        )
    }

    @Test
    fun `send outbound message will send the key as session ID`() {
        sender.send(outboundState)

        assertThat(serializedObject.firstValue).isEqualTo(
            ReEstablishSessionMessage(
                "sessionId",
            ),
        )
    }

    @Test
    fun `send will send the correct data`() {
        sender.send(inboundState)

        val record = publishedRecords.firstValue.firstOrNull()
        val message = ((record?.value as? AppMessage)?.message) as? AuthenticatedMessage
        val header = message?.header
        assertSoftly {
            it.assertThat(record?.topic).isEqualTo(P2P_OUT_TOPIC)
            it.assertThat(message?.payload).isEqualTo(rawData)
            it.assertThat(header?.destination?.x500Name).isEqualTo("O=Bob, L=London, C=GB")
            it.assertThat(header?.destination?.groupId).isEqualTo("groupId")
            it.assertThat(header?.source?.x500Name).isEqualTo("O=Alice, L=London, C=GB")
            it.assertThat(header?.source?.groupId).isEqualTo("groupId")
            it.assertThat(header?.subsystem).isEqualTo(LINK_MANAGER_SUBSYSTEM)
            it.assertThat(header?.statusFilter).isEqualTo(MembershipStatusFilter.ACTIVE)
            it.assertThat(header?.ttl).isNull()
            it.assertThat(header?.traceId).isNull()
        }
    }
}
