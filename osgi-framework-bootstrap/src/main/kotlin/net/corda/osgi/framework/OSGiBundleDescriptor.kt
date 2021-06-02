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
 *
 * The [shutdown] latch is decremented once if the bundle implements [net.corda.osgi.api.Application]
 * before to call [net.corda.osgi.api.Application.shutdown] to assure it is called once.
 */
internal data class OSGiBundleDescriptor(
    val bundle: Bundle,
    val active: CountDownLatch = CountDownLatch(1),
    val shutdown: CountDownLatch = CountDownLatch(1),
)
