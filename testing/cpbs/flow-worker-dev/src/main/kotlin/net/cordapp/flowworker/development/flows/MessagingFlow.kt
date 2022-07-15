package net.cordapp.flowworker.development.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.application.messaging.unwrap
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.cordapp.flowworker.development.messages.MessageFlowInput

@InitiatingFlow(protocol = "flowDevProtocol")
class MessagingFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var memberLookupService: MemberLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Hello world is starting... [${flowEngine.flowId}]")
        val input = requestBody.getRequestBodyAs<MessageFlowInput>(jsonMarshallingService)
        val counterparty = MemberX500Name.parse(input.counterparty.toString())
        log.info("Looking up member $counterparty in the network.")
        val findCounterparty = memberLookupService.lookup(counterparty)
            ?: throw IllegalStateException("Failed to lookup the member $counterparty")
        log.info("Preparing to initiate flow with member from group: ${findCounterparty.name}")

        val session = flowMessaging.initiateFlow(findCounterparty.name)

        val received = session.sendAndReceive<MyClass>(MyClass("Serialize me please", 1)).unwrap { it }

        log.info("Received data from initiated flow 1: $received")

        flowEngine.subFlow(InlineSubFlow(session))

        flowEngine.subFlow(InitiatingSubFlow(counterparty))

        log.info("Finished initiating subflow")

        val received3 = session.receive<MyClass>().unwrap { it }

        log.info("Received data from initiated flow 3: $received3")

        session.close()

        log.info("Closed session")
        log.info("Hello world completed.")

        return "finished top level flow"
    }
}

@InitiatedBy(protocol = "flowDevProtocol")
class MessagingInitiatedFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("I have been called [${flowEngine.flowId}]")

        val received = session.receive<MyClass>().unwrap { it }

        log.info("Received data from peer: $received")

        session.send(received.copy(string = "this is a new object", int = 2))

        val received2 = session.receive<MyClass>().unwrap { it }

        log.info("Received data from peer 2: $received2")

        session.send(received.copy(string = "this is a new object 2", int = 2))

        session.send(received.copy(string = "this is a new object 3", int = 2))

        log.info("Closing session")

        session.close()
        log.info("Closed session 1")
        session.close()
        log.info("Closed session 2")
        session.close()
        log.info("Closed session 3")
        session.close()
        log.info("Closed session 4")
        session.close()
        log.info("Closed session 4")
        session.close()
        log.info("Closed session 5")
    }
}

class InlineSubFlow(private val session: FlowSession) : SubFlow<Unit> {

    private companion object {
        val log = contextLogger()
    }

    @Suspendable
    override fun call() {
        log.info("Inline subFlow is starting...")
        val received = session.sendAndReceive<MyClass>(MyClass("Serialize me please", 1)).unwrap { it }

        log.info("Received data from initiated flow 2 (inlined subFlow): $received")
    }
}

@InitiatingFlow(protocol = "subFlowDevProtocol")
class InitiatingSubFlow(private val counterparty: MemberX500Name) : SubFlow<Unit> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call() {
        log.info("Initiating subFlow is starting...")
        val session = flowMessaging.initiateFlow(counterparty)

        val received = session.sendAndReceive<MyClass>(MyClass("Serialize me please", 1)).unwrap { it }

        log.info("Received data from initiated subFlow: $received")
    }
}

@InitiatedBy(protocol = "subFlowDevProtocol")
class InitiatingSubFlowInitiatedFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("I have been called [${flowEngine.flowId}]")

        val received = session.receive<MyClass>().unwrap { it }

        log.info("Received data from peer: $received")

        session.send(received.copy(string = "this is a new object", int = 2))

        // should explode when we implement more close logic
//        session.receive<MyClass>().unwrap { it }
    }
}

@CordaSerializable
data class MyClass(
    val string: String,
    val int: Int
)