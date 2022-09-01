package net.corda.cordapptestutils.internal.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.marshalling.parse
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name

@InitiatingFlow("ping-ack")
class PingAckFlow : RPCStartableFlow {

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val whoToPing = jsonMarshallingService.parse<MemberX500Name>(requestBody.getRequestBody())
        val session = flowMessaging.initiateFlow(whoToPing)
        session.send(jsonMarshallingService.format(PingAckMessage("Ping")))
        return session.receive(PingAckMessage::class.java).unwrap { it }.message
    }
}

@InitiatedBy("ping-ack")
class PingAckResponderFlow : ResponderFlow {
    @Suspendable
    override fun call(session: FlowSession) {
        session.send(PingAckMessage("Ack"))
    }
}

data class PingAckMessage(val message: String)
