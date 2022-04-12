package net.corda.osgi.framework

import org.osgi.framework.Bundle
import java.util.concurrent.CountDownLatch

/**
 * Description of bundles handled by [OSGiFrameworkWrap].
 *
 * The class describes the [bundle] and its property needed for a synchronization of
 * bundle state.
 *
 * The [active] latch is decremented once: the first time the bundle results
 * activated. OSGi framework can notify bundle states more than once.
 */
internal data class OSGiBundleDescriptor(
    val bundle: Bundle,
    val active: CountDownLatch = CountDownLatch(1)
)
