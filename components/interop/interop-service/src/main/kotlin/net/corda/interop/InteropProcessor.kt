package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.interop.InteropMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.interop.service.impl.InteropMessageTransformer
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.Constants
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*

@Suppress("LongParameterList", "Unused")
class InteropProcessor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val config: SmartConfig,
    private val facadeToFlowMapperService: InteropFacadeToFlowMapperService
) : DurableProcessor<String, FlowMapperEvent> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
    }

    private val interopAvroDeserializer: CordaAvroDeserializer<InteropMessage> =
        cordaAvroSerializationFactory.createAvroDeserializer({}, InteropMessage::class.java)
    private val sessionAvroDeserializer: CordaAvroDeserializer<SessionEvent> =
        cordaAvroSerializationFactory.createAvroDeserializer({}, SessionEvent::class.java)
    private val  sessionEventSerializer: CordaAvroSerializer<SessionEvent> =
        cordaAvroSerializationFactory.createAvroSerializer{}

    fun buildSessionEvent(
        messageDirection: MessageDirection,
        sessionId: String,
        sequenceNum: Int?,
        payload: Any? = null,
        receivedSequenceNum: Int = 0,
        outOfOrderSeqNums: List<Int> = listOf(0),
        timestamp: Instant = Instant.now(),
        initiatingIdentity: net.corda.data.identity.HoldingIdentity = net.corda.data.identity.HoldingIdentity(
            "alice",
            "group1"
        ),
        initiatedIdentity: net.corda.data.identity.HoldingIdentity = net.corda.data.identity.HoldingIdentity(
            "bob",
            "group1"
        ),
    ): SessionEvent {
        return SessionEvent.newBuilder()
            .setSessionId(sessionId)
            .setMessageDirection(messageDirection)
            .setSequenceNum(sequenceNum)
            .setInitiatingIdentity(initiatingIdentity)
            .setInitiatedIdentity(initiatedIdentity)
            .setPayload(payload)
            .setTimestamp(timestamp)
            .setReceivedSequenceNum(receivedSequenceNum)
            .setOutOfOrderSequenceNums(outOfOrderSeqNums)
            .build()
    }

    override fun onNext(
        events: List<Record<String, FlowMapperEvent>>
    ): List<Record<*, *>> = events.mapNotNull { (_, key, value) ->
        val sessionEvent = value?.payload
        if (sessionEvent == null) {
            logger.warn("Dropping message with empty payload")
            return@mapNotNull null
        }

        if (sessionEvent !is SessionEvent) {
            logger.warn("Dropping message with payload of type ${sessionEvent::class.java}, required SessionEvent type.")
            return@mapNotNull null
        }
        val (sourceIdentity, destinationIdentity) = getSourceAndDestinationIdentity(sessionEvent)
        if (sessionEvent.messageDirection == MessageDirection.INBOUND) {
            val destinationAlias = destinationIdentity

            val realHoldingIdentity = InteropAliasProcessor.getRealHoldingIdentity(
                destinationAlias.toCorda().x500Name.toString()
            )

            if (realHoldingIdentity == null) {
                logger.info("Could not find a holding identity for alias $destinationAlias.")
                return@mapNotNull null
            }
            val facadeRequest = when (val sessionPayload = sessionEvent.payload) {
                is SessionInit -> InteropMessageTransformer.getFacadeRequest(
                    interopAvroDeserializer.deserialize(sessionPayload.payload.array())!!
                )
                is SessionData -> InteropMessageTransformer.getFacadeRequest(sessionPayload.payload as InteropMessage)
                else -> null
            }

            if (facadeRequest == null) {
                logger.info("Pass-through event ${sessionEvent::class.java} without FacadeRequest")
            } else {
                logger.info(
                    "Processing message from flow.interop.event with subsystem $SUBSYSTEM." +
                            " Key: $key, facade request: $facadeRequest."
                )
                val flowName = facadeToFlowMapperService.getFlowName(
                    realHoldingIdentity, facadeRequest.facadeId.toString(),
                    facadeRequest.methodName
                )
                //TODO utilise flowName as input to data send to FlowProcessor (for now it's only used by the logger,
                // this change is required for CORE-10426 Support For Façade Handlers
                logger.info("Flow name associated with facade request : $flowName")
            }
            Record(FLOW_MAPPER_EVENT_TOPIC, sessionEvent.sessionId, FlowMapperEvent(sessionEvent))
        } else {
            //TODO taken from FlowMapperHelper function generateAppMessage
            val header = AuthenticatedMessageHeader(
                destinationIdentity,
                sourceIdentity,
                Instant.ofEpochMilli(sessionEvent.timestamp.toEpochMilli() + config.getLong(FlowConfig.SESSION_P2P_TTL)),
                sessionEvent.sessionId + "-" + UUID.randomUUID(),
                "",
                Constants.FLOW_SESSION_SUBSYSTEM,
                MembershipStatusFilter.ACTIVE
            )
            Record(
                P2P_OUT_TOPIC, sessionEvent.sessionId,
                AppMessage(AuthenticatedMessage(header, ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))))
            )
        }
    }

    override val keyClass = String::class.java
    override val valueClass = FlowMapperEvent::class.java

    private fun getRealHoldingIdentityFromAliasMapping(fakeHoldingIdentity: HoldingIdentity): String? {
        val groupReader = membershipGroupReaderProvider.getGroupReader(fakeHoldingIdentity)
        val memberInfo = groupReader.lookup(fakeHoldingIdentity.x500Name)
        return memberInfo?.memberProvidedContext?.get(MemberInfoExtension.INTEROP_ALIAS_MAPPING)
    }

    //TODO taken from FlowMapperHelper
    /**
     * Get the source and destination holding identity from the [sessionEvent].
     * @param sessionEvent Session event to extract identities from
     * @return Source and destination identities for a SessionEvent message.
     */
    private fun getSourceAndDestinationIdentity(sessionEvent: SessionEvent):
            Pair<net.corda.data.identity.HoldingIdentity, net.corda.data.identity.HoldingIdentity> {
        return if (sessionEvent.sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)) {
            Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
        } else {
            Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
        }
    }
}
