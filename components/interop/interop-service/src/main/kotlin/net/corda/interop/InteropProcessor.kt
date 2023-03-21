package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.interop.FacadeInvocation
import net.corda.schema.Schemas.Flow.FLOW_MAPPER_EVENT_TOPIC
import net.corda.session.manager.Constants
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant

//Based on FlowP2PFilter
@Suppress("LongParameterList", "Unused")
class InteropProcessor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val config: SmartConfig,
    private val facadeToFlowMapperService: InteropFacadeToFlowMapperService
) : DurableProcessor<String, AppMessage> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
    }

  //  private val cordaAvroDeserializer: CordaAvroDeserializer<InteropMessage> =
  //      cordaAvroSerializationFactory.createAvroDeserializer({}, InteropMessage::class.java)
  //  private val cordaAvroSerializer: CordaAvroSerializer<InteropMessage> = cordaAvroSerializationFactory.createAvroSerializer {}

    private val sessionAvroDeserializer: CordaAvroDeserializer<SessionEvent> = cordaAvroSerializationFactory.createAvroDeserializer({},
        SessionEvent::class.java)

    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> = events.mapNotNull { (_, key, value) ->
        val authMessage = value?.message as AuthenticatedMessage
         val header = with(authMessage.header) { CommonHeader(source, destination, null, messageId) }
        val realHoldingIdentity = InteropAliasProcessor.getRealHoldingIdentity(
            getRealHoldingIdentityFromAliasMapping(authMessage.header.destination.toCorda())
        )
        logger.info(
            "The alias ${authMessage.header.destination.x500Name} is mapped to the real holding identity $realHoldingIdentity"
        )
        getOutputRecord(header, authMessage.payload, key)
    }

    private fun getOutputRecord(
        header: CommonHeader,
        payload: ByteBuffer,
        key: String
    ): Record<String, FlowMapperEvent>? {
        val sessionEvent = sessionAvroDeserializer.deserialize(payload.array())

        if (sessionEvent!!.payload is SessionData) {
            return generateAppMessage(key, sessionEvent)
        }

        val interopMessage = sessionEvent.payload as? FacadeInvocation

        if (interopMessage == null) {
            logger.warn("Fail to converted interop message to facade request: empty payload")
            return null
        } else {
            logger.info("Processing message from p2p.in with subsystem $SUBSYSTEM. Key: $key, " +
                    "facade request: $interopMessage, header $header.")
        }
        logger.info("Converted interop message to facade request: facade=${interopMessage.facadeName}, " +
                "method=${interopMessage.methodName}")

        val flowName = facadeToFlowMapperService.getFlowName(
            HoldingIdentity(
                MemberX500Name.parse(header.destination.x500Name),
                header.destination.groupId
            ), interopMessage.facadeName, interopMessage.methodName
        )
        logger.info("Flow name associated with facade request : $flowName")

        return generateAppMessage(key, sessionEvent)
    }

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java

    private fun generateAppMessage(
        key: String,
        sessionEvent: SessionEvent?
    ): Record<String, FlowMapperEvent>? {

        logger.info("Generating output message: key: $key, Event: $sessionEvent")
        return if (sessionEvent != null) {
            sessionEvent.messageDirection = MessageDirection.INBOUND
            val sessionId = toggleSessionId(key)
            sessionEvent.sessionId = sessionId
            Record(FLOW_MAPPER_EVENT_TOPIC, sessionId, FlowMapperEvent(sessionEvent))
        } else {
            null
        }
    }

    private fun getRealHoldingIdentityFromAliasMapping(fakeHoldingIdentity: HoldingIdentity): String? {
        val groupReader = membershipGroupReaderProvider.getGroupReader(fakeHoldingIdentity)
        val memberInfo = groupReader.lookup(fakeHoldingIdentity.x500Name)
        return memberInfo?.memberProvidedContext?.get(MemberInfoExtension.INTEROP_ALIAS_MAPPING)
    }

    //The class gathers common fields of UnauthenticatedMessageHeader and AuthenticateMessageHeader
    data class CommonHeader(
        val source: net.corda.data.identity.HoldingIdentity,
        val destination: net.corda.data.identity.HoldingIdentity,
        val ttl: Instant? = null,
        val messageId: String,
        val traceId: String? = null,
        val subsystem: String = SUBSYSTEM
    )


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
