package net.corda.flow.application.messaging

import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.messaging.ExternalMessaging
import net.corda.v5.base.annotations.Suspendable
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
    private val idFactoryFunc: () -> String
) : ExternalMessaging, UsedByFlow, SingletonSerializeAsToken {

    @Activate
    constructor(
        @Reference(service = FlowFiberService::class)
        flowFiberService: FlowFiberService
    ) : this(flowFiberService, { UUID.randomUUID().toString() })

    @Suspendable
    override fun send(channelName: String, message: String) {
        send(channelName, idFactoryFunc(), message)
    }

    @Suspendable
    override fun send(channelName: String, messageId: String, message: String) {
        flowFiberService
            .getExecutingFiber()
            .suspend(FlowIORequest.SendExternalMessage(channelName, messageId, message))
    }
}

