package net.corda.simulator

import net.corda.simulator.exceptions.ResponderFlowException
import net.corda.simulator.runtime.testutils.createMember
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SessionManagementTest {

    companion object {

        val alice = createMember("Alice")
        val bob = createMember("Bob")
        val charlie =createMember("Charlie")

        @InitiatingFlow(protocol = "send-receive")
        class InitiatingSendingFlow: ClientStartableFlow {
            @CordaInject
            private lateinit var flowMessaging: FlowMessaging

            @Suspendable
            override fun call(requestBody: ClientRequestBody): String {
                val session = flowMessaging.initiateFlow(bob)
                session.send(Unit)
                return ""
            }
        }

        @InitiatedBy(protocol = "send-receive")
        class ReceivingAndSendingOnFlow: ResponderFlow {
            @CordaInject
            private lateinit var flowEngine: FlowEngine

            @Suspendable
            override fun call(session: FlowSession) {
                session.receive(Any::class.java)
                flowEngine.subFlow(SendingOnSubFlow())

            }
        }

        @InitiatingFlow(protocol = "receive-send")
        class SendingOnSubFlow: SubFlow<String> {
            @CordaInject
            private lateinit var flowMessaging: FlowMessaging

            @Suspendable
            override fun call(): String {
                val session = flowMessaging.initiateFlow(charlie)
                session.receive(Any::class.java)
                return ""
            }
        }

        class SleepingEndFlow: ResponderFlow {
            var finished = false

            override fun call(session: FlowSession) {
                Thread.sleep(200)
                session.send(Any::class.java)
                finished = true
            }
        }

        class ErroringEndFlow: ResponderFlow {
            override fun call(session: FlowSession) { throw CharliesException() }
        }

        class CharliesException : Exception("Because")
    }

    @Test
    fun `Simulator nodes should wait for all sessions to finish before returning`() {

        // Given flow logic that look like:
        //     Alice sends to Bob
        //     Bob receives from Alice and starts a sub-flow that sends to Charlie
        //     Charlie receives from Bob and waits for 200 ms before returning
        // So we have a responder thread kicking off another responder thread in a subflow

        val simulator = Simulator()
        val charliesInstanceFlow = SleepingEndFlow()

        val aliceNode = simulator.createVirtualNode(alice, InitiatingSendingFlow::class.java)
        simulator.createVirtualNode(bob, ReceivingAndSendingOnFlow::class.java)
        simulator.createInstanceNode(charlie, "receive-send", charliesInstanceFlow)

        // When we call Alice's flow
        aliceNode.callFlow(RequestData.create("r1", InitiatingSendingFlow::class.java, Unit))

        // Then it shouldn't finish until Charlie's does
        assertTrue(charliesInstanceFlow.finished)
    }

    @Test
    fun `Simulator nodes should propagate any errors back to the initiating flow`() {

        // Given flow logic that look like:
        //     Alice sends to Bob
        //     Bob receives from Alice and starts a sub-flow that sends to Charlie
        //     Charlie errors

        val simulator = Simulator()
        val charliesInstanceFlow = ErroringEndFlow()

        val aliceNode = simulator.createVirtualNode(alice, InitiatingSendingFlow::class.java)
        simulator.createVirtualNode(bob, ReceivingAndSendingOnFlow::class.java)
        simulator.createInstanceNode(charlie, "receive-send", charliesInstanceFlow)

        // When we call Alice's flow
        // Then it should get the error from Charlie
        assertThrows<ResponderFlowException> {
            aliceNode.callFlow(RequestData.create("r1", InitiatingSendingFlow::class.java, Unit))
        }.also {
            assertInstanceOf(CharliesException::class.java, it.cause?.cause)
        }
    }


}