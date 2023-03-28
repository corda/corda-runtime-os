package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.interop.InteropMessage
import net.corda.data.interop.InteropState
import net.corda.data.interop.InteropStateType
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.interop.service.impl.InteropMessageTransformer
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

//Based on FlowP2PFilter
@Suppress("LongParameterList", "Unused")
class InteropProcessor(
    cordaAvroSerializationFactory: CordaAvroSerializationFactory,
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory,
    private val subscriptionFactory: SubscriptionFactory,
    private val config: SmartConfig,
    private val facadeToFlowMapperService: InteropFacadeToFlowMapperService
) : StateAndEventProcessor<String, InteropState, AppMessage> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
    }

    private val cordaAvroDeserializer: CordaAvroDeserializer<InteropMessage> =
        cordaAvroSerializationFactory.createAvroDeserializer({}, InteropMessage::class.java)
    private val cordaAvroSerializer: CordaAvroSerializer<InteropMessage> = cordaAvroSerializationFactory.createAvroSerializer {}

    override fun onNext(
        state: InteropState?,
        event: Record<String, AppMessage>
    ): StateAndEventProcessor.Response<InteropState> {
        val unAuthMessage = event.value?.message
        //TODO temporary using UnauthenticatedMessage instead of AuthenticatedMessage
        if (unAuthMessage == null ||
            unAuthMessage !is UnauthenticatedMessage ||
            unAuthMessage.header.subsystem != SUBSYSTEM
        ) return StateAndEventProcessor.Response(state, emptyList())

        //TODO consider checking SUBSYSTEM for other message types and log warning if they have SUBSYSTEM=Interop but
        // they are of not the expected type (not UnauthenticatedMessage)
        return if (isValidState(state)) {
            val header = with(unAuthMessage.header) { CommonHeader(source, destination, null, messageId) }
            val realHoldingIdentity = InteropAliasProcessor.getRealHoldingIdentity(
                getRealHoldingIdentityFromAliasMapping(unAuthMessage.header.destination.toCorda())
            )
            logger.info(
                "The alias ${unAuthMessage.header.destination.x500Name} is mapped to the real holding identity $realHoldingIdentity"
            )
            getOutputRecord(state, header, unAuthMessage.payload, event.key)
        } else {
            StateAndEventProcessor.Response(state, emptyList())
        }
    }

    // Returns an OUTBOUND message to P2P layer, in the future it will pass a message to FlowProcessor
    private fun getOutputRecord(
        state: InteropState?,
        header: CommonHeader,
        payload: ByteBuffer,
        key: String
    ): StateAndEventProcessor.Response<InteropState> {
        val interopMessage = cordaAvroDeserializer.deserialize(payload.array())

        //following logging is added just check serialisation/de-serialisation result and can be removed later
        logger.info("Processing message from p2p.in with subsystem $SUBSYSTEM. Key: $key, facade request: $interopMessage, header $header.")
        if (interopMessage == null) {
            logger.warn("Fail to converted interop message to facade request: empty payload")
            return StateAndEventProcessor.Response(state, emptyList())
        }

        //TODO temporary logic for seed messages only, to process the first 10 messages as more is not required
        // this check will be phased out as part of eliminating seed messages in CORE-10446
        if (interopMessage.messageId.startsWith("seed-message")
            && ((interopMessage.messageId.extractInt() ?: 0) > 10))
            return StateAndEventProcessor.Response(state, emptyList())

        val facadeRequest = InteropMessageTransformer.getFacadeRequest(interopMessage)
        logger.info("Converted interop message to facade request : $facadeRequest")

        val flowName = facadeToFlowMapperService.getFlowName(
            HoldingIdentity(
                MemberX500Name.parse(header.destination.x500Name),
                header.destination.groupId
            ), facadeRequest.facadeId.toString(), facadeRequest.methodName
        )
        logger.info("Flow name associated with facade request : $flowName")

        val message: InteropMessage = InteropMessageTransformer.getInteropMessage(
                interopMessage.messageId.incrementOrUuid(), facadeRequest)
        logger.info("Converted facade request to interop message : $message")
        val result = generateAppMessage(header, message, cordaAvroSerializer)
        // Following is temporary hack to recognize the message as INBOUND message
        // If the message is INBOUND message we are creating new state else fetching the alias identity from the previous
        val returnState = if(header.destination.x500Name.contains("Alice Alias Alter Ego")) {
            InteropState(UUID.randomUUID().toString(), null, InteropStateType.VALID, header.destination.x500Name.toString())
        } else {
            logger.info(
                "The alias ${state?.aliasHoldingIdentity} is mapped to the real holding identity ${header.destination.x500Name}"
            )
            state
        }
        return StateAndEventProcessor.Response(returnState, listOf(Record(Schemas.P2P.P2P_OUT_TOPIC, key, result)))
    }

    private fun generateAppMessage(
        header: CommonHeader,
        interopMessage: InteropMessage,
        interopMessageSerializer: CordaAvroSerializer<InteropMessage>
    ): AppMessage {
        val responseHeader = UnauthenticatedMessageHeader(
            header.source,
            header.destination,
            SUBSYSTEM,
            header.messageId.incrementOrUuid()
        )
        logger.info("Generating output message: header=$responseHeader, payload=$interopMessage")
        return AppMessage(
            UnauthenticatedMessage(
                responseHeader,
                ByteBuffer.wrap(interopMessageSerializer.serialize(interopMessage))
            )
        )
    }

    private fun getRealHoldingIdentityFromAliasMapping(fakeHoldingIdentity: HoldingIdentity): String? {
        val groupReader = membershipGroupReaderProvider.getGroupReader(fakeHoldingIdentity)
        val memberInfo = groupReader.lookup(fakeHoldingIdentity.x500Name)
        return memberInfo?.memberProvidedContext?.get(MemberInfoExtension.INTEROP_ALIAS_MAPPING)
    }

    //Temporary function to increment message id to debug the lifecycle of seed messages
    private fun String.incrementOrUuid(): String =
        if (this.contains("-")) {
            val text = this.substringBeforeLast('-')
            val number = this.substringAfterLast('-')
            try {
                "$text-${number.toInt() + 1}"
            } catch (e: NumberFormatException) {
                "${UUID.randomUUID()}"
            }
        } else
            "${toInt() + 1}"

    //Temporary function to filter number from messageId to debug the lifecycle of seed messages
    private fun String.extractInt(): Int? =
        if (this.contains("-"))
            try {
                this.substringAfterLast('-').toInt()
            } catch (e: NumberFormatException) {
                null
            }
        else
            null

    //The class gathers common fields of UnauthenticatedMessageHeader and AuthenticateMessageHeader
    data class CommonHeader(
        val source: net.corda.data.identity.HoldingIdentity,
        val destination: net.corda.data.identity.HoldingIdentity,
        val ttl: Instant? = null,
        val messageId: String,
        val traceId: String? = null,
        val subsystem: String = SUBSYSTEM
    )

    override val keyClass = String::class.java
    override val stateValueClass = InteropState::class.java
    override val eventValueClass = AppMessage::class.java

    private fun isValidState(state: InteropState?): Boolean {
        return state == null || state.status == InteropStateType.VALID
    }
}
