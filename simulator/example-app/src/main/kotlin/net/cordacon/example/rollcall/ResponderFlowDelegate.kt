package net.cordacon.example.rollcall

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.cordacon.example.rollcall.utils.rollCallName

class ResponderFlowDelegate {



    @Suspendable
    fun callDelegate(session: FlowSession, flowEngine: FlowEngine) {
        session.receive(RollCallRequest::class.java)

        val response = RollCallResponse(
            if (flowEngine.virtualNodeName.rollCallName == "Bueller") {
                ""
            } else {
                "Here!"
            }
        )
        session.send(response)

    }
}
