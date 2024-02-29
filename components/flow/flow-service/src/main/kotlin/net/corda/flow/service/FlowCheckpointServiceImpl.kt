package net.corda.flow.service

import net.corda.flow.application.services.FlowCheckpointService
import net.corda.flow.fiber.FlowFiberExecutionContext
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Component

@Component(service = [FlowCheckpointService::class])
class FlowCheckpointServiceImpl(
    val flowFiberExecutionContext: FlowFiberExecutionContext
): FlowCheckpointService {
    override fun getCheckpoint(): FlowCheckpoint {
        return flowFiberExecutionContext.flowCheckpoint
    }

}