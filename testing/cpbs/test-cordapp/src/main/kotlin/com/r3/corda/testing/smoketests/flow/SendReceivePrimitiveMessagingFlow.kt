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
import java.lang.StringBuilder

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

        val received = StringBuilder()

        // Send the payloads 3 times to test receive(), receiveAll() and receiveAllMap() on the counterparty side
        repeat(3) {
            sendPayloads(session)
            received.appendLine(session.receive(String::class.java))
            log.info("Received from initiated: $received")
        }

        log.info("Closing session 1")
        session.close()
        log.info("Closed session")

        return received.toString()
    }

    /**
     * Send our payloads to the provided [FlowSession], with sends divided across all three implementations.
     *
     * @param session the [FlowSession] to send our payloads to.
     */
    @Suspendable
    private fun sendPayloads(session: FlowSession) {
        for ((index, payload) in payloads.withIndex()) {
            log.info("Sending ${payload::class.java.name} payload")
            when (index / 3) {
                0 -> session.send(payload)                                  // SessionManagerImpl::send()
                1 -> flowMessaging.sendAll(payload, setOf(session))         // FlowMessagingImpl::sendAll()
                2 -> flowMessaging.sendAllMap(mapOf(session to payload))    // FlowMessagingImpl::sendAllMap()
            }
        }
    }
}

@InitiatedBy(protocol = "SendReceivePrimitiveProtocol")
class SendReceivePrimitiveInitiatedFlow : ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(session: FlowSession) {
        log.info("I have been called [${flowEngine.flowId}]")

        // Testing FlowSessionImpl::receive()
        val receivedList = receivePayloads(methodName = "receive") { payload ->
            session.receive(payload::class.java)
        }
        session.send("Successfully received ${receivedList.size} items.")

        // Testing FlowMessaging::receiveAll()
        val receiveAllList = receivePayloads(methodName = "receiveAll") { payload ->
            flowMessaging.receiveAll(payload::class.java, setOf(session))
        }
        session.send("Successfully received ${receiveAllList.size} items from receiveAll.")

        // Testing FlowMessaging::receiveAllMap()
        val receiveAllMap = receivePayloads(methodName = "receiveAllMap") { payload ->
            flowMessaging.receiveAllMap(mapOf(session to payload::class.java))
        }
        session.send("Successfully received ${receiveAllMap.size} items from receiveAllMap.")

        log.info("Closing initiated session")
        session.close()
        log.info("Closed initiated session")
    }

    /**
     * For each item in [payloads] we make a single receive call using the passed [action] and return the result.
     * The received value is also logged, along with the [methodName] passed to the function.
     * Importantly, we try to receive the [payloads] in the same order as they're sent by the counterparty flow.
     *
     * @param methodName the name of the method being logged
     * @param action the action to perform on each payload (must not return null)
     * @return list of received items
     */
    @Suspendable
    private fun <T> receivePayloads(methodName: String, action: (Any) -> T): List<T> {
        return payloads.map { payload ->
            val received = action(payload)
            log.info("Method: $methodName - Received ${received!!::class.java} with value: " +
                    "$received. Expected type was ${payload::class.java}.")
            received
        }
    }
}
