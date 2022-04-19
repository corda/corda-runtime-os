package net.corda.flow.test.utils

import com.typesafe.config.ConfigFactory
import net.corda.data.flow.event.FlowEvent
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.records.Record

private const val FLOW_ID = "flow id"

fun <T> buildFlowEventContext(
    checkpoint: FlowCheckpoint,
    inputEventPayload: T,
    config: SmartConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty()),
    outputRecords: List<Record<*, *>> = emptyList(),
    flowId: String = FLOW_ID
): FlowEventContext<T> {

    return FlowEventContext(checkpoint, FlowEvent(flowId, inputEventPayload), inputEventPayload, config, outputRecords)
}