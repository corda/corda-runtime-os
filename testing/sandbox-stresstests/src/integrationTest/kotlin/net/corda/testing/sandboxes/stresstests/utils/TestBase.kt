package net.corda.testing.sandboxes.stresstests.utils

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import org.osgi.framework.BundleContext
import java.lang.StringBuilder

open class TestBase {
    companion object {
        const val TIMEOUT_MILLIS = 10000L
    }

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
        JvmMemoryMetrics().bindTo(prometheusMeterRegistry)
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

    fun getOrCreateSandbox(builder: (holdingIdentity: HoldingIdentity, hashes: Set<SecureHash>)-> SandboxGroupContext,
                           vNode: VirtualNodeInfo): SandboxGroupContext {
        // create the sandbox
        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(vNode)
        return builder.invoke(vNode.holdingIdentity, cpkFileHashes)
    }

    fun getMemoryUsage(): String {
        val sb = StringBuilder()
        prometheusMeterRegistry.scrape().lines().forEach {
            if (it.contains("jvm")) {
                sb.append(it)
                sb.append(System.lineSeparator())
            }
        }
        return sb.toString()
    }
 }

enum class StressTestType(val numSandboxes: Int, val testName: String) {
    TEN_SANDBOXES(10, "10-sandboxes"),
    ONE_HUNDRED_SANDBOXES(100, "100-sandboxes"),
    TWO_HUNDRED_FIFTY_SANDBOXES(250, "250-sandboxes")
}