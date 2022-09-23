package net.cordapp.testing.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.cordapp.testing.testflows.messages.MessageFlowInput

@InitiatingFlow(protocol = "SendReceiveAllProtocol")
class SendReceiveAllMessagingFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Hello world is starting... [${flowEngine.flowId}]")
        val input = requestBody.getRequestBodyAs<MessageFlowInput>(jsonMarshallingService)
        val counterparty = MemberX500Name.parse(input.counterparty.toString())
        log.info("Preparing to initiate flow with member from group: $counterparty")

        val sessionOne = flowMessaging.initiateFlow(counterparty)
        val sessionTwo = flowMessaging.initiateFlow(counterparty)
        log.info("Called initiate sessions")

        val sendMap: Map<FlowSession, Any> = mapOf(sessionOne to MyClass("Serialize me please", 1), sessionTwo to MyClass("Serialize me " +
                "please", 2))
        flowMessaging.sendAllMap(sendMap)
        log.info("Sent Map, sending all")
        flowMessaging.sendAll(MyClass("Serialize me please", 3), setOf(sessionOne, sessionTwo))

        //additional send via session to help verify init isn't sent again
        sessionOne.send(MyClass("Serialize me please", 4))
        sessionTwo.send(MyClass("Serialize me please", 5))


        log.info("Sent data to two sessions")

        val receivedMap = flowMessaging.receiveAllMap(mapOf(sessionOne to MyClass::class.java, sessionTwo to MyClass::class.java))
        log.info("received Map")

        receivedMap.forEach { (_, received) ->
            log.info("Session received map data: ${(received as MyClass).int} ")
        }

        val receivedAll = flowMessaging.receiveAll(MyClass::class.java, setOf(sessionOne, sessionTwo))
        log.info("received all")
        receivedAll.forEach {
            log.info("Session received all data: ${it.int} ")
        }

        log.info("Closing session1")
        sessionOne.close()
        log.info("Closing session2")
        sessionTwo.close()

        log.info("Closed session")
        log.info("Hello world completed.")

        return "finished top level flow"
    }
}

@InitiatedBy(protocol = "SendReceiveAllProtocol")
class SendReceiveAllInitiatedFlow : ResponderFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("I have been called [${flowEngine.flowId}]")

        val received = session.receive<MyClass>()
        log.info("Receive from send map from peer: $received")
        session.send(received.copy(string = "this is a new object 1"))


        val received2 = session.receive<MyClass>()
        log.info("Receive from send all from peer: $received2")
        session.send(received.copy(string = "this is a new object 2"))

        val received3 = session.receive<MyClass>()
        log.info("Receive from send from peer: $received3")
        log.info("Closing session")

        session.close()
        log.info("Closed session 1")
    }
}

