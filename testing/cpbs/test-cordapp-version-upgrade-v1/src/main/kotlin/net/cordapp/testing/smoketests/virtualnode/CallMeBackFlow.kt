package net.cordapp.testing.smoketests.virtualnode

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger

@InitiatingFlow(protocol = "CallMeBack")
class CallMeBackFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("CallMeBackFlow starting by getting member infos...")
        val recipient = MemberX500Name.parse(
            requestBody.getRequestBodyAs(jsonMarshallingService, Recipient::class.java).x500Name
        )

        log.info("CallMeBackFlow initiating session with recipient $recipient...")
        val session = flowMessaging.initiateFlow(recipient)

        session.send(ImportantInfo("really important data"))

        val receivedInfo = session.receive(ImportantInfo::class.java)
        log.info("Closing session")
        session.close()

        return "receivedInfo: $receivedInfo"
    }
}

@InitiatedBy(protocol = "CallMeBack")
class CallMeBackResponder : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("CallMeBackResponder has been called")

        val received = session.receive<ImportantInfo>()
        log.info("Received important info in responder: $received")

        log.info("Invoking subflow that initiates a flow on peer: ${session.counterparty}.")
        flowEngine.subFlow(InitiateCallbackFlow(session.counterparty))

        session.send(ImportantInfo("Sending back info"))

        session.close()
        log.info("Closed session 1")
    }
}
@CordaSerializable
data class Recipient(val x500Name: String)
@CordaSerializable
data class ImportantInfo(val info: String)