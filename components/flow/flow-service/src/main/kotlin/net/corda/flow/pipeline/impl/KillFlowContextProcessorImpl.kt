package net.corda.flow.pipeline.impl

import java.time.Instant
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.KillFlowContextProcessor
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [KillFlowContextProcessor::class])
class KillFlowContextProcessorImpl @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : KillFlowContextProcessor {

    override fun createKillFlowContext(context: FlowEventContext<Any>, details: Map<String, String>?): FlowEventContext<Any> {
        val now = Instant.now()
        return context.copy(
            outputRecords = createFlowMapperSessionKilledEvents(context, now) + createFlowKilledStatusRecords(context, details),
            sendToDlq = false // killed flows do not go to DLQ
        )
    }

    private fun createFlowMapperSessionKilledEvents(context: FlowEventContext<Any>, now: Instant): List<Record<*, FlowMapperEvent>> {
        val expiryTime = getScheduledCleanupExpiryTime(context, now)
        return context.checkpoint.sessions
            // not necessary to clean up sessions that are already closed or errored.
            .filterNot { sessionState -> sessionState.status == SessionStateType.CLOSED || sessionState.status == SessionStateType.ERROR }
            .onEach { sessionState -> sessionState.hasScheduledCleanup = true }
            .map {
                flowRecordFactory.createFlowMapperEventRecord(
                    it.sessionId,
                    ScheduleCleanup(expiryTime)
                )
            }
    }

    private fun createFlowKilledStatusRecords(context: FlowEventContext<Any>, details: Map<String, String>?): Record<FlowKey, FlowStatus> {
        return flowRecordFactory.createFlowStatusRecord(
            flowMessageFactory.createFlowKilledStatusMessage(context.checkpoint, details)
        )
    }

    private fun getScheduledCleanupExpiryTime(context: FlowEventContext<Any>, now: Instant): Long {
        val flowCleanupTime = context.config.getLong(FlowConfig.SESSION_FLOW_CLEANUP_TIME)
        return now.plusMillis(flowCleanupTime).toEpochMilli()
    }
}