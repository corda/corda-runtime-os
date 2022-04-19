package net.corda.flow.pipeline.factory

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.messaging.api.records.Record

/**
 * The [FlowRecordFactory] is responsible for creating instances of messaging records used by the flow engine.
 */
interface FlowRecordFactory {

    /**
     * Creates a generic [FlowEvent] record
     *
     * @param flowId the id of the flow generating or receiving the event.
     * @param payload the instance of the specific flow event to be carried by the record.
     * valid types are [StartFlow], [Wakeup], [SessionEvent]
     * @return a new instance of a [FlowEvent] record.
     */
    fun createFlowEventRecord(flowId: String, payload: Any): Record<String, FlowEvent>

    /**
     * Creates a [FlowStatus] record
     *
     * @param status the status to be carried by the record
     * @return a new instance of a [FlowStatus] record.
     */
    fun createFlowStatusRecord(status: FlowStatus): Record<FlowKey, FlowStatus>

    /**
     * Creates a [FlowMapperEvent] record
     *
     * @param sessionEvent the session event to be carried by the record
     * @return a new instance of a [FlowMapperEvent] record.
     */
    fun createFlowMapperSessionEventRecord(sessionEvent: SessionEvent): Record<String, FlowMapperEvent>
}