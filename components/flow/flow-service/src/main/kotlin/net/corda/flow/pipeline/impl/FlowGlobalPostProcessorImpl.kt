package net.corda.flow.pipeline.impl

import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowGlobalPostProcessor::class])
class FlowGlobalPostProcessorImpl @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowGlobalPostProcessor {

    override fun postProcess(context: FlowEventContext<Any>): FlowEventContext<Any> {

        val now = Instant.now()

        val checkpoint = context.checkpoint
        return if (checkpoint.doesExist) {
            val records = checkpoint.sessions
                .map { sessionState -> sessionManager.getMessagesToSend(sessionState, now, context.config, checkpoint.flowKey.identity) }
                .onEach { (updatedSessionState, _) -> checkpoint.putSessionState(updatedSessionState) }
                .flatMap { (_, events) -> events }
                .map { event -> flowRecordFactory.createFlowMapperSessionEventRecord(event) }

            context.copy(outputRecords = context.outputRecords + records)
        } else {
            context
        }
    }
}