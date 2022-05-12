package net.corda.flow.service

import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.LifecycleStatus
import net.corda.lifecycle.test.impl.TestLifecycleCoordinatorFactoryImpl
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class FlowServiceTest {

    @Test
    fun `flow service correctly enters UP state`() {
        val lifecycleCoordinatorFactory = TestLifecycleCoordinatorFactoryImpl()
        val flowService = FlowService(
            lifecycleCoordinatorFactory,
            mock(),
            mock(),
            mock(),
        )

        flowService.start()

        val lifecycleCoordinator = lifecycleCoordinatorFactory.coordinators[LifecycleCoordinatorName.forComponent<FlowService>()]!!

        val followedCoordinators = setOf(
            LifecycleCoordinatorName.forComponent<ConfigurationReadService>(),
            LifecycleCoordinatorName.forComponent<SandboxGroupContextComponent>(),
            LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>(),
            LifecycleCoordinatorName.forComponent<CpiInfoReadService>(),
        )

        assertThat(lifecycleCoordinator.registrations.first().coordinators).isEqualTo(followedCoordinators)

        assertThat(lifecycleCoordinator.isRunning)
        assertThat(lifecycleCoordinator.status).isEqualTo(LifecycleStatus.DOWN)
    }
}
