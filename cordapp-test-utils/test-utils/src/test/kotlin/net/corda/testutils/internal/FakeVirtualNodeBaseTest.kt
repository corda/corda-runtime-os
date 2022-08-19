package net.corda.testutils.internal

import net.corda.testutils.HoldingIdentity
import net.corda.testutils.tools.RPCRequestDataWrapper
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.persistence.PersistenceService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FakeVirtualNodeBaseTest {

    @Test
    fun `should instantiate flow, inject services and call flow`() {
        // Given a virtual node with dependencies
        val fakeFiber = mock<FakeFiber>()
        val flowFactory = mock<FlowFactory>()
        val injector = mock<FlowServicesInjector>()
        val flow = mock<RPCStartableFlow>()

        whenever(flowFactory.createInitiatingFlow(any(), any())).thenReturn(flow)

        val holdingId = HoldingIdentity.create("IRunCordapps")
        val virtualNode = FakeVirtualNodeBase(
            holdingId,
            fakeFiber,
            injector,
            flowFactory
        )

        // When a flow class is run on that node
        // (NB: it doesn't actually matter if the flow class was created in that node or not)
        val input = RPCRequestDataWrapper("r1", "aClass", "someData")
        virtualNode.callFlow(input)

        // Then it should have instantiated the node and injected the services into it
        verify(injector, times(1)).injectServices(flow, holdingId.member, fakeFiber, flowFactory)

        // And the flow should have been called
        verify(flow, times(1)).call(argThat {request -> request.getRequestBody() == "someData" })
    }

    @Test
    fun `should return any persistence service registered for that member on the FakeFiber`() {
        // Given a virtual node with dependencies
        val fakeFiber = mock<FakeFiber>()
        val flowFactory = mock<FlowFactory>()
        val injector = mock<FlowServicesInjector>()

        val holdingId = HoldingIdentity.create("IRunCordapps")
        val virtualNode = FakeVirtualNodeBase(
            holdingId,
            fakeFiber,
            injector,
            flowFactory
        )

        // And a persistence service registered on the fiber
        val persistenceService = mock<PersistenceService>()
        whenever(fakeFiber.getOrCreatePersistenceService(holdingId.member)).thenReturn(persistenceService)

        // When we get the persistence service
        val result = virtualNode.getPersistenceService()

        // Then it should be the one that was registered
        assertThat(result, `is`(persistenceService))
    }
}