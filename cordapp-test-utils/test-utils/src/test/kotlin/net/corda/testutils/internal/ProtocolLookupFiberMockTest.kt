package net.corda.testutils.internal

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProtocolLookupFiberMockTest {

    val memberA = MemberX500Name.parse("CN=CorDapperA, OU=Application, O=R3, L=London, C=GB")
    val memberB = MemberX500Name.parse("CN=CorDapperB, OU=Application, O=R3, L=London, C=GB")
    val memberC = MemberX500Name.parse("CN=CorDapperC, OU=Application, O=R3, L=London, C=GB")


    @Test
    fun `should look up concrete implementations for a given protocol and a given party`() {
        // Given a fiber with a concrete implementation registered for a protocol
        val fiber = ProtocolLookUpFiberMock()
        val flow = Flow1()
        fiber.registerResponderInstance(memberA, "protocol-1", flow)

        // When we look up an instance of a flow
        val result = fiber.lookUpResponderInstance(memberA, "protocol-1")

        // Then it should successfully return it
        assertThat(result, `is`(flow))
    }

    @Test
    fun `should look up the matching flow class for a given protocol and a given party`() {
        // Given a fiber and two nodes with some shared flow protocol
        val fiber = ProtocolLookUpFiberMock()
        fiber.registerResponderClass(memberA, "protocol-1", Flow1::class.java)
        fiber.registerResponderClass(memberA, "protocol-2", Flow2::class.java)
        fiber.registerResponderClass(memberB, "protocol-1", Flow3InitBy1::class.java)
        fiber.registerResponderClass(memberB, "protocol-2", Flow4InitBy2::class.java)

        // When we look up a given protocol for a given party
        val flowClass = fiber.lookUpResponderClass(memberB, "protocol-2")

        // Then we should get the flow that matches both the protocol and the party
        assertThat(flowClass, `is`(Flow4InitBy2::class.java))
    }

    @Test
    fun `should prevent us from uploading a responder twice for a given party and protocol`() {
        // Given a fiber and a node with a flow and protocol already
        val fiber = ProtocolLookUpFiberMock()
        fiber.registerResponderClass(memberA, "protocol-1", Flow1::class.java)
        fiber.registerResponderInstance(memberB, "protocol-1", Flow2())

        // When we try to register a node and flow with the same protocol
        // regardless of whether it is, or was, a class or instance
        // Then it should throw an error
        assertThrows<IllegalStateException> { // class, then class
            fiber.registerResponderClass(memberA, "protocol-1", Flow2::class.java)
        }
        assertThrows<IllegalStateException> { // instance, then class
            fiber.registerResponderClass(memberB, "protocol-1", Flow2::class.java)
        }
        assertThrows<IllegalStateException> { // instance, then instance
            fiber.registerResponderInstance(memberA, "protocol-1", Flow1())
        }
        assertThrows<IllegalStateException> { // class, then instance
            fiber.registerResponderInstance(memberA, "protocol-1", Flow1())
        }
    }

    @Test
    fun `should tell us if it cant find a flow for a given party and protocol`() {
        // Given a fiber and a node with a flow and protocol already
        val fiber = ProtocolLookUpFiberMock()
        fiber.registerResponderClass(memberA, "protocol-1", Flow1::class.java)
        fiber.registerResponderInstance(memberB, "protocol-2", Flow2())

        // When we try to look up a member or protocol that doesn't exist
        // Then it should throw an error
        assertNull(fiber.lookUpResponderInstance(memberA, "protocol-2"))
        assertNull(fiber.lookUpResponderClass(memberA, "protocol-2"))

        assertNull(fiber.lookUpResponderInstance(memberB, "protocol-1"))
        assertNull(fiber.lookUpResponderClass(memberB, "protocol-1"))
    }

    class Flow1 : ResponderFlow { override fun call(session: FlowSession) {} }
    class Flow2 : ResponderFlow { override fun call(session: FlowSession) {} }
    class Flow3InitBy1 : ResponderFlow { override fun call(session: FlowSession) {} }
    class Flow4InitBy2 : ResponderFlow { override fun call(session: FlowSession) {} }
}