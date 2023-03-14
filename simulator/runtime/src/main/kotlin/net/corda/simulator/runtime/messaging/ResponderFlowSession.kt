package net.corda.simulator.runtime.messaging

import java.util.concurrent.BlockingQueue
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession

interface ResponderFlowSession : FlowSession

class BaseResponderFlowSession(
    flowDetails: FlowContext,
    from: BlockingQueue<Any>,
    to: BlockingQueue<Any>,
    flowContextProperties: SimFlowContextProperties,
): BlockingQueueFlowSession(flowDetails, from, to, flowContextProperties), ResponderFlowSession {
    override fun rethrowAnyResponderError() {}

    override fun close() {
        state = SessionState.CLOSED
    }

    override fun getCounterpartyFlowInfo(): FlowInfo {
        TODO("Not yet implemented")
    }

    override fun send(payload: Any) {
        state.closedCheck()
        to.put(payload)
    }
}