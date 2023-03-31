package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
//import net.corda.data.KeyValuePair
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
import net.corda.interop.InteropAliasProcessor.Companion.addAliasSubstringToOrganisationName
import net.corda.interop.InteropAliasProcessor.Companion.removeAliasSubstringFromOrganisationName
import net.corda.interop.service.InteropFacadeToFlowMapperService
//import net.corda.interop.service.impl.InteropMessageTransformer
import net.corda.libs.configuration.SmartConfig
//import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
//TODO import commented out - see TODO adding FLOW_CONFIG below:
//import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.Constants
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
class InteropProcessor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val config: SmartConfig,
    private val facadeToFlowMapperService: InteropFacadeToFlowMapperService
) : DurableProcessor<String, FlowMapperEvent> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
        const val INTEROP_GROUP_ID = "3dfc0aae-be7c-44c2-aa4f-4d0d7145cf08"
    }

    private val interopAvroDeserializer: CordaAvroDeserializer<InteropMessage> =
        cordaAvroSerializationFactory.createAvroDeserializer({}, InteropMessage::class.java)
    private val sessionEventSerializer: CordaAvroSerializer<SessionEvent> =
        cordaAvroSerializationFactory.createAvroSerializer{}

    override fun onNext(
        events: List<Record<String, FlowMapperEvent>>
    ): List<Record<*, *>> = events.mapNotNull { (_, key, value) ->
        val sessionEvent = value?.payload
        if (sessionEvent == null) {
            logger.warn("Dropping message with empty payload, key $key.")
            return@mapNotNull null
        }

        if (sessionEvent !is SessionEvent) {
            logger.warn("Dropping message with payload of type ${sessionEvent::class.java}, required SessionEvent type.")
            return@mapNotNull null
        }
        val (sourceIdentity, destinationIdentity) = getSourceAndDestinationIdentity(sessionEvent)
        if (sessionEvent.messageDirection == MessageDirection.INBOUND) {
            val destinationAlias = destinationIdentity

            val realHoldingIdentity = getRealHoldingIdentityFromAliasMapping(
                InteropAliasProcessor.getRealHoldingIdentity(destinationAlias.toCorda().x500Name.toString()))

            if (realHoldingIdentity == null) {
                logger.warn("Could not find a holding identity for alias $destinationAlias.")
                return@mapNotNull null
            }

            val facadeRequest = when (val sessionPayload = sessionEvent.payload) {
                is SessionInit -> sessionPayload::class.java //InteropMessageTransformer.getFacadeRequest(
                    //interopAvroDeserializer.deserialize(sessionPayload.payload.array())!!
                //)
                is SessionData ->  sessionPayload::class.java
                 //InteropMessageTransformer.getFacadeRequest(sessionPayload.payload as InteropMessage)
                else -> sessionPayload::class.java
            }

//            if (facadeRequest == null) {
//                logger.info("Pass-through event ${sessionEvent::class.java}/${sessionEvent.payload::class.java}  without FacadeRequest")
//            } else {
//                logger.info(
//                    "Processing message from flow.interop.event with subsystem $SUBSYSTEM." +
//                            " Key: $key, facade request: $facadeRequest."
//                )
//                val flowName = facadeToFlowMapperService.getFlowName(
//                    realHoldingIdentity, facadeRequest.facadeId.toString(),
//                    facadeRequest.methodName
//                )
//                // this change is required for CORE-10426 Support For Façade Handlers
//                logger.info("Flow name associated with facade request : $flowName")
//            }
            Record(FLOW_MAPPER_EVENT_TOPIC, sessionEvent.sessionId, FlowMapperEvent(sessionEvent.apply {
                if (isInitiatingIdentityDestination()) {
                    initiatingIdentity = initiatingIdentity.apply {
                        x500Name = removeAliasSubstringFromOrganisationName(this.toCorda()).x500Name.toString()
                        groupId = realHoldingIdentity.groupId
                    }
                }
                if (isInitiatedIdentityDestination()) {
                    initiatedIdentity = initiatedIdentity.apply {
                        x500Name = removeAliasSubstringFromOrganisationName(this.toCorda()).x500Name.toString()
                        groupId = realHoldingIdentity.groupId
                    }
                }
            }.apply {
                val (newDestinationIdentity, newSourceIdentity) = getSourceAndDestinationIdentity(sessionEvent)
                logger.info("INBOUND: $newSourceIdentity -> $newDestinationIdentity, $facadeRequest")
            }
            ))
        } else { //MessageDirection.OUTBOUND
            val translatedSource = sourceIdentity.apply {
                x500Name = addAliasSubstringToOrganisationName(this.toCorda()).x500Name.toString()
                groupId = INTEROP_GROUP_ID
            }
            val translatedDestination = destinationIdentity.apply {
                groupId = INTEROP_GROUP_ID
            }
            logger.info("OUTBOUND: $translatedSource -> $translatedDestination")
            Record(
                P2P_OUT_TOPIC, sessionEvent.sessionId,
                AppMessage(
                    AuthenticatedMessage(
                        AuthenticatedMessageHeader(
                            translatedDestination,
                            translatedSource,
                            //TODO adding FLOW_CONFIG to InteropService breaks InteropDataSetupIntegrationTest, use hardcoded 500000 for now
                            Instant.ofEpochMilli(sessionEvent.timestamp.toEpochMilli() + 500000),
                            //+ config.getLong(FlowConfig.SESSION_P2P_TTL)),
                            sessionEvent.sessionId + "-" + UUID.randomUUID(),
                            "",
                            SUBSYSTEM,
                            MembershipStatusFilter.ACTIVE
                        ), ByteBuffer.wrap(sessionEventSerializer.serialize(sessionEvent))
                    )
                )
            )
        }
    }

    override val keyClass = String::class.java
    override val valueClass = FlowMapperEvent::class.java

    private fun getRealHoldingIdentityFromAliasMapping(fakeHoldingIdentity: HoldingIdentity?): HoldingIdentity? {
        fakeHoldingIdentity ?: return null
        val groupReader = membershipGroupReaderProvider.getGroupReader(fakeHoldingIdentity)
        val memberProvidedContext = groupReader.lookup(fakeHoldingIdentity.x500Name)?.memberProvidedContext
        memberProvidedContext ?: return null
        val x500Name = memberProvidedContext.get("corda.interop.mapping.x500name")
        val groupId = memberProvidedContext.get("corda.interop.mapping.group")
        x500Name ?: return null
        groupId ?: return null
        return HoldingIdentity(MemberX500Name.parse(x500Name), groupId)
    }

    //TODO taken from FlowMapperHelper
    /**
     * Get the source and destination holding identity from the [sessionEvent].
     * @param sessionEvent Session event to extract identities from
     * @return Source and destination identities for a SessionEvent message.
     */
    private fun getSourceAndDestinationIdentity(sessionEvent: SessionEvent):
            Pair<net.corda.data.identity.HoldingIdentity, net.corda.data.identity.HoldingIdentity> =
         if (sessionEvent.sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX))
            Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
        else
            Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)

    private fun SessionEvent.isInitiatingIdentityDestination() = !sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)
    private fun SessionEvent.isInitiatedIdentityDestination() = !isInitiatingIdentityDestination()
}
