package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class BaseFlowRegistryTest {
    private val memberA = MemberX500Name.parse("CN=CorDapperA, OU=Application, O=R3, L=London, C=GB")
    private val memberB = MemberX500Name.parse("CN=CorDapperB, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should look up instance flows for a given member`() {
        // Given a fiber with a concrete implementation registered for a protocol
        val flowRegistry = BaseFlowRegistry()
        val flow = mock<RPCStartableFlow>()
        val responder = mock<ResponderFlow>()
        val nonFlow = DummyFlow()
        val protocol = "protocol-1"
        flowRegistry.registerFlowInstance(memberA, protocol, flow)
        flowRegistry.registerFlowInstance(memberB, protocol, responder)

        assertThrows<IllegalArgumentException>{
            flowRegistry.registerFlowInstance(memberA, protocol, nonFlow)
        }

        // When we look up an instance of a flow
        val result = flowRegistry.lookupFlowInstance(memberA)
        val result1 = flowRegistry.lookupFlowInstance(memberB)

        // Then it should successfully return it
        MatcherAssert.assertThat(result, Matchers.`is`(hashMapOf(flow to protocol)))
        MatcherAssert.assertThat(result1, Matchers.`is`(hashMapOf(responder to protocol)))
    }

    @Test
    fun `should tell us if it cant find a flow for a given party and protocol`() {
        // Given a fiber and a node with a flow and protocol already
        val flowRegistry = BaseFlowRegistry()
        flowRegistry.registerResponderClass(memberA, "protocol-1", SimFiberBaseTest.Flow1::class.java)
        flowRegistry.registerFlowInstance(memberB, "protocol-2", SimFiberBaseTest.Flow2())

        // When we try to look up a member or protocol that doesn't exist
        // Then it should throw an error
        Assertions.assertNull(flowRegistry.lookUpResponderInstance(memberA, "protocol-2"))
        Assertions.assertNull(flowRegistry.lookUpResponderClass(memberA, "protocol-2"))

        Assertions.assertNull(flowRegistry.lookUpResponderInstance(memberB, "protocol-1"))
        Assertions.assertNull(flowRegistry.lookUpResponderClass(memberB, "protocol-1"))
    }

    @Test
    fun `should look up concrete implementations for a given protocol and a given party`() {
        // Given a fiber with a concrete implementation registered for a protocol
        val flowRegistry = BaseFlowRegistry()
        val flow = SimFiberBaseTest.Flow1()
        flowRegistry.registerFlowInstance(memberA, "protocol-1", flow)

        // When we look up an instance of a flow
        val result = flowRegistry.lookUpResponderInstance(memberA, "protocol-1")

        // Then it should successfully return it
        MatcherAssert.assertThat(result, Matchers.`is`(flow))
    }

    @Test
    fun `should look up the matching flow class for a given protocol and a given party`() {
        // Given a fiber and two nodes with some shared flow protocol
        val flowRegistry = BaseFlowRegistry()
        flowRegistry.registerResponderClass(memberA, "protocol-1", SimFiberBaseTest.Flow1::class.java)
        flowRegistry.registerResponderClass(memberA, "protocol-2", SimFiberBaseTest.Flow2::class.java)
        flowRegistry.registerResponderClass(memberB, "protocol-1", SimFiberBaseTest.Flow3InitBy1::class.java)
        flowRegistry.registerResponderClass(memberB, "protocol-2", SimFiberBaseTest.Flow4InitBy2::class.java)

        // When we look up a given protocol for a given party
        val flowClass = flowRegistry.lookUpResponderClass(memberB, "protocol-2")

        // Then we should get the flow that matches both the protocol and the party
        MatcherAssert.assertThat(flowClass, Matchers.`is`(SimFiberBaseTest.Flow4InitBy2::class.java))
    }

    @Test
    fun `should prevent us from uploading a responder twice for a given party and protocol`() {
        // Given a fiber and a node with a flow and protocol already
        val flowRegistry = BaseFlowRegistry()
        flowRegistry.registerResponderClass(memberA, "protocol-1", SimFiberBaseTest.Flow1::class.java)
        flowRegistry.registerFlowInstance(memberB, "protocol-1", SimFiberBaseTest.Flow2())

        // When we try to register a node and flow with the same protocol
        // regardless of whether it is, or was, a class or instance
        // Then it should throw an error
        assertThrows<IllegalStateException> { // class, then class
            flowRegistry.registerResponderClass(memberA, "protocol-1", SimFiberBaseTest.Flow2::class.java)
        }
        assertThrows<IllegalStateException> { // instance, then class
            flowRegistry.registerResponderClass(memberB, "protocol-1", SimFiberBaseTest.Flow2::class.java)
        }
        assertThrows<IllegalStateException> { // instance, then instance
            flowRegistry.registerFlowInstance(memberA, "protocol-1", SimFiberBaseTest.Flow1())
        }
        assertThrows<IllegalStateException> { // class, then instance
            flowRegistry.registerFlowInstance(memberA, "protocol-1", SimFiberBaseTest.Flow1())
        }
    }
}

class DummyFlow: Flow