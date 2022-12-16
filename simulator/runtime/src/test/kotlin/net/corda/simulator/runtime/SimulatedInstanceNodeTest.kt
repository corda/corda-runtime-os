package net.corda.simulator.runtime

import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowManager
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.signing.SimKeyStore
import net.corda.simulator.runtime.testutils.createMember
import net.corda.v5.application.flows.RPCStartableFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SimulatedInstanceNodeTest {

    private lateinit var fiber : SimFiber
    private lateinit var flowFactory : FlowFactory
    private lateinit var injector : FlowServicesInjector
    private lateinit var keyStore : SimKeyStore
    private val holdingId = HoldingIdentityBase(createMember("IRunCordapps"), "GroupId")

    @BeforeEach
    fun `setup mocks`() {
        fiber = mock()
        flowFactory = mock()
        injector = mock()
        keyStore = mock()
    }

    @Test
    fun `should inject services into instance flow and call flow`() {
        // Given a virtual node with dependencies
        val flowManager = mock<FlowManager>()
        val flow = mock<RPCStartableFlow>()

        val virtualNode = SimulatedInstanceNodeBase(
            holdingId,
            "a protocol",
            flow,
            fiber,
            injector,
            flowFactory,
            flowManager
        )

        // When we create a node for an instance flow
        val input = RPCRequestDataWrapperFactory().create("r1", "aClass", "someData")

        virtualNode.callInstanceFlow(input)

        // Then it should have instantiated the node and injected the services into it
        verify(injector, times(1)).injectServices(eq(flow), eq(holdingId.member), eq(fiber), any())

        // And the flow should have been called
        verify(flowManager, times(1)).call(
            argThat { request -> request.getRequestBody() == "someData" },
            eq(flow)
        )
    }
}