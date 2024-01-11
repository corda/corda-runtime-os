//package net.corda.testing.sandboxes.stresstests
//
//import net.corda.cpiinfo.read.CpiInfoReadService
//import net.corda.cpk.read.CpkReadService
//import net.corda.db.persistence.testkit.components.VirtualNodeService
//import net.corda.testing.sandboxes.SandboxSetup
//import net.corda.testing.sandboxes.fetchService
//import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
//import net.corda.virtualnode.read.VirtualNodeInfoReadService
//import org.junit.jupiter.api.BeforeAll
//import org.junit.jupiter.api.TestInstance
//import org.junit.jupiter.api.extension.ExtendWith
//import org.junit.jupiter.api.extension.RegisterExtension
//import org.junit.jupiter.api.io.TempDir
//import org.osgi.framework.BundleContext
//import org.osgi.test.common.annotation.InjectBundleContext
//import org.osgi.test.common.annotation.InjectService
//import org.osgi.test.junit5.context.BundleContextExtension
//import org.osgi.test.junit5.service.ServiceExtension
//import java.nio.file.Path
//
//@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//class VerificationSandboxStressTests {
//
//    private companion object {
//        private const val TIMEOUT_MILLIS = 10000L
//    }
//
//    @RegisterExtension
//    private val lifecycle = EachTestLifecycle()
//
//    private lateinit var virtualNode: VirtualNodeService
//    private lateinit var cpiInfoReadService: CpiInfoReadService
//    private lateinit var cpkReadService: CpkReadService
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
//        lifecycle.accept(sandboxSetup) { setup ->
//            virtualNode = setup.fetchService(TIMEOUT_MILLIS)
//        }
//    }
//}