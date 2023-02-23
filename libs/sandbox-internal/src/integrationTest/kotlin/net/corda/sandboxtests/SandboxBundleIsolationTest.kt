package net.corda.sandboxtests

import java.nio.file.Path
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.testing.sandboxes.fetchService
import net.corda.testing.sandboxes.lifecycle.AllTestsLifecycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.test.common.annotation.InjectBundleContext
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.context.BundleContextExtension
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the isolation of bundles across sandbox groups. */
@ExtendWith(ServiceExtension::class, BundleContextExtension::class)
@TestInstance(PER_CLASS)
@Suppress("FunctionName")
class SandboxBundleIsolationTest {

    companion object {
        @JvmStatic
        @RegisterExtension
        private val lifecycle = AllTestsLifecycle()
    }

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
    fun `sandbox can see bundles in its own sandbox group`() {
        val thisGroup = sandboxFactory.group1
        // This flow returns all bundles visible to this bundle.
        val bundles = runFlow<List<Bundle>>(thisGroup, BUNDLES_FLOW)

        val expectedBundleNames = setOf(
            FRAMEWORK_BUNDLE_NAME, SCR_BUNDLE_NAME, CPK_ONE_BUNDLE_NAME, CPK_TWO_BUNDLE_NAME, CPK_LIBRARY_BUNDLE_NAME
        )

        assertTrue(bundles.any { bundle -> sandboxGroupContainsBundle(thisGroup, bundle) })
        assertTrue(bundles.map(Bundle::getSymbolicName).containsAll(expectedBundleNames))
    }

    @Test
    fun `sandbox cannot see bundles in other sandbox groups`() {
        val thisGroup = sandboxFactory.group1
        val otherGroup = sandboxFactory.group2

        // This flow returns all bundles visible to this bundle.
        val bundles = runFlow<List<Bundle>>(thisGroup, BUNDLES_FLOW)

        assertTrue(bundles.none { bundle -> sandboxGroupContainsBundle(otherGroup, bundle) })
    }

    @Test
    fun `we can load two copies of the same sandbox`() {
        val copyOne = assertDoesNotThrow {
            sandboxFactory.createSandboxGroupFor(CPI_ONE)
        }
        try {
            assertAll(
                { assertTrue(copyOne.metadata.keys.stream().allMatch { bundle -> bundle.state == Bundle.ACTIVE }) },
                { assertEquals(sandboxFactory.group1.metadata.keys.names, copyOne.metadata.keys.names) },
                { assertNotEquals(sandboxFactory.group1.metadata.keys.ids, copyOne.metadata.keys.ids) }
            )
        } finally {
            sandboxFactory.destroySandboxGroup(copyOne)
        }
    }

    private val Iterable<Bundle>.names: Set<String> get() {
        return mapTo(LinkedHashSet()) { "${it.symbolicName}:${it.version}" }
    }

    private val Iterable<Bundle>.ids: Set<Long> get() {
        return mapTo(LinkedHashSet(), Bundle::getBundleId)
    }
}
