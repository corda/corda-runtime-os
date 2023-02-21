package net.corda.interop

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.interop.InteropMessage
import net.corda.data.p2p.app.AppMessage
import net.corda.data.p2p.app.UnauthenticatedMessage
import net.corda.data.p2p.app.UnauthenticatedMessageHeader
import net.corda.interop.service.InteropMessageTransformer
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID

//Based on FlowP2PFilter
@Suppress("Unused")
class InteropProcessor(cordaAvroSerializationFactory: CordaAvroSerializationFactory) :
    DurableProcessor<String, AppMessage> {

    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val SUBSYSTEM = "interop"
    }

    private val cordaAvroDeserializer: CordaAvroDeserializer<InteropMessage> =
        cordaAvroSerializationFactory.createAvroDeserializer({}, InteropMessage::class.java)
    private val cordaAvroSerializer: CordaAvroSerializer<InteropMessage> = cordaAvroSerializationFactory.createAvroSerializer {}

    override fun onNext(
        events: List<Record<String, AppMessage>>
    ): List<Record<*, *>> {
        val outputEvents = mutableListOf<Record<*, *>>()
        events.forEach { appMessage ->
            val authMessage = appMessage.value?.message
            if (authMessage != null && authMessage is UnauthenticatedMessage && authMessage.header.subsystem == SUBSYSTEM) {
                getOutputRecord(authMessage.toCommonHeader(), authMessage.payload, appMessage.key)?.let { outputRecord ->
                    outputEvents.add(outputRecord)
                }
            }
        }
        return outputEvents
    }

    // Returns an OUTBOUND message to P2P layer, in the future it will pass a message to FlowProcessor
    private fun getOutputRecord(
        header: CommonHeader,
        payload: ByteBuffer,
        key: String
    ): Record<String, AppMessage>? {
        val interopMessage  = cordaAvroDeserializer.deserialize(payload.array())
        //following logging is added just check serialisation/de-serialisation result and can be removed later
        logger.info ( "Processing message from p2p.in with subsystem $SUBSYSTEM. Key: $key, facade request: $interopMessage" )
        return if (interopMessage != null) {
            val facadeRequest = InteropMessageTransformer.getFacadeRequest(interopMessage)
            logger.info("Converted interop message to facade request : $facadeRequest")
            val message : InteropMessage = InteropMessageTransformer.getInteropMessage(interopMessage.messageId, facadeRequest)
            logger.info("Converted facade request to interop message : $message")
            Record(Schemas.P2P.P2P_OUT_TOPIC, key, generateAppMessage(header, message, cordaAvroSerializer))
        } else {
            null
        }
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
            header.messageId + "-" + UUID.randomUUID(),
            SUBSYSTEM
        )
        return AppMessage(
            UnauthenticatedMessage(
                responseHeader,
                ByteBuffer.wrap(interopMessageSerializer.serialize(interopMessage))
            )
        )
    }

    //This class gathers common fields from both AuthenticateMessageHeader and UnauthenticatedMessageHeader
    data class CommonHeader(val destination: net.corda.data.identity.HoldingIdentity,
                            val source: net.corda.data.identity.HoldingIdentity,
                            val ttl: Instant? = null, val messageId: String, val traceId: String? = null,
                            val subsystem: String = SUBSYSTEM)

    private fun UnauthenticatedMessage.toCommonHeader() =
        CommonHeader(header.source, header.destination, null, header.messageId)
    }





