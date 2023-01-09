package net.cordacon.example.rollcall

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.cordacon.example.utils.createMember
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RollCallFlowTest {

    companion object {
        private fun receiveAndSendResponse(session: FlowSession, response: String) {
            session.receive<Any>()
            session.send(RollCallResponse(response))
        }
    }

    @Test
    fun `should initiate roll call with members in the same org`() {
        val students = listOf("Alice", "Bob", "Charlie").map { createMember(it) }
        val notAStudent = createMember("David", org="DifferentOrg")
        val truancyOffice = createMember("TruancyOffice", org="AlsoDifferentOrg")

        val simulator = Simulator()
        val aliceNode = simulator.createVirtualNode(students[0], RollCallFlow::class.java)

        val otherNodes = listOf(students[1], students[2], notAStudent).associateWith {
            val flow = mock<ResponderFlow>()
            whenever(flow.call(any())).then { f ->
                receiveAndSendResponse(f.getArgument(0), "Here!")
            }
            simulator.createInstanceNode(it, "roll-call", flow)
            flow
        }

        val truancyOfficeFlow = mock<ResponderFlow>()
        whenever(truancyOfficeFlow.call(any())).then {
            it.getArgument<FlowSession>(0).receive<TruancyRecord>()
        }
        simulator.createInstanceNode(truancyOffice, "truancy-record", truancyOfficeFlow)

        aliceNode.callFlow(RequestData.create(
            "r1",
            RollCallFlow::class.java,
            RollCallInitiationRequest(truancyOffice)
        ))

        listOf(students[1], students[2]).forEach { verify(otherNodes[it]!!, times(1)).call(any()) }
        verify(otherNodes[notAStudent]!!, never()).call(any())
    }

    @Test
    fun `should retry any empty responses as absences then send signed record to the truancy office`() {
        val alice = createMember("Alice")
        val bob = createMember("Bob")
        val truancyOffice = createMember("TruancyOffice", org="DifferentOrg")

        val simulator = Simulator()

        val bobsAbsentFlow = mock<ResponderFlow>()
        whenever(bobsAbsentFlow.call(any())).then {
            receiveAndSendResponse(it.getArgument(0), "")
        }

        val truancyOfficeFlow = mock<ResponderFlow>()
        lateinit var receivedRecord: TruancyRecord
        whenever(truancyOfficeFlow.call(any())).then {
            val flowSession = it.getArgument<FlowSession>(0)
            receivedRecord = flowSession.receive()
            receivedRecord
        }

        simulator.createInstanceNode(bob, "roll-call", bobsAbsentFlow)
        simulator.createInstanceNode(bob, "absence-call", bobsAbsentFlow)
        simulator.createInstanceNode(truancyOffice, "truancy-record", truancyOfficeFlow)

        val aliceNode = simulator.createVirtualNode(alice, RollCallFlow::class.java)
        aliceNode.generateKey("my-key", HsmCategory.LEDGER, "any-scheme")

        aliceNode.callFlow(RequestData.create(
            "r1",
            RollCallFlow::class.java,
            RollCallInitiationRequest(truancyOffice)
        ))

        verify(bobsAbsentFlow, times(3)).call(any())
        assertThat(receivedRecord.absentees, `is`(listOf(bob)))
    }
}