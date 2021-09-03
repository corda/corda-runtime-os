package net.corda.sandbox.internal

import net.corda.packaging.Cpk
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.utilities.BundleUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import java.util.UUID

class SandboxImplTests {
    private fun generateSandboxId() = UUID.randomUUID()
    private val mockBundleUtils = BundleUtils(mock(BundleContext::class.java))

    @Test
    fun `can load class from CorDapp bundles in sandbox`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(String::class.java.name)).thenReturn(String::class.java)
        }
        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, emptySet())

        assertEquals(String::class.java, sandbox.loadClass(String::class.java.name))
    }

    @Test
    fun `cannot load class from other bundles in sandbox`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(any())).thenThrow(ClassNotFoundException::class.java)
        }
        val otherBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(Int::class.java.name)).thenReturn(Int::class.java)
        }
        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, setOf(otherBundle))

        assertThrows<SandboxException> {
            sandbox.loadClass(Int::class.java.name)
        }
    }

    @Test
    fun `throws if loading class from sandbox with an uninstalled bundle`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(any())).thenThrow(IllegalStateException::class.java)
        }
        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, emptySet())

        assertThrows<SandboxException> {
            sandbox.loadClass(Int::class.java.name)
        }
    }

    @Test
    fun `correctly indicates whether bundles are in the sandbox`() {
        val cordappBundle = mock(Bundle::class.java)
        val otherBundle = mock(Bundle::class.java)
        val excludedBundle = mock(Bundle::class.java)

        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, setOf(otherBundle))
        assertTrue(sandbox.containsBundle(cordappBundle))
        assertTrue(sandbox.containsBundle(otherBundle))
        assertFalse(sandbox.containsBundle(excludedBundle))
    }

    @Test
    fun `correctly indicates whether classes are in the sandbox`() {
        val cordappBundle = mock(Bundle::class.java)
        val otherBundle = mock(Bundle::class.java)
        val excludedBundle = mock(Bundle::class.java)

        val mockBundleUtils = mock(BundleUtils::class.java).apply {
            // We assign `String` to the CorDapp bundle, and `Int` to the other bundle.
            whenever(getBundle(String::class.java)).thenReturn(cordappBundle)
            whenever(getBundle(Int::class.java)).thenReturn(otherBundle)
            whenever(getBundle(Boolean::class.java)).thenReturn(excludedBundle)
        }

        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, setOf(otherBundle))
        assertTrue(sandbox.containsClass(String::class.java))
        assertTrue(sandbox.containsClass(Int::class.java))
        assertFalse(sandbox.containsClass(Boolean::class.java))
    }

    @Test
    fun `can retrieve the bundle for a class in the sandbox`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(symbolicName).thenReturn("mockBundleName")
        }
        val sourceCpk = mock(Cpk.Expanded::class.java)
        val otherBundle = mock(Bundle::class.java)

        val mockBundleUtils = mock(BundleUtils::class.java).apply {
            // We assign `String` to the CorDapp bundle, and `Int` to the other bundle.
            whenever(getBundle(String::class.java)).thenReturn(cordappBundle)
            whenever(getBundle(Int::class.java)).thenReturn(otherBundle)
        }

        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), sourceCpk, cordappBundle, setOf(otherBundle))

        assertEquals(cordappBundle, sandbox.getBundle(String::class.java))
        assertEquals(otherBundle, sandbox.getBundle(Int::class.java))
    }

    @Test
    fun `throws if asked to retrieve the bundle of a class not in the sandbox`() {
        val cordappBundle = mock(Bundle::class.java).apply {
            whenever(loadClass(any())).thenThrow(IllegalStateException::class.java)
        }

        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, emptySet())

        assertThrows<SandboxException> {
            sandbox.getBundle(String::class.java)
        }
    }

    @Test
    fun `correctly identifies whether a bundle is the CorDapp bundle`() {
        val cordappBundle = mock<Bundle>()
        val otherBundle = mock<Bundle>()

        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), cordappBundle, setOf(otherBundle))

        assertTrue(sandbox.isCordappBundle(cordappBundle))
        assertFalse(sandbox.isCordappBundle(otherBundle))
    }

    @Test
    fun `sandbox has visibility of itself`() {
        val sandbox = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), mock(), emptySet())
        assertTrue(sandbox.hasVisibility(sandbox))
    }

    @Test
    fun `sandbox does not have visibility of other sandboxes by default`() {
        val sandboxOne = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), mock(), emptySet())
        val sandboxTwo = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), mock(), emptySet())
        assertFalse(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `sandbox has visibility of other sandboxes to which it is granted visibility`() {
        val sandboxOne = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), mock(), emptySet())
        val sandboxTwo = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), mock(), emptySet())
        sandboxOne.grantVisibility(sandboxTwo)
        assertTrue(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `visibility between sandboxes is one-way`() {
        val sandboxOne = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), mock(), emptySet())
        val sandboxTwo = CpkSandboxImpl(mockBundleUtils, generateSandboxId(), mock(), mock(), emptySet())
        sandboxOne.grantVisibility(sandboxTwo)
        assertFalse(sandboxTwo.hasVisibility(sandboxOne))
    }
}