package net.corda.flow.pipeline.factory.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.output.FlowStatus
import net.corda.flow.pipeline.factory.RecordFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.Companion.FLOW_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_MAPPER_EVENT_TOPIC
import net.corda.schema.Schemas.Flow.Companion.FLOW_STATUS_TOPIC
import org.osgi.service.component.annotations.Component

@Component(service = [RecordFactory::class])
class RecordFactoryImpl : RecordFactory {

    override fun  createFlowEventRecord(flowId: String, payload: Any): Record<String, FlowEvent> {
        return Record(
            topic = FLOW_EVENT_TOPIC,
            key = flowId,
            value = FlowEvent(flowId, payload)
        )
    }

    override fun createFlowStatusRecord(status: FlowStatus): Record<FlowKey, FlowStatus> {
        return Record(
            topic = FLOW_STATUS_TOPIC,
            key = status.key,
            value = status
        )
    }

    override fun createFlowMapperSessionEventRecord(sessionEvent: SessionEvent): Record<String, FlowMapperEvent> {
        return Record(
            topic = FLOW_MAPPER_EVENT_TOPIC,
            key = sessionEvent.sessionId,
            value = FlowMapperEvent(sessionEvent)
        )
    }
}