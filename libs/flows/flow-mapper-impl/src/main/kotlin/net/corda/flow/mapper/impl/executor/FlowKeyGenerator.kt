package net.corda.flow.mapper.impl.executor

import net.corda.data.flow.FlowKey
import net.corda.data.identity.HoldingIdentity
import java.util.*

class FlowKeyGenerator {

    /**
     * Include identity in the flowKey
     */
    fun generateFlowKey(identity: HoldingIdentity): FlowKey {
        return FlowKey(generateFlowId(), identity)
    }

    /**
     * Random ID for flowId
     */
    private fun generateFlowId(): String {
        return UUID.randomUUID().toString()
    }
}