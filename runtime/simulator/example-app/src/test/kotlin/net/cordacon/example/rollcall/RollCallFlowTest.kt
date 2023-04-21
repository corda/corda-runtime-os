package net.cordacon.example.rollcall

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.crypto.HsmCategory
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.cordacon.example.rollcall.utils.ScriptMaker
import net.cordacon.example.utils.createMember
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RollCallFlowTest {

    companion object {
        private fun receiveAndSendResponse(session: FlowSession, response: String) {
            session.receive(Any::class.java)
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
            it.getArgument<FlowSession>(0).receive(TruancyRecord::class.java)
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
            receivedRecord = flowSession.receive(TruancyRecord::class.java)
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

    @Test
    fun `should pass responses to ScriptMaker to make into script`() {
        val students = listOf("Alice", "Bob", "Charlie").map { createMember(it) }
        val teacher = createMember("Ben")
        val truancyOffice = createMember("TruancyOffice", org="AlsoDifferentOrg")

        val simulator = Simulator()

        students.forEach {
            val flow = mock<ResponderFlow>()
            whenever(flow.call(any())).then { f ->
                receiveAndSendResponse(f.getArgument(0), "Here!")
            }
            simulator.createInstanceNode(it, "roll-call", flow)
        }

        val scriptMaker = mock<ScriptMaker>()
        whenever(scriptMaker.createScript(students.map { Pair(it, "Here!") }, teacher)).doReturn("Success!")

        // Note we have isolated the script logic here; we're using Simulator's instance injection to mock this one
        // class out without having to mock all the other services in play.
        val aliceNode = simulator.createInstanceNode(teacher, "roll-call", RollCallFlow(scriptMaker))

        val result = aliceNode.callFlow(RequestData.create(
            "r1",
            RollCallFlow::class.java,
            RollCallInitiationRequest(truancyOffice)
        ))
        assertThat(result, `is`("Success!"))
    }
}