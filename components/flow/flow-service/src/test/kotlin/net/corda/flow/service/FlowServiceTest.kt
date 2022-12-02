package net.corda.flow.service

import java.util.stream.Stream
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.flow.MINIMUM_SMART_CONFIG
import net.corda.flow.scheduler.FlowWakeUpScheduler
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.test.impl.LifecycleTest
import net.corda.sandboxgroupcontext.service.SandboxGroupContextComponent
import net.corda.schema.configuration.ConfigKeys
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class FlowServiceTest {

    companion object {
        @JvmStatic
        fun dependants(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(LifecycleCoordinatorName.forComponent<ConfigurationReadService>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<SandboxGroupContextComponent>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<VirtualNodeInfoReadService>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<CpiInfoReadService>()),
                Arguments.of(LifecycleCoordinatorName.forComponent<FlowExecutor>())
            )
        }
    }

    private val flowExecutor = mock<FlowExecutor>()
    private val flowWakeUpScheduler = mock<FlowWakeUpScheduler>()

    private val exampleConfig = mapOf(
        ConfigKeys.BOOT_CONFIG to MINIMUM_SMART_CONFIG,
        ConfigKeys.MESSAGING_CONFIG to MINIMUM_SMART_CONFIG,
        ConfigKeys.FLOW_CONFIG to MINIMUM_SMART_CONFIG
    )

    @Test
    fun `start event starts the flow executor`() {
        getFlowServiceTestContext().run {
            testClass.start()

            verify(flowExecutor).start()
        }
    }

    @Test
    fun `configuration service event registration once all dependent components are up`() {
        getFlowServiceTestContext().run {
            val flowServiceCoordinator = getCoordinatorFor<FlowService>()
            testClass.start()

            verify(this.configReadService, times(0)).registerComponentForUpdates(any(), any())

            bringDependenciesUp()

            verify(this.configReadService).registerComponentForUpdates(
                eq(flowServiceCoordinator),
                eq(setOf(ConfigKeys.BOOT_CONFIG, ConfigKeys.MESSAGING_CONFIG, ConfigKeys.FLOW_CONFIG))
            )
        }
    }

    @Test
    fun `on configuration event mark service up`() {
        getFlowServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()

            sendConfigUpdate<FlowService>(exampleConfig)

            verifyIsUp<FlowService>()
        }
    }

    @Test
    fun `on configuration event configures services`() {
        getFlowServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()

            sendConfigUpdate<FlowService>(exampleConfig)

            verify(flowExecutor).onConfigChange(any())
            verify(flowWakeUpScheduler).onConfigChange(any())
        }
    }

    @Test
    fun `on all dependents up flow service should not be up`() {
        getFlowServiceTestContext().run {
            testClass.start()
            bringDependenciesUp()

            verifyIsDown<FlowService>()
        }
    }

    @ParameterizedTest(name = "on component {0} going down the flow service should go down")
    @MethodSource("dependants")
    fun `on any dependent going down the flow service should go down`(name: LifecycleCoordinatorName) {
        getFlowServiceTestContext().run {
            testClass.start()

            bringDependenciesUp()
            sendConfigUpdate<FlowService>(exampleConfig)
            verifyIsUp<FlowService>()

            bringDependencyDown(name)

            verifyIsDown<FlowService>()
        }
    }

    @ParameterizedTest(name = "on component {0} going down the flow service should go to error")
    @MethodSource("dependants")
    fun `on any dependent going to error the flow service should go down`(name: LifecycleCoordinatorName) {
        getFlowServiceTestContext().run {
            testClass.start()

            bringDependenciesUp()
            sendConfigUpdate<FlowService>(exampleConfig)
            verifyIsUp<FlowService>()

            setDependencyToError(name)

            verifyIsDown<FlowService>()
        }
    }

    private fun getFlowServiceTestContext(): LifecycleTest<FlowService> {
        return LifecycleTest {
            addDependency<ConfigurationReadService>()
            addDependency<SandboxGroupContextComponent>()
            addDependency<VirtualNodeInfoReadService>()
            addDependency<CpiInfoReadService>()
            addDependency<FlowExecutor>()

            FlowService(
                coordinatorFactory,
                configReadService,
                flowExecutor,
                flowWakeUpScheduler,
            )
        }
    }
}