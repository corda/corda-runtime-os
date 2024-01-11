package net.corda.testing.sandboxes.stresstests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.db.persistence.testkit.components.VirtualNodeService
import net.corda.db.persistence.testkit.helpers.Resources
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.test.util.dsl.entities.cpx.getCpkFileHashes
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import org.junit.jupiter.api.extension.RegisterExtension
import org.osgi.framework.BundleContext

open class TestBase {
    companion object {
        const val TIMEOUT_MILLIS = 10000L
    }

    @RegisterExtension
    val lifecycle = EachTestLifecycle()

    lateinit var virtualNode: VirtualNodeService
    lateinit var cpiInfoReadService: CpiInfoReadService
    lateinit var flowSandboxService: FlowSandboxService

    lateinit var bundleContext: BundleContext
    
    lateinit var vNodes: MutableList<VirtualNodeInfo>

    fun createVnodes(quantity: Int) {
        vNodes = mutableListOf()
        repeat(quantity) {
            vNodes.add(virtualNode.load(Resources.EXTENDABLE_CPB))
        }
    }

    fun getOrCreateSandbox(builder: (holdingIdentity: HoldingIdentity, hashes: Set<SecureHash>)-> SandboxGroupContext,
                           vNode: VirtualNodeInfo): SandboxGroupContext {
        // create the sandbox
        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(vNode)
        return builder.invoke(vNode.holdingIdentity, cpkFileHashes)
    }
}

enum class StressTestType(val numSandboxes: Int, val testName: String) {
    TEN_SANDBOXES(10, "Create 10 sandboxes"),
    ONE_HUNDRED_SANDBOXES(100, "Create 100 sandboxes"),
    TWO_HUNDRED_FIFTY_SANDBOXES(250, "Create 250 sandboxes")
}