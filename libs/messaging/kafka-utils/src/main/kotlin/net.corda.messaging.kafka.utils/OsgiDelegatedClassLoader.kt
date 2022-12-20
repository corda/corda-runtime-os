package net.corda.messaging.kafka.utils

import org.osgi.framework.Bundle
import java.net.URL
import java.util.*

/**
 * The [OsgiDelegatedClassLoader] is used to delegate class loading back to a given OSGI bundle. This is required to
 * allow Kafka to dynamically load the login module defined in the JAAS configuration.
 */
class OsgiDelegatedClassLoader(val bundle: Bundle) : ClassLoader() {
    override fun findClass(name: String?): Class<*>? {
        return synchronized(getClassLoadingLock(name)) {
            try {
                bundle.loadClass(name)
            } catch (e: ClassNotFoundException) {
                getSystemClassLoader().loadClass(name)
            }
        }
    }

    override fun findResource(name: String?): URL? {
        return bundle.getResource(name)
    }

    override fun findResources(name: String?): Enumeration<URL>? {
        return bundle.getResources(name)
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        val cls = findClass(name)
        if (resolve) resolveClass(cls)
        return cls
    }

    override fun loadClass(name: String): Class<*>? {
        return loadClass(name, false)
    }
}
