package net.corda.osgi.framework

import net.corda.osgi.api.Lifecycle
import org.osgi.framework.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/**
 * Description of bundles handled by [OSGiFrameworkWrap].
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

    /**
     * Used to distinguish the application bundles.
     * Set to the class implementing [Lifecycle] after [Lifecycle.startup] is called.
     *
     * @see [OSGiFrameworkWrap.startApplications]
     * @see [OSGiFrameworkWrap.stop]
     */
    val lifecycleAtomic: AtomicReference<Lifecycle?> = AtomicReference()
)
