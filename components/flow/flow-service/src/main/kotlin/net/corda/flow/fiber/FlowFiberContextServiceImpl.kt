package net.corda.flow.fiber

import net.corda.flow.fiber.context.FlowFiberContextService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowFiberContextService::class, SingletonSerializeAsToken::class])
class FlowFiberContextServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
): FlowFiberContextService, SingletonSerializeAsToken {

    override fun get(): FlowFiberExecutionContext {
        return flowFiberService.getExecutingFiber().getExecutionContext()
    }
}