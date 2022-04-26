package net.corda.flow.acceptance.dsl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.v5.base.util.uncheckedCast

fun StateAndEventProcessor.Response<Checkpoint>.filterOutputFlowTopicRecords(): List<Record<String, FlowEvent>> {
    return responseEvents
        .filter { it.topic == Schemas.Flow.FLOW_EVENT_TOPIC }
        .map { record ->
            require(record.key is String) {
                "${Schemas.Flow.FLOW_EVENT_TOPIC} should only receive records with keys of type ${FlowKey::class.qualifiedName}"
            }
            require(record.value is FlowEvent) {
                "${Schemas.Flow.FLOW_EVENT_TOPIC} should only receive records with values of type ${FlowEvent::class.qualifiedName}"
            }
            uncheckedCast(record)
        }
}

fun StateAndEventProcessor.Response<Checkpoint>.filterOutputFlowTopicEvents(): List<FlowEvent> {
    return filterOutputFlowTopicRecords().mapNotNull { it.value }
}

fun StateAndEventProcessor.Response<Checkpoint>.filterOutputFlowTopicEventPayloads(): List<*> {
    return filterOutputFlowTopicEvents().map { it.payload }
}