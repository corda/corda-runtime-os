package net.corda.testutils

import net.corda.testutils.exceptions.NoDefaultConstructorException
import net.corda.testutils.flows.HelloFlow
import net.corda.testutils.flows.PingAckFlow
import net.corda.testutils.flows.ValidStartingFlow
import net.corda.testutils.internal.SimFiber
import net.corda.testutils.tools.FlowChecker
import net.corda.testutils.tools.RPCRequestDataWrapper
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CordaSimTest {
    private val holdingId = HoldingIdentity.create("IRunCordapps")

    @Test
    fun `should pass on any errors from the flow checker`() {
        // Given a mock flow checker in our simulated Corda network
        val flowChecker = mock<FlowChecker>()
        val corda = CordaSim(flowChecker)

        // That is set to provide an error
        whenever(flowChecker.check(any())).doThrow(NoDefaultConstructorException(HelloFlow::class.java))

        // When we upload the flow
        // Then it should error
        assertThrows<NoDefaultConstructorException> { corda.createVirtualNode(holdingId, HelloFlow::class.java) }
    }

    @Test
    fun `should be able to choose between multiple flows for a given party`() {
        // Given a simulated Corda network
        val corda = CordaSim()

        // When I upload two flows
        val helloVirtualNode = corda.createVirtualNode(holdingId, HelloFlow::class.java, ValidStartingFlow::class.java)

        // And I invoke the first one (let's use the constructor for RPC requests for fun)
        val response = helloVirtualNode.callFlow(
            RPCRequestDataWrapper("r1", HelloFlow::class.java.name, "{ \"name\" : \"CordaDev\" }")
        )

        // Then it should appear to properly invoke the flow
        assertThat(response, `is`("Hello CordaDev!"))
    }

    @Test
    fun `should be able to upload a concrete instance of a responder for a member and protocol`() {
        // Given a simulated Corda network with a simulated fiber we control
        val fiber = mock<SimFiber>()
        val corda = CordaSim(fiber = fiber)

        // And a concrete responder
        val responder = object : ResponderFlow {
            override fun call(session: FlowSession) {
            }
        }

        // When I upload the relevant flow and concrete responder
        corda.createVirtualNode(holdingId, PingAckFlow::class.java)
        corda.createVirtualNode(holdingId, "ping-ack", responder)

        // Then it should have registered the responder with the fiber
        verify(fiber, times(1)).registerResponderInstance(holdingId.member,"ping-ack", responder)
    }

    @Test
    fun `should close the fiber when it is closed`() {
        // Given a simulated Corda network with a fiber we control
        val fiber = mock<SimFiber>()
        val corda = CordaSim(fiber = fiber)

        // When we close Corda
        corda.close()

        // Then it should close the fiber too
        verify(fiber, times(1)).close()
    }
}




