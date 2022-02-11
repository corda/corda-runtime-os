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
import org.osgi.framework.BundleEvent
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of bundle events across sandbox groups. */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
class SandboxBundleEventIsolationTest {
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
    fun `sandbox receives bundle events from its own sandbox group`() {
        val thisGroup = sandboxFactory.group1

        // This flow returns all bundle events visible to this bundle.
        val bundleEvents = runFlow<List<BundleEvent>>(thisGroup, BUNDLE_EVENTS_FLOW)

        assertTrue(bundleEvents.any { event -> sandboxGroupContainsBundle(thisGroup, event.bundle) })
        assertTrue(bundleEvents.any { event -> sandboxGroupContainsBundle(thisGroup, event.origin) })
    }

    @Test
    fun `sandbox does not receive bundle events from other sandbox groups`() {
        val thisGroup = sandboxFactory.group1
        val otherGroup = sandboxFactory.group2

        // This flow returns all bundle events visible to this bundle.
        val bundleEvents = runFlow<List<BundleEvent>>(thisGroup, BUNDLE_EVENTS_FLOW)

        assertTrue(bundleEvents.none { event -> sandboxGroupContainsBundle(otherGroup, event.bundle) })
        assertTrue(bundleEvents.none { event -> sandboxGroupContainsBundle(otherGroup, event.origin) })
    }
}
