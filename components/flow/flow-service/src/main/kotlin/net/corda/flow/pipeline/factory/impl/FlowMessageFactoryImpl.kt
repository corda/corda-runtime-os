package net.corda.flow.pipeline.factory.impl

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.state.FlowCheckpoint
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import java.time.Instant

@Component(service = [FlowMessageFactory::class])
@Suppress("Unused")
class FlowMessageFactoryImpl(private val currentTimeProvider: () -> Instant) : FlowMessageFactory {

    @Activate
    constructor() : this(Instant::now)

    override fun createFlowCompleteStatusMessage(checkpoint: FlowCheckpoint, flowResult: String?): FlowStatus {
        return getCommonFlowStatus(checkpoint).apply {
            flowStatus = FlowStates.COMPLETED
            result = flowResult
        }
    }

    override fun createFlowStartedStatusMessage(checkpoint: FlowCheckpoint): FlowStatus {
        return getCommonFlowStatus(checkpoint).apply {
            flowStatus = FlowStates.RUNNING
        }
    }

    override fun createFlowRetryingStatusMessage(checkpoint: FlowCheckpoint): FlowStatus {
        return getCommonFlowStatus(checkpoint).apply {
            flowStatus = FlowStates.RETRYING
        }
    }

    override fun createFlowFailedStatusMessage(checkpoint: FlowCheckpoint, errorType: String, message: String): FlowStatus {
        return getCommonFlowStatus(checkpoint).apply {
            flowStatus = FlowStates.FAILED
            error = ExceptionEnvelope(errorType, message)
        }
    }

    override fun createFlowKilledStatusMessage(checkpoint: FlowCheckpoint, message: String?): FlowStatus {
        return getCommonFlowStatus(checkpoint).apply {
            flowStatus = FlowStates.KILLED
            message?.let { processingTerminatedReason = it }
        }
    }

    private fun getCommonFlowStatus(checkpoint: FlowCheckpoint): FlowStatus {
        val startContext = checkpoint.flowStartContext
        return FlowStatus().apply {
            key = startContext.statusKey
            initiatorType = startContext.initiatorType
            flowId = checkpoint.flowId
            flowClassName = startContext.flowClassName
            createdTimestamp = startContext.createdTimestamp
            lastUpdateTimestamp = currentTimeProvider()
        }
    }
}