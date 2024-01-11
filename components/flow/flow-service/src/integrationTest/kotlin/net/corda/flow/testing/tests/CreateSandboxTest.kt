package net.corda.flow.testing.tests

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.test.flow.util.VirtualNodeCreationService
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension
import java.nio.file.Path

@Suppress("Unused")
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@Execution(ExecutionMode.SAME_THREAD)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreateSandboxTest {
    companion object {
        private const val TIMEOUT_MILLIS = 10000L
    }

//    @RegisterExtension
//    private val lifecycle = EachTestLifecycle()

//    @InjectService
//    lateinit var sandboxGroupContextComponent: SandboxGroupContextComponent

    @InjectService
    private lateinit var virtualNodeService: VirtualNodeCreationService
//
//    @InjectService
//    private lateinit var flowSandboxService: FlowSandboxService
//
    @InjectService
    private lateinit var cpiInfoReadService: CpiInfoReadService
//    @InjectService
//    private lateinit var cpkReadService: CpkReadService
//    @InjectService
//    private lateinit var virtualNodeInfoReadService: VirtualNodeInfoReadService

//    @BeforeAll
//    fun setup(
//        @InjectService(timeout = TIMEOUT_MILLIS)
//        sandboxSetup: SandboxSetup,
//        @InjectBundleContext
//        bundleContext: BundleContext,
//        @TempDir
//        testDirectory: Path
//    ) {
//        sandboxSetup.configure(bundleContext, testDirectory)
//        lifecycle.accept(sandboxSetup) { _ ->
////            virtualNodeService = setup.fetchService(TIMEOUT_MILLIS)
////            cpiInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
////            cpkReadService = setup.fetchService(TIMEOUT_MILLIS)
////            virtualNodeInfoReadService = setup.fetchService(TIMEOUT_MILLIS)
//        }
//
////        sandboxGroupContextComponent.resizeCaches(0)
//    }

//    @BeforeAll
//    fun setup() {
//        sandboxGroupContextComponent.resizeCaches(0) // WHY DOES THIS CAUSE ERRORS?
//    }

    @Test
    fun `create 10 sandboxes`() {
        // create 10 virtual nodes (what cpb/cpi/cpk are we using)?

        // 1. load cpb
        val holdingIdentity = HoldingIdentity(MemberX500Name.parse("C=GB,L=London,O=R3"), "dummy")
        println(holdingIdentity)

//        val sandboxGroupContext = flowSandboxService.get(holdingIdentity, setOf(CPK1_CHECKSUM))
//        println(sandboxGroupContext.virtualNodeContext.holdingIdentity)

//        val vNodeInfo = virtualNodeLoader.loadVirtualNode("/META-INF/testing-fish.cpb", holdingIdentity)
//        println(vNodeInfo)
        // 2. create sandbox
//        val cpkFileHashes = cpiInfoReadService.getCpkFileHashes(vNodeInfo)
//        val sandbox = flowSandboxService.get(holdingIdentity, cpkFileHashes)
//        println(sandbox)
    }

//    @Test
//    fun `create sandboxes`() {
//
//        // Why does this cause weird errors?
////        sandboxGroupContextComponent.resizeCaches(0)
//        // NEED TO CREATE SANDBOX GROUP CONTEXT FOR FLOWS
//
//        // need to generate a holding identity
//        val holdingIdentity = HoldingIdentity(MemberX500Name.parse("C=GB,L=London,O=R3"), "dummy")
//        val cpkFileHashes = emptySet<SecureHash>()
//        val vNodeContext = VirtualNodeContext(holdingIdentity, cpkFileHashes, SandboxGroupType.FLOW, null)
//
//        // cpks need to be loaded into the sandbox group context component
//
//        val sandboxGroupContext = sandboxGroupContextComponent.getOrCreate(vNodeContext) { _, _ ->
//            AutoCloseable { }
//        }
//
//        println(sandboxGroupContext)
//    }
//
//    @Test
//    fun `create flow sandbox`() {
//        val holdingIdentity = HoldingIdentity(MemberX500Name.parse("C=GB,L=London,O=R3"), "dummy")
//        val sandboxGroupContext = flowSandboxService.get(holdingIdentity, setOf(CPK1_CHECKSUM))
//        println(sandboxGroupContext.virtualNodeContext.holdingIdentity)
//
////        sandboxGroupContext.sandboxGroup.
//    }


}