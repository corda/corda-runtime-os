package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.exceptions.NoDefaultConstructorException
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.testflows.HelloFlow
import net.corda.simulator.runtime.testflows.PingAckFlow
import net.corda.simulator.runtime.testflows.ValidStartingFlow
import net.corda.simulator.tools.FlowChecker
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

class SimulatedCordaNetworkBaseTest {
    private val holdingId = HoldingIdentity.create("IRunCordapps")

    @Test
    fun `should pass on any errors from the flow checker`() {
        // Given a mock flow checker in our simulated Corda network
        val flowChecker = mock<FlowChecker>()
        val corda = SimulatorDelegateBase(mock(), flowChecker)

        // That is set to provide an error
        whenever(flowChecker.check(any())).doThrow(NoDefaultConstructorException(HelloFlow::class.java))

        // When we upload the flow
        // Then it should error
        assertThrows<NoDefaultConstructorException> { corda.createVirtualNode(holdingId, HelloFlow::class.java) }
    }

    @Test
    fun `should be able to choose between multiple flows for a given party`() {
        // Given a simulated Corda network
        val corda = SimulatorDelegateBase(mock())

        // When I upload two flows
        val helloVirtualNode = corda.createVirtualNode(holdingId, HelloFlow::class.java, ValidStartingFlow::class.java)

        // And I invoke the first one (let's use the constructor for RPC requests for fun)
        val response = helloVirtualNode.callFlow(
            RPCRequestDataWrapperFactory().create("r1", HelloFlow::class.java.name, "{ \"name\" : \"CordaDev\" }")
        )

        // Then it should appear to properly invoke the flow
        assertThat(response, `is`("Hello CordaDev!"))
    }

    @Test
    fun `should be able to upload a concrete instance of a responder for a member and protocol`() {
        // Given a simulated Corda network with a simulated fiber we control
        val fiber = mock<SimFiber>()
        val corda = SimulatorDelegateBase(mock(), fiber = fiber)

        // And a concrete responder
        val responder = object : ResponderFlow {
            override fun call(session: FlowSession) = Unit
        }

        // When I upload the relevant flow and concrete responder
        corda.createVirtualNode(holdingId, PingAckFlow::class.java)
        corda.createVirtualNode(holdingId, "ping-ack", responder)

        // Then it should have registered the responder with the fiber
        verify(fiber, times(1)).registerFlowInstance(holdingId.member,"ping-ack", responder)
    }

    @Test
    fun `should register initiating members with the fiber`() {
        // Given a simulated Corda network with a simulated fiber we control
        val fiber = mock<SimFiber>()
        val corda = SimulatorDelegateBase(mock(), fiber = fiber)

        // When I upload an initiating flow
        corda.createVirtualNode(holdingId, PingAckFlow::class.java)

        // Then it should have registered the initiating member with the fiber
        verify(fiber, times(1)).registerMember(holdingId.member)
    }

    @Test
    fun `should close the fiber when it is closed`() {
        // Given a simulated Corda network with a fiber we control
        val fiber = mock<SimFiber>()
        val corda = SimulatorDelegateBase(mock(), fiber = fiber)

        // When we close Corda
        corda.close()

        // Then it should close the fiber too
        verify(fiber, times(1)).close()
    }

    @Test
    fun `should error if no flows are provided`() {
        val corda = SimulatorDelegateBase(mock())
        assertThrows<java.lang.IllegalArgumentException> { corda.createVirtualNode(holdingId) }
    }
}




