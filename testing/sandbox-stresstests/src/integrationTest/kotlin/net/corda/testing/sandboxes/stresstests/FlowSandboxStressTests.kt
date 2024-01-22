package net.corda.testing.sandboxes.stresstests

import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.stresstests.utils.StressTestType
import net.corda.testing.sandboxes.stresstests.utils.TestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlowSandboxStressTests : TestBase() {

    @BeforeAll
    fun setup(
        @InjectService(timeout = TIMEOUT_MILLIS)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        this.bundleContext = bundleContext
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            virtualNodeService = setup.fetchService(TIMEOUT_MILLIS)
            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
            flowSandboxService = setup.fetchService(TIMEOUT_MILLIS)
        }
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class)
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `create sandboxes - no cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.FLOW) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash} ${sandbox.sandboxGroup.id}")
        }

        assertThat(evictions).isEqualTo(testType.numSandboxes)
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class)
    @Timeout(value = 1, unit = TimeUnit.MINUTES) // This times out for 100 and 250 vnodes when using 1min; using 5 mins
    fun `retrieve sandboxes using large cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes)

        //set cache size to 251
        virtualNodeService.sandboxGroupContextComponent.resizeCache(SandboxGroupType.FLOW, 251)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.FLOW) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }

        // no evictions should have happened when creating the sandboxes
        assertThat(evictions).isEqualTo(0)

        // retrieve all sandboxes from the cache
        val sandboxes = mutableSetOf<UUID>()
        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            sandboxes.add(sandbox.sandboxGroup.id)
            println("Retrieving sandbox ${sandbox.sandboxGroup.id} for virtual node ${it.holdingIdentity.shortHash}")

            // TODO: should probably exercise the sandbox somehow
        }

        assertThat(sandboxes.size).isEqualTo(testType.numSandboxes)
        // no evictions should have happened when retrieving the sandboxes
        assertThat(evictions).isEqualTo(0)
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class, names = ["ONE_HUNDRED_SANDBOXES", "TWO_HUNDRED_FIFTY_SANDBOXES"])
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes using small cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes)

        //set cache size to 10
        virtualNodeService.sandboxGroupContextComponent.resizeCache(SandboxGroupType.FLOW, 10)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.FLOW) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }


        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            println("Created sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
        }

        println("NUMBER OF EVICTIONS IS $evictions")
        assertThat(evictions).isEqualTo(testType.numSandboxes - 10)
        evictions = 0

        val sandboxes = mutableSetOf<UUID>()
        // retrieve all sandboxes from the cache
        // go in reverse order, so we can at least get some from the cache
        vNodes.reverse()
        vNodes.forEach {
            val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            sandboxes.add(sandbox.sandboxGroup.id)
            println("Pulled sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")

            // TODO: should probably exercise the sandbox somehow
        }

        assertThat(sandboxes.size).isEqualTo(testType.numSandboxes)
    }
}