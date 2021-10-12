package net.corda.osgi.framework

import org.mockito.Mockito.mock
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleException
import org.osgi.framework.Constants
import org.osgi.framework.FrameworkEvent
import org.osgi.framework.FrameworkListener
import org.osgi.framework.ServiceReference
import org.osgi.framework.Version
import org.osgi.framework.launch.Framework
import java.io.File
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.net.URL
import java.security.cert.X509Certificate
import java.util.Dictionary
import java.util.Enumeration
import java.util.Hashtable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.Throws

class OSGiFrameworkMock(
    private val configurationMap: MutableMap<String, String>,
    private val version: Version = Version(0, 0, 0, "mock"),
    private val beginningStartLevel: Int = SAFE_START_LEVEL
) : Framework {

    companion object {

        const val GO_LATCH = 0

        const val INIT_START_LEVEL = 0

        const val SAFE_START_LEVEL = 1

        const val WAIT_LATCH = 1
    } // ~ companion object

    private val bundleContextAtomic = AtomicReference<OSGiBundleContextMock>()

    private val bundleIdAccumulator = AtomicLong(Constants.SYSTEM_BUNDLE_ID)

    private val bundleMap = ConcurrentHashMap<Long, Bundle>()

    private val bundleLocationMap = ConcurrentHashMap<String, Bundle>()

    private val shutdownLatchAtomic = AtomicReference(CountDownLatch(GO_LATCH))

    private val stateAtomic = AtomicInteger(Bundle.INSTALLED)

    private val startLevelAtomic = AtomicInteger(INIT_START_LEVEL)

    private val versionAtomic = AtomicReference(version)

    /**
     * OSGi Core r7 4.2.8
     * Each framework must have a unique identity every time before the framework is started.
     *
     * @see [init]
     */
    private val uuidAtomic = AtomicReference<UUID>()

    init {
        bundleMap[Constants.SYSTEM_BUNDLE_ID] = this
        bundleLocationMap[Constants.SYSTEM_BUNDLE_LOCATION] = this
    }

    internal fun getBundle(location: String): Bundle? {
        return bundleLocationMap[location]
    }

    internal fun getBundle(id: Long): Bundle? {
        return bundleMap[id]
    }

    internal fun getBundles(): Array<Bundle> {
        return bundleMap.values.toTypedArray()
    }

    internal fun installBundle(location: String): OSGiBundleMock {
        val bundleId = bundleIdAccumulator.incrementAndGet()
        val bundle = OSGiBundleMock(bundleId, location)
        bundleMap[bundleId] = bundle
        bundleLocationMap[location] = bundle
        return bundle
    }

    // : Framework

    /**
     * See [Framework.getBundleId].
     *
     * @return 0.
     */
    override fun getBundleId(): Long {
        return Constants.SYSTEM_BUNDLE_ID
    }

    /**
     * See [Framework.getLocation].
     *
     * @return The string "System Bundle".
     */
    override fun getLocation(): String {
        return Constants.SYSTEM_BUNDLE_LOCATION
    }

    /**
     * See [Framework.getSymbolicName].
     *
     * @return The string "system.bundle".
     */
    override fun getSymbolicName(): String {
        return Constants.SYSTEM_BUNDLE_SYMBOLICNAME
    }

    /**
     * See [Framework.init].
     *
     * @throws BundleException if this Framework could not be initialized.
     */
    @Throws(
        BundleException::class
    )
    @Suppress("SpreadOperator")
    override fun init() {
        init(*arrayOf())
    }

    /**
     * See [Framework.init].
     *
     * This Framework will not actually be started until [start] is called,
     * but if [start] is called before this method, [start] calls [init].
     *
     * The method is effective if bundle state is [Bundle.INSTALLED] or [Bundle.RESOLVED] or [Bundle.UNINSTALLED].
     *
     * @param listeners Zero or more listeners to be notified when framework events occur
     * only while initializing the framework.
     *
     * @throws BundleException if this Framework could not be initialized.
     */
    @Throws(
        BundleException::class
    )
    override fun init(vararg listeners: FrameworkListener) {
        // Effective only when this Framework is in Bundle.INSTALLED or Bundle.RESOLVED or Bundle.UNINSTALLED.
        when (startLevelAtomic.get()) {
            // This method does nothing if called when this Framework is
            // in the `Bundle.STARTING`, `Bundle.ACTIVE` or `Bundle.STOPPING` states.
            Bundle.STARTING, Bundle.ACTIVE, Bundle.STOPPING -> {
                return
            }
        }
        // Be in the `Bundle.STARTING` state.
        stateAtomic.set(Bundle.STARTING)

        // After calling this method, this Framework must:
        // Have generated a new framework UUID.
        uuidAtomic.set(UUID.randomUUID())
        // Have a valid Bundle Context.
        bundleContextAtomic.set(OSGiBundleContextMock(this, this))

        // Be at start level 0.
        startLevelAtomic.set(INIT_START_LEVEL)
        // Have event handling enabled.

        // Have reified Bundle objects for all installed bundles.

        // Have registered any framework services.

        // Be adaptable to the OSGi defined types to which a system bundle can be adapted.

        // Have called the start method of the extension bundle activator for all resolved extension bundles.
        val frameworkEvent = FrameworkEvent(FrameworkEvent.STARTLEVEL_CHANGED, this, null)
        for (listener in listeners) {
            listener.frameworkEvent(frameworkEvent)
        }
    }

    /**
     * See [Framework.start].
     *
     * If [init] wasn't called before, call [init].
     *
     *  @throws BundleException if this [Framework] could not be started.
     *  @throws SecurityException if  the caller does not have the appropriate `AdminPermission[this,EXECUTE]`,
     * and the Java Runtime Environment supports permissions.
     */
    override fun start() {
        // If this Framework is not in the Bundle.STARTING state, initialize this Framework.
        if (stateAtomic.get() != Bundle.STARTING) {
            init()
        }

        // All installed bundles must be started in accordance with each bundle's persistent autostart setting.

        // The start level of this Framework is moved to the start level specified by the beginning start level
        // framework property, as described in the Start Level Specification.
        startLevelAtomic.set(beginningStartLevel)
        // This Framework's state is set to Bundle.ACTIVE.
        stateAtomic.set(Bundle.ACTIVE)

        // A bundle event of type BundleEvent.STARTED is fired.
        bundleContextAtomic.get().notifyToListeners(BundleEvent(BundleEvent.STARTED, this))
        // A framework event of type Framework.STARTED is fired.
    }

    /**
     * See [Framework.start].
     *
     * @param ignored There are no start options for the [Framework].
     * @throws BundleException If this Framework could not be started.
     * @throws SecurityException If the caller does not have the appropriate `AdminPermission[this,EXECUTE]`,
     * and the Java Runtime Environment supports permissions.
     * @see [start]
     */
    override fun start(ignored: Int) {
        start()
    }

    /**
     * See [Framework.stop].
     *
     * The method returns immediately to the caller
     *
     * @throws BundleException if stopping this Framework could not be initiated.
     * @throws SecurityException if the caller does not have the appropriate `AdminPermission[this,EXECUTE]`,
     * and the Java Runtime Environment supports permissions.
     */
    override fun stop() {
        shutdownLatchAtomic.set(CountDownLatch(WAIT_LATCH))
        Executors.newSingleThreadExecutor().run {
            // This Framework's state is set to Bundle.STOPPING.
            stateAtomic.set(Bundle.STOPPING)
            bundleContextAtomic.get().notifyToListeners(BundleEvent(BundleEvent.STOPPING, this@OSGiFrameworkMock))
            // All installed bundles must be stopped without changing each bundle's persistent autostart setting.
            // The start level of this Framework is moved to start level zero (0).
            startLevelAtomic.set(INIT_START_LEVEL)
            // Unregister all services registered by this Framework.

            // Event handling is disabled.

            // This Framework's state is set to Bundle.RESOLVED.
            stateAtomic.set(Bundle.RESOLVED)
            // All resources held by this Framework are released.
            bundleContextAtomic.set(null)
            shutdownLatchAtomic.get().countDown()
        }
    }

    /**
     * See [Framework.stop].
     *
     * @param ignored  There are no stop options for the [Framework].
     * @throws BundleException if stopping this Framework could not be initiated.
     * @throws SecurityException if the caller does not have the appropriate `AdminPermission[this,EXECUTE]`,
     * and the Java Runtime Environment supports permissions.
     */
    override fun stop(ignored: Int) {
        stop()
    }

    /**
     * See [Framework.waitForStop].
     *
     * @param timeout Maximum number of milliseconds to wait until this []Framework] has completely stopped.
     *                A value of zero will wait indefinitely.
     * @return A [FrameworkEvent] indicating the reason this method returned.
     * The following FrameworkEvent types may be returned by this method.
     * * [FrameworkEvent.STOPPED] This [Framework] has been stopped.
     * * [FrameworkEvent.WAIT_TIMEDOUT] This method timed out before this [Framework] has stopped.
     * @throws InterruptedException if another thread interrupted the current thread before or while the current thread
     * was waiting for this Framework to completely stop.
     * @throws IllegalArgumentException if the value of [timeout] is negative.
     */
    @Throws(
        InterruptedException::class,
        IllegalArgumentException::class
    )
    override fun waitForStop(timeout: Long): FrameworkEvent {
        return if (shutdownLatchAtomic.get().await(timeout, TimeUnit.MILLISECONDS)) {
            FrameworkEvent(FrameworkEvent.STOPPED, this, null)
        } else {
            FrameworkEvent(FrameworkEvent.WAIT_TIMEDOUT, this, TimeoutException("$timeout ms time-out"))
        }
    }

// : Bundle

    /**
     * See [Bundle.getBundleContext].
     *
     * @return the [BundleContext] for this bundle or `null` if this bundle has no valid [BundleContext].
     * If [init] or [start] wasn't called before, it returns `null`.
     * @throws [SecurityException] if the caller does not have the appropriate `AdminPermission[this,CONTEXT]`,
     * and the Java Runtime Environment supports permissions.
     */
    @Throws(
        SecurityException::class
    )
    override fun getBundleContext(): BundleContext? {
        return bundleContextAtomic.get()
    }

    /**
     * See [Bundle.getState].
     *
     * @return An element of [Bundle.UNINSTALLED], [Bundle.INSTALLED], [Bundle.RESOLVED], [Bundle.STARTING],
     * [Bundle.STOPPING], [Bundle.ACTIVE].
     */
    override fun getState(): Int {
        return stateAtomic.get()
    }

    /**
     * See [Bundle.getVersion].
     *
     * @return The [Version] of this bundle.
     */
    override fun getVersion(): Version {
        return versionAtomic.get()
    }

// : Comparable

    /**
     * See [Comparable.compareTo]
     *
     * @param other bundle to compare.
     * @return a negative integer, zero, or a positive integer as this object is
     * less than, equal to, or greater than the specified object.
     */
    override fun compareTo(other: Bundle): Int {
        val thisId = this.bundleId
        val thatId = other.bundleId
        return if (thisId < thatId) -1 else if (thisId == thatId) 0 else 1
    }

    override fun update() {
        TODO("Not yet implemented")
    }

    override fun update(`in`: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun uninstall() {
        TODO("Not yet implemented")
    }

    override fun getHeaders(): Dictionary<String, String> {
        return Hashtable()
    }

    override fun getHeaders(ignored: String?): Dictionary<String, String> {
        return Hashtable()
    }

    override fun getRegisteredServices(): Array<ServiceReference<*>> {
        TODO("Not yet implemented")
    }

    override fun getServicesInUse(): Array<ServiceReference<*>> {
        TODO("Not yet implemented")
    }

    override fun hasPermission(permission: Any?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getResource(name: String?): URL {
        TODO("Not yet implemented")
    }

    override fun loadClass(name: String?): Class<*> {
        TODO("Not yet implemented")
    }

    override fun getResources(name: String?): Enumeration<URL> {
        TODO("Not yet implemented")
    }

    override fun getEntryPaths(path: String?): Enumeration<String> {
        TODO("Not yet implemented")
    }

    override fun getEntry(path: String?): URL {
        TODO("Not yet implemented")
    }

    override fun getLastModified(): Long {
        TODO("Not yet implemented")
    }

    override fun findEntries(path: String?, filePattern: String?, recurse: Boolean): Enumeration<URL> {
        TODO("Not yet implemented")
    }

    override fun getSignerCertificates(signersType: Int): MutableMap<X509Certificate, MutableList<X509Certificate>> {
        TODO("Not yet implemented")
    }

    override fun <A : Any?> adapt(type: Class<A>): A {
        return mock(type)
    }

    override fun getDataFile(filename: String): File {
        TODO("Not yet implemented")
    }
}
