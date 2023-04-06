package net.corda.interop

//TODO CORE-12208 import commented out - see TODO adding FLOW_CONFIG below:
//import net.corda.schema.configuration.FlowConfig
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.interop.InteropMessage
import net.corda.data.interop.InteropState
import net.corda.data.interop.InteropStateType
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.interop.InteropAliasProcessor.Companion.addAliasSubstringToOrganisationName
import net.corda.interop.InteropAliasProcessor.Companion.removeAliasSubstringFromOrganisationName
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.session.manager.Constants
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

class InteropProcessor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val facadeToFlowMapperService: InteropFacadeToFlowMapperService
) : StateAndEventProcessor<String, InteropState, FlowMapperEvent> {

    override val keyClass = String::class.java
    override val stateValueClass = InteropState::class.java
    override val eventValueClass = FlowMapperEvent::class.java

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
        state: InteropState?,
        event: Record<String, FlowMapperEvent>
    ): StateAndEventProcessor.Response<InteropState> {
        val sessionEvent = event.value?.payload
        if (sessionEvent == null) {
            logger.warn("Dropping message with empty payload.")
            return StateAndEventProcessor.Response(state, emptyList())
        }

        if (sessionEvent !is SessionEvent) {
            logger.warn("Dropping message with payload of type ${sessionEvent::class.java}, required SessionEvent type.")
            return StateAndEventProcessor.Response(state, emptyList())
        }

        if (sessionEvent.messageDirection == MessageDirection.INBOUND) {
            val (destinationAlias, oldSource) = if(sessionEvent.isInitiatingIdentityDestination())
                Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
            else
                Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
            logEntering("INBOUND", oldSource, destinationAlias, sessionEvent)

            val realHoldingIdentity = getRealHoldingIdentityFromAliasMapping(
                InteropAliasProcessor.getRealHoldingIdentity(destinationAlias.toCorda().x500Name.toString()))

            if (realHoldingIdentity == null) {
                logger.warn("Could not find a holding identity for alias $destinationAlias.")
                return StateAndEventProcessor.Response(state, emptyList())
            }

            val facadeRequest = when (val sessionPayload = sessionEvent.payload) {
                is SessionInit -> sessionPayload::class.java  //InteropMessageTransformer.getFacadeRequest(
                    //interopAvroDeserializer.deserialize(sessionPayload.payload.array())!!
                //)
                is SessionData -> sessionPayload::class.java  //{
                    //val payload : ByteBuffer = sessionPayload.payload as ByteBuffer
                    //InteropMessageTransformer.getFacadeRequest(interopAvroDeserializer.deserialize(payload.array())!!)
                //}
                else -> sessionPayload::class.java //null
            }
            logger.trace(facadeRequest.toString())
//            if (facadeRequest == null) {
//                logger.info("Pass-through event ${sessionEvent::class.java} without FacadeRequest")
//            } else {
//                logger.info(
//                    "Processing message from flow.interop.event with subsystem $SUBSYSTEM." +
//                            " Key: ${event.key}, facade request: $facadeRequest."
//                )
//                val flowName = facadeToFlowMapperService.getFlowName(
//                    realHoldingIdentity, facadeRequest.facadeId.toString(),
//                    facadeRequest.methodName
//                )
//                //TODO utilise flowName as input to data send to FlowProcessor (for now it's only used by the logger),
//                // this change is required for CORE-10426 Support For Façade Handlers
//                logger.info("Flow name associated with facade request : $flowName")
//            }

            return StateAndEventProcessor.Response(
                InteropState(
                    UUID.randomUUID().toString(),
                    null,
                    InteropStateType.VALID,
                    destinationAlias.x500Name.toString(),
                    destinationAlias.groupId
                ),
                listOf(
                    Record(
                        FLOW_MAPPER_EVENT_TOPIC,
                        sessionEvent.sessionId,
                        FlowMapperEvent(sessionEvent.apply {
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
                            val (newDest, newSource) = if (isInitiatingIdentityDestination())
                                Pair(initiatingIdentity, initiatedIdentity)
                            else
                                Pair(initiatedIdentity, initiatingIdentity)
                            logLeaving("INBOUND", newSource, newDest, sessionEvent)
                        }
                        )
                    )
                )
            )
        } else { //MessageDirection.OUTBOUND
            val (sourceIdentity, destinationIdentity) = getSourceAndDestinationIdentity(sessionEvent)
            logEntering("OUTBOUND", sourceIdentity, destinationIdentity, sessionEvent)
            val translatedSource = sourceIdentity.apply {
                x500Name = state?.aliasHoldingIdentity ?: addAliasSubstringToOrganisationName(this.toCorda()).x500Name.toString()
                groupId = state?.groupId ?: INTEROP_GROUP_ID
            }
            val translatedDestination = destinationIdentity.apply {
                x500Name = addAliasSubstringToOrganisationName(this.toCorda()).x500Name.toString() //TODO
                groupId = INTEROP_GROUP_ID
            }
            logLeaving("OUTBOUND", translatedSource, translatedDestination, sessionEvent)
            return StateAndEventProcessor.Response(
                state,
                listOf(
                    Record(
                        P2P_OUT_TOPIC, sessionEvent.sessionId,
                        AppMessage(
                            AuthenticatedMessage(
                                AuthenticatedMessageHeader(
                                    translatedDestination,
                                    translatedSource,
                                    //TODO CORE-12208 adding FLOW_CONFIG to InteropService breaks InteropDataSetupIntegrationTest,
                                    // use hardcoded 500000 for now
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
                )
            )
        }
    }

    private fun logEntering(direction: String, source: net.corda.data.identity.HoldingIdentity,
                            dest: net.corda.data.identity.HoldingIdentity, event: SessionEvent) =
        logger.info("Start processing $direction from $source to $dest," +
                "event=${event.payload::class.java}, session/seq=${event.sessionId}/${event.sequenceNum}," +
                "initiating=${event.initiatingIdentity}")

    private fun logLeaving(direction: String, source: net.corda.data.identity.HoldingIdentity,
                           dest: net.corda.data.identity.HoldingIdentity, event: SessionEvent) =
        logger.info("Finished processing $direction from $source to $dest," +
                "event=${event.payload::class.java}, session/seq=${event.sessionId}/${event.sequenceNum}")

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
