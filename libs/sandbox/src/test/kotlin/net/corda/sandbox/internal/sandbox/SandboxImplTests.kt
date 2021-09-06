package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.utilities.BundleUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import java.util.UUID

class SandboxImplTests {
    private fun generateSandboxId() = UUID.randomUUID()
    private val mockBundleUtils = BundleUtils(mock(BundleContext::class.java))

    @Test
    fun `correctly indicates whether bundles are in the sandbox`() {
        val containedBundle = mock(Bundle::class.java)
        val excludedBundle = mock(Bundle::class.java)

        val sandbox = SandboxImpl(mockBundleUtils, generateSandboxId(), setOf(containedBundle))
        assertTrue(sandbox.containsBundle(containedBundle))
        assertFalse(sandbox.containsBundle(excludedBundle))
    }

    @Test
    fun `correctly indicates whether classes are in the sandbox`() {
        val containedBundle = mock(Bundle::class.java)
        val excludedBundle = mock(Bundle::class.java)

        val mockBundleUtils = mock(BundleUtils::class.java).apply {
            // We assign `Int` to the contained bundle.
            whenever(getBundle(Int::class.java)).thenReturn(containedBundle)
            whenever(getBundle(Boolean::class.java)).thenReturn(excludedBundle)
        }

        val sandbox = SandboxImpl(mockBundleUtils, generateSandboxId(), setOf(containedBundle))
        assertTrue(sandbox.containsClass(Int::class.java))
        assertFalse(sandbox.containsClass(Boolean::class.java))
    }

    @Test
    fun `can retrieve the bundle for a class in the sandbox`() {
        val containedBundle = mock(Bundle::class.java)

        val mockBundleUtils = mock(BundleUtils::class.java).apply {
            // We assign `String` to the contained bundle.
            whenever(getBundle(Int::class.java)).thenReturn(containedBundle)
        }

        val sandbox = SandboxImpl(mockBundleUtils, generateSandboxId(), setOf(containedBundle))

        assertEquals(containedBundle, sandbox.getBundle(Int::class.java))
    }

    @Test
    fun `throws if asked to retrieve the bundle of a class not in the sandbox`() {
        val sandbox = SandboxImpl(mockBundleUtils, generateSandboxId(), emptySet())

        assertThrows<SandboxException> {
            sandbox.getBundle(String::class.java)
        }
    }

    @Test
    fun `sandbox has visibility of itself`() {
        val sandbox = SandboxImpl(mockBundleUtils, generateSandboxId(), emptySet())
        assertTrue(sandbox.hasVisibility(sandbox))
    }

    @Test
    fun `sandbox does not have visibility of other sandboxes by default`() {
        val sandboxOne = SandboxImpl(mockBundleUtils, generateSandboxId(), emptySet())
        val sandboxTwo = SandboxImpl(mockBundleUtils, generateSandboxId(), emptySet())
        assertFalse(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `sandbox has visibility of other sandboxes to which it is granted visibility`() {
        val sandboxOne = SandboxImpl(mockBundleUtils, generateSandboxId(), emptySet())
        val sandboxTwo = SandboxImpl(mockBundleUtils, generateSandboxId(), emptySet())
        sandboxOne.grantVisibility(sandboxTwo)
        assertTrue(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `visibility between sandboxes is one-way`() {
        val sandboxOne = SandboxImpl(mockBundleUtils, generateSandboxId(), emptySet())
        val sandboxTwo = SandboxImpl(mockBundleUtils, generateSandboxId(), emptySet())
        sandboxOne.grantVisibility(sandboxTwo)
        assertFalse(sandboxTwo.hasVisibility(sandboxOne))
    }
}