package net.corda.flow.mapper.impl

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionAck
import net.corda.data.flow.event.session.SessionError
import net.corda.data.p2p.app.AppMessage
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
    private val locallyHostedIdentitiesService: LocallyHostedIdentitiesService,
    private val appMessageFactory: (SessionEvent, CordaAvroSerializer<SessionEvent>, SmartConfig) -> AppMessage
): RecordFactory {
    private val sessionEventSerializer = cordaAvroSerializationFactory.createAvroSerializer<SessionEvent> { }

    /*override fun createAndSendRecord(
        eventKey: String,
        sessionEvent: SessionEvent,
        flowMapperState: FlowMapperState?,
        instant: Instant,
        flowConfig: SmartConfig,
        messageDirection: MessageDirection,
        exceptionEnvelope: ExceptionEnvelope
    ): Record<*, *> {
        val outputTopic = getSessionEventOutputTopic(sessionEvent, messageDirection)
        return when (messageDirection) {
            MessageDirection.INBOUND -> {
                Record(outputTopic, flowMapperState.flowId, FlowEvent(flowMapperState.flowId, sessionEvent))
            }
            MessageDirection.OUTBOUND -> {
                forwardError(sessionEvent, exceptionEnvelope, instant, flowConfig, messageDirection)
            }
        }
    }
*/


    override fun forwardError(
        sessionEvent: SessionEvent,
        exceptionEnvelope: ExceptionEnvelope,
        instant: Instant,
        flowConfig: SmartConfig,
        messageDirection: MessageDirection
    ): Record<*, *> {
        val outputTopic = getSessionEventOutputTopic(sessionEvent, messageDirection)
        val errEvent = SessionEvent(
            MessageDirection.INBOUND,
            instant,
            toggleSessionId(sessionEvent.sessionId),
            null,
            sessionEvent.initiatingIdentity,
            sessionEvent.initiatedIdentity,
            sessionEvent.receivedSequenceNum,
            emptyList(),
            SessionError(
                exceptionEnvelope
            )
        )

        return if (isLocalCluster(sessionEvent)) {
            Record(outputTopic, errEvent.sessionId, FlowMapperEvent(errEvent))
        } else {
            createOutboundRecord(
                sessionEvent,
                SessionError(
                    exceptionEnvelope
                ),
                instant,
                sessionEventSerializer,
                appMessageFactory,
                flowConfig,
                sessionEvent.receivedSequenceNum,
                outputTopic
            )
        }
    }

    override fun forwardAck(
        sessionEvent: SessionEvent,
        instant: Instant,
        flowConfig: SmartConfig,
        messageDirection: MessageDirection
    ): Record<*, *> {
        val outputTopic = getSessionEventOutputTopic(sessionEvent, messageDirection)
        val ackEvent = SessionEvent(
            MessageDirection.INBOUND,
            instant,
            toggleSessionId(sessionEvent.sessionId),
            null,
            sessionEvent.initiatingIdentity,
            sessionEvent.initiatedIdentity,
            sessionEvent.sequenceNum,
            emptyList(),
            SessionAck()
        )

        return if (isLocalCluster(sessionEvent)) {
            Record(outputTopic, ackEvent.sessionId, FlowMapperEvent(ackEvent))
        } else {
            createOutboundRecord(
                sessionEvent,
                SessionAck(),
                instant,
                sessionEventSerializer,
                appMessageFactory,
                flowConfig,
                sessionEvent.sequenceNum,
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

    /**
     * Inbound records should be directed to the flow event topic.
     * Outbound records should be directed to the p2p out topic.
     * @return the output topic based on [messageDirection].
     */

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

