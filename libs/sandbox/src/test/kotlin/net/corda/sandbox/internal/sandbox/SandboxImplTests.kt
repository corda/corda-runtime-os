package net.corda.sandbox.internal.sandbox

import net.corda.sandbox.internal.utilities.BundleUtils
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import java.util.UUID.randomUUID

class SandboxImplTests {
    private val publicBundle = mock(Bundle::class.java)
    private val privateBundle = mock(Bundle::class.java)
    private val nonSandboxBundle = mock(Bundle::class.java)

    private val publicBundleClass = Int::class.java
    private val privateBundleClass = String::class.java
    private val nonSandboxClass = Boolean::class.java

    private val mockBundleUtils = mock(BundleUtils::class.java).apply {
        // `Int` is assigned to the public bundle, `String` is assigned to the private bundle, and `Boolean` is
        // assigned to the excluded bundle.
        whenever(getBundle(publicBundleClass)).thenReturn(publicBundle)
        whenever(getBundle(privateBundleClass)).thenReturn(privateBundle)
        whenever(getBundle(nonSandboxClass)).thenReturn(nonSandboxBundle)
    }

    @Test
    fun `correctly indicates whether bundles are in the sandbox`() {
        val sandbox = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        assertTrue(sandbox.containsBundle(publicBundle))
        assertTrue(sandbox.containsBundle(privateBundle))
        assertFalse(sandbox.containsBundle(nonSandboxBundle))
    }

    @Test
    fun `correctly indicates whether classes are in the sandbox`() {
        val sandbox = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        assertTrue(sandbox.containsClass(publicBundleClass))
        assertTrue(sandbox.containsClass(privateBundleClass))
        assertFalse(sandbox.containsClass(nonSandboxClass))
    }

    @Test
    fun `sandbox has visibility of itself`() {
        val sandbox = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        assertTrue(sandbox.hasVisibility(sandbox))
    }

    @Test
    fun `sandbox does not have visibility of other sandboxes by default`() {
        val sandboxOne = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        val sandboxTwo = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        assertFalse(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `sandbox has visibility of other sandboxes to which it is granted visibility`() {
        val sandboxOne = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        val sandboxTwo = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        sandboxOne.grantVisibility(sandboxTwo)
        assertTrue(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `visibility between sandboxes is one-way`() {
        val sandboxOne = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        val sandboxTwo = SandboxImpl(mockBundleUtils, randomUUID(), setOf(publicBundle), setOf(privateBundle))
        sandboxOne.grantVisibility(sandboxTwo)
        assertFalse(sandboxTwo.hasVisibility(sandboxOne))
    }
}