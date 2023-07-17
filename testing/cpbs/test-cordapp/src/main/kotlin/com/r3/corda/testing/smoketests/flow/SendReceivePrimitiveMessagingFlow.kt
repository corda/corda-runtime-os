package com.r3.corda.testing.smoketests.flow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

val payloads = listOf(
    42.toByte(),    // Byte
    1234.toShort(), // Short
    56789,          // Int
    123456789L,     // Long
    3.14f,          // Float
    2.71828,        // Double
    'A',            // Char
    true            // Bool
)

@InitiatingFlow(protocol = "SendReceivePrimitiveProtocol")
class SendReceivePrimitiveMessagingFlow(
    private val x500Name: MemberX500Name,
) : SubFlow<String> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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

        val session = flowMessaging.initiateFlow(x500Name)
        log.info("Called initiate sessions")

        for ((index, payload) in payloads.withIndex()) {
            log.info("Sending ${payload::class.java.name} payload")
            when (index / 3) {
                0 -> session.send(payload)                                  // SessionManagerImpl::send()
                1 -> flowMessaging.sendAll(payload, setOf(session))         // FlowMessagingImpl::sendAll()
                2 -> flowMessaging.sendAllMap(mapOf(session to payload))    // FlowMessagingImpl::sendAllMap()
            }
        }

        val received = session.receive(String::class.java)
        log.info("Received from initiated: $received")

        log.info("Closing session 1")
        session.close()

        log.info("Closed session")

        return received
    }
}

@InitiatedBy(protocol = "SendReceivePrimitiveProtocol")
class SendReceivePrimitiveInitiatedFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("I have been called [${flowEngine.flowId}]")

        val receivedList = payloads.map { payload ->
            val received = session.receive(payload::class.java)
            log.info("Received ${received::class.java} with value: " +
                    "$received.Expected type was ${payload::class.java}.")
            received
        }

        session.send("Successfully received ${receivedList.size} items.")

        log.info("Closing initiated session")
        session.close()
        log.info("Closed initiated session")
    }
}
