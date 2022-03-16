package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.MessageDirection
import net.corda.data.identity.HoldingIdentity
import net.corda.schema.Schemas
import java.util.*

/**
 * Generate and returns a new [FlowKey] based on the [identity] of the party.
 * FlowKey.flowId will be a random UUID.
 * @return a flow key
 */
fun generateFlowKey(identity: HoldingIdentity): FlowKey {
    return FlowKey(generateFlowId(), identity)
}

/**
 * Generate and return random ID for flowId
 * @return a new flow id
 */
private fun generateFlowId(): String {
    return UUID.randomUUID().toString()
}

/**
 * Inbound records should be directed to the flow event topic.
 * Outbound records should be directed to the p2p out topic.
 * @return the output topic based on [messageDirection].
 */
fun getSessionEventOutputTopic(messageDirection: MessageDirection): String {
    return if (messageDirection == MessageDirection.INBOUND) {
        Schemas.Flow.FLOW_EVENT_TOPIC
    } else {
        Schemas.P2P.P2P_OUT_TOPIC
    }
}
