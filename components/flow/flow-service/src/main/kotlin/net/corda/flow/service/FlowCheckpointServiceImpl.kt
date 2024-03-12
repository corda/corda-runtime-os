package net.corda.flow.service

import net.corda.flow.application.services.FlowCheckpointService
import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowCheckpointService::class])
class FlowCheckpointServiceImpl @Activate constructor(
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : FlowCheckpointService {
    override fun getCheckpoint(): FlowCheckpoint {
        return flowFiberService.getExecutingFiber().getExecutionContext().flowCheckpoint
    }

}