package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.data.persistence.EntityRequest
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_STATUS_TOPIC
import net.corda.schema.Schemas.VirtualNode.Companion.ENTITY_PROCESSOR
import org.osgi.service.component.annotations.Component

@Component(service = [FlowRecordFactory::class])
class FlowRecordFactoryImpl : FlowRecordFactory {

    override fun  createFlowEventRecord(flowId: String, payload: Any): Record<String, FlowEvent> {
        return Record(
            topic = FLOW_EVENT_TOPIC,
            key = flowId,
            value = FlowEvent(flowId, payload)
        )
    }

    override fun createEntityRequestRecord(requestId: String, payload: EntityRequest): Record<String,
            EntityRequest> {
        return Record(
            topic = ENTITY_PROCESSOR,
            key = requestId,
            value = payload
        )
    }

    override fun createFlowStatusRecord(status: FlowStatus): Record<FlowKey, FlowStatus> {
        return Record(
            topic = FLOW_STATUS_TOPIC,
            key = status.key,
            value = status
        )
    }

    override fun createFlowMapperEventRecord(key: String, payload: Any): Record<*, FlowMapperEvent> {
        return Record(
            topic = FLOW_MAPPER_EVENT_TOPIC,
            key = key,
            value = FlowMapperEvent(payload)
        )
    }
}