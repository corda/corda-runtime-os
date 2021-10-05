package net.corda.sandbox.internal.hooks.service

import net.corda.sandbox.internal.SandboxServiceInternal
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.createMockBundleContext
import net.corda.sandbox.internal.hooks.HookTestUtils.Companion.createMockServiceReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceEvent
import org.osgi.framework.hooks.service.ListenerHook

class IsolatingEventListenerHookTests {
    private val bundleOne = mock<Bundle>()
    private val bundleOneContext = createMockBundleContext(bundleOne)
    private val bundleTwo = mock<Bundle>()
    private val bundleTwoServiceReference = createMockServiceReference(bundleTwo)
    private val bundleTwoServiceEvent = ServiceEvent(0, bundleTwoServiceReference)
    private val listeners = mutableMapOf<BundleContext, MutableCollection<ListenerHook.ListenerInfo>>(bundleOneContext to mutableListOf())

    @BeforeEach
    fun setUp() {
        // We reset the list of listeners to be filtered by the hook.
        listeners.clear()
        listeners[bundleOneContext] = mutableListOf()
    }

    @Test
    fun `event is received if a bundle has visibility`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(true)
            whenever(isStarted).thenReturn(true)
        }

        val isolatingEventListenerHook = IsolatingEventListenerHook(sandboxService)
        isolatingEventListenerHook.event(bundleTwoServiceEvent, listeners)
        assertEquals(1, listeners.size)
    }

    @Test
    fun `event is not received if a bundle does not have visibility`() {
        val sandboxService = mock<SandboxServiceInternal>().apply {
            whenever(hasVisibility(bundleOne, bundleTwo)).thenReturn(false)
            whenever(isStarted).thenReturn(true)
        }

        val isolatingEventListenerHook = IsolatingEventListenerHook(sandboxService)
        isolatingEventListenerHook.event(bundleTwoServiceEvent, listeners)
        assertEquals(0, listeners.size)
    }
}