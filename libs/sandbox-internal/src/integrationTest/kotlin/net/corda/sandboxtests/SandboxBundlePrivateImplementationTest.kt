package net.corda.sandboxtests

import java.nio.file.Path
import net.corda.testing.sandboxes.SandboxSetup
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the use of non-exported implementation classes from one bundle in another bundle. */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class SandboxBundlePrivateImplementationTest {
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
    fun `sandbox can invoke a private implementation in a non-exported package of another bundle`() {
        val returnString = runFlow<String>(sandboxFactory.group1, INVOKE_PRIVATE_IMPL_FLOW)
        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
    }

    @Test
    fun `sandbox can use a private implementation in a non-exported package of another bundle as a generic`() {
        val returnString = runFlow<String>(sandboxFactory.group1, PRIVATE_IMPL_AS_GENERIC_FLOW)
        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
    }
}
