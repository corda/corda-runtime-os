package net.cordacon.example.rollcall

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.types.MemberX500Name
import net.cordacon.example.utils.createMember
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test

abstract class ResponderFlowDelegateTest {

    abstract val protocol: String
    abstract val flowClass: Class<out Flow>

    companion object {

        private fun rollCallFlowFor(student: MemberX500Name) = object : RPCStartableFlow {
            @CordaInject
            private lateinit var flowMessaging: FlowMessaging

            override fun call(requestBody: RPCRequestData): String {
                val session = flowMessaging.initiateFlow(student)
                session.send(RollCallRequest(student))
                return session.receive(RollCallResponse::class.java).response
            }
        }
    }

    @Test
    fun `should generally respond to roll call with Here`() {
        val simulator = Simulator()

        val student = createMember("Alice")
        val teacher = createMember("Bob")

        val teacherFlow = rollCallFlowFor(student)

        val teacherNode = simulator.createInstanceNode(teacher, protocol, teacherFlow)
        simulator.createVirtualNode(student, flowClass)

        val result = teacherNode.callFlow(RequestData.IGNORED)

        MatcherAssert.assertThat(result, Matchers.`is`("Here!"))
    }

    @Test
    fun `should respond with an empty string if called Bueller`() {
        val simulator = Simulator()

        val student = createMember("Bueller")
        val teacher = createMember("Ben Stein")

        val teacherFlow = rollCallFlowFor(student)

        val teacherNode = simulator.createInstanceNode(teacher, protocol, teacherFlow)
        simulator.createVirtualNode(student, flowClass)

        val result = teacherNode.callFlow(RequestData.IGNORED)

        MatcherAssert.assertThat(result, Matchers.`is`(""))
    }
}
