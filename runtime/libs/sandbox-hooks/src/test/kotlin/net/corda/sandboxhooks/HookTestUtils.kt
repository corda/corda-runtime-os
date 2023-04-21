package net.corda.sandboxhooks

import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.framework.wiring.BundleCapability
import org.osgi.framework.wiring.BundleRequirement
import org.osgi.framework.wiring.BundleRevision

/** Creates a mock [BundleContext] for the bundle [bundleForContext]. */
internal fun mockBundleContext(bundleForContext: Bundle) = mock<BundleContext>().apply {
    whenever(bundle).thenReturn(bundleForContext)
}

/** Creates a mock [ServiceReference] for the bundle [bundleForServiceReference]. */
internal fun mockServiceReference(bundleForServiceReference: Bundle) = mock<ServiceReference<*>>().apply {
    whenever(bundle).thenReturn(bundleForServiceReference)
}

/** Creates a mock [BundleRevision] for the bundle [bundleForRevision]. */
internal fun mockBundleRevision(bundleForRevision: Bundle) = mock<BundleRevision>().apply {
    whenever(bundle).thenReturn(bundleForRevision)
}

/** Creates a mock [BundleCapability] for the bundle revision [revisionForBundleCapability]. */
internal fun mockBundleCapability(revisionForBundleCapability: BundleRevision) = mock<BundleCapability>().apply {
    whenever(revision).thenReturn(revisionForBundleCapability)
}

/** Creates a mock [BundleRequirement] for the bundle revision [revisionForBundleCapability]. */
internal fun mockBundleRequirement(revisionForBundleCapability: BundleRevision) = mock<BundleRequirement>().apply {
    whenever(revision).thenReturn(revisionForBundleCapability)
}