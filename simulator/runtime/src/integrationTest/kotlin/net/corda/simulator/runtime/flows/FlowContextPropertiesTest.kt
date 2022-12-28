package net.corda.simulator.runtime.flows

import net.corda.simulator.RequestData
import net.corda.simulator.Simulator
import net.corda.simulator.runtime.testutils.createMember
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test

class FlowContextPropertiesTest {

    @Test
    fun `should return correct flow context properties from nested subflows`() {
        // Given the virtual nodes Alice, Bob and Charlie
        val simulator = Simulator()
        val alice = createMember("Alice")
        val bob = createMember("Bob")
        val charlie = createMember("Charlie")
        val aliceNode = simulator.createVirtualNode(alice, FlowContextPropertiesMainFlow::class.java)
        simulator.createVirtualNode(bob, FlowContextPropertiesSubFlowResponder::class.java)
        simulator.createVirtualNode(charlie, FlowContextPropertiesSubFlowResponder::class.java)

        // When flow having nested subflow is called
        val requestData = RequestData.create(
            "r1",
            FlowContextPropertiesMainFlow::class.java,
            ""
        )
        val response = aliceNode.callFlow(requestData)

       // Then it should return correct flow context properties from each subflow and their responder flow
        val list = listOf(
            "main-flow : { key-1 : main-flow, key-2 : main-flow, key-3 : null, key-4 : null } ",
            "subflow-1 : { key-1 : main-flow, key-2 : subflow-1, key-3 : subflow-1, key-4 : null } ",
            "subflow-2 : { key-1 : main-flow, key-2 : subflow-1, key-3 : subflow-2, key-4 : null } ",
            "bob : { key-1 : main-flow, key-2 : subflow-1, key-3 : bob, key-4 : null } ",
            "charlie : { key-1 : main-flow, key-2 : subflow-1, key-3 : charlie, key-4 : from-builder } "
        )
        val result = list.joinToString(System.lineSeparator())
        assertThat(response, `is`(result))
    }


    @Test
    fun `should return correct flow context properties from different responders`() {
        // Given the virtual nodes Alice, Bob and Charlie
        val simulator = Simulator()
        val alice = createMember("Alice")
        val bob = createMember("Bob")
        val charlie = createMember("Charlie")
        val aliceNode = simulator.createVirtualNode(alice, FlowContextPropertiesInitiator::class.java)
        simulator.createVirtualNode(bob, FlowContextPropertiesResponder::class.java)
        simulator.createVirtualNode(charlie, FlowContextPropertiesResponder::class.java)

        // When a flow having multiple responders is called
        val requestData = RequestData.create(
            "r1",
            FlowContextPropertiesInitiator::class.java,
            FlowRequest(listOf(bob, charlie))
        )
        val response = aliceNode.callFlow(requestData)

        // Then it should return correct flow context properties from each responder flow and their subflows
        val list = listOf(
            "initiator : { key-1 : initiator, key-2 : initiator, key-3 : null, key-4 : null } ",
            "bob : { key-1 : initiator, key-2 : bob-charlie, key-3 : bob, key-4 : null } ",
            "charlie : { key-1 : initiator, key-2 : bob-charlie, key-3 : charlie, key-4 : from-builder } ",
            "bob-subflow : { key-1 : initiator, key-2 : bob-charlie, key-3 : bob, key-4 : bob-subflow } ",
            "charlie-subflow : { key-1 : initiator, key-2 : bob-charlie, key-3 : charlie, key-4 : charlie-subflow } "
        )
        val result = list.joinToString(System.lineSeparator())
        assertThat(response, `is`(result))
    }
}