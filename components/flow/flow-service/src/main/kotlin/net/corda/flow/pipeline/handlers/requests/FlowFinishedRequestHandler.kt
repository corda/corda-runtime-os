package net.corda.flow.pipeline.handlers.requests

import java.time.Instant
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.helper.recordFlowRuntimeMetric
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.minutes
import net.corda.v5.base.util.trace
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class FlowFinishedRequestHandler @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.FlowFinished> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.FlowFinished::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.FlowFinished): WaitingFor {
        return WaitingFor(null)
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.FlowFinished
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint
        log.trace { "Flow [${checkpoint.flowId}] completed successfully" }
        recordFlowRuntimeMetric(checkpoint, FlowStates.COMPLETED.toString())

        val status = flowMessageFactory.createFlowCompleteStatusMessage(checkpoint, request.result)

        val expiryTime = Instant.now().plusMillis(1.minutes.toMillis()).toEpochMilli() // TODO Should be configurable?
        val records = listOf(
            flowRecordFactory.createFlowStatusRecord(status),
            flowRecordFactory.createFlowMapperEventRecord(checkpoint.flowKey.toString(), ScheduleCleanup(expiryTime))
        )

        context.checkpoint.markDeleted()
        return context.copy(outputRecords = context.outputRecords + records)
    }
}
