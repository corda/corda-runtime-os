package net.corda.testing.sandboxes.stresstests

import io.micrometer.core.instrument.Tag
import net.corda.sandboxgroupcontext.SandboxGroupContext
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
        // set-up file to dump metrics in
        val metricsFilePath = Paths.get("flow_no-cache_${testType.testName}")
        val bw = Files.newBufferedWriter(metricsFilePath)

        createVnodes(testType.numSandboxes)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.FLOW) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        // memory usage before creating sandboxes
        bw.write("### Memory usage before creating sandboxes")
        bw.newLine()
        bw.write(getMemoryUsage())
        bw.newLine()

        vNodes.forEach {
            var sandbox: SandboxGroupContext? = null
            prometheusMeterRegistry.timer("flow.sandbox.create.time",
                listOf(Tag.of("virtual.node", it.holdingIdentity.shortHash.value))).recordCallable {
                sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            }
            println("Create sandbox for vNode ${it.holdingIdentity.shortHash} ${sandbox!!.sandboxGroup.id}")

            bw.write("### Memory usage after creating sandbox with ID ${sandbox!!.sandboxGroup.id} " +
                    "for vNode ${it.holdingIdentity.shortHash}")
            bw.newLine()
            bw.write(getMemoryUsage())
            bw.newLine()
        }

        assertThat(evictions).isEqualTo(testType.numSandboxes)

        // add all measurements (mostly used to get the sandbox creation times)
        bw.write("### End of test metrics")
        bw.newLine()
        bw.write(prometheusMeterRegistry.scrape())
        bw.close()
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class)
    @Timeout(value = 5, unit = TimeUnit.MINUTES) // This times out for 100 and 250 vnodes when using 1min; using 5 mins
    fun `retrieve sandboxes using large cache`(testType: StressTestType) {
        // set-up file to dump metrics in
        val metricsFilePath = Paths.get("flow_large-cache_${testType.testName}")
        executeTest(metricsFilePath, 251, testType, 0)
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class, names = ["ONE_HUNDRED_SANDBOXES", "TWO_HUNDRED_FIFTY_SANDBOXES"])
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes using small cache`(testType: StressTestType) {
        // set-up file to dump metrics in
        val metricsFilePath = Paths.get("flow_small-cache_${testType.testName}")
        executeTest(metricsFilePath, 10, testType, testType.numSandboxes - 10)
    }

    private fun executeTest(metricsFilePath: Path, cacheSize: Long, testType: StressTestType, numberOfEvictions: Int) {
        val bw = Files.newBufferedWriter(metricsFilePath)

        //set cache size to 10
        virtualNodeService.sandboxGroupContextComponent.resizeCache(SandboxGroupType.FLOW, cacheSize)

        createVnodes(testType.numSandboxes)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(SandboxGroupType.FLOW) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        // memory usage before creating sandboxes
        bw.write("### Memory usage before creating sandboxes")
        bw.newLine()
        bw.write(getMemoryUsage())
        bw.newLine()


        vNodes.forEach {
            var sandbox: SandboxGroupContext? = null

            prometheusMeterRegistry.timer("flow.sandbox.create.time",
                listOf(Tag.of("virtual.node", it.holdingIdentity.shortHash.value))).recordCallable {
                sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            }

            println("Create sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox!!.sandboxGroup.id}")

            bw.write("### Memory usage after creating sandbox with ID ${sandbox!!.sandboxGroup.id} " +
                    "for vNode ${it.holdingIdentity.shortHash}")
            bw.newLine()
            bw.write(getMemoryUsage())
            bw.newLine()
        }

        assertThat(evictions).isEqualTo(numberOfEvictions)

        val sandboxes = mutableSetOf<UUID>()
        // retrieve all sandboxes from the cache
        // go in reverse order, so we can at least get some from the cache
        vNodes.reverse()
        vNodes.forEach {
            var sandbox: SandboxGroupContext? = null

            prometheusMeterRegistry.timer("flow.sandbox.retrieve.time",
                listOf(Tag.of("virtual.node", it.holdingIdentity.shortHash.value))).recordCallable {
                sandbox = getOrCreateSandbox(flowSandboxService::get, it)
            }

            sandboxes.add(sandbox!!.sandboxGroup.id)
            println("Retrieving sandbox ${sandbox!!.sandboxGroup.id} for virtual node ${it.holdingIdentity.shortHash}")

            bw.write("### Memory usage after retrieving sandbox with ID ${sandbox!!.sandboxGroup.id} " +
                    "for vNode ${it.holdingIdentity.shortHash}")
            bw.newLine()
            bw.write(getMemoryUsage())
            bw.newLine()
        }

        assertThat(sandboxes.size).isEqualTo(testType.numSandboxes)

        // add all measurements (mostly used to get the sandbox creation times)
        bw.write("### End of test metrics")
        bw.newLine()
        bw.write(prometheusMeterRegistry.scrape())
        bw.close()
    }
}