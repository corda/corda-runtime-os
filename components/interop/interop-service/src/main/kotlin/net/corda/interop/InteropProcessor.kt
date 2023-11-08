package net.corda.interop

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.interop.InteropMessage
import net.corda.data.interop.InteropState
import net.corda.data.interop.InteropStateType
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.libs.configuration.SmartConfig
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.P2P.P2P_OUT_TOPIC
import net.corda.schema.configuration.FlowConfig
import net.corda.session.manager.Constants
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.event.session.SessionInit
import net.corda.data.interop.InteropProcessorEvent
import net.corda.interop.identity.registry.InteropIdentityRegistryService
import net.corda.membership.lib.MemberInfoExtension
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_SESSION_IN

class InteropProcessor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val facadeToFlowMapperService: InteropFacadeToFlowMapperService,
    private val interopIdentityRegistryService: InteropIdentityRegistryService,
    private val flowConfig: SmartConfig
) : StateAndEventProcessor<String, InteropState, InteropProcessorEvent> {

    override val keyClass = String::class.java
    override val stateValueClass = InteropState::class.java
    override val eventValueClass = InteropProcessorEvent::class.java

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
        private const val INTEROP_RESPONDER_FLOW = "INTEROP_RESPONDER_FLOW"
    }

    private val interopAvroDeserializer: CordaAvroDeserializer<InteropMessage> =
        cordaAvroSerializationFactory.createAvroDeserializer({}, InteropMessage::class.java)
    private val sessionEventSerializer: CordaAvroSerializer<SessionEvent> =
        cordaAvroSerializationFactory.createAvroSerializer{}

    private fun getInitPayload(payload: Any) = when (payload) {
        is SessionInit -> payload
        is SessionData -> payload.sessionInit
        else -> null
    }

    @Suppress("ThrowsCount")
    private fun processInboundEvent(state: StateAndEventProcessor.State<InteropState>?, eventKey: String, sessionEvent: SessionEvent):
            StateAndEventProcessor.Response<InteropState> {

        val (destinationInteropIdentity, sourceInteropIdentity) = if (sessionEvent.isInitiatingIdentityDestination()) {
            Pair(sessionEvent.initiatingIdentity, sessionEvent.initiatedIdentity)
        } else {
            Pair(sessionEvent.initiatedIdentity, sessionEvent.initiatingIdentity)
        }

        logEntering("INBOUND", sourceInteropIdentity, destinationInteropIdentity, sessionEvent)

        val realDestinationIdentity = lookupOwningIdentity(destinationInteropIdentity.toCorda())

        val sessionPayload = sessionEvent.payload
        val sessionInit = getInitPayload(sessionPayload)

        // If the session init field is present
        val updatedPayload = if (sessionInit != null) {
            val parameters = InteropSessionParameters.fromContextUserProperties(sessionInit.contextUserProperties)

            logger.info(
                "Processing message from flow.interop.event with subsystem $SUBSYSTEM. " +
                "Key: $eventKey, facade request: ${parameters.facadeId}/${parameters.facadeMethod}")

            val flowName = try {
                facadeToFlowMapperService.getFlowName(realDestinationIdentity, parameters.facadeId, parameters.facadeMethod)
            } catch (e: IllegalStateException) {
                throw InteropProcessorException("Failed to find responder flow for facade ${parameters.facadeId}.", state, e)
            }

            logger.info("Mapped flowName=$flowName for facade=${parameters.facadeId}/${parameters.facadeMethod}")

            (sessionPayload as SessionData).apply {
                this.sessionInit = sessionInit.apply {
                    contextUserProperties = KeyValuePairList(
                        contextUserProperties.items + KeyValuePair(INTEROP_RESPONDER_FLOW, flowName)
                    )
                }
            }
        } else {
            logger.info("Pass-through non session init event ${sessionEvent.payload::class.java} without FacadeRequest")
            sessionPayload
        }

        return StateAndEventProcessor.Response(
            StateAndEventProcessor.State(
                    InteropState(
                    UUID.randomUUID().toString(),
                    null,
                    InteropStateType.VALID,
                    destinationInteropIdentity.x500Name.toString(),
                    destinationInteropIdentity.groupId
                ),
                state?.metadata
            ),
            listOf(
                Record(
                    FLOW_MAPPER_SESSION_IN,
                    sessionEvent.sessionId,
                    FlowMapperEvent(sessionEvent.apply {
                        if (isInitiatingIdentityDestination()) {
                            initiatingIdentity = initiatingIdentity.apply {
                                x500Name = realDestinationIdentity.x500Name.toString()
                                groupId = realDestinationIdentity.groupId
                            }
                        }
                        if (isInitiatedIdentityDestination()) {
                            initiatedIdentity = initiatedIdentity.apply {
                                x500Name = realDestinationIdentity.x500Name.toString()
                                groupId = realDestinationIdentity.groupId
                            }
                        }
                        val (newDest, newSource) = if (isInitiatingIdentityDestination()) {
                            Pair(initiatingIdentity, initiatedIdentity)
                        } else {
                            Pair(initiatedIdentity, initiatingIdentity)
                        }
                        payload = updatedPayload
                        logLeaving("INBOUND", newSource, newDest, sessionEvent)
                    })
                )
            )
        )
    }

    @Suppress("ThrowsCount")
    private fun processOutboundEvent(state: StateAndEventProcessor.State<InteropState>?, sessionEvent: SessionEvent):
            StateAndEventProcessor.Response<InteropState> {

        val (sourceIdentity, destinationIdentity) = getSourceAndDestinationIdentity(sessionEvent)

        logEntering("OUTBOUND", sourceIdentity, destinationIdentity, sessionEvent)

        val sessionPayload = sessionEvent.payload

        // If group ID is null, get the group ID from the initial session data event
        val interopGroupId = if (state?.value?.groupId == null) {
            assert(sessionPayload is SessionData) {
                "State group ID is null and no session data event."
            }
            val sessionData = sessionPayload as SessionData
            val sessionInit = requireNotNull(sessionData.sessionInit) {
                "Session init property missing from initial session data event."
            }
            val parameters = InteropSessionParameters.fromContextUserProperties(sessionInit.contextUserProperties)
            parameters.interopGroupId
        } else {
            state.value?.groupId?.let { UUID.fromString(it) } ?: throw InteropProcessorException(
                "Interop group ID missing from InteropProcessor state. ", state
            )
        }

        val sourceIdentityShortHash = sourceIdentity.toCorda().shortHash
        val sourceRegistryView = interopIdentityRegistryService.getVirtualNodeRegistryView(sourceIdentityShortHash)

        // The translated source is our source identity in the given interop group
        val translatedSource = sourceIdentity.apply {
            val interopIdentity = checkNotNull(sourceRegistryView.getOwnedIdentity(interopGroupId)) {
                "Source identity '${this.x500Name}' does not own an interop identity in interop group '$interopGroupId'"
            }
            this.x500Name = interopIdentity.x500Name
            this.groupId = interopGroupId.toString()
        }

        // The translated destination is the non-owned interop identity within the given group which has the
        // given destination interop identity x500 name
        val translatedDestination = destinationIdentity.apply {
            val groupIdentities = checkNotNull(sourceRegistryView.getIdentitiesByGroupId(interopGroupId)) {
                "No identities in group '$interopGroupId' found within view of source identity '$sourceIdentity'"
            }
            val destinationIdentityList = groupIdentities.filter {
                it.shortHash != sourceIdentityShortHash && it.x500Name == this.x500Name
            }
            if (destinationIdentityList.size > 1) {
                throw IllegalStateException(
                    "Multiple destination identity candidates found with name '${this.x500Name}'"
                )
            } else if (destinationIdentityList.isEmpty()) {
                throw IllegalStateException(
                    "No destination identity with name '${this.x500Name}' found"
                )
            }
            this.x500Name = destinationIdentityList.single().x500Name
            this.groupId = interopGroupId.toString()
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
                                Instant.ofEpochMilli(
                                sessionEvent.timestamp.toEpochMilli() + flowConfig.getLong(FlowConfig.SESSION_P2P_TTL)),
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

    override fun onNext(
        state: StateAndEventProcessor.State<InteropState>?,
        event: Record<String, InteropProcessorEvent>
    ): StateAndEventProcessor.Response<InteropState> {
        val eventPayload = event.value?.payload

        if (eventPayload == null) {
            logger.warn("Dropping message with empty payload.")
            return StateAndEventProcessor.Response(state, emptyList())
        }

        if (eventPayload !is SessionEvent) {
            logger.warn("Dropping message with payload of type ${eventPayload::class.java}, required SessionEvent type.")
            return StateAndEventProcessor.Response(state, emptyList())
        }

        return try {
            if (eventPayload.messageDirection == MessageDirection.INBOUND) {
                processInboundEvent(state, event.key, eventPayload)
            } else {
                processOutboundEvent(state, eventPayload)
            }
        } catch (e: InteropProcessorException) {
            logger.warn("${e.message} Key: ${event.key}")
            return StateAndEventProcessor.Response(e.state, emptyList())
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

    private fun lookupOwningIdentity(interopIdentity: HoldingIdentity): HoldingIdentity {
        val errorPrefix: String by lazy { "Failed to find owning identity of '${interopIdentity.x500Name}'" }

        val membershipGroupReader = membershipGroupReaderProvider.getGroupReader(interopIdentity)

        val memberInfo = checkNotNull(membershipGroupReader.lookup(interopIdentity.x500Name)) {
            "$errorPrefix, member info not found."
        }

        val memberProvidedContext = memberInfo.memberProvidedContext

        val x500NameString = memberProvidedContext.get(MemberInfoExtension.INTEROP_MAPPING_X500_NAME)
        val groupIdString = memberProvidedContext.get(MemberInfoExtension.INTEROP_MAPPING_GROUP)

        checkNotNull(x500NameString) {
            "$errorPrefix, holding identity x500 name is missing from member provided context."
        }

        checkNotNull(groupIdString) {
            "$errorPrefix, holding identity group id is missing from member provided context."
        }

        val x500Name = try {
            MemberX500Name.parse(x500NameString)
        } catch (e: Exception) {
            throw IllegalStateException(
                "$errorPrefix, x500 name '$x500NameString' from member provided context is invalid.", e)
        }

        try {
            UUID.fromString(groupIdString)
        } catch (e: Exception) {
            throw IllegalStateException(
                "$errorPrefix, groupId '$groupIdString' from member provided context is not a valid UUID.", e)
        }

        return HoldingIdentity(x500Name, groupIdString)
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
