package net.corda.flow.mapper.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
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
import net.corda.flow.mapper.impl.executor.toggleSessionId
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
        sessionEvent: SessionEvent,
        exceptionEnvelope: ExceptionEnvelope,
        instant: Instant,
        flowConfig: SmartConfig,
        messageDirection: MessageDirection
    ): Record<*, *> {
        val outputTopic = getSessionEventOutputTopic(sessionEvent, messageDirection)
        return if (isLocalCluster(sessionEvent)) {
            val errEvent = SessionEvent(
                MessageDirection.INBOUND,
                instant,
                toggleSessionId(sessionEvent.sessionId),
                null,
                sessionEvent.initiatingIdentity,
                sessionEvent.initiatedIdentity,
                SessionError(
                    exceptionEnvelope
                ),
                sessionEvent.contextSessionProperties
            )
            Record(outputTopic, errEvent.sessionId, FlowMapperEvent(errEvent))
        } else {
            createOutboundRecord(
                sessionEvent,
                SessionError(
                    exceptionEnvelope
                ),
                instant,
                flowConfig,
                outputTopic
            )
        }
    }

    override fun forwardEvent(
        sessionEvent: SessionEvent,
        instant: Instant,
        flowConfig: SmartConfig,
        messageDirection: MessageDirection
    ): Record<*, *> {
        val outputTopic = getSessionEventOutputTopic(sessionEvent, messageDirection)
        return if (isLocalCluster(sessionEvent)) {
            sessionEvent.messageDirection = MessageDirection.INBOUND
            sessionEvent.sessionId = toggleSessionId(sessionEvent.sessionId)
            Record(
                outputTopic,
                sessionEvent.sessionId,
                FlowMapperEvent(sessionEvent)
            )
        } else {
            createOutboundRecord(
                sessionEvent,
                sessionEvent.payload,
                instant,
                flowConfig,
                outputTopic
            )
        }
    }

    private fun isLocalCluster(
        sessionEvent: SessionEvent
    ): Boolean {
        val destinationIdentity = getSourceAndDestinationIdentity(sessionEvent).destinationIdentity
        return when (locallyHostedIdentitiesService.getIdentityInfo(destinationIdentity.toCorda())) {
            null -> false
            else -> true
        }
    }

    override fun getSessionEventOutputTopic(sessionEvent: SessionEvent, messageDirection: MessageDirection): String {
        return when (messageDirection) {
            MessageDirection.INBOUND -> Schemas.Flow.FLOW_EVENT_TOPIC
            MessageDirection.OUTBOUND -> {
                if (isLocalCluster(sessionEvent)) {
                    Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
                } else {
                    Schemas.P2P.P2P_OUT_TOPIC
                }
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

    /**
     * Generate an AppMessage to send to the P2P.out topic.
     * @param sessionEvent Flow event to send
     * @param flowConfig config
     * @return AppMessage to send to the P2P.out topic with the serialized session event as payload
     */
    private fun generateAppMessage(
        sessionEvent: SessionEvent,
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

    /**
     * Creates [Record] for P2P.out topic.
     * @param sessionEvent Flow event to send
     * @param payload Flow event payload
     * @param instant Instant
     * @param flowConfig config
     * @param outputTopic topic where the record should be sent
     */
    @Suppress("LongParameterList")
    private fun createOutboundRecord(
        sessionEvent: SessionEvent,
        payload: Any,
        instant: Instant,
        flowConfig: SmartConfig,
        outputTopic: String
    ) : Record<*, *> {
        val sessionId = sessionEvent.sessionId
        return Record(
            outputTopic, sessionId, generateAppMessage(
                SessionEvent(
                    MessageDirection.OUTBOUND,
                    instant,
                    sessionId,
                    null,
                    sessionEvent.initiatingIdentity,
                    sessionEvent.initiatedIdentity,
                    payload,
                    sessionEvent.contextSessionProperties
                ),
                flowConfig
            )
        )
    }
}

