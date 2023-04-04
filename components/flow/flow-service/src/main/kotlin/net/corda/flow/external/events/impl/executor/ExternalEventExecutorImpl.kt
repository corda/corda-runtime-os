package net.corda.flow.external.events.impl.executor

import java.util.UUID
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.flow.external.events.factory.ExternalEventFactory
import net.corda.flow.fiber.FlowFiber
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.v5.base.annotations.Suspendable
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
    override fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        requestId: String,
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        @Suppress("unchecked_cast")
        return with(flowFiberService.getExecutingFiber()) {
            suspend(
                FlowIORequest.ExternalEvent(
                    requestId,
                    factoryClass,
                    parameters,
                    externalContext(this)
                )
            )
        } as RESUME
    }

    private fun externalContext(flowFiber: FlowFiber): Map<String, String> =
        with(flowFiber.getExecutionContext().flowCheckpoint.flowContext) {
            localToExternalContextMapper(
                userContextProperties = this.flattenUserProperties(),
                platformContextProperties = this.flattenPlatformProperties()
            )
        }

    @Suspendable
    override fun <PARAMETERS : Any, RESPONSE : Any, RESUME> execute(
        factoryClass: Class<out ExternalEventFactory<PARAMETERS, RESPONSE, RESUME>>,
        parameters: PARAMETERS
    ): RESUME {
        return execute(UUID.randomUUID().toString(), factoryClass, parameters)
    }
}
