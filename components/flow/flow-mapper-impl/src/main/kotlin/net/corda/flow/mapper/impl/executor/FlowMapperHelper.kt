package net.corda.flow.mapper.impl.executor

import java.util.UUID

/**
 * Generate and return random ID for flowId
 * @return a new flow id
 */
fun generateFlowId(): String {
    return UUID.randomUUID().toString()
}
