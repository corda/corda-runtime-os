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

class OSGiBundleMock: Bundle {

    override fun compareTo(other: Bundle?): Int {
        TODO("Not yet implemented")
    }

    override fun getState(): Int {
        TODO("Not yet implemented")
    }

    override fun start(options: Int) {
        TODO("Not yet implemented")
    }

    override fun start() {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun getHeaders(locale: String?): Dictionary<String, String> {
        TODO("Not yet implemented")
    }

    override fun getBundleId(): Long {
        TODO("Not yet implemented")
    }

    override fun getLocation(): String {
        TODO("Not yet implemented")
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

    override fun getBundleContext(): BundleContext {
        TODO("Not yet implemented")
    }

    override fun getSignerCertificates(signersType: Int): MutableMap<X509Certificate, MutableList<X509Certificate>> {
        TODO("Not yet implemented")
    }

    override fun getVersion(): Version {
        TODO("Not yet implemented")
    }

    override fun <A : Any?> adapt(type: Class<A>?): A {
        TODO("Not yet implemented")
    }

    override fun getDataFile(filename: String?): File {
        TODO("Not yet implemented")
    }
}