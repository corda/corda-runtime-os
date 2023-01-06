package net.corda.simulator.runtime.messaging

import net.corda.v5.application.messaging.FlowSession
import java.util.concurrent.BlockingQueue

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

    override fun send(payload: Any) {
        state.closedCheck()
        to.put(payload)
    }
}