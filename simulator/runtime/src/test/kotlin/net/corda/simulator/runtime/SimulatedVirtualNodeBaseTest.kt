package net.corda.simulator.runtime

import net.corda.simulator.factories.RequestDataFactory
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowManager
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.messaging.SimFlowContextProperties
import net.corda.simulator.runtime.signing.SimKeyStore
import net.corda.simulator.runtime.testutils.createMember
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.persistence.PersistenceService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.isA
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SimulatedVirtualNodeBaseTest {


    private lateinit var fiber : SimFiber
    private lateinit var flowFactory : FlowFactory
    private lateinit var injector : FlowServicesInjector
    private lateinit var keyStore : SimKeyStore
    private val holdingId = HoldingIdentityBase(createMember("IRunCordapps"), "groupId")

    @Test
    fun `should be a RequestDataFactory`() {
        assertThat(RPCRequestDataWrapperFactory(), isA(RequestDataFactory::class.java))
    }

    @BeforeEach
    fun `setup mocks`() {
        fiber = mock()
        flowFactory = mock()
        injector = mock()
        keyStore = mock()
    }

    @Test
    fun `should instantiate flow, inject services and call flow`() {
        // Given a virtual node with dependencies
        val flowManager = mock<FlowManager>()
        val flow = mock<RPCStartableFlow>()
        val contextProperties = SimFlowContextProperties(emptyMap())
        whenever(flowFactory.createInitiatingFlow(any(), any())).thenReturn(flow)

        val virtualNode = SimulatedVirtualNodeBase(
            holdingId,
            fiber,
            injector,
            flowFactory,
            flowManager
        )

        // When a flow class is run on that node
        // (NB: it doesn't actually matter if the flow class was created in that node or not)
        val input = RPCRequestDataWrapperFactory().create("r1", "aClass", "someData")
        virtualNode.callFlow(input)

        // Then it should have instantiated the node and injected the services into it
        verify(injector, times(1)).injectServices(eq(flow), eq(holdingId.member), eq(fiber),
            eq(contextProperties))

        // And the flow should have been called
        verify(flowManager, times(1)).call(
            argThat { request -> request.getRequestBody() == "someData" },
            eq(flow)
        )
    }

    @Test
    fun `should return any persistence service registered for that member on the fiber`() {
        // Given a virtual node with dependencies
        val virtualNode = SimulatedVirtualNodeBase(
            holdingId,
            fiber,
            injector,
            flowFactory,
            mock()
        )

        // And a persistence service registered on the fiber
        val persistenceService = mock<PersistenceService>()
        whenever(fiber.getOrCreatePersistenceService(holdingId.member)).thenReturn(persistenceService)

        // When we get the persistence service
        val result = virtualNode.getPersistenceService()

        // Then it should be the one that was registered
        assertThat(result, `is`(persistenceService))
    }

}