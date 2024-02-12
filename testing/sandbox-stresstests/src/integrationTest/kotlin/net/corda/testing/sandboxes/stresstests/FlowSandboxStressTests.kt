package net.corda.testing.sandboxes.stresstests

import io.micrometer.core.instrument.Tag
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.stresstests.utils.MeasurementType
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
import java.util.concurrent.TimeUnit
import kotlin.test.Ignore

@Ignore
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
        noCacheMetricsWriter = Files.newBufferedWriter(Paths.get("flow_no_cache.txt"))
        smallCacheMetricsWriter = Files.newBufferedWriter(Paths.get("flow_small_cache.txt"))
        largeCacheMetricsWriter = Files.newBufferedWriter(Paths.get("flow_large_cache.txt"))

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

        // memory usage before creating sandboxes
        noCacheMetricsWriter.write("### Memory usage before creating sandboxes - ${testType.testName}")
        noCacheMetricsWriter.newLine()
        noCacheMetricsWriter.write(getMeasurements(MeasurementType.MEMORY))
        noCacheMetricsWriter.newLine()

        prometheusMeterRegistry.timer("flow.sandbox.create.time",
            listOf(Tag.of("test.type", testType.name))).recordCallable {

            vNodes.forEach {
                val sandbox = getOrCreateSandbox(flowSandboxService::get, it)
                println("Created sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
            }
        }

        noCacheMetricsWriter.write("### Memory usage after creating sandboxes - ${testType.testName}")
        noCacheMetricsWriter.newLine()
        noCacheMetricsWriter.write(getMeasurements(MeasurementType.MEMORY))
        noCacheMetricsWriter.newLine()

        assertThat(evictions).isEqualTo(testType.numSandboxes)

        noCacheMetricsWriter.write("### Sandbox manipulation times - ${testType.numSandboxes}")
        noCacheMetricsWriter.newLine()
        noCacheMetricsWriter.write(getMeasurements(MeasurementType.SANDBOX_TIMES))
        noCacheMetricsWriter.newLine()
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class)
    @Timeout(value = 5, unit = TimeUnit.MINUTES) // This times out for 100 and 250 vnodes when using 1min; using 5 mins
    fun `retrieve sandboxes using large cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes)
        executeTest(largeCacheMetricsWriter,
            SandboxGroupType.FLOW,
            flowSandboxService::get,
            251,
            testType,
            0)
    }

    @ParameterizedTest
    @EnumSource(StressTestType::class, names = ["ONE_HUNDRED_SANDBOXES", "TWO_HUNDRED_FIFTY_SANDBOXES"])
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    fun `retrieve sandboxes using small cache`(testType: StressTestType) {
        createVnodes(testType.numSandboxes)
        executeTest(smallCacheMetricsWriter,
            SandboxGroupType.FLOW,
            flowSandboxService::get,
            10,
            testType,
            testType.numSandboxes - 10)
    }
}