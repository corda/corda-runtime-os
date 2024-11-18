package net.corda.flow.application.messaging

import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.messaging.ExternalMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.util.UUID

@Suppress("Unused")
@Component(service = [ExternalMessaging::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE)
class ExternalMessagingImpl(
    private val flowFiberService: FlowFiberService,
    private val idFactoryFunc: () -> String,
    cordaAvroSerializationFactory: CordaAvroSerializationFactory
) : ExternalMessaging, UsedByFlow, SingletonSerializeAsToken {

    private val maxAllowedMessageSize: Long = flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint.maxMessageSize
    private val serializer = cordaAvroSerializationFactory.createAvroSerializer<Any>()

    @Activate
    constructor(
        @Reference(service = FlowFiberService::class)
        flowFiberService: FlowFiberService,
        @Reference(service = CordaAvroSerializationFactory::class)
        cordaAvroSerializationFactory: CordaAvroSerializationFactory
    ) : this(flowFiberService, { UUID.randomUUID().toString() }, cordaAvroSerializationFactory)

    @Suspendable
    override fun send(channelName: String, message: String) {
        val bytesSize = serializer.serialize(message)
        if (bytesSize != null && maxAllowedMessageSize < bytesSize.size) {
            throw CordaRuntimeException("Cannot send external messaging content as " +
                    "it exceeds the max message size allowed. Message Size: [${bytesSize.size}], Max Size: [$maxAllowedMessageSize}]")
        }
        send(channelName, idFactoryFunc(), message)
    }

    @Suspendable
    override fun send(channelName: String, messageId: String, message: String) {
        flowFiberService
            .getExecutingFiber()
            .suspend(FlowIORequest.SendExternalMessage(channelName, messageId, message))
    }
}

