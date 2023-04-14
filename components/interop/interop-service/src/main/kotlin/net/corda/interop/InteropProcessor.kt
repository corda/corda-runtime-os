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
        private const val POSTFIX_DENOTING_ALIAS = " Alias"
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
            val (destinationAlias, oldSource) = if(sessionEvent.isInitiatingIdentityDestination()) {
                Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
            } else {
                Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
            }
            logEntering("INBOUND", oldSource, destinationAlias, sessionEvent)

            val realHoldingIdentity = getRealHoldingIdentityFromAliasMapping(
                InteropAliasProcessor.getRealHoldingIdentity(destinationAlias.toCorda().x500Name.toString()))

            if (realHoldingIdentity == null) {
                logger.warn("Could not find a holding identity for alias $destinationAlias.")
                return StateAndEventProcessor.Response(state, emptyList())
            }

            //TODO payload contains a facadeName, will be change by CORE-10419
            val facadeName = when (val sessionPayload = sessionEvent.payload) {
                is SessionInit -> null
                is SessionData -> {
                    val payload : ByteBuffer = sessionPayload.payload as ByteBuffer
                    val message = String(payload.array())
                    message
                }
                else -> null
            }

            if (facadeName == null) {
                logger.info("Pass-through event ${sessionEvent.payload::class.java} without FacadeRequest")
            } else {
                logger.info("Processing message from flow.interop.event with subsystem $SUBSYSTEM." +
                            " Key: ${event.key}, facade request: $facadeName."
                )
                val flowName = try {
                    facadeToFlowMapperService.getFlowName(realHoldingIdentity, facadeName, "say-hello")
                } catch (e: IllegalStateException) {
                    logger.warn(e.message)
                }
                //TODO utilise flowName as input to data send to FlowProcessor (for now it's only used by the logger),
                // this change is required for CORE-10426 Support For Façade Handlers
                logger.info("Flow name associated with facade request : $flowName")
            }

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
                                    x500Name = realHoldingIdentity.x500Name.toString()
                                    groupId = realHoldingIdentity.groupId
                                }
                            }
                            if (isInitiatedIdentityDestination()) {
                                initiatedIdentity = initiatedIdentity.apply {
                                    x500Name = realHoldingIdentity.x500Name.toString()
                                    groupId = realHoldingIdentity.groupId
                                }
                            }
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
                groupId = state?.groupId ?: INTEROP_GROUP_ID // TODO CORE-12208 For the first message need to find an alias group,
            // as FlowProcessor assigns a real group of the source
            }
            val translatedDestination = destinationIdentity.apply {
                //TODO review destination from initiating flow vs from initiated
                val name = this.toCorda()
                if (!name.toString().contains(POSTFIX_DENOTING_ALIAS)) {
                    //TODO CORE-12208 this should be always an alias (set by API or kept by responder flow)
                    x500Name = addAliasSubstringToOrganisationName(name).x500Name.toString()
                }
                groupId = INTEROP_GROUP_ID // TODO CORE-12208 For the first message need to find an alias group,
                // as FlowProcessor assigns a real group of the source,
                // for the following messages it should already have a alias group worth comparing with the session
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
        val x500Name = memberProvidedContext.get("corda.interop.mapping.x500name") ?: return null
        val groupId = memberProvidedContext.get("corda.interop.mapping.group") ?: return null
        return HoldingIdentity(MemberX500Name.parse(x500Name), groupId)
    }

    /**
     * Get the source and destination holding identity from the [sessionEvent].
     * @param sessionEvent Session event to extract identities from
     * @return Source and destination identities for a SessionEvent message.
     */
    private fun getSourceAndDestinationIdentity(sessionEvent: SessionEvent):
            Pair<net.corda.data.identity.HoldingIdentity, net.corda.data.identity.HoldingIdentity> =
         if (sessionEvent.sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)) {
             Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
         } else {
             Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
         }

    private fun SessionEvent.isInitiatingIdentityDestination() = !sessionId.contains(Constants.INITIATED_SESSION_ID_SUFFIX)
    private fun SessionEvent.isInitiatedIdentityDestination() = !isInitiatingIdentityDestination()
}
