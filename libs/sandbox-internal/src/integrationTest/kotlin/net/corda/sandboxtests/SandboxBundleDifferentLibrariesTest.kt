package net.corda.sandboxtests

import java.nio.file.Path
import net.corda.testing.sandboxes.SandboxSetup
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the ability to have different copies of the same library in different sandboxes. */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class SandboxBundleDifferentLibrariesTest {
    @Suppress("unused")
    companion object {
        @InjectService(timeout = 1000)
        lateinit var sandboxSetup: SandboxSetup

        @JvmStatic
        @BeforeAll
        fun setup(@InjectBundleContext bundleContext: BundleContext, @TempDir testDirectory: Path) {
            sandboxSetup.configure(bundleContext, testDirectory)
        }

        @JvmStatic
        @AfterAll
        fun done() {
            sandboxSetup.shutdown()
        }
    }

    @InjectService(timeout = 1500)
    lateinit var sandboxFactory: SandboxFactory

    @Test
    fun `different CPKs in the same sandbox group can use different versions of the same library`() {
        // This flow returns a string containing the version of the Joda-Time library used.
        val cpkOneReturnString = runFlow<String>(sandboxFactory.group1, LIBRARY_VERSION_FLOW_CPK_1)
        val cpkTwoReturnString = runFlow<String>(sandboxFactory.group1, LIBRARY_VERSION_FLOW_CPK_2)

        assertTrue(cpkOneReturnString.contains(CPK_1_JODATIME_VERSION))
        assertTrue(cpkTwoReturnString.contains(CPK_2_JODATIME_VERSION))
    }
}
