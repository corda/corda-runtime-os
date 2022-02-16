package net.corda.sandboxtests

import java.nio.file.Path
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.EachTestLifecycle
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of service events across sandbox groups. */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class SandboxServiceEventIsolationTest {
    companion object {
        @RegisterExtension
        private val lifecycle = EachTestLifecycle()

        @InjectService(timeout = 1000)
        lateinit var sandboxSetup: SandboxSetup

        lateinit var sandboxFactory: SandboxFactory

        @Suppress("unused")
        @JvmStatic
        @BeforeAll
        fun setup(@InjectBundleContext bundleContext: BundleContext, @TempDir testDirectory: Path) {
            sandboxSetup.configure(bundleContext, testDirectory)
            lifecycle.accept(sandboxSetup) { setup ->
                sandboxFactory = setup.fetchService(timeout = 1000)
            }
        }
    }

    @Test
    fun `sandbox receives service events from its own sandbox group`() {
        val thisGroup = sandboxFactory.group1

        // This flow returns all service events visible to this bundle.
        val serviceEvents = runFlow<List<ServiceEvent>>(thisGroup, SERVICE_EVENTS_FLOW)

        assertTrue(serviceEvents.any { event -> sandboxGroupContainsBundle(thisGroup, event.serviceReference.bundle) })
    }

    @Test
    fun `sandbox does not receive service events from other sandbox groups`() {
        val thisGroup = sandboxFactory.group1
        val otherGroup = sandboxFactory.group2

        // This flow returns all service events visible to this bundle.
        val serviceEvents = runFlow<List<ServiceEvent>>(thisGroup, SERVICE_EVENTS_FLOW)

        assertTrue(serviceEvents.none { event -> sandboxGroupContainsBundle(otherGroup, event.serviceReference.bundle) })
    }
}
