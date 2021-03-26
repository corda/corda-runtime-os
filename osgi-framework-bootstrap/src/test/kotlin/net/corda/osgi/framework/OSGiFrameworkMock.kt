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

    private val bundleContext = MockOsgi.newBundleContext()

    private val bundle = MockBundle(bundleContext)

    private val listenersAtomic = AtomicReference(listOf<FrameworkListener>())

    private val stateAtomic = AtomicInteger(Bundle.INSTALLED)

    private val versionAtomic = AtomicReference(version)

    private fun setState(state: Int) {
        synchronized(stateAtomic) {
            stateAtomic.set(state)
            val frameworkEvent = FrameworkEvent(state, this, null)
            for (listener in listenersAtomic.get()) {
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
        bundle.update()
    }

    override fun update(`in`: InputStream?) {
        bundle.update(`in`)
    }

    override fun uninstall() {
        bundle.uninstall()
    }

    override fun getHeaders(): Dictionary<String, String> {
        return bundle.headers
    }

    override fun getHeaders(locale: String?): Dictionary<String, String> {
        return bundle.getHeaders(locale)
    }

    override fun getBundleId(): Long {
        return Constants.SYSTEM_BUNDLE_ID
    }

    override fun getLocation(): String {
        return Constants.SYSTEM_BUNDLE_LOCATION
    }

    override fun getRegisteredServices(): Array<ServiceReference<*>> {
        return bundle.registeredServices
    }

    override fun getServicesInUse(): Array<ServiceReference<*>> {
        return bundle.servicesInUse
    }

    override fun hasPermission(permission: Any?): Boolean {
        return bundle.hasPermission(permission)
    }

    override fun getResource(name: String?): URL {
        return bundle.getResource(name)
    }

    override fun getSymbolicName(): String {
        return Constants.SYSTEM_BUNDLE_SYMBOLICNAME
    }

    override fun loadClass(name: String?): Class<*> {
        return bundle.loadClass(name)
    }

    override fun getResources(name: String?): Enumeration<URL> {
        return bundle.getResources(name)
    }

    override fun getEntryPaths(path: String?): Enumeration<String> {
        return bundle.getEntryPaths(path)
    }

    override fun getEntry(path: String?): URL {
        return bundle.getEntry(path)
    }

    override fun getLastModified(): Long {
        return bundle.lastModified
    }

    override fun findEntries(path: String?, filePattern: String?, recurse: Boolean): Enumeration<URL> {
        return bundle.findEntries(path, filePattern, recurse)
    }

    override fun getBundleContext(): BundleContext {
        return bundle.bundleContext
    }

    override fun getSignerCertificates(signersType: Int): MutableMap<X509Certificate, MutableList<X509Certificate>> {
        return bundle.getSignerCertificates(signersType)
    }

    override fun getVersion(): Version {
        return versionAtomic.get()
    }

    override fun <A : Any?> adapt(type: Class<A>): A {
        return bundle.adapt(type)
    }

    override fun getDataFile(filename: String): File {
        return bundle.getDataFile(filename)
    }

    override fun init() {
    }

    override fun init(vararg listeners: FrameworkListener) {
        listenersAtomic.set(listeners.toList())
    }

    override fun waitForStop(timeout: Long): FrameworkEvent {
        return FrameworkEvent(FrameworkEvent.STOPPED, this, null)
    }
}