package net.cordapp.testing.smoketests.flow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.cordapp.testing.testflows.MyClass

@InitiatingFlow(protocol = "SendReceiveAllProtocol")
class SendReceiveAllMessagingFlow(
    private val x500Name: MemberX500Name,
) : SubFlow<String> {

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
    override fun call(): String {
        log.info("Send and receive is starting... [${flowEngine.flowId}]")
        log.info("Preparing to initiate flow with member from group: $x500Name")

        val sessionOne = flowMessaging.initiateFlow(x500Name)
        val sessionTwo = flowMessaging.initiateFlow(x500Name)
        log.info("Called initiate sessions")

        val sendMap: Map<FlowSession, Any> = mapOf(sessionOne to MyClass("Serialize me please", 1), sessionTwo to MyClass("Serialize me " +
                "please", 2)
        )
        flowMessaging.sendAllMap(sendMap)
        log.info("Sent Map, sending all")
        flowMessaging.sendAll(MyClass("Serialize me please", 3), setOf(sessionOne, sessionTwo))

        //additional send via session to help verify init isn't sent again
        sessionOne.send(MyClass("Serialize me please", 4))
        sessionTwo.send(MyClass("Serialize me please", 5))

        log.info("Sent data to two sessions")
        val receivedMap = flowMessaging.receiveAllMap(mapOf(sessionOne to MyClass::class.java, sessionTwo to MyOtherClass::class.java))
        log.info("received Map")

        receivedMap.forEach { (_, received) ->
            if (received is MyClass) {
                log.info("Session received map data (type: MyClass): ${received.int} ")
            } else if (received is MyOtherClass) {
                log.info("Session received map data (type: MyOtherClass): ${received.int} ")
            }
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

        return "Completed"
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
        if (received.int == 2) {
            session.send(MyOtherClass( 1, "this is a new object 1", received.int))
        } else {
            session.send(received.copy(string = "this is a new object 1"))
        }

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

@CordaSerializable
data class MyOtherClass (
    val long: Long,
    val string: String,
    val int: Int
)

