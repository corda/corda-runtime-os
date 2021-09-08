package net.corda.osgi.framework

import org.apache.sling.testing.mock.osgi.MockOsgi
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleException
import org.osgi.framework.BundleListener
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class OSGiBundleContextMock(
    private val framework: OSGiFrameworkMock,
    private val bundle: Bundle
) : BundleContext by MockOsgi.newBundleContext() {

    /**
     * Set of [BundleListener] to notify when bundle
     */
    private val bundleListenerSet = ConcurrentHashMap.newKeySet<BundleListener>()

    /**
     * Notify to [bundleListenerSet] the [bundleEvent].
     * OSGi Core r7 4.7.3 "Synchronization Pitfalls" requires call-backs don't run in synchronized sections.
     *
     * Thread safe.
     *
     * @param bundleEvent to notify.
     * @return this.
     */
    internal fun notifyToListeners(bundleEvent: BundleEvent) {
        // Get a snapshot of listeners.
        val bundleListenerSet: Set<BundleListener>
        bundleListenerSet = this.bundleListenerSet.toSet()
        bundleListenerSet.forEach { bundleListener ->
            bundleListener.bundleChanged(bundleEvent)
        }
    }

    // : BundleContext

    /**
     * See [BundleContext.getBundle].
     *
     * @return The [Bundle] object associated with this [BundleContext].
     * @throws IllegalStateException If this BundleContext is no longer valid.
     */
    @Throws(
        IllegalStateException::class
    )
    override fun getBundle(): Bundle {
        return bundle
    }

    /**
     * See [BundleContext.getBundle].
     */
    override fun getBundle(id: Long): Bundle? {
        return framework.getBundle(id)
    }

    /**
     * See [BundleContext.getBundle].
     */
    override fun getBundle(location: String): Bundle? {
        return framework.getBundle(location)
    }

    /**
     * See [BundleContext.getBundles].
     */
    override fun getBundles(): Array<Bundle> {
        return framework.getBundles()
    }

    /**
     * See [BundleContext.installBundle].
     */
    override fun installBundle(location: String): Bundle {
        return installBundle(location, null)
    }

    /**
     * See [BundleContext.installBundle].
     *
     * The specified [location] identifier will be used as the identity of
     * the bundle. Every installed bundle is uniquely identified by its location
     * identifier which is typically in the form of a URL.
     *
     * @return The [Bundle] object of the installed bundle.
     * @throws BundleException If the installation failed.
     */
    @Throws(
        BundleException::class
    )
    override fun installBundle(location: String, input: InputStream?): Bundle =
        // If the specified `InputStream` is `null`, the Framework must
        // create the `InputStream` from which to read the bundle by
        // interpreting, in an implementation dependent manner, the specified `location`.

        // The following steps are required to install a bundle.
        // If a bundle containing the same location identifier is already
        // installed, the `Bundle` object for that bundle is returned.
        framework.getBundle(location) ?: framework.installBundle(location)
            .also {
                // The bundle's associated resources are allocated. The associated
                // resources minimally consist of a unique identifier and a persistent
                // storage area if the platform has file system support. If this step fails,
                // a `BundleException` is thrown.
                // The bundle's state is set to `Bundle.INSTALLED`.
                notifyToListeners(BundleEvent(BundleEvent.INSTALLED, bundle))
                // The `Bundle` object for the newly or previously installed bundle is returned.
            }
}
