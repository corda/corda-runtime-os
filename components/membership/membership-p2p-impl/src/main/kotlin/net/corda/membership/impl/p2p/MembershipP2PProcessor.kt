package net.corda.membership.impl.p2p

import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.membership.impl.p2p.handler.MessageHandler
import net.corda.membership.impl.p2p.handler.RegistrationRequestHandler
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.p2p.app.AppMessage
import net.corda.p2p.app.AuthenticatedMessage
import net.corda.p2p.app.UnauthenticatedMessage
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import java.lang.reflect.Constructor
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

class MembershipP2PProcessor(
    private val avroSchemaRegistry: AvroSchemaRegistry
) : DurableProcessor<String, AppMessage> {
    override val keyClass = String::class.java
    override val valueClass = AppMessage::class.java

    companion object {
        val logger = contextLogger()

        const val MEMBERSHIP_P2P_SUBSYSTEM = "membership"
    }

    private val messageProcessorFactories: Map<Class<MembershipRegistrationRequest>, () -> MessageHandler> = mapOf(
        MembershipRegistrationRequest::class.java to { RegistrationRequestHandler(avroSchemaRegistry) }
    )

    override fun onNext(events: List<Record<String, AppMessage>>): List<Record<*, *>> {
        return events.mapNotNull { it.value?.message }
            .filter { it.isMembershipSubsystem() }
            .mapNotNull { msg ->
                val classType = try {
                    avroSchemaRegistry.getClassType(msg.payload)
                } catch (ex: UnsupportedOperationException) {
                    logger.error("Error parsing message payload.", ex)
                    null
                } catch (ex: CordaRuntimeException) {
                    logger.error("Error parsing membership message payload", ex)
                    null
                } catch (ex: Exception) {
                    logger.error("Unexpected exception occurred.", ex)
                    null
                }
                val processor = try {
                    classType?.let { getHandler(it) }
                } catch (ex: MembershipP2PException) {
                    logger.error("Could not get handler for request.", ex)
                    null
                }
                if (processor == null) {
                    logger.warn("No processor found for message of type $classType.")
                }
                processor?.invoke(
                    msg.header,
                    msg.payload
                )
            }
    }

    private fun getHandler(requestClass: Class<*>): MessageHandler {
        val factory = messageProcessorFactories[requestClass]
            ?: throw MembershipP2PException(
                "No handler has been registered to handle the p2p request received." +
                        "Request received: [$requestClass]"
            )
        return factory.invoke()
    }

    class MembershipP2PException(msg: String) : CordaRuntimeException(msg)

    private fun Any.isMembershipSubsystem(): Boolean {
        return (this as? AuthenticatedMessage)?.isMembershipSubsystem() ?: false
                || (this as? UnauthenticatedMessage)?.isMembershipSubsystem() ?: false
    }

    private fun AuthenticatedMessage.isMembershipSubsystem() = header.subsystem == MEMBERSHIP_P2P_SUBSYSTEM
    private fun UnauthenticatedMessage.isMembershipSubsystem() = header.subsystem == MEMBERSHIP_P2P_SUBSYSTEM

    private val Any.header: Any
        get() = (this as? AuthenticatedMessage)?.header
            ?: (this as? UnauthenticatedMessage)?.header
            ?: throw UnsupportedOperationException(
                "Tried to get header from message other than AuthenticatedMessage or UnauthenticatedMessage."
            )

    private val Any.payload: ByteBuffer
        get() = (this as? AuthenticatedMessage)?.payload
            ?: (this as? UnauthenticatedMessage)?.payload
            ?: throw UnsupportedOperationException(
                "Tried to get payload from message other than AuthenticatedMessage or UnauthenticatedMessage."
            )
}