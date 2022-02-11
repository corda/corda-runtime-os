package net.corda.sandboxtests

import java.nio.file.Path
import net.corda.sandbox.SandboxGroup
import net.corda.testing.sandboxes.SandboxSetup
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the ability to retrieve the calling sandbox group. */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class SandboxGetCallingGroupTest {
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
    fun `can retrieve the calling sandbox group from within a sandbox`() {
        val callingSandboxGroup = applyFunction<Any, SandboxGroup>(
            sandboxFactory.group1, GET_CALLING_SANDBOX_GROUP_FUNCTION, sandboxFactory.sandboxContextService
        )
        assertEquals(sandboxFactory.group1, callingSandboxGroup)
    }

    @Test
    fun `retrieving the calling sandbox group from outside a sandbox returns null`() {
        assertNull(sandboxFactory.sandboxContextService.getCallingSandboxGroup())
    }
}
