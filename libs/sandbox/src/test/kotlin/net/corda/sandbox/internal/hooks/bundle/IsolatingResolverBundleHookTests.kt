package net.corda.sandbox.internal.hooks.bundle

import net.corda.sandbox.internal.sandbox.SandboxInternal
import net.corda.sandbox.internal.SandboxServiceInternal
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.mockBundleCapability
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.mockBundleRequirement
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.mockBundleRevision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle

class IsolatingResolverBundleHookTests {
    private val bundleOne = mock<Bundle>()
    private val bundleTwo = mock<Bundle>()
    private val bundleThree = mock<Bundle>()
    private val bundleOneRevision = mockBundleRevision(bundleOne)
    private val bundleOneCapability = mockBundleCapability(bundleOneRevision)
    private val bundleTwoRevision = mockBundleRevision(bundleTwo)
    private val bundleTwoCapability = mockBundleCapability(bundleTwoRevision)
    private val bundleThreeRevision = mockBundleRevision(bundleThree)
    private val bundleThreeCapability = mockBundleCapability(bundleThreeRevision)
    private val bundleRequirement = mockBundleRequirement(bundleOneRevision)
    private val candidates = mutableListOf(bundleTwoCapability)
    private val revisions = mutableListOf(bundleOneRevision)

    @BeforeEach
    fun setup() {
        candidates.clear()
        candidates.add(bundleTwoCapability)
        revisions.clear()
        revisions.add(bundleTwoRevision)
    }

    @Test
    fun `a bundle can resolve against another bundle it has visibility of`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
        }

        val isolatingResolverBundleHook = IsolatingResolverBundleHook(sandboxService)
        isolatingResolverBundleHook.filterMatches(bundleRequirement, candidates)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `a bundle cannot resolve against a bundle it doesn't have visibility of`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
        }

        val isolatingResolverBundleHook = IsolatingResolverBundleHook(sandboxService)
        isolatingResolverBundleHook.filterMatches(bundleRequirement, candidates)
        assertEquals(0, candidates.size)
    }

    @Test
    fun `a bundle will prioritise matches in its own sandbox`() {
        val candidates = mutableListOf(bundleTwoCapability, bundleThreeCapability)
        
        val sandboxOne = mock<SandboxInternal>()
        val sandboxTwo = mock<SandboxInternal>()

        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
            whenever(getSandbox(bundleOne)).thenReturn(sandboxOne)
            whenever(getSandbox(bundleTwo)).thenReturn(sandboxTwo)
            whenever(getSandbox(bundleThree)).thenReturn(sandboxOne)
        }

        val isolatingResolverBundleHook = IsolatingResolverBundleHook(sandboxService)
        isolatingResolverBundleHook.filterMatches(bundleRequirement, candidates)
        assertEquals(1, candidates.size)
        assertEquals(bundleThree, candidates.single().revision.bundle)
    }

    @Test
    fun `singletons can co-exist is they are not visible to one another`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
            whenever(hasVisibility(bundleTwo, bundleOne)).thenReturn(false)
        }

        val isolatingResolverBundleHook = IsolatingResolverBundleHook(sandboxService)
        isolatingResolverBundleHook.filterSingletonCollisions(bundleOneCapability, candidates)
        assertEquals(0, candidates.size)
    }

    @Test
    fun `singletons cannot co-exist is the candidate is visible to the singleton`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
            whenever(hasVisibility(bundleTwo, bundleOne)).thenReturn(false)
        }

        val isolatingResolverBundleHook = IsolatingResolverBundleHook(sandboxService)
        isolatingResolverBundleHook.filterSingletonCollisions(bundleOneCapability, candidates)
        assertEquals(1, candidates.size)
    }

    @Test
    fun `singletons cannot co-exist is the singleton is visible to the candidate`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
            whenever(hasVisibility(bundleTwo, bundleOne)).thenReturn(false)
        }

        val isolatingResolverBundleHook = IsolatingResolverBundleHook(sandboxService)
        isolatingResolverBundleHook.filterSingletonCollisions(bundleOneCapability, candidates)
        assertEquals(1, candidates.size)
    }
}