package net.corda.flow.test.utils

import com.typesafe.config.ConfigFactory
import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.FlowEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.flow.pipeline.FlowEventContext
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messaging.api.records.Record

private const val FLOW_ID = "flow id"
private val HOLDING_IDENTITY = HoldingIdentity("x500 name", "group id")
private val FLOW_KEY = FlowKey(FLOW_ID, HOLDING_IDENTITY)

fun <T> buildFlowEventContext(
    checkpoint: Checkpoint?,
    inputEventPayload: T,
    config: SmartConfig = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.empty()),
    outputRecords: List<Record<*, *>> = emptyList(),
    flowKey: FlowKey = FLOW_KEY
): FlowEventContext<T> {
    return FlowEventContext(checkpoint, FlowEvent(flowKey, inputEventPayload), inputEventPayload, config, outputRecords)
}