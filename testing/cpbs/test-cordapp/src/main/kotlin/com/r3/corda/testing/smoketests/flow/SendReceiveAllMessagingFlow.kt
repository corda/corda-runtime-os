package com.r3.corda.testing.smoketests.flow

import com.r3.corda.testing.testflows.MyClass
import java.util.concurrent.ThreadLocalRandom
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "SendReceiveAllProtocol")
class SendReceiveAllMessagingFlow(
    private val x500Name: MemberX500Name,
) : SubFlow<String> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val CHARS_PER_KB = 1000
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

        log.info("Received FlowInfo from sessionOne: ${sessionOne.counterpartyFlowInfo}")
        log.info("Received FlowInfo from sessionTwo: ${sessionTwo.counterpartyFlowInfo}")

        val sendMap: Map<FlowSession, Any> = mapOf(sessionOne to MyClass("Serialize me please", 1), sessionTwo to MyClass("Serialize me " +
                "please", 2)
        )
        flowMessaging.sendAllMap(sendMap)
        log.info("Sent Map")

        flowMessaging.sendAll(MyClass("Serialize me please", 3), setOf(sessionOne, sessionTwo))
        log.info("Sent All")

        //additional send via session to help verify init isn't sent again
        val largeString = getLargeString(1100)
        sessionOne.send(MyClass(largeString, 4))
        log.info("Session 1 single send")

        sessionTwo.send(MyClass("Serialize me please", 5))
        log.info("Session 2 single send")

        val receivedMap = flowMessaging.receiveAllMap(mapOf(sessionOne to MyClass::class.java, sessionTwo to MyOtherClass::class.java))
        log.info("Received Map")

        var receivedNumSum = 0
        receivedMap.forEach { (_, received) ->
            if (received is MyClass) {
                receivedNumSum+=received.int
                log.info("Session received map data (type: MyClass): ${received.int} ")
            } else if (received is MyOtherClass) {
                receivedNumSum+=received.int
                log.info("Session received map data (type: MyOtherClass): ${received.int} ")
            }
        }

        val receivedAll = flowMessaging.receiveAll(MyClass::class.java, setOf(sessionOne, sessionTwo))
        log.info("Received All")
        receivedAll.forEach {
            receivedNumSum+=it.int
            log.info("Session received all data: ${it.int} ")
        }

        sessionOne.receive(MyClass::class.java).let {
            receivedNumSum+=it.int
        }
        log.info("Received single value from session 1")

        sessionTwo.receive(MyClass::class.java).let {
            receivedNumSum+=it.int
        }
        log.info("Received single value from session 2")

        log.info("Closing session 1")
        sessionOne.close()
        log.info("Closing session 2")
        sessionTwo.close()

        log.info("Closed session")
        log.info("Hello world completed.")

        return "Completed. Sum:$receivedNumSum"
    }

    /**
     * Generate a large string whose size is roughly equal to the given amount of [kiloBytes]
     */
    private fun getLargeString(kiloBytes: Int) : String {
        val stringBuilder = StringBuilder()
        for (i in 0..CHARS_PER_KB*kiloBytes) {
            stringBuilder.append(ThreadLocalRandom.current().nextInt(0,9) )
        }
        return stringBuilder.toString()
    }
}

@InitiatedBy(protocol = "SendReceiveAllProtocol")
class SendReceiveAllInitiatedFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("I have been called [${flowEngine.flowId}]")

        val received = session.receive(MyClass::class.java)
        log.info("Receive from send map from peer: $received")

        if (received.int == 2) {
            session.send(MyOtherClass( 1, "this is a new object 1", received.int))
            log.info("Responding with MyOtherClass")
        } else {
            session.send(received.copy(string = "this is a new object 1"))
            log.info("Responding with copy of received")
        }

        val received2 = session.receive(MyClass::class.java)
        log.info("Receive from send all from peer: $received2")

        session.send(received2.copy(string = "this is a new object 2"))
        log.info("Responding to send all from peer")


        val received3 = session.receive(MyClass::class.java)
        //this string is so large it activates chunking so do not log it
        log.info("Receive from single send from peer. Message size: ${received3.string.length}")

        session.send(received3.copy(string = "this is a new object 3"))
        log.info("Sending final message to initiator flow")

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

