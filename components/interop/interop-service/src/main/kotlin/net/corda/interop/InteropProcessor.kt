package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.interop.InteropMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.interop.service.InteropAliasTranslator
import net.corda.interop.service.impl.InteropAliasTranslatorImpl
import net.corda.interop.service.InteropFacadeToFlowMapperService
import net.corda.interop.service.impl.InteropMessageTransformer
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.lib.MemberInfoExtension
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.schema.Schemas
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toCorda
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

//Based on FlowP2PFilter
@Suppress("Unused")
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

    private val cordaAvroDeserializer: CordaAvroDeserializer<InteropMessage> =
        cordaAvroSerializationFactory.createAvroDeserializer({}, InteropMessage::class.java)
    private val cordaAvroSerializer: CordaAvroSerializer<InteropMessage> = cordaAvroSerializationFactory.createAvroSerializer {}
    private val interopAliasTranslator: InteropAliasTranslator =
        InteropAliasTranslatorImpl(lifecycleCoordinatorFactory, subscriptionFactory, config)

    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> = events.mapNotNull { (_, key, value) ->
        val unAuthMessage = value?.message
        //TODO temporary using UnauthenticatedMessage instead of AuthenticatedMessage
        if (unAuthMessage == null ||
            unAuthMessage !is UnauthenticatedMessage ||
            unAuthMessage.header.subsystem != SUBSYSTEM
        ) return@mapNotNull null
        //TODO consider checking SUBSYSTEM for other message types and log warning if they have SUBSYSTEM=Interop but
        // they are of not the expected type (not UnauthenticatedMessage)

        val header = with(unAuthMessage.header) { CommonHeader(source, destination, null, messageId) }
        logger.info(
            "The alias ${unAuthMessage.header.destination.x500Name} is mapped to the real holding identity ${
                interopAliasTranslator.getRealHoldingIdentity(
                    getRealHoldingIdentityFromAliasMapping(unAuthMessage.header.destination.toCorda())
                )}"
        )
        getOutputRecord(header, unAuthMessage.payload, key)
    }

    // Returns an OUTBOUND message to P2P layer, in the future it will pass a message to FlowProcessor
    private fun getOutputRecord(
        header: CommonHeader,
        payload: ByteBuffer,
        key: String
    ): Record<String, AppMessage>? {
        val interopMessage = cordaAvroDeserializer.deserialize(payload.array())

        //following logging is added just check serialisation/de-serialisation result and can be removed later
        logger.info("Processing message from p2p.in with subsystem $SUBSYSTEM. Key: $key, facade request: $interopMessage, header $header.")
        if (interopMessage == null) {
            logger.warn("Fail to converted interop message to facade request: empty payload")
            return null
        }

        //TODO temporary logic for seed messages only, to process the first 10 messages as more is not required
        // this check will be phased out as part of eliminating seed messages in CORE-10446
        if (interopMessage.messageId.startsWith("seed-message")
            && ((interopMessage.messageId.extractInt() ?: 0) > 10)) return null

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
        return Record(Schemas.P2P.P2P_OUT_TOPIC, key, result)
    }

    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java

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
}
