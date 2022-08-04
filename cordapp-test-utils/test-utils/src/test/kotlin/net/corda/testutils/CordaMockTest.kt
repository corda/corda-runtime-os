package net.corda.testutils

import net.corda.testutils.exceptions.NoDefaultConstructorException
import net.corda.testutils.flows.HelloFlow
import net.corda.testutils.flows.PingAckFlow
import net.corda.testutils.flows.ValidStartingFlow
import net.corda.testutils.internal.FiberMock
import net.corda.testutils.tools.FlowChecker
import net.corda.testutils.tools.RPCRequestDataMock
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
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

class CordaMockTest {
    private val x500 = MemberX500Name.parse("CN=IRunCorDapps, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should pass on any errors from the flow checker`() {
        // Given a mock flow checker in our mock Corda network
        val flowChecker = mock<FlowChecker>()
        val corda = CordaMock(flowChecker)

        // That is set to provide an error
        whenever(flowChecker.check(any())).doThrow(NoDefaultConstructorException(HelloFlow::class.java))

        // When we upload the flow
        // Then it should error
        assertThrows<NoDefaultConstructorException> { corda.upload(x500, HelloFlow::class.java) }
    }

    @Test
    fun `should be able to choose between multiple flows for a given party`() {
        // Given a mock Corda network
        val corda = CordaMock()

        // When I upload two flows
        corda.upload(x500, HelloFlow::class.java)
        corda.upload(x500, ValidStartingFlow::class.java)

        // And I invoke the first one (let's use the constructor for RPC requests for fun)
        val response = corda.invoke(x500,
            RPCRequestDataMock("r1", HelloFlow::class.java.name, "{ \"name\" : \"CordaDev\" }")
        )

        // Then it should appear to properly invoke the flow
        assertThat(response, `is`("Hello CordaDev!"))
    }

    @Test
    fun `should be able to upload a concrete instance of a responder for a member and protocol`() {
        // Given a mock Corda network with a fiber we control
        val fiber = mock<FiberMock>()
        val corda = CordaMock(fiberMock = fiber)

        // And a concrete responder
        val responder = object : ResponderFlow {
            override fun call(session: FlowSession) {
            }
        }

        // When I upload the relevant flow and concrete responder
        corda.upload(x500, PingAckFlow::class.java)
        corda.upload(x500, "ping-ack", responder)

        // Then it should have registered the responder with the fiber
        verify(fiber, times(1)).registerResponderInstance(x500,"ping-ack", responder)
    }
}




