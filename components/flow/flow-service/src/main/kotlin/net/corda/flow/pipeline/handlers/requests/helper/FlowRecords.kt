package net.corda.flow.pipeline.handlers.requests.helper

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import net.corda.virtualnode.toCorda
import java.time.Instant

/**
 * Generate the key used to store RPC start flow flow mapper states
 */
fun getRPCMapperKey(flowKey: FlowKey): String {
    val identityShortHash = flowKey.identity.toCorda().shortHash
    val clientRequestId = flowKey.id
    return "${clientRequestId}-$identityShortHash"
}

/**
 * When a flow is started by the REST api, a FlowKey is sent to the Flow mapper, so we send a cleanup event for this key.
 *
 * Flows triggered by SessionInit don't have this FlowKey, so we do not send a cleanup event
 */
fun getRecords(
    flowRecordFactory: FlowRecordFactory,
    context: FlowEventContext<Any>,
    status: FlowStatus
): List<Record<*,*>> {
    val checkpoint = context.checkpoint
    val records = if (checkpoint.flowStartContext.initiatorType == FlowInitiatorType.RPC) {
        val flowCleanupTime = context.flowConfig.getLong(FlowConfig.PROCESSING_FLOW_MAPPER_CLEANUP_TIME)
        val expiryTime = Instant.now().plusMillis(flowCleanupTime).toEpochMilli()
        listOf(
            flowRecordFactory.createFlowStatusRecord(status), flowRecordFactory.createFlowMapperEventRecord(
                getRPCMapperKey(checkpoint.flowKey), ScheduleCleanup(expiryTime)
            )
        )
    } else {
        listOf(
            flowRecordFactory.createFlowStatusRecord(status)
        )
    }
    return records
}

