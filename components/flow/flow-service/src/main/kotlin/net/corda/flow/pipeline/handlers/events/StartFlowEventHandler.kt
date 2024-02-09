package net.corda.flow.pipeline.handlers.events

import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.pipeline.CheckpointInitializer
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.data.flow.state.waiting.WaitingForStartFlow
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [FlowEventHandler::class])
class StartFlowEventHandler @Activate constructor(
    @Reference(service = CheckpointInitializer::class)
    private val checkpointInitializer: CheckpointInitializer
) : FlowEventHandler<StartFlow> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override val type = StartFlow::class.java

    override fun preProcess(context: FlowEventContext<StartFlow>): FlowEventContext<StartFlow> {
        log.info("Flow [${context.checkpoint.flowId}] started")

        checkpointInitializer.initialize(
            context.checkpoint,
            WaitingFor(WaitingForStartFlow()),
            context.inputEventPayload.startContext.identity.toCorda(),
        ) {
            context.inputEventPayload.startContext
        }

        context.flowMetrics.flowStarted()

        return context
    }
}
