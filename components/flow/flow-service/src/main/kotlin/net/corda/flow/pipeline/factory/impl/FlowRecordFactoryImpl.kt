package net.corda.flow.pipeline.factory.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.*
import net.corda.schema.Schemas.P2P
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.Constants
import org.osgi.service.component.annotations.Component
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

@Component(service = [FlowRecordFactory::class])
class FlowRecordFactoryImpl : FlowRecordFactory {

    override fun createFlowEventRecord(flowId: String, payload: Any): Record<String, FlowEvent> {
        return Record(
            topic = FLOW_EVENT_TOPIC,
            key = flowId,
            value = FlowEvent(flowId, payload)
        )
    }

    override fun createP2PEventRecord(
        event: SessionEvent,
        serializerFactory: CordaAvroSerializationFactory,
        flowConfig: SmartConfig
    ): Record<*, *> {
        return Record(
            topic = P2P.P2P_OUT_TOPIC,
            key = event.sessionId,
            value = generateAppMessage(
                sessionEvent = event,
                flowConfig = flowConfig,
                sessionEventSerializer = serializerFactory.createAvroSerializer()
            )
        )
    }

    override fun createFlowStatusRecord(status: FlowStatus): Record<FlowKey, FlowStatus> {
        return Record(
            topic = FLOW_STATUS_TOPIC,
            key = status.key,
            value = status
        )
    }

    override fun createFlowMapperEventRecord(key: String, payload: Any): Record<*, FlowMapperEvent> {
        return Record(
            topic = FLOW_MAPPER_EVENT_TOPIC,
            key = key,
            value = FlowMapperEvent(payload)
        )
    }
}

fun generateAppMessage(
    sessionEvent: SessionEvent,
    sessionEventSerializer: CordaAvroSerializer<SessionEvent>,
    flowConfig: SmartConfig
): AppMessage {
    val (sourceIdentity, destinationIdentity) = getSourceAndDestinationIdentity(sessionEvent)
    val header = AuthenticatedMessageHeader(
        destinationIdentity,
        sourceIdentity,
        Instant.ofEpochMilli(sessionEvent.timestamp.toEpochMilli() + flowConfig.getLong(FlowConfig.SESSION_P2P_TTL)),
        sessionEvent.sessionId + "-" + UUID.randomUUID(),
        "",
        Constants.FLOW_SESSION_SUBSYSTEM,
        MembershipStatusFilter.ACTIVE
    )
    return AppMessage(AuthenticatedMessage(header, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))))
}

private fun getSourceAndDestinationIdentity(sessionEvent: SessionEvent): Pair<HoldingIdentity, HoldingIdentity> {
    return if (sessionEvent.sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)) {
        Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
    } else {
        Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
    }
}