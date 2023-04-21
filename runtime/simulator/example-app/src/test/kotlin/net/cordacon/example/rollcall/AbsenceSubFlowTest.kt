package net.cordacon.example.rollcall

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.cordacon.example.utils.createMember
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AbsenceSubFlowTest {

    @Test
    fun `should send a roll call request to the given student`() {
        val alice = createMember("Alice")
        val bob = createMember("Bob")

        val simulator = Simulator()

        val initiatingFlow = object: ClientStartableFlow {
            @CordaInject
            private lateinit var flowEngine: FlowEngine

            override fun call(requestBody: ClientRequestBody): String {
                return flowEngine.subFlow(AbsenceSubFlow(bob))
            }
        }

        val respondingFlow = mock<ResponderFlow>()
        whenever(respondingFlow.call(any())).then {
            val session = it.getArgument<FlowSession>(0)
            session.receive(RollCallRequest::class.java)
            session.send(RollCallResponse("Here!"))
        }

        val aliceNode = simulator.createInstanceNode(alice, "roll-call", initiatingFlow)
        simulator.createVirtualNode(bob, AbsenceCallResponderFlow::class.java)

        val result = aliceNode.callFlow(RequestData.IGNORED)

        assertThat(result, `is`("Here!"))
    }
}