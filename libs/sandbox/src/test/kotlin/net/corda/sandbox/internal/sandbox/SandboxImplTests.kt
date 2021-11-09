package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.mockBundle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import java.util.UUID.randomUUID

class SandboxImplTests {
    companion object {
        private const val PUBLIC_BUNDLE_NAME = "public_bundle_name"
        private const val PRIVATE_BUNDLE_NAME = "private_bundle_name"
    }

    private val publicBundleClass = Int::class.java
    private val privateBundleClass = String::class.java
    private val nonSandboxClass = Boolean::class.java

    private val uninstalledBundles = mutableSetOf<Bundle>()

    private val publicBundle = mockUninstallableBundle(PUBLIC_BUNDLE_NAME, publicBundleClass)
    private val privateBundle = mockUninstallableBundle(PRIVATE_BUNDLE_NAME, privateBundleClass)
    private val nonSandboxBundle = mockUninstallableBundle("", nonSandboxClass)

    /** Creates a mock [Bundle] that tracks its own uninstallation. */
    private fun mockUninstallableBundle(bundleSymbolicName: String, klass: Class<*>) = mockBundle(
        bundleSymbolicName = bundleSymbolicName,
        klass = klass
    ).apply {
        whenever(uninstall()).then { uninstalledBundles.add(this) }
    }

    /** Creates a [SandboxImpl] for testing. */
    private fun createSandboxImpl() =
        SandboxImpl(randomUUID(), setOf(publicBundle), setOf(privateBundle))

    @AfterEach
    fun resetUninstalledBundles() = uninstalledBundles.clear()

    @Test
    fun `correctly indicates whether bundles are in the sandbox`() {
        val sandbox = createSandboxImpl()
        assertTrue(sandbox.containsBundle(publicBundle))
        assertTrue(sandbox.containsBundle(privateBundle))
        assertFalse(sandbox.containsBundle(nonSandboxBundle))
    }

    @Test
    fun `sandbox has visibility of itself`() {
        val sandbox = createSandboxImpl()
        assertTrue(sandbox.hasVisibility(sandbox))
    }

    @Test
    fun `sandbox does not have visibility of other sandboxes by default`() {
        val sandboxOne = createSandboxImpl()
        val sandboxTwo = createSandboxImpl()
        assertFalse(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `sandbox has visibility of other sandboxes to which it is granted visibility`() {
        val sandboxOne = createSandboxImpl()
        val sandboxTwo = createSandboxImpl()
        sandboxOne.grantVisibility(setOf(sandboxTwo))
        assertTrue(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `visibility between sandboxes is one-way`() {
        val sandboxOne = createSandboxImpl()
        val sandboxTwo = createSandboxImpl()
        sandboxOne.grantVisibility(setOf(sandboxTwo))
        assertFalse(sandboxTwo.hasVisibility(sandboxOne))
    }

    @Test
    fun `can load classes from public and private sandbox bundles`() {
        val sandbox = createSandboxImpl()
        assertEquals(publicBundleClass, sandbox.loadClass(publicBundleClass.name, PUBLIC_BUNDLE_NAME))
        assertEquals(privateBundleClass, sandbox.loadClass(privateBundleClass.name, PRIVATE_BUNDLE_NAME))
    }

    @Test
    fun `returns null when attempting to load a class from a bundle that doesn't exist`() {
        val sandbox = createSandboxImpl()
        assertNull(sandbox.loadClass(publicBundleClass.name, "bad_name"))
    }

    @Test
    fun `returns null when attempting to load a class from a bundle that doesn't contain the class`() {
        val sandbox = createSandboxImpl()
        assertNull(sandbox.loadClass(privateBundleClass.name, PUBLIC_BUNDLE_NAME))
    }

    @Test
    fun `throws when attempting to load a class from an uninstalled bundle`() {
        val publicBundle = mock<Bundle>().apply {
            whenever(symbolicName).thenReturn(PUBLIC_BUNDLE_NAME)
            whenever(loadClass(any())).thenThrow(IllegalStateException::class.java)
        }

        val sandbox = SandboxImpl(randomUUID(), setOf(publicBundle), setOf(privateBundle))
        assertThrows<SandboxException> {
            sandbox.loadClass(privateBundleClass.name, PUBLIC_BUNDLE_NAME)
        }
    }

    @Test
    fun `sandbox can be unloaded`() {
        val sandbox = createSandboxImpl()
        sandbox.unload()

        assertEquals(setOf(publicBundle, privateBundle), uninstalledBundles)
    }

    @Test
    fun `unloading a sandbox attempts to uninstall all bundles`() {
        val cantBeUninstalledMainBundle = mockBundle().apply {
            whenever(uninstall()).then { throw IllegalStateException() }
        }
        val libraryBundle = mockBundle().apply {
            whenever(uninstall()).then { uninstalledBundles.add(this) }
        }

        SandboxImpl(
            randomUUID(),
            setOf(cantBeUninstalledMainBundle, libraryBundle),
            emptySet()
        ).unload()

        assertEquals(libraryBundle, uninstalledBundles.single())
    }
}