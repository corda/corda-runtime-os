package net.corda.osgi.framework

import org.osgi.framework.Bundle
import java.util.concurrent.CountDownLatch

/**
 * Description of bundles handled by [OSGiFrameworkWrap].
 * The class describes the [bundle] and its property needed for a synchronization of
 * bundle state.
 *
 * The [active] [CountDownLatch] is decremented once: the first time the bundle results
 * activated. OSGi framework can notify bundle states more than once.
 */
internal data class OSGiBundleDescriptor(

    /**
     * Installed bundle.
     */
    val bundle: Bundle,

    /**
     * Used to wait the bundles is active.
     *
     * @see [OSGiFrameworkWrap.activate]
     * @see [OSGiFrameworkWrap.start]
     */
    val active: CountDownLatch = CountDownLatch(1),

    )
