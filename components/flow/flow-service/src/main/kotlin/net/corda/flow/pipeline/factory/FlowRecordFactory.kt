package net.corda.flow.pipeline.factory

import net.corda.data.crypto.wire.ops.flow.FlowOpsRequest
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.StartFlow
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.persistence.EntityRequest
import net.corda.messaging.api.records.Record

/**
 * The [FlowRecordFactory] is responsible for creating instances of messaging records used by the flow engine.
 */
interface FlowRecordFactory {

    /**
     * Creates a generic [FlowEvent] record
     *
     * @param flowId The id of the flow generating or receiving the event.
     * @param payload The instance of the specific flow event to be carried by the record. Valid types are [StartFlow], [Wakeup],
     * [SessionEvent].
     * @return a new instance of a [FlowEvent] record.
     */
    fun createFlowEventRecord(flowId: String, payload: Any): Record<String, FlowEvent>

    /**
     * Wrap an EntityRequest obj in a record to be sent to the crypto processor.
     * @param requestId UUID of the request. Set as the record key.
     * @param payload Request payload. Set as record value.
     * @return Record to be sent to the crypto processor.
     */
    fun createFlowOpsRequestRecord(flowId: String, payload: FlowOpsRequest): Record<String, FlowOpsRequest>

    /**
     * Wrap an EntityRequest obj in a record to be sent to the db entity processor.
     * @param requestId UUID of the request. Set as the record key.
     * @param payload Request payload. Set as record value.
     * @return Record to be sent to the db entity processor.
     */
    fun createEntityRequestRecord(requestId: String, payload: EntityRequest): Record<String, EntityRequest>

    /**
     * Creates a [FlowStatus] record
     *
     * @param status The status to be carried by the record
     * @return A new instance of a [FlowStatus] record.
     */
    fun createFlowStatusRecord(status: FlowStatus): Record<FlowKey, FlowStatus>

    /**
     * Creates a [FlowMapperEvent] record
     *
     * @param key The key of the created record.
     * @param payload The instance of the specific flow mapper event to be carried by the record.
     * @return A new instance of a [FlowMapperEvent] record.
     */
    fun createFlowMapperEventRecord(key: String, payload: Any): Record<*, FlowMapperEvent>
}