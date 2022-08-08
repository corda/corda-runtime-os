package net.corda.flow.external.events.impl.executor

import co.paralleluniverse.fibers.Suspendable
import java.util.*
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.handler.ExternalEventHandler
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.base.util.uncheckedCast
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ExternalEventExecutor::class, SingletonSerializeAsToken::class])
class ExternalEventExecutorImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : ExternalEventExecutor, SingletonSerializeAsToken {

    @Suspendable
    override fun <PARAMETERS : Any, RESPONSE, RESUME> execute(
        requestId: String,
        handlerClass: Class<out ExternalEventHandler<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        return uncheckedCast(
            flowFiberService.getExecutingFiber().suspend(
                FlowIORequest.ExternalEvent(
                    requestId,
                    handlerClass,
                    parameters
                )
            )
        )
    }

    @Suspendable
    override fun <PARAMETERS : Any, RESPONSE, RESUME> execute(
        handlerClass: Class<out ExternalEventHandler<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        return execute(UUID.randomUUID().toString(), handlerClass, parameters)
    }
}