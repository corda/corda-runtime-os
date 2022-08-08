package net.cordacon.example

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.util.contextLogger

class ResponderFlowDelegate {

    private companion object {
        val log = contextLogger()
    }

    fun callDelegate(session: FlowSession, flowEngine: FlowEngine) {
        log.info("Responder ${flowEngine.virtualNodeName} called")
        session.receive(RollCallRequest::class.java)

        log.info("Responder ${flowEngine.virtualNodeName} received request; sending response")
        val response = RollCallResponse(
            if (flowEngine.virtualNodeName.rollCallName == "Bueller") {
                ""
            } else {
                "Here!"
            }
        )
        session.send(response)

        log.info("Responder ${flowEngine.virtualNodeName} sent response")
    }
}
