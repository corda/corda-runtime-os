package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.interop.InteropMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.data.p2p.app.MembershipStatusFilter
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.session.manager.Constants
import net.corda.virtualnode.HoldingIdentity
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
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
    private val sessionEventSerializer: CordaAvroSerializer<SessionEvent> =
        cordaAvroSerializationFactory.createAvroSerializer{}

    override fun onNext(
        events: List<Record<String, FlowMapperEvent>>
    ): List<Record<*, *>> = events.mapNotNull { (_, _, value) ->
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
            println(destinationAlias)
            null
        } else { //MessageDirection.OUTBOUND
            //TODO taken from FlowMapperHelper function generateAppMessage
            val header = AuthenticatedMessageHeader(
                destinationIdentity,
                sourceIdentity,
                //TODO adding FLOW_CONFIG to InteropService breaks InteropDataSetupIntegrationTest, use hardcoded 500000 for now
                Instant.ofEpochMilli(sessionEvent.timestamp.toEpochMilli() + 500000),//+ config.getLong(FlowConfig.SESSION_P2P_TTL)),
                sessionEvent.sessionId + "-" + UUID.randomUUID(),
                "",
                Constants.FLOW_SESSION_SUBSYSTEM,
                MembershipStatusFilter.ACTIVE
            )
            println(header)
            null
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
