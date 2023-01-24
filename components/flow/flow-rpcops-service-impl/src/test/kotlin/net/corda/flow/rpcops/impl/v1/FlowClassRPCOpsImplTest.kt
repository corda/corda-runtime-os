package net.corda.flow.rpcops.impl.v1

import java.time.Instant
import java.util.UUID
import java.util.stream.Stream
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.rpcops.v1.FlowClassRestResource
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.VirtualNodeState
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.provider.Arguments
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlowClassRPCOpsImplTest {

    companion object{
        @JvmStatic
        fun dependants(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<CpiInfoReadService>())
            )
        }
    }

    private lateinit var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory
    private lateinit var cpiInfoReadService: CpiInfoReadService
    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

    private fun getStubVirtualNode(): VirtualNodeInfo {
        return VirtualNodeInfo(createTestHoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", ""),
            CpiIdentifier("", "",
            SecureHash("", "bytes".toByteArray())),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            VirtualNodeState.ACTIVE,
            0,
            Instant.now()
        )
    }

    @BeforeEach
    fun setup() {
        lifecycleCoordinatorFactory = mock()
        cpiInfoReadService = mock()
        virtualNodeInfoReadService = mock()

        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(getStubVirtualNode())
        whenever(cpiInfoReadService.get(any())).thenReturn(mock())
    }

    @Test
    fun `start event doesnt post up status`() {
        val context = getFlowClassRPCOpsTestContext()
        context.run {
            testClass.start()

            context.verifyIsDown<FlowClassRestResource>()
        }
    }

    @Test
    fun `start event posts up status after all components are up`() {
        val context = getFlowClassRPCOpsTestContext()
        context.run {
            testClass.start()
            bringDependenciesUp()
            context.verifyIsUp<FlowClassRestResource>()
        }
    }

    @Test
    fun `Resource not found error when no vNode exists`() {
        whenever(virtualNodeInfoReadService.getByHoldingIdentityShortHash(any())).thenReturn(null)
        val flowClassRPCOps = FlowClassRestResourceImpl(lifecycleCoordinatorFactory, virtualNodeInfoReadService, cpiInfoReadService)
        assertThrows<ResourceNotFoundException> {
            flowClassRPCOps.getStartableFlows("1234567890ab")
        }
        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(cpiInfoReadService, times(0)).get(any())
    }

    @Test
    fun `Resource not found error when no CPI exists`() {
        whenever(cpiInfoReadService.get(any())).thenReturn(null)

        val flowClassRPCOps = FlowClassRestResourceImpl(lifecycleCoordinatorFactory, virtualNodeInfoReadService, cpiInfoReadService)
        assertThrows<ResourceNotFoundException> {
            flowClassRPCOps.getStartableFlows("1234567890ab")
        }
        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(cpiInfoReadService, times(1)).get(any())
    }

    @Test
    fun `Get flow classes executes cpi service and vnode service and returns list of strings`() {
        val flowClassRPCOps = FlowClassRestResourceImpl(lifecycleCoordinatorFactory, virtualNodeInfoReadService, cpiInfoReadService)
        flowClassRPCOps.getStartableFlows("1234567890ab")
        verify(virtualNodeInfoReadService, times(1)).getByHoldingIdentityShortHash(any())
        verify(cpiInfoReadService, times(1)).get(any())
    }

    private fun getFlowClassRPCOpsTestContext(): LifecycleTest<FlowClassRestResourceImpl> {
        return LifecycleTest {
            addDependency<LifecycleCoordinatorFactory>()
            addDependency<VirtualNodeInfoReadService>()
            addDependency<CpiInfoReadService>()

            FlowClassRestResourceImpl(
                coordinatorFactory,
                virtualNodeInfoReadService,
                cpiInfoReadService
            )
        }
    }
}
