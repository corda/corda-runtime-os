package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

@InitiatingFlow(protocol = "InitiatedCallBack")
class InitiateCallbackFlow(
    private val x500Name: MemberX500Name
) : SubFlow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): String {
        log.info("Subflow to call back original flow")

        val session = flowMessaging.initiateFlow(x500Name)
        log.info("callback flow sending data to responder")
        session.send(CallbackData("calling back with data"))

        val acknowledge = session.receive(CallbackData::class.java)
        log.info("callback flow received data from responder: $acknowledge")

        session.close()
        log.info("Callback session closed in InitiateCallbackFlow initiator")
        return "done"
    }
}

@InitiatedBy(protocol = "InitiatedCallBack")
class InitiateCallbackResponder : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("Call back initiated from subflow in responder flow")

        val received = session.receive<CallbackData>()
        log.info("Received callback message from peer: $received")

        session.send(CallbackData("final call"))

        session.close()
        log.info("Callback session closed in InitiatedCallbackFlow responder")
    }
}

data class CallbackData(val info: String)