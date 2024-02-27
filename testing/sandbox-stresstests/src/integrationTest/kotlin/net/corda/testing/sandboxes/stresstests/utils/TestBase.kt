package net.corda.testing.sandboxes.stresstests.utils

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmHeapPressureMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.metrics.CordaMetrics
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.osgi.framework.BundleContext
import java.io.BufferedWriter
import java.lang.StringBuilder
import java.util.UUID

open class TestBase {
    companion object {
        const val TIMEOUT_MILLIS = 10000L
    }

    lateinit var noCacheMetricsWriter: BufferedWriter
    lateinit var largeCacheMetricsWriter: BufferedWriter
    lateinit var smallCacheMetricsWriter: BufferedWriter

    @RegisterExtension
    val lifecycle = EachTestLifecycle()

    lateinit var virtualNodeService: VirtualNodeService
    lateinit var cpiInfoReadService: CpiInfoReadService
    lateinit var flowSandboxService: FlowSandboxService

    lateinit var bundleContext: BundleContext
    
    lateinit var vNodes: MutableList<VirtualNodeInfo>

    lateinit var prometheusMeterRegistry: PrometheusMeterRegistry

    @BeforeEach
    fun setUpMetrics() {
        prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        CordaMetrics.configure("test", prometheusMeterRegistry, null, null)

        JvmMemoryMetrics().bindTo(prometheusMeterRegistry)
        JvmGcMetrics().bindTo(CordaMetrics.registry)
        JvmHeapPressureMetrics().bindTo(CordaMetrics.registry)
        ProcessorMetrics().bindTo(CordaMetrics.registry)
        JvmThreadMetrics().bindTo(CordaMetrics.registry)

        Metrics.globalRegistry.add(prometheusMeterRegistry)
    }

    @AfterEach
    fun cleanUpMetrics() {
        prometheusMeterRegistry.forEachMeter {
            prometheusMeterRegistry.remove(it)
        }

        // Remove every meter created by the Corda code, so it doesn't pollute the data from the following test
        Metrics.globalRegistry.meters.forEach {
            Metrics.globalRegistry.remove(it)
        }

        Metrics.removeRegistry(prometheusMeterRegistry)

        Assertions.assertThat(Metrics.globalRegistry.registries.size == 0)
    }

    @AfterAll
    fun closeWriters() {
        noCacheMetricsWriter.close()
        smallCacheMetricsWriter.close()
        largeCacheMetricsWriter.close()
    }

    fun createVnodes(quantity: Int, doMigration: Boolean = false) {
        vNodes = mutableListOf()
        repeat(quantity) {
            if (doMigration) {
                vNodes.add(virtualNodeService.loadWithDbMigration(Resources.EXTENDABLE_CPB, it))
            } else {
                vNodes.add(virtualNodeService.load(Resources.EXTENDABLE_CPB, it))
            }
        }
    }

    @Suppress("LongParameterList")
    fun executeTest(writer: BufferedWriter,
                    sandboxType: SandboxGroupType,
                    builder: (holdingIdentity: HoldingIdentity, hashes: Set<SecureHash>)-> SandboxGroupContext,
                    cacheSize: Long,
                    testType: StressTestType,
                    numberOfEvictions: Int) {

        virtualNodeService.sandboxGroupContextComponent.resizeCache(sandboxType, cacheSize)

        // track evictions
        var evictions = 0
        virtualNodeService.sandboxGroupContextComponent.addEvictionListener(sandboxType) {
            evictions++
            println("Flow sandbox for virtual node ${it.holdingIdentity.shortHash} has been evicted")
        }

        // memory usage before creating sandboxes
        writer.write("### Memory usage before creating sandboxes - ${testType.testName}")
        writer.newLine()
        writer.write(getMeasurements(MeasurementType.MEMORY))
        writer.newLine()

        prometheusMeterRegistry.timer("${sandboxType.name.lowercase()}.sandbox.create.time",
            listOf(Tag.of("test.type", testType.name))).recordCallable {

            vNodes.forEach {
                val sandbox = getOrCreateSandbox(builder, it)
                println("Created sandbox for vNode ${it.holdingIdentity.shortHash}\n${sandbox.sandboxGroup.id}")
            }
        }

        writer.write("### Memory usage after creating sandboxes - ${testType.testName}")
        writer.newLine()
        writer.write(getMeasurements(MeasurementType.MEMORY))
        writer.newLine()
        Assertions.assertThat(evictions).isEqualTo(numberOfEvictions)

        val sandboxes = mutableSetOf<UUID>()
        // retrieve all sandboxes from the cache
        // go in reverse order, so we can at least get some from the cache
        vNodes.reverse()

        prometheusMeterRegistry.timer("${sandboxType.name.lowercase()}.sandbox.pull.time",
            listOf(Tag.of("test.type", testType.name))).recordCallable {
            vNodes.forEach {
                val sandbox = getOrCreateSandbox(builder, it)
                sandboxes.add(sandbox.sandboxGroup.id)
                println("Retrieved sandbox ${sandbox.sandboxGroup.id} for virtual node ${it.holdingIdentity.shortHash}")
            }
        }

        Assertions.assertThat(sandboxes.size).isEqualTo(testType.numSandboxes)

        writer.write("### Memory usage after pulling sandboxes - ${testType.testName}")
        writer.newLine()
        writer.write(getMeasurements(MeasurementType.MEMORY))
        writer.newLine()

        writer.write("### Sandbox manipulation times - ${testType.numSandboxes}")
        writer.newLine()
        writer.write(getMeasurements(MeasurementType.SANDBOX_TIMES))
        writer.newLine()
    }

    fun getOrCreateSandbox(builder: (holdingIdentity: HoldingIdentity, hashes: Set<SecureHash>)-> SandboxGroupContext,
                           vNode: VirtualNodeInfo): SandboxGroupContext {
        // create the sandbox
        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(vNode)
        return builder.invoke(vNode.holdingIdentity, cpkFileHashes)
    }

    fun getMeasurements(measurementType: MeasurementType): String {
        val sb = StringBuilder()
        prometheusMeterRegistry.scrape().lines().forEach {
            if (measurementType.testLine(it)) {
                sb.append(it)
                sb.append(System.lineSeparator())
            }
        }
        return sb.toString()
    }
 }

enum class MeasurementType(private val stringsToSearch: List<String>) {
    MEMORY(listOf("jvm")),
    SANDBOX_TIMES(listOf("sandbox_create_time", "sandbox_pull_time"));

    fun testLine(line: String): Boolean {
        stringsToSearch.forEach {
            if (line.contains(it))
                return true
        }

        return false
    }
}

enum class StressTestType(val numSandboxes: Int, val testName: String) {
    TEN_SANDBOXES(10, "10-sandboxes"),
    ONE_HUNDRED_SANDBOXES(100, "100-sandboxes"),
    TWO_HUNDRED_FIFTY_SANDBOXES(250, "250-sandboxes")
}