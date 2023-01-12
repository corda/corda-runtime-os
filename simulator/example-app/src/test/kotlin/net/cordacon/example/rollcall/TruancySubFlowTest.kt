package net.cordacon.example.rollcall

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.cordacon.example.utils.createMember
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TruancySubFlowTest {

    @Test
    fun `should send on the truancy record with which it was constructed`() {
        val alice = createMember("Alice")
        val bob = createMember("Bob")
        val charlie = createMember("Charlie")

        val simulator = Simulator()
        val truancyRecord = TruancyRecord(
            listOf(bob),
            mock()
        )

        val initiatingFlow = object: RestStartableFlow {
            @CordaInject
            private lateinit var flowEngine: FlowEngine

            override fun call(requestBody: RestRequestBody): String {
                return flowEngine.subFlow(TruancySubFlow(charlie, truancyRecord))
            }
        }

        val respondingFlow = mock<ResponderFlow>()
        var receivedRecord: TruancyRecord? = null
        whenever(respondingFlow.call(any())).then {
            receivedRecord = it.getArgument<FlowSession>(0).receive(TruancyRecord::class.java)
            receivedRecord
        }

        val aliceNode = simulator.createInstanceNode(alice, "truancy-record", initiatingFlow)
        simulator.createInstanceNode(charlie, "truancy-record", respondingFlow)

        aliceNode.callInstanceFlow(RequestData.IGNORED)

        assertThat(receivedRecord, `is`(truancyRecord))
    }
}