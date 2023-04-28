package net.corda.flow.pipeline.handlers.requests.helper

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import org.apache.avro.specific.SpecificRecordBase
import java.time.Instant

    /**
     * When a flow is started by the REST api, a FlowKey is sent to the Flow mapper, so we send a cleanup event for this key.
     *
     * Flows triggered by SessionInit don't have this FlowKey, so we do not send a cleanup event
     */
    fun getRecords(
        flowRecordFactory: FlowRecordFactory,
        context: FlowEventContext<Any>,
        status: FlowStatus
    ): List<Record<out Any, out SpecificRecordBase>> {
        val checkpoint = context.checkpoint
        val records = if (checkpoint.flowStartContext.initiatorType == FlowInitiatorType.RPC) {
            val flowCleanupTime = context.config.getLong(FlowConfig.PROCESSING_FLOW_CLEANUP_TIME)
            val expiryTime = Instant.now().plusMillis(flowCleanupTime).toEpochMilli()
            listOf(
                flowRecordFactory.createFlowStatusRecord(status), flowRecordFactory.createFlowMapperEventRecord(
                    checkpoint.flowKey.toString(), ScheduleCleanup(expiryTime)
                )
            )
        } else {
            listOf(
                flowRecordFactory.createFlowStatusRecord(status)
            )
        }
        return records
    }

