package net.corda.sandbox.internal.hooks

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.framework.wiring.BundleCapability
import org.osgi.framework.wiring.BundleRequirement
import org.osgi.framework.wiring.BundleRevision

/** Helpers shared across the hook tests. */
internal class HookTestUtils {
    companion object {
        /** Creates a mock [BundleContext] for the bundle [bundleForContext]. */
        internal fun createMockBundleContext(bundleForContext: Bundle) = mock<BundleContext>().apply {
            whenever(bundle).thenReturn(bundleForContext)
        }

        /** Creates a mock [ServiceReference] for the bundle [bundleForServiceReference]. */
        internal fun createMockServiceReference(bundleForServiceReference: Bundle) = mock<ServiceReference<*>>().apply {
            whenever(bundle).thenReturn(bundleForServiceReference)
        }

        /** Creates a mock [BundleRevision] for the bundle [bundleForRevision]. */
        internal fun createMockBundleRevision(bundleForRevision: Bundle) = mock<BundleRevision>().apply {
            whenever(bundle).thenReturn(bundleForRevision)
        }

        /** Creates a mock [BundleCapability] for the bundle revision [revisionForBundleCapability]. */
        internal fun createMockBundleCapability(revisionForBundleCapability: BundleRevision) = mock<BundleCapability>().apply {
            whenever(revision).thenReturn(revisionForBundleCapability)
        }

        /** Creates a mock [BundleRequirement] for the bundle revision [revisionForBundleCapability]. */
        internal fun createMockBundleRequirement(revisionForBundleCapability: BundleRevision) = mock<BundleRequirement>().apply {
            whenever(revision).thenReturn(revisionForBundleCapability)
        }
    }
}