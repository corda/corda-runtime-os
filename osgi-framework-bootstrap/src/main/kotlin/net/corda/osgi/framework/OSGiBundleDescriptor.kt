package net.corda.osgi.framework

import org.osgi.framework.Bundle
import java.util.concurrent.CountDownLatch

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

)
