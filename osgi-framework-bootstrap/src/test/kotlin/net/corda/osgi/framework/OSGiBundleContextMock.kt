package net.corda.osgi.framework

import org.osgi.framework.*
import java.io.File
import java.io.InputStream
import java.util.*

class OSGiBundleContextMock: BundleContext {

    override fun getBundle(): Bundle {
        TODO("Not yet implemented")
    }

    override fun getBundle(id: Long): Bundle {
        TODO("Not yet implemented")
    }

    override fun getBundle(location: String?): Bundle {
        TODO("Not yet implemented")
    }

    override fun getProperty(key: String?): String {
        TODO("Not yet implemented")
    }

    override fun installBundle(location: String?, input: InputStream?): Bundle {
        TODO("Not yet implemented")
    }

    override fun installBundle(location: String?): Bundle {
        TODO("Not yet implemented")
    }

    override fun getBundles(): Array<Bundle> {
        TODO("Not yet implemented")
    }

    override fun addServiceListener(listener: ServiceListener?, filter: String?) {
        TODO("Not yet implemented")
    }

    override fun addServiceListener(listener: ServiceListener?) {
        TODO("Not yet implemented")
    }

    override fun removeServiceListener(listener: ServiceListener?) {
        TODO("Not yet implemented")
    }

    override fun addBundleListener(listener: BundleListener?) {
        TODO("Not yet implemented")
    }

    override fun removeBundleListener(listener: BundleListener?) {
        TODO("Not yet implemented")
    }

    override fun addFrameworkListener(listener: FrameworkListener?) {
        TODO("Not yet implemented")
    }

    override fun removeFrameworkListener(listener: FrameworkListener?) {
        TODO("Not yet implemented")
    }

    override fun registerService(
        clazzes: Array<out String>?,
        service: Any?,
        properties: Dictionary<String, *>?
    ): ServiceRegistration<*> {
        TODO("Not yet implemented")
    }

    override fun registerService(
        clazz: String?,
        service: Any?,
        properties: Dictionary<String, *>?
    ): ServiceRegistration<*> {
        TODO("Not yet implemented")
    }

    override fun <S : Any?> registerService(
        clazz: Class<S>?,
        service: S,
        properties: Dictionary<String, *>?
    ): ServiceRegistration<S> {
        TODO("Not yet implemented")
    }

    override fun <S : Any?> registerService(
        clazz: Class<S>?,
        factory: ServiceFactory<S>?,
        properties: Dictionary<String, *>?
    ): ServiceRegistration<S> {
        TODO("Not yet implemented")
    }

    override fun getServiceReferences(clazz: String?, filter: String?): Array<ServiceReference<*>> {
        TODO("Not yet implemented")
    }

    override fun <S : Any?> getServiceReferences(
        clazz: Class<S>?,
        filter: String?
    ): MutableCollection<ServiceReference<S>> {
        TODO("Not yet implemented")
    }

    override fun getAllServiceReferences(clazz: String?, filter: String?): Array<ServiceReference<*>> {
        TODO("Not yet implemented")
    }

    override fun getServiceReference(clazz: String?): ServiceReference<*> {
        TODO("Not yet implemented")
    }

    override fun <S : Any?> getServiceReference(clazz: Class<S>?): ServiceReference<S> {
        TODO("Not yet implemented")
    }

    override fun <S : Any?> getService(reference: ServiceReference<S>?): S {
        TODO("Not yet implemented")
    }

    override fun ungetService(reference: ServiceReference<*>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun <S : Any?> getServiceObjects(reference: ServiceReference<S>?): ServiceObjects<S> {
        TODO("Not yet implemented")
    }

    override fun getDataFile(filename: String?): File {
        TODO("Not yet implemented")
    }

    override fun createFilter(filter: String?): Filter {
        TODO("Not yet implemented")
    }
}