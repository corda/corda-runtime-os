package net.corda.osgi.framework

import org.apache.sling.testing.mock.osgi.MockOsgi
import org.osgi.framework.*
import java.io.File

import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.jvm.Throws

class OSGiBundleContextMock(
    private val framework: OSGiFrameworkMock,
    private val bundle: Bundle
) : BundleContext {

    /**
     * Temporary delegation to Apache Sling mock context.
     */
    private val slingContext = MockOsgi.newBundleContext()

    /**
     * Set of [BundleListener] to notify when bundle
     * Access to this property must be synchronized.
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
        synchronized(this.bundleListenerSet) {
            bundleListenerSet = this.bundleListenerSet.toSet()
        }
        bundleListenerSet.forEach { bundleListener ->
            bundleListener.bundleChanged(bundleEvent)
        }
    }

    //: BundleContext

    /**
     * See [BundleContext.addBundleListener].
     *
     * @param listener to be added.
     */
    override fun addBundleListener(listener: BundleListener) {
        slingContext.addBundleListener(listener)
    }

    /**
     * See [BundleContext.addBundleListener].
     */
    override fun addFrameworkListener(listener: FrameworkListener?) {
        slingContext.addFrameworkListener(listener)
    }


    /**
     * See [BundleContext.addBundleListener].
     */
    override fun addServiceListener(listener: ServiceListener?) {
        TODO("Not yet implemented")
    }

    /**
     * See [BundleContext.addServiceListener].
     */
    override fun addServiceListener(listener: ServiceListener?, filter: String?) {
        slingContext.addServiceListener(listener, filter)
    }

    /**
     * See [BundleContext.createFilter].
     */
    override fun createFilter(filter: String?): Filter {
        return slingContext.createFilter(filter)
    }

    /**
     * See [BundleContext.getAllServiceReferences].
     */
    override fun getAllServiceReferences(clazz: String?, filter: String?): Array<ServiceReference<*>> {
        return slingContext.getAllServiceReferences(clazz, filter)
    }

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
     * See [BundleContext.getDataFile]
     */
    override fun getDataFile(filename: String?): File {
        return slingContext.getDataFile(filename)
    }

    /**
     * No mock implementation, simulate that no property is found and return `null`.
     *
     * @return `null`.
     */
    override fun getProperty(key: String?): String {
        return slingContext.getProperty(key)
    }

    /**
     * See [BundleContext.getService].
     */
    override fun <S : Any?> getService(reference: ServiceReference<S>?): S {
        return slingContext.getService(reference)
    }

    /**
     * See [BundleContext.getServiceObjects].
     */
    override fun <S : Any?> getServiceObjects(reference: ServiceReference<S>?): ServiceObjects<S> {
        return slingContext.getServiceObjects(reference)
    }

    /**
     * See [BundleContext.getServiceReference].
     */
    override fun getServiceReference(clazz: String?): ServiceReference<*> {
        return slingContext.getServiceReference(clazz)
    }

    /**
     * See [BundleContext.getServiceReference].
     */
    override fun <S : Any?> getServiceReference(clazz: Class<S>?): ServiceReference<S> {
        return slingContext.getServiceReference(clazz)
    }

    /**
     * See [BundleContext.getServiceReferences]
     */
    override fun <S : Any?> getServiceReferences(clazz: Class<S>?, filter: String?
    ): MutableCollection<ServiceReference<S>> {
        return slingContext.getServiceReferences(clazz, filter)
    }

    /**
     * See [BundleContext.getServiceReferences].
     */
    override fun getServiceReferences(clazz: String?, filter: String?): Array<ServiceReference<*>> {
        return slingContext.getServiceReferences(clazz, filter)
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
    override fun installBundle(location: String, input: InputStream?): Bundle {
        // If the specified `InputStream` is `null`, the Framework must
        // create the `InputStream` from which to read the bundle by
        // interpreting, in an implementation dependent manner, the specified `location`.

        // The following steps are required to install a bundle.
        // If a bundle containing the same location identifier is already
        // installed, the {@code Bundle} object for that bundle is returned.
        var bundle = framework.getBundle(location)
        if (bundle == null) {
            // The bundle's associated resources are allocated. The associated
            // resources minimally consist of a unique identifier and a persistent
            // storage area if the platform has file system support. If this step fails,
            // a `BundleException` is thrown.
            // The bundle's state is set to {@code INSTALLED}.
            bundle = framework.installBundle(location)
            // A bundle event of type {@link BundleEvent#INSTALLED} is fired.</li>
            notifyToListeners(BundleEvent(BundleEvent.INSTALLED, bundle))
            // The `Bundle` object for the newly or previously installed bundle is returned.
        }
        return bundle
    }

    /**
     * See [BundleContext.registerService].
     */
    override fun registerService(
        clazzes: Array<out String>?,
        service: Any?,
        properties: Dictionary<String, *>?
    ): ServiceRegistration<*> {
        return slingContext.registerService(clazzes, service, properties)
    }

    /**
     * See [BundleContext.registerService].
     */
    override fun registerService(
        clazz: String?,
        service: Any?,
        properties: Dictionary<String, *>?
    ): ServiceRegistration<*> {
        return slingContext.registerService(clazz, service, properties)
    }

    /**
     * See [BundleContext.registerService].
     */
    override fun <S : Any?> registerService(
        clazz: Class<S>?,
        service: S,
        properties: Dictionary<String, *>?
    ): ServiceRegistration<S> {
        return slingContext.registerService(clazz, service, properties)
    }

    /**
     * See [BundleContext.registerService].
     */
    override fun <S : Any?> registerService(
        clazz: Class<S>?,
        factory: ServiceFactory<S>?,
        properties: Dictionary<String, *>?
    ): ServiceRegistration<S> {
        return slingContext.registerService(clazz, factory, properties)
    }

    /**
     * See [BundleContext.removeBundleListener].
     */
    override fun removeBundleListener(listener: BundleListener) {
        slingContext.removeBundleListener(listener)
    }

    /**
     * See [BundleContext.removeFrameworkListener].
     */
    override fun removeFrameworkListener(listener: FrameworkListener?) {
        slingContext.removeFrameworkListener(listener)
    }

    /**
     * See [BundleContext.removeServiceListener].
     */
    override fun removeServiceListener(listener: ServiceListener?) {
        slingContext.removeServiceListener(listener)
    }

    /**
     * See [BundleContext.ungetService].
     */
    override fun ungetService(reference: ServiceReference<*>?): Boolean {
        return slingContext.ungetService(reference)
    }
}