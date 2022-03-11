package net.corda.flow.pipeline.factory.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.Checkpoint
import net.corda.flow.pipeline.factory.FlowMessageFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [FlowMessageFactory::class])
@Suppress("Unused")
class FlowMessageFactoryImpl(private val currentTimeProvider: () -> Instant) : FlowMessageFactory {

    @Activate
    constructor() : this(Instant::now)

    override fun createFlowCompleteStatusMessage(checkpoint: Checkpoint, flowResult: String?): FlowStatus {
        return getCommonFlowStatus(checkpoint).apply {
            flowStatus = FlowStates.COMPLETED
            result = flowResult
        }
    }

    override fun createFlowStartedStatusMessage(checkpoint: Checkpoint): FlowStatus {
        return getCommonFlowStatus(checkpoint).apply {
            flowStatus = FlowStates.RUNNING
        }
    }

    override fun createFlowFailedStatusMessage(checkpoint: Checkpoint, errorType: String, message: String): FlowStatus {
        return getCommonFlowStatus(checkpoint).apply {
            flowStatus =  FlowStates.COMPLETED
            error =  ExceptionEnvelope(errorType, message)
        }
    }

    private fun getCommonFlowStatus(checkpoint: Checkpoint):FlowStatus{
        val startContext = checkpoint.flowStartContext
        return FlowStatus().apply {
            key = startContext.statusKey
            initiatorType = startContext.initiatorType
            flowId =  checkpoint.flowKey.flowId
            flowClassName = startContext.flowClassName
            createdTimestamp =  startContext.createdTimestamp
            lastUpdateTimestamp =   currentTimeProvider()
        }
    }
}