package net.corda.flow.mapper.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.data.identity.HoldingIdentity
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.flow.utils.isInitiatedIdentity
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.Constants
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

@Component(service = [RecordFactory::class])
class RecordFactoryImpl @Activate constructor(
    @Reference(service = CordaAvroSerializationFactory::class)
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    @Reference(service = LocallyHostedIdentitiesService::class)
    private val locallyHostedIdentitiesService: LocallyHostedIdentitiesService
): RecordFactory {
    private val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }

    override fun forwardError(
        sourceEvent: SessionEvent,
        exceptionEnvelope: ExceptionEnvelope,
        instant: Instant,
        flowConfig: SmartConfig,
        flowId: String
    ): Record<*, *> {
        return buildSessionRecord(
            sourceEvent,
            SessionError(
                exceptionEnvelope
            ),
            instant,
            flowConfig,
            flowId
        )
    }

    override fun forwardEvent(
        sourceEvent: SessionEvent,
        instant: Instant,
        flowConfig: SmartConfig,
        flowId: String
    ): Record<*, *> {
        return buildSessionRecord(
            sourceEvent,
            sourceEvent.payload,
            instant,
            flowConfig,
            flowId
        )
    }

    override fun sendBackError(
        sourceEvent: SessionEvent,
        exceptionEnvelope: ExceptionEnvelope,
        instant: Instant,
        flowConfig: SmartConfig
    ): Record<*, *> {
        if (sourceEvent.messageDirection == MessageDirection.INBOUND) {
            // In this case, the mapper should send the error back from where it came. To do this, switch the message
            // direction to OUTBOUND and then use the usual forwarding machinery to ensure it goes to the right place.
            sourceEvent.messageDirection = MessageDirection.OUTBOUND
        } else {
            // The mapper does not have the flow ID available to it, and so cannot send the session error back. Raise an
            // error instead. At present this is done by providing a `null` flow ID, and letting the forwarding code
            // raise an error when it discovers it needs it.
            sourceEvent.messageDirection = MessageDirection.INBOUND
        }
        return buildSessionRecord(
            sourceEvent,
            SessionError(exceptionEnvelope),
            instant,
            flowConfig,
            null
        )
    }

    private fun getSessionEventOutputTopic(sessionEvent: SessionEvent): String {
        return when (sessionEvent.messageDirection) {
            MessageDirection.INBOUND -> Schemas.Flow.FLOW_SESSION
            MessageDirection.OUTBOUND -> {
                if (isLocalCluster(sessionEvent)) {
                    Schemas.Flow.FLOW_MAPPER_SESSION_IN
                } else {
                    Schemas.P2P.P2P_OUT_TOPIC
                }
            }
            else -> {
                throw IllegalArgumentException("Session event had an invalid message direction set: ${sessionEvent.messageDirection}")
            }
        }
    }

    private fun buildSessionRecord(
        sourceEvent: SessionEvent,
        newPayload: Any,
        timestamp: Instant,
        config: SmartConfig,
        flowId: String?
    ) : Record<*, *> {
        val outputTopic = getSessionEventOutputTopic(sourceEvent)
        val (newDirection, sessionId) = when (outputTopic) {
            Schemas.Flow.FLOW_MAPPER_SESSION_IN -> Pair(MessageDirection.INBOUND, toggleSessionId(sourceEvent.sessionId))
            Schemas.Flow.FLOW_SESSION -> Pair(MessageDirection.INBOUND, sourceEvent.sessionId)
            else -> Pair(MessageDirection.OUTBOUND, sourceEvent.sessionId)
        }
        val sequenceNumber = if (newPayload is SessionError) null else sourceEvent.sequenceNum
        val sessionEvent = SessionEvent(
            newDirection,
            timestamp,
            sessionId,
            sequenceNumber,
            sourceEvent.initiatingIdentity,
            sourceEvent.initiatedIdentity,
            newPayload,
            sourceEvent.contextSessionProperties
        )
        return when (outputTopic) {
            Schemas.Flow.FLOW_SESSION -> {
                if (flowId == null) {
                    throw IllegalArgumentException("Flow ID is required to forward an event back to the flow event" +
                            "topic, but it was not provided.")
                }
                Record(outputTopic, flowId, FlowEvent(flowId, sessionEvent))
            }
            Schemas.Flow.FLOW_MAPPER_SESSION_IN -> {
                Record(outputTopic, sessionEvent.sessionId, FlowMapperEvent(sessionEvent))
            }
            Schemas.P2P.P2P_OUT_TOPIC -> {
                generateAppMessageRecord(sessionEvent, config, sessionId)
            }
            else -> {
                throw IllegalArgumentException("Invalid output topic of $outputTopic was found when forwarding a session event")
            }
        }
    }

    /**
     * Data class for source and destination holding identities from a session event.
     */
    private data class SourceAndDestination(
        val sourceIdentity: HoldingIdentity,
        val destinationIdentity: HoldingIdentity
    )

    /**
     * Get the source and destination holding identity from the [sessionEvent].
     * @param sessionEvent Session event to extract identities from
     * @return Source and destination identities for a SessionEvent message.
     */
    private fun getSourceAndDestinationIdentity(sessionEvent: SessionEvent): SourceAndDestination {
        return if (isInitiatedIdentity(sessionEvent.sessionId)) {
            SourceAndDestination(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
        } else {
            SourceAndDestination(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
        }
    }

    private fun isLocalCluster(
        sessionEvent: SessionEvent
    ): Boolean {
        val destinationIdentity = getSourceAndDestinationIdentity(sessionEvent).destinationIdentity
        return locallyHostedIdentitiesService.isHostedLocally(destinationIdentity.toCorda())
    }

    /**
     * Generate an record to send to the P2P.out topic.
     * @param sessionEvent Flow event to send
     * @param flowConfig config
     * @return AppMessage to send to the P2P.out topic with the serialized session event as payload
     */
    private fun generateAppMessageRecord(
        sessionEvent: SessionEvent,
        flowConfig: SmartConfig,
        sessionId: String,
    ): Record<String, AppMessage> {
        val (sourceIdentity, destinationIdentity) = getSourceAndDestinationIdentity(sessionEvent)
        val header = AuthenticatedMessageHeader(
            destinationIdentity,
            sourceIdentity,
            Instant.ofEpochMilli(sessionEvent.timestamp.toEpochMilli() + flowConfig.getLong(FlowConfig.SESSION_P2P_TTL)),
            sessionEvent.sessionId + "-" + UUID.randomUUID(),
            sessionId,
            Constants.FLOW_SESSION_SUBSYSTEM,
            MembershipStatusFilter.ACTIVE
        )
        val message = AppMessage(AuthenticatedMessage(header, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))))
        return Record(
            key = header.messageId,
            value = message,
            topic = Schemas.P2P.P2P_OUT_TOPIC,
        )
    }

    /**
     * Toggle the [sessionId] to that of the other party and return it.
     * Initiating party sessionId will be a random UUID.
     * Initiated party sessionId will be the initiating party session id with a suffix of "-INITIATED" added.
     * @return the toggled session id
     */
    private fun toggleSessionId(sessionId: String): String {
        return if (sessionId.endsWith(Constants.INITIATED_SESSION_ID_SUFFIX)) {
            sessionId.removeSuffix(Constants.INITIATED_SESSION_ID_SUFFIX)
        } else {
            "$sessionId${Constants.INITIATED_SESSION_ID_SUFFIX}"
        }
    }
}

