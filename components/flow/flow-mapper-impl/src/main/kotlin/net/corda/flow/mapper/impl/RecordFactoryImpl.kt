package net.corda.flow.mapper.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionError
import net.corda.flow.mapper.factory.RecordFactory
import net.corda.flow.mapper.impl.executor.createOutboundRecord
import net.corda.flow.mapper.impl.executor.getDestinationIdentity
import net.corda.flow.mapper.impl.executor.toggleSessionId
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.locally.hosted.identities.LocallyHostedIdentitiesService
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

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
                sessionEventSerializer,
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
                sessionEventSerializer,
                flowConfig,
                outputTopic
            )
        }
    }

    private fun isLocalCluster(
        sessionEvent: SessionEvent
    ): Boolean {
        val destinationIdentity = getDestinationIdentity(sessionEvent)
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
}

