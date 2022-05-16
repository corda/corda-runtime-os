package net.corda.flow.pipeline.impl

import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.session.manager.SessionManager
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowGlobalPostProcessor::class])
class FlowGlobalPostProcessorImpl @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowGlobalPostProcessor {

    private companion object {
        val log = contextLogger()
    }

    override fun postProcess(context: FlowEventContext<Any>): FlowEventContext<Any> {

        val now = Instant.now()

        val checkpoint = context.checkpoint

        var outputRecords = if (checkpoint.doesExist) {
            postProcessRetries(context) + checkpoint.sessions
                .map { sessionState ->
                    sessionManager.getMessagesToSend(
                        sessionState,
                        now,
                        context.config,
                        checkpoint.flowKey.identity
                    )
                }
                .onEach { (updatedSessionState, _) -> checkpoint.putSessionState(updatedSessionState) }
                .flatMap { (_, events) -> events }
                .map { event -> flowRecordFactory.createFlowMapperSessionEventRecord(event) }
        }else{
            listOf()
        }

        return context.copy(outputRecords = context.outputRecords + outputRecords)
    }

    private fun postProcessRetries(context: FlowEventContext<Any>): List<Record<*, *>> {
        /**
         * When the flow enters a retry state the flow status is updated to "RETRYING", this
         * needs to be set back when a retry clears, however we only need to do this if the flow
         * is still running, if it is now complete the status will have been updated already
         */

        val checkpoint = context.checkpoint

        // The flow was not in a retry state so nothing to do
        if (!checkpoint.inRetryState) {
            return listOf()
        }

        // If we reach the post-processing step with a retry set we
        // assume whatever the previous retry was it has now cleared
        log.debug("The Flow was in a retry state that has now cleared.")
        checkpoint.markRetrySuccess()

        // If the flow has been completed, no need to update the status
        if (!checkpoint.doesExist) {
            return listOf()
        }

        val status = flowMessageFactory.createFlowStartedStatusMessage(checkpoint)
        return listOf(
            flowRecordFactory.createFlowStatusRecord(status)
        )
    }
}