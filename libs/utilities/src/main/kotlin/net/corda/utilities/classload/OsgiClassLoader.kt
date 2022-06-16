package net.corda.utilities.classload

import net.corda.utilities.EmptyEnumeration
import org.osgi.framework.Bundle
import java.net.URL
import java.util.*

/**
 * Allows combining multiple OSGi bundles under the same classloader. This might be useful in places where
 * `contextClassloader` is used and no single bundle can satisfy all the required dependencies.
 *
 * Inspired by `org.hibernate.osgi.OsgiClassLoader`, but is simpler and immutable.
 */
class OsgiClassLoader(private val bundles: List<Bundle>) : ClassLoader(null) {

    companion object {
        init {
            registerAsParallelCapable()
        }
    }

    override fun findClass(name: String): Class<*> {
        bundles.forEach { bundle ->
            bundle.loadClass(name)?.let { return it }
        }
        throw ClassNotFoundException("Could not load requested class : $name")
    }

    override fun findResource(name: String): URL? {
        bundles.forEach { bundle ->
            bundle.getResource(name)?.let { return it }
        }

        return null
    }

    override fun findResources(name: String): Enumeration<URL> {
        bundles.forEach { bundle ->
            bundle.getResources(name)?.let { return it }
        }

        return EmptyEnumeration()
    }
}