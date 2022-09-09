package net.corda.sandboxtests

import java.nio.file.Path
import net.corda.sandbox.SandboxException
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

@Suppress("FunctionName")
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
class SandboxShadowPlatformBundleTest {
    @RegisterExtension
    private val lifecycle = AllTestsLifecycle()

    private lateinit var sandboxFactory: SandboxFactory

    @BeforeAll
    fun setup(
        @InjectService(timeout = 1000)
        sandboxSetup: SandboxSetup,
        @InjectBundleContext
        bundleContext: BundleContext,
        @TempDir
        testDirectory: Path
    ) {
        sandboxSetup.configure(bundleContext, testDirectory)
        lifecycle.accept(sandboxSetup) { setup ->
            sandboxFactory = setup.fetchService(timeout = 1000)
        }
    }

    @Test
    fun `sandboxes cannot contain their own version of platform bundles`() {
        val ex = assertThrows<SandboxException> {
            sandboxFactory.createSandboxGroupFor("META-INF/sandbox-shadow-cpk.cpb")
        }
        assertThat(ex)
            .hasMessage("Bundle com.esotericsoftware.reflectasm shadows a Corda platform bundle.")
            .hasNoCause()
    }
}
