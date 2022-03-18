package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.messaging.api.records.Record

interface RecordFactory {
    fun createFlowEventRecord(flowId: String, payload: Any): Record<String, FlowEvent>

    fun createFlowStatusRecord(status: FlowStatus): Record<FlowKey, FlowStatus>

    fun createFlowMapperSessionEventRecord(sessionEvent: SessionEvent): Record<String, FlowMapperEvent>
}