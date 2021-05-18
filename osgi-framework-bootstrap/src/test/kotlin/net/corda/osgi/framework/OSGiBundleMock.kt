package net.corda.osgi.framework

import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.ServiceReference
import org.osgi.framework.Version
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

class OSGiBundleMock(
    private val id: Long,
    private val location: String
): Bundle {

    //: Bundle

    private val stateAtomic = AtomicInteger(Bundle.INSTALLED)

    /**
     * See [Bundle.getState].
     *
     * @return An element of [Bundle.UNINSTALLED], [Bundle.INSTALLED], [Bundle.RESOLVED], [Bundle.STARTING],
     *                       [Bundle.STOPPING], [Bundle.ACTIVE].
     */
    override fun getState(): Int {
        return stateAtomic.get()
    }

    //: Comparable

    /**
     * See [Comparable.compareTo]
     *
     * @param other bundle to compare.
     * @return a negative integer, zero, or a positive integer as this object is
     *         less than, equal to, or greater than the specified object.
     */
    override fun compareTo(other: Bundle): Int {
        val thisId = this.bundleId
        val thatId = other.bundleId
        return if (thisId < thatId) -1 else if (thisId == thatId) 0 else 1
    }


    override fun start(ignored: Int) {
        start()
    }

    override fun start() {
        stateAtomic.set(Bundle.ACTIVE)
    }

    override fun stop(options: Int) {
        TODO("Not yet implemented")
    }

    override fun stop() {
        TODO("Not yet implemented")
    }

    override fun update(input: InputStream?) {
        TODO("Not yet implemented")
    }

    override fun update() {
        TODO("Not yet implemented")
    }

    override fun uninstall() {
        TODO("Not yet implemented")
    }

    override fun getHeaders(): Dictionary<String, String> {
        return Hashtable()
    }

    override fun getHeaders(ignored: String?): Dictionary<String, String> {
        return getHeaders()
    }

    override fun getBundleId(): Long {
        return id
    }

    override fun getLocation(): String {
        return location
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

    override fun getSymbolicName(): String {
        return "mock-symbolic-name"
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

    override fun getBundleContext(): BundleContext {
        TODO("Not yet implemented")
    }

    override fun getSignerCertificates(signersType: Int): MutableMap<X509Certificate, MutableList<X509Certificate>> {
        TODO("Not yet implemented")
    }

    override fun getVersion(): Version {
        return Version(0, 0, 0, "mock")
    }

    override fun <A : Any?> adapt(type: Class<A>?): A {
        TODO("Not yet implemented")
    }

    override fun getDataFile(filename: String?): File {
        TODO("Not yet implemented")
    }
}