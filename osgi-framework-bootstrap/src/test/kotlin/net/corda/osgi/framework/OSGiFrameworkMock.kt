package net.corda.osgi.framework

import org.apache.sling.testing.mock.osgi.MockBundle
import org.apache.sling.testing.mock.osgi.MockOsgi
import org.osgi.framework.*
import org.osgi.framework.launch.Framework
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.Throws


class OSGiFrameworkMock(
    private val configurationMap: MutableMap<String, String>,
    private val transitionDelay: Long = 1,
    private val version: Version = Version(0, 0, 0, "mock")
) : Framework {

    private val bundleContextAtomic = AtomicReference<BundleContext>()

    private val bundleAtomic = AtomicReference<MockBundle>()

    private val frameworkListenersAtomic = AtomicReference(listOf<FrameworkListener>())

    private val stateAtomic = AtomicInteger(Bundle.INSTALLED)

    private val versionAtomic = AtomicReference(version)

    private val uuidAtomic = AtomicReference<UUID>()

    private fun setState(state: Int) {
        synchronized(stateAtomic) {
            stateAtomic.set(state)
            val frameworkEvent = FrameworkEvent(state, this, null)
            for (listener in frameworkListenersAtomic.get()) {
                listener.frameworkEvent(frameworkEvent)
            }
        }
    }

    // Framework

    override fun compareTo(other: Bundle): Int {
        val thisId = this.bundleId
        val thatId = other.bundleId
        return if (thisId < thatId) -1 else if (thatId == thatId) 0 else 1
    }

    override fun getState(): Int {
        return stateAtomic.get()
    }

    @Throws(
        InterruptedException::class
    )
    override fun start() {
        if (OSGiFrameworkWrap.isStartable(state)) {
            CompletableFuture.runAsync {
                Thread.sleep(transitionDelay)
                setState(Bundle.RESOLVED)
            }.thenRunAsync {
                Thread.sleep(transitionDelay)
                setState(Bundle.STARTING)
            }.thenRunAsync {
                Thread.sleep(transitionDelay)
                setState(Bundle.ACTIVE)
            }
        }
    }

    override fun start(ignored: Int) {
        start()
    }

    override fun stop() {
        if (OSGiFrameworkWrap.isStoppable(state)) {
            CompletableFuture.runAsync {
                Thread.sleep(transitionDelay)
                setState(Bundle.STOPPING)
            }.thenRunAsync {
                Thread.sleep(transitionDelay)
                setState(Bundle.UNINSTALLED)
            }
        }
    }

    override fun stop(ignored: Int) {
        stop()
    }

    override fun update() {
        bundleAtomic.get().update()
    }

    override fun update(`in`: InputStream?) {
        bundleAtomic.get().update(`in`)
    }

    override fun uninstall() {
        bundleAtomic.get().uninstall()
    }

    override fun getHeaders(): Dictionary<String, String> {
        return bundleAtomic.get().headers
    }

    override fun getHeaders(locale: String?): Dictionary<String, String> {
        return bundleAtomic.get().getHeaders(locale)
    }

    override fun getBundleId(): Long {
        return Constants.SYSTEM_BUNDLE_ID
    }

    override fun getLocation(): String {
        return Constants.SYSTEM_BUNDLE_LOCATION
    }

    override fun getRegisteredServices(): Array<ServiceReference<*>> {
        return bundleAtomic.get().registeredServices
    }

    override fun getServicesInUse(): Array<ServiceReference<*>> {
        return bundleAtomic.get().servicesInUse
    }

    override fun hasPermission(permission: Any?): Boolean {
        return bundleAtomic.get().hasPermission(permission)
    }

    override fun getResource(name: String?): URL {
        return bundleAtomic.get().getResource(name)
    }

    override fun getSymbolicName(): String {
        return Constants.SYSTEM_BUNDLE_SYMBOLICNAME
    }

    override fun loadClass(name: String?): Class<*> {
        return bundleAtomic.get().loadClass(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        return bundleAtomic.get().getResources(name)
    }

    override fun getEntryPaths(path: String?): Enumeration<String> {
        return bundleAtomic.get().getEntryPaths(path)
    }

    override fun getEntry(path: String?): URL {
        return bundleAtomic.get().getEntry(path)
    }

    override fun getLastModified(): Long {
        return bundleAtomic.get().lastModified
    }

    override fun findEntries(path: String?, filePattern: String?, recurse: Boolean): Enumeration<URL> {
        return bundleAtomic.get().findEntries(path, filePattern, recurse)
    }

    override fun getBundleContext(): BundleContext {
        return bundleAtomic.get().bundleContext
    }

    override fun getSignerCertificates(signersType: Int): MutableMap<X509Certificate, MutableList<X509Certificate>> {
        return bundleAtomic.get().getSignerCertificates(signersType)
    }

    override fun getVersion(): Version {
        return versionAtomic.get()
    }

    override fun <A : Any?> adapt(type: Class<A>): A {
        return bundleAtomic.get().adapt(type)
    }

    override fun getDataFile(filename: String): File {
        return bundleAtomic.get().getDataFile(filename)
    }

    override fun init() {
    }

    @Synchronized
    override fun init(vararg listeners: FrameworkListener) {
        uuidAtomic.set(UUID.randomUUID())
        stateAtomic.set(Bundle.STARTING)
        val bundleContext = MockOsgi.newBundleContext()
        bundleContextAtomic.set(bundleContext)
        bundleAtomic.set(MockBundle(bundleContext))

    }

    override fun waitForStop(timeout: Long): FrameworkEvent {
        return FrameworkEvent(FrameworkEvent.STOPPED, this, null)
    }
}