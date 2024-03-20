package net.corda.p2p.linkmanager.sessions

import net.corda.data.p2p.ReEstablishSessionMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.libs.statemanager.api.State
import net.corda.messaging.api.records.Record
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.isOutbound
import net.corda.p2p.linkmanager.sessions.metadata.OutboundSessionMetadata.Companion.toOutbound
import net.corda.p2p.linkmanager.sessions.metadata.toCounterparties
import net.corda.schema.Schemas
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.UUID

internal class ReEstablishmentMessageSender(
    private val schemaRegistry: AvroSchemaRegistry,
    sessionManagerImpl: SessionManagerImpl,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ReEstablishmentMessageSender::class.java)
    }

    private val publisher by lazy {
        sessionManagerImpl.publisher
    }
    fun send(
        state: State,
    ) {
        val sessionId = if (state.metadata.isOutbound()) {
            state.metadata.toOutbound().sessionId
        } else {
            state.key
        }
        val counterparties = state.toCounterparties()
        val source = counterparties.ourId
        val destination = counterparties.counterpartyId
        val messageBytes = schemaRegistry.serialize(
            ReEstablishSessionMessage(sessionId),
        ).array()
        val record = createAuthenticatedMessageRecord(source, destination, messageBytes)
        logger.info("Sending '{}' to session initiator '{}'.", ReEstablishSessionMessage::class.simpleName, destination)
        publisher.publish(listOf(record))
    }

    private fun createAuthenticatedMessageRecord(
        source: HoldingIdentity,
        destination: HoldingIdentity,
        payload: ByteArray,
    ): Record<String, AppMessage> {
        val header = AuthenticatedMessageHeader.newBuilder()
            .setDestination(destination.toAvro())
            .setSource(source.toAvro())
            .setMessageId(UUID.randomUUID().toString())
            .setSubsystem(StatefulSessionManagerImpl.LINK_MANAGER_SUBSYSTEM)
            .setStatusFilter(MembershipStatusFilter.ACTIVE)
            .setTtl(null)
            .setTraceId(null)
            .build()
        val message = AuthenticatedMessage.newBuilder()
            .setHeader(header)
            .setPayload(ByteBuffer.wrap(payload))
            .build()
        val appMessage = AppMessage(message)

        return Record(
            Schemas.P2P.P2P_OUT_TOPIC,
            UUID.randomUUID().toString(),
            appMessage,
        )
    }
}
